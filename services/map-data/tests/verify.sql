DO $test$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM osm.features) THEN RAISE EXCEPTION 'No imported features'; END IF;
    IF EXISTS (SELECT 1 FROM osm.features WHERE ST_SRID(geom) <> 3857 OR NOT ST_IsValid(geom)) THEN
        RAISE EXCEPTION 'Invalid geometry or unexpected projection';
    END IF;
    IF has_schema_privilege('map_tiles', 'osm_middle', 'USAGE') THEN RAISE EXCEPTION 'Tile role can access raw OSM state'; END IF;
    IF has_table_privilege('map_tiles', 'osm.features', 'INSERT') THEN RAISE EXCEPTION 'Tile role can write map data'; END IF;
    IF NOT has_table_privilege('map_tiles', 'osm.features', 'SELECT') THEN RAISE EXCEPTION 'Tile role cannot read map data'; END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname IN ('map_import', 'map_tiles') AND (rolsuper OR rolcreatedb OR rolcreaterole)) THEN
        RAISE EXCEPTION 'Map runtime roles have excessive privileges';
    END IF;
    IF octet_length(tiles.basemap(0, 0, 0)) <> 0 OR octet_length(tiles.basemap(15, -1, 0)) <> 0 THEN
        RAISE EXCEPTION 'Invalid tile coordinates were not rejected';
    END IF;
END;
$test$;
SELECT kind, count(*) FROM osm.features GROUP BY kind ORDER BY kind;