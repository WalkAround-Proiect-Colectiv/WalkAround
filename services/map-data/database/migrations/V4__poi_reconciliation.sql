ALTER TABLE places.poi_records
    ADD COLUMN category text CHECK (category IS NULL OR category IN (
        'food_drink', 'healthcare', 'shopping', 'transport', 'public_service', 'culture',
        'tourism', 'recreation', 'accommodation', 'education', 'utilities', 'other'
    )),
    ADD COLUMN subtype text,
    ADD COLUMN country_code text CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    ADD COLUMN address_line text,
    ADD COLUMN website text CHECK (website IS NULL OR website ~ '^https?://'),
    ADD COLUMN identifiers jsonb NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(identifiers) = 'object'
            AND NOT jsonb_path_exists(identifiers, '$.* ? (@.type() != "string" || @ == "")'));

CREATE TABLE places.poi_identifiers (
    namespace text NOT NULL CHECK (namespace ~ '^[a-z][a-z0-9_]*$'),
    value text NOT NULL CHECK (btrim(value) <> ''),
    poi_id uuid NOT NULL REFERENCES places.pois(id),
    PRIMARY KEY (namespace, value)
);
CREATE INDEX poi_identifiers_poi_idx ON places.poi_identifiers (poi_id);
CREATE INDEX pois_geography_idx ON places.pois USING gist ((geom::geography));
ALTER TABLE places.poi_links ADD COLUMN confirmed_by text, ADD COLUMN confirmed_at timestamptz;

CREATE FUNCTION places.normalized_name(value text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE
RETURN btrim(lower(regexp_replace(coalesce(value, ''), '[[:space:][:punct:]]+', ' ', 'g')));

CREATE FUNCTION places.reconcile_record(record_id uuid)
RETURNS TABLE (poi_id uuid, outcome text)
LANGUAGE plpgsql
AS $function$
DECLARE
    source places.poi_records%ROWTYPE;
    selected_id uuid;
    candidates uuid[];
    method text;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('walkaround-poi-reconciliation'));
    SELECT record.* INTO source FROM places.poi_records record
        JOIN places.providers provider ON provider.code = record.provider_code
        LEFT JOIN places.import_runs run ON run.id = record.last_seen_run_id
        WHERE record.id = record_id AND record.record_status = 'current'
            AND provider.approval_status = 'approved'
            AND provider.license_id = record.license_id
            AND (record.last_seen_run_id IS NULL OR run.status = 'succeeded')
        FOR UPDATE OF record;
    IF NOT FOUND THEN RETURN QUERY SELECT NULL::uuid, 'ineligible'::text; RETURN; END IF;

    SELECT link.poi_id INTO selected_id FROM places.poi_links link
        WHERE link.source_record_id = record_id AND link.match_status = 'confirmed';
    IF FOUND THEN RETURN QUERY SELECT selected_id, 'linked'::text; RETURN; END IF;
    IF source.category IS NULL THEN RETURN QUERY SELECT NULL::uuid, 'unclassified'::text; RETURN; END IF;

    SELECT array_agg(DISTINCT identity.poi_id) INTO candidates
        FROM jsonb_each_text(source.identifiers) supplied
        JOIN places.poi_identifiers identity ON identity.namespace = supplied.key AND identity.value = supplied.value;
    IF coalesce(cardinality(candidates), 0) > 1 THEN
        RETURN QUERY SELECT NULL::uuid, 'identifier_conflict'::text; RETURN;
    END IF;
    IF cardinality(candidates) = 1 THEN
        selected_id := candidates[1];
        IF NOT EXISTS (SELECT 1 FROM places.pois poi WHERE poi.id = selected_id
            AND poi.status = 'active' AND poi.category = source.category
            AND (poi.country_code IS NULL OR source.country_code IS NULL OR poi.country_code = source.country_code)
            AND ST_DWithin(poi.geom::geography, source.geom::geography, 250)) THEN
            RETURN QUERY SELECT NULL::uuid, 'identifier_conflict'::text; RETURN;
        END IF;
        method := 'shared_identifier';
    ELSE
        SELECT array_agg(candidate.id) INTO candidates FROM (
            SELECT poi.id FROM places.pois poi
            WHERE poi.status = 'active' AND poi.category = source.category
                AND poi.subtype IS NOT DISTINCT FROM source.subtype
                AND places.normalized_name(source.name) <> ''
                AND places.normalized_name(poi.name) = places.normalized_name(source.name)
                AND (poi.country_code IS NULL OR source.country_code IS NULL OR poi.country_code = source.country_code)
                AND ST_DWithin(poi.geom::geography, source.geom::geography, 50)
            ORDER BY poi.id LIMIT 2
        ) candidate;
        IF coalesce(cardinality(candidates), 0) > 1 THEN
            RETURN QUERY SELECT NULL::uuid, 'ambiguous'::text; RETURN;
        END IF;
        IF cardinality(candidates) = 1 THEN
            selected_id := candidates[1];
            INSERT INTO places.poi_links (source_record_id, poi_id, match_method, match_status, confidence)
            VALUES (record_id, selected_id, 'proximity_candidate', 'proposed', 0.5)
            ON CONFLICT (source_record_id) DO UPDATE SET poi_id = EXCLUDED.poi_id,
                match_method = EXCLUDED.match_method, match_status = EXCLUDED.match_status, confidence = EXCLUDED.confidence;
            RETURN QUERY SELECT selected_id, 'proposed'::text; RETURN;
        END IF;
        INSERT INTO places.pois (name, category, subtype, geom, country_code, address_line, website)
        VALUES (source.name, source.category, source.subtype, source.geom, source.country_code, source.address_line, source.website)
        RETURNING id INTO selected_id;
        method := 'manual';
    END IF;

    INSERT INTO places.poi_links (source_record_id, poi_id, match_method, match_status, confidence, contributed_fields)
    VALUES (record_id, selected_id, method, 'confirmed', 1,
        CASE WHEN method = 'manual' THEN ARRAY['name', 'category', 'subtype', 'geom', 'country_code', 'address_line', 'website'] ELSE '{}'::text[] END)
    ON CONFLICT (source_record_id) DO UPDATE SET poi_id = EXCLUDED.poi_id,
        match_method = EXCLUDED.match_method, match_status = EXCLUDED.match_status,
        confidence = EXCLUDED.confidence, contributed_fields = EXCLUDED.contributed_fields;
    INSERT INTO places.poi_identifiers (namespace, value, poi_id)
        SELECT supplied.key, supplied.value, selected_id FROM jsonb_each_text(source.identifiers) supplied
        ON CONFLICT (namespace, value) DO NOTHING;
    RETURN QUERY SELECT selected_id, CASE WHEN method = 'manual' THEN 'created' ELSE 'matched' END;
END;
$function$;

REVOKE ALL ON FUNCTION places.normalized_name(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION places.reconcile_record(uuid) FROM PUBLIC;
REVOKE ALL ON places.poi_identifiers FROM PUBLIC;

CREATE FUNCTION places.confirm_match(record_id uuid, target_id uuid, reviewer text) RETURNS uuid
LANGUAGE plpgsql AS $function$
DECLARE
    source places.poi_records%ROWTYPE;
BEGIN
    IF reviewer IS NULL OR btrim(reviewer) = '' THEN RAISE EXCEPTION 'A reviewer is required'; END IF;
    PERFORM pg_advisory_xact_lock(hashtext('walkaround-poi-reconciliation'));
    SELECT record.* INTO source FROM places.poi_records record
        JOIN places.providers provider ON provider.code = record.provider_code
        LEFT JOIN places.import_runs run ON run.id = record.last_seen_run_id
        WHERE record.id = record_id AND record.record_status = 'current'
            AND provider.approval_status = 'approved' AND provider.license_id = record.license_id
            AND (record.last_seen_run_id IS NULL OR run.status = 'succeeded')
        FOR UPDATE OF record;
    IF NOT FOUND THEN RAISE EXCEPTION 'Source record is not eligible'; END IF;
    IF NOT EXISTS (SELECT 1 FROM places.pois poi WHERE poi.id = target_id AND poi.status = 'active'
        AND poi.category = source.category
        AND (poi.country_code IS NULL OR source.country_code IS NULL OR poi.country_code = source.country_code)
        AND ST_DWithin(poi.geom::geography, source.geom::geography, 250)) THEN
        RAISE EXCEPTION 'Target is incompatible with the source record';
    END IF;
    IF EXISTS (SELECT 1 FROM places.poi_links link WHERE link.source_record_id = record_id
        AND link.match_status = 'confirmed' AND link.poi_id <> target_id) THEN
        RAISE EXCEPTION 'Existing confirmed link cannot be reassigned';
    END IF;
    IF EXISTS (SELECT 1 FROM jsonb_each_text(source.identifiers) supplied
        JOIN places.poi_identifiers identity ON identity.namespace = supplied.key AND identity.value = supplied.value
        WHERE identity.poi_id <> target_id) THEN
        RAISE EXCEPTION 'Shared identifier belongs to a different canonical POI';
    END IF;
    INSERT INTO places.poi_links (source_record_id, poi_id, match_method, match_status, confidence, confirmed_by, confirmed_at)
    VALUES (record_id, target_id, 'manual', 'confirmed', 1, reviewer, now())
    ON CONFLICT (source_record_id) DO UPDATE SET poi_id = EXCLUDED.poi_id, match_method = 'manual',
        match_status = 'confirmed', confidence = 1, confirmed_by = EXCLUDED.confirmed_by, confirmed_at = EXCLUDED.confirmed_at;
    INSERT INTO places.poi_identifiers (namespace, value, poi_id)
        SELECT supplied.key, supplied.value, target_id FROM jsonb_each_text(source.identifiers) supplied
        ON CONFLICT (namespace, value) DO NOTHING;
    RETURN target_id;
END;
$function$;
REVOKE ALL ON FUNCTION places.confirm_match(uuid, uuid, text) FROM PUBLIC;