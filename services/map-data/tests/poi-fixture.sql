INSERT INTO places.providers (code, name, documentation_url, license_id, license_url, attribution, approval_status, reviewed_at, reviewed_by)
VALUES ('synthetic_poi', 'Synthetic POI fixture', 'https://example.test/docs', 'CC0-1.0', 'https://example.test/license',
    'Synthetic test data', 'approved', now(), 'smoke-test');

INSERT INTO places.poi_records (provider_code, external_id, name, geom, category, subtype, country_code, license_id, attribution, identifiers)
VALUES ('synthetic_poi', 'primary', 'Fixture cafe', ST_SetSRID(ST_MakePoint(26.1026, 44.4265), 4326),
    'food_drink', 'cafe', 'RO', 'CC0-1.0', 'Synthetic test data', '{"osm":"node/10","fixture":"cafe"}'),
    ('synthetic_poi', 'duplicate', 'Duplicate source cafe', ST_SetSRID(ST_MakePoint(26.1026, 44.4265), 4326),
    'food_drink', 'cafe', 'RO', 'CC0-1.0', 'Synthetic test data', '{"fixture":"cafe"}');
SELECT * FROM places.reconcile_record((SELECT id FROM places.poi_records WHERE provider_code = 'synthetic_poi' AND external_id = 'primary'));
SELECT * FROM places.reconcile_record((SELECT id FROM places.poi_records WHERE provider_code = 'synthetic_poi' AND external_id = 'duplicate'));