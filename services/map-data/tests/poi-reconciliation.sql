BEGIN;
DO $test$
DECLARE
    provider_a text := 'test_a_' || replace(gen_random_uuid()::text, '-', '');
    provider_b text := 'test_b_' || replace(gen_random_uuid()::text, '-', '');
    identity_value text := gen_random_uuid()::text;
    point geometry := ST_SetSRID(ST_MakePoint(26.1026, 44.4265), 4326);
    first_record uuid;
    shared_record uuid;
    nearby_record uuid;
    conflict_record uuid;
    canonical_id uuid;
    other_id uuid;
    matched_id uuid;
    result text;
    count_before bigint;
    rejected boolean := false;
BEGIN
    INSERT INTO places.providers (code, name, documentation_url, license_id, license_url, attribution,
        approval_status, reviewed_at, reviewed_by)
    VALUES (provider_a, 'Synthetic A', 'https://example.test/a', 'CC0-1.0', 'https://example.test/license', 'Synthetic A', 'approved', now(), 'test'),
        (provider_b, 'Synthetic B', 'https://example.test/b', 'CC0-1.0', 'https://example.test/license', 'Synthetic B', 'approved', now(), 'test');
    INSERT INTO places.poi_records (provider_code, external_id, name, geom, category, subtype, country_code, license_id, attribution, identifiers)
    VALUES (provider_a, 'first', 'Synthetic Cafe', point, 'food_drink', 'cafe', 'RO', 'CC0-1.0', 'Synthetic A', jsonb_build_object('wikidata', identity_value))
    RETURNING id INTO first_record;
    SELECT count(*) INTO count_before FROM places.pois;
    SELECT match.poi_id, match.outcome INTO canonical_id, result FROM places.reconcile_record(first_record) match;
    IF result <> 'created' OR canonical_id IS NULL THEN RAISE EXCEPTION 'New record was not canonicalized'; END IF;
    SELECT match.outcome INTO result FROM places.reconcile_record(first_record) match;
    IF result <> 'linked' OR (SELECT count(*) FROM places.pois) <> count_before + 1 THEN
        RAISE EXCEPTION 'Repeated reconciliation created duplicates';
    END IF;

    INSERT INTO places.poi_records (provider_code, external_id, name, geom, category, subtype, country_code, license_id, attribution, identifiers)
    VALUES (provider_b, 'shared', 'Different provider name', point, 'food_drink', 'cafe', 'RO', 'CC0-1.0', 'Synthetic B', jsonb_build_object('wikidata', identity_value))
    RETURNING id INTO shared_record;
    SELECT match.poi_id, match.outcome INTO matched_id, result FROM places.reconcile_record(shared_record) match;
    IF result <> 'matched' OR matched_id <> canonical_id THEN RAISE EXCEPTION 'Shared identifier did not match'; END IF;
    IF (SELECT name FROM places.pois WHERE id = canonical_id) <> 'Synthetic Cafe' THEN RAISE EXCEPTION 'Canonical fields were overwritten'; END IF;

    INSERT INTO places.poi_records (provider_code, external_id, name, geom, category, subtype, country_code, license_id, attribution)
    VALUES (provider_b, 'nearby', '  SYNTHETIC Cafe! ', point, 'food_drink', 'cafe', 'RO', 'CC0-1.0', 'Synthetic B')
    RETURNING id INTO nearby_record;
    SELECT match.outcome INTO result FROM places.reconcile_record(nearby_record) match;
    IF result <> 'proposed' OR NOT EXISTS (SELECT 1 FROM places.poi_links WHERE source_record_id = nearby_record AND match_status = 'proposed') THEN
        RAISE EXCEPTION 'Proximity-only match was not held for review';
    END IF;
    BEGIN
        PERFORM places.confirm_match(nearby_record, canonical_id, '');
    EXCEPTION WHEN raise_exception THEN rejected := true;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Blank reviewer was accepted'; END IF;
    PERFORM places.confirm_match(nearby_record, canonical_id, 'synthetic-reviewer');
    IF NOT EXISTS (SELECT 1 FROM places.poi_links WHERE source_record_id = nearby_record
        AND match_status = 'confirmed' AND confirmed_by = 'synthetic-reviewer') THEN
        RAISE EXCEPTION 'Explicit review was not recorded';
    END IF;

    INSERT INTO places.pois (name, category, subtype, geom) VALUES ('Other canonical', 'food_drink', 'cafe', point) RETURNING id INTO other_id;
    INSERT INTO places.poi_identifiers (namespace, value, poi_id) VALUES ('other', identity_value, other_id);
    INSERT INTO places.poi_records (provider_code, external_id, name, geom, category, license_id, attribution, identifiers)
    VALUES (provider_a, 'conflicting', 'Conflicting', point, 'food_drink', 'CC0-1.0', 'Synthetic A',
        jsonb_build_object('wikidata', identity_value, 'other', identity_value)) RETURNING id INTO conflict_record;
    SELECT match.outcome INTO result FROM places.reconcile_record(conflict_record) match;
    IF result <> 'identifier_conflict' OR EXISTS (SELECT 1 FROM places.poi_links WHERE source_record_id = conflict_record) THEN
        RAISE EXCEPTION 'Conflicting identifiers were linked';
    END IF;

    INSERT INTO places.pois (name, category, subtype, geom) VALUES ('Synthetic Cafe', 'food_drink', 'cafe', point);
    INSERT INTO places.poi_records (provider_code, external_id, name, geom, category, subtype, license_id, attribution)
    VALUES (provider_a, 'ambiguous', 'Synthetic Cafe', point, 'food_drink', 'cafe', 'CC0-1.0', 'Synthetic A')
    RETURNING id INTO conflict_record;
    SELECT match.outcome INTO result FROM places.reconcile_record(conflict_record) match;
    IF result <> 'ambiguous' OR EXISTS (SELECT 1 FROM places.poi_links WHERE source_record_id = conflict_record) THEN
        RAISE EXCEPTION 'Ambiguous nearby matches were linked';
    END IF;
    INSERT INTO places.poi_records (provider_code, external_id, name, geom, category, license_id, attribution, identifiers)
    VALUES (provider_a, 'far-away', 'Synthetic Cafe', ST_SetSRID(ST_MakePoint(27, 45), 4326), 'food_drink', 'CC0-1.0', 'Synthetic A',
        jsonb_build_object('wikidata', identity_value)) RETURNING id INTO conflict_record;
    SELECT match.outcome INTO result FROM places.reconcile_record(conflict_record) match;
    IF result <> 'identifier_conflict' THEN RAISE EXCEPTION 'Far-away shared identity was accepted'; END IF;

    IF NOT EXISTS (SELECT 1 FROM places.published_pois WHERE id = canonical_id AND sources LIKE '%Synthetic A%' AND sources LIKE '%Synthetic B%') THEN
        RAISE EXCEPTION 'Confirmed source attribution is missing';
    END IF;
    UPDATE places.providers SET approval_status = 'rejected' WHERE code = provider_a;
    IF EXISTS (SELECT 1 FROM places.published_pois WHERE id = canonical_id) THEN
        RAISE EXCEPTION 'Identity-only corroboration republished rejected-source display fields';
    END IF;
    IF EXISTS (SELECT 1 FROM places.published_pois WHERE id = canonical_id AND sources LIKE '%Synthetic A%') THEN
        RAISE EXCEPTION 'Rejected source was published';
    END IF;
    SELECT match.outcome INTO result FROM places.reconcile_record(first_record) match;
    IF result <> 'ineligible' THEN RAISE EXCEPTION 'Rejected source remained eligible'; END IF;
    UPDATE places.poi_records SET record_status = 'withdrawn' WHERE provider_code = provider_b;
    IF EXISTS (SELECT 1 FROM places.published_pois WHERE id = canonical_id) THEN RAISE EXCEPTION 'POI without publishable sources remained visible'; END IF;
    IF has_function_privilege('map_tiles', 'places.reconcile_record(uuid)', 'EXECUTE')
        OR has_function_privilege('map_tiles', 'places.confirm_match(uuid,uuid,text)', 'EXECUTE') THEN
        RAISE EXCEPTION 'Map role can mutate deduplication';
    END IF;
    IF octet_length(tiles.pois(0, 0, 0)) <> 0 OR octet_length(tiles.pois(15, -1, 0)) <> 0 THEN
        RAISE EXCEPTION 'POI tile bounds were not validated';
    END IF;
END;
$test$;
ROLLBACK;