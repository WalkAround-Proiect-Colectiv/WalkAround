BEGIN;
DO $test$
DECLARE
    provider text := 'test_poi_' || replace(gen_random_uuid()::text, '-', '');
    point geometry := ST_SetSRID(ST_MakePoint(26.1025, 44.4268), 4326);
    poi_id uuid;
    record_id uuid;
    run_id uuid;
BEGIN
    IF (SELECT count(*) FROM places.providers WHERE code IN ('osm', 'wikidata', 'geonames')) <> 3 THEN
        RAISE EXCEPTION 'Missing POI source candidates';
    END IF;
    IF NOT has_table_privilege('map_tiles', 'places.pois', 'SELECT')
        OR NOT has_column_privilege('map_tiles', 'places.providers', 'attribution', 'SELECT') THEN
        RAISE EXCEPTION 'Map role cannot read POI display/source metadata';
    END IF;
    IF has_table_privilege('map_tiles', 'places.pois', 'INSERT')
        OR has_column_privilege('map_tiles', 'places.providers', 'reviewed_by', 'SELECT')
        OR has_column_privilege('map_tiles', 'places.providers', 'review_notes', 'SELECT')
        OR has_table_privilege('map_tiles', 'places.poi_records', 'SELECT')
        OR has_table_privilege('map_tiles', 'places.poi_links', 'SELECT')
        OR has_table_privilege('map_tiles', 'places.import_runs', 'SELECT') THEN
        RAISE EXCEPTION 'Map role has excessive POI permissions';
    END IF;

    INSERT INTO places.providers (code, name, documentation_url, license_id, license_url, attribution)
    VALUES (provider, 'Synthetic source', 'https://example.test/docs', 'CC0-1.0', 'https://example.test/license', 'Synthetic test data');
    IF (SELECT approval_status FROM places.providers WHERE code = provider) <> 'pending_review' THEN
        RAISE EXCEPTION 'New sources must start pending review';
    END IF;
    BEGIN
        UPDATE places.providers SET approval_status = 'approved' WHERE code = provider;
        RAISE EXCEPTION 'Unreviewed source approval was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE places.providers SET approval_status = 'approved', reviewed_at = now(), reviewed_by = 'schema-test',
            license_id = 'CC-BY-version-to-review' WHERE code = provider;
        RAISE EXCEPTION 'Source with unresolved license was approved';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    UPDATE places.providers SET approval_status = 'approved', reviewed_at = now(), reviewed_by = 'schema-test' WHERE code = provider;
    INSERT INTO places.pois (name, category, geom, country_code)
    VALUES ('Synthetic POI', 'healthcare', point, 'RO') RETURNING id INTO poi_id;
    INSERT INTO places.import_runs (provider_code, triggered_by)
    VALUES (provider, 'schema-test') RETURNING id INTO run_id;
    INSERT INTO places.poi_records (provider_code, external_id, name, geom, license_id, attribution, last_seen_run_id)
    VALUES (provider, 'fixture-1', 'Synthetic provider POI', point, 'CC0-1.0', 'Synthetic test data', run_id)
    RETURNING id INTO record_id;
    INSERT INTO places.poi_links (source_record_id, poi_id, match_method, match_status, confidence, contributed_fields)
    VALUES (record_id, poi_id, 'manual', 'confirmed', 1, ARRAY['name', 'geom']);

    BEGIN
        INSERT INTO places.poi_records (provider_code, external_id, geom, license_id, attribution)
        VALUES (provider, 'fixture-1', point, 'CC0-1.0', 'Synthetic test data');
        RAISE EXCEPTION 'Duplicate provider identity was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO places.pois (category, geom) VALUES ('other', ST_SetSRID(ST_MakePoint(181, 44), 4326));
        RAISE EXCEPTION 'Invalid longitude was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO places.pois (category, geom) VALUES ('other', ST_GeomFromText('POINT EMPTY', 4326));
        RAISE EXCEPTION 'Empty geometry was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO places.pois (category, geom) VALUES ('unmapped-category', point);
        RAISE EXCEPTION 'Unknown normalized category was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO places.import_runs (provider_code, trigger_mode, triggered_by)
        VALUES (provider, 'scheduled', 'schema-test');
        RAISE EXCEPTION 'Scheduled POI run was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE places.import_runs SET status = 'succeeded' WHERE id = run_id;
        RAISE EXCEPTION 'Completed run without a finish timestamp was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO places.poi_records (provider_code, external_id, geom, license_id, attribution, last_seen_run_id)
        VALUES ('wikidata', 'fixture-wrong-run', point, 'CC0-1.0', 'Synthetic test data', run_id);
        RAISE EXCEPTION 'Cross-provider run provenance was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        UPDATE places.poi_links SET confidence = 1.5 WHERE source_record_id = record_id;
        RAISE EXCEPTION 'Invalid match confidence was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        DELETE FROM places.pois WHERE id = poi_id;
        RAISE EXCEPTION 'Canonical POI deletion orphaned a source link';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    UPDATE places.import_runs SET status = 'succeeded', finished_at = now(), records_seen = 1, records_written = 1 WHERE id = run_id;
END;
$test$;
ROLLBACK;