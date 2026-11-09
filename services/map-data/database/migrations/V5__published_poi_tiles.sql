CREATE VIEW places.published_pois WITH (security_barrier = true) AS
SELECT poi.id, poi.name, poi.category, poi.subtype, poi.geom, poi.importance, poi.min_zoom,
    string_agg(DISTINCT provider.name, ', ' ORDER BY provider.name) AS sources,
    string_agg(DISTINCT record.attribution, '; ' ORDER BY record.attribution) AS attribution,
    string_agg(DISTINCT record.license_id, ', ' ORDER BY record.license_id) AS licenses
FROM places.pois poi
JOIN places.poi_links link ON link.poi_id = poi.id AND link.match_status = 'confirmed'
JOIN places.poi_records record ON record.id = link.source_record_id AND record.record_status = 'current'
JOIN places.providers provider ON provider.code = record.provider_code AND provider.approval_status = 'approved'
LEFT JOIN places.import_runs run ON run.id = record.last_seen_run_id
WHERE poi.status = 'active' AND record.license_id = provider.license_id
    AND (record.last_seen_run_id IS NULL OR run.status = 'succeeded')
GROUP BY poi.id
HAVING bool_or(ARRAY['name', 'category', 'geom']::text[] <@ link.contributed_fields);

CREATE VIEW places.published_osm_references WITH (security_barrier = true) AS
SELECT identity.poi_id, identity.value
FROM places.poi_identifiers identity
JOIN places.published_pois poi ON poi.id = identity.poi_id
WHERE identity.namespace = 'osm';

CREATE FUNCTION places.category_for_osm(value text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE
RETURN CASE
    WHEN value IN ('restaurant', 'cafe', 'bar', 'pub', 'fast_food', 'bakery', 'food_court') THEN 'food_drink'
    WHEN value IN ('pharmacy', 'hospital', 'clinic', 'doctors', 'dentist') THEN 'healthcare'
    WHEN value IN ('supermarket', 'convenience', 'mall', 'clothes', 'books') THEN 'shopping'
    WHEN value IN ('bus_station', 'parking', 'bicycle_parking', 'fuel', 'charging_station') THEN 'transport'
    WHEN value IN ('police', 'fire_station', 'post_office', 'townhall', 'bank', 'atm') THEN 'public_service'
    WHEN value IN ('museum', 'theatre', 'arts_centre', 'library', 'cinema') THEN 'culture'
    WHEN value IN ('attraction', 'viewpoint', 'information', 'artwork') THEN 'tourism'
    WHEN value IN ('park', 'garden', 'playground', 'sports_centre') THEN 'recreation'
    WHEN value IN ('hotel', 'hostel', 'guest_house', 'motel', 'camp_site') THEN 'accommodation'
    WHEN value IN ('school', 'university', 'kindergarten', 'college') THEN 'education'
    WHEN value IN ('toilets', 'drinking_water', 'recycling', 'bench') THEN 'utilities'
    ELSE 'other'
END;

CREATE FUNCTION tiles.pois(z integer, x integer, y integer) RETURNS bytea
LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE
AS $function$
DECLARE
    bounds geometry;
    geographic_bounds geometry;
    mercator_bounds geometry;
    result bytea;
BEGIN
    IF z < 4 OR z > 18 OR x < 0 OR y < 0 OR x >= (1 << z) OR y >= (1 << z) THEN
        RETURN ''::bytea;
    END IF;
    bounds := ST_TileEnvelope(z, x, y);
    mercator_bounds := ST_TileEnvelope(z, x, y, margin => 64.0 / 4096);
    geographic_bounds := ST_Transform(mercator_bounds, 4326);
    WITH canonical AS (
        SELECT poi.id::text AS poi_id, coalesce(poi.name, initcap(replace(poi.category, '_', ' '))) AS name,
            poi.category, poi.subtype, poi.sources, poi.attribution, poi.licenses,
            poi.importance, ST_Transform(poi.geom, 3857) AS geom
        FROM places.published_pois poi
        WHERE poi.geom && geographic_bounds AND poi.min_zoom <= z
    ), baseline AS (
        SELECT 'osm:' || lower(feature.osm_type) || '/' || feature.osm_id AS poi_id,
            coalesce(feature.name, initcap(replace(feature.class, '_', ' '))) AS name,
            places.category_for_osm(feature.class) AS category, feature.class AS subtype,
            'OpenStreetMap'::text AS sources, 'OpenStreetMap contributors'::text AS attribution,
            'ODbL-1.0'::text AS licenses, 0::smallint AS importance, feature.geom
        FROM osm.features feature
        WHERE feature.kind = 'poi' AND feature.geom && mercator_bounds AND feature.minzoom <= z
            AND NOT EXISTS (SELECT 1 FROM places.published_osm_references reference
                WHERE reference.value = CASE feature.osm_type
                    WHEN 'N' THEN 'node/' WHEN 'W' THEN 'way/' WHEN 'R' THEN 'relation/' END || feature.osm_id)
    ), selected AS (
        SELECT * FROM canonical UNION ALL SELECT * FROM baseline
    ), limited AS (
        SELECT * FROM selected ORDER BY importance DESC, poi_id LIMIT 500
    )
    SELECT ST_AsMVT(tile, 'pois', 4096, 'geom') INTO result FROM (
        SELECT poi_id, name, category, subtype, sources, attribution, licenses, importance,
            ST_AsMVTGeom(geom, bounds, 4096, 64, true) AS geom FROM limited
    ) tile WHERE tile.geom IS NOT NULL;
    RETURN coalesce(result, ''::bytea);
END;
$function$;

REVOKE ALL ON places.published_pois, places.published_osm_references FROM PUBLIC;
GRANT SELECT ON places.published_pois, places.published_osm_references TO map_tiles;
REVOKE ALL ON FUNCTION places.category_for_osm(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION places.category_for_osm(text) TO map_tiles;
REVOKE ALL ON FUNCTION tiles.pois(integer, integer, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION tiles.pois(integer, integer, integer) TO map_tiles;

COMMENT ON FUNCTION tiles.pois(integer, integer, integer) IS $json$
{
  "description": "Reviewed canonical POIs with unmatched OSM fallback",
  "attribution": "<a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a> | <a href=\"https://www.wikidata.org/wiki/Wikidata:Licensing\">Wikidata</a> | <a href=\"https://www.geonames.org/\">GeoNames</a>",
  "vector_layers": [{"id": "pois", "fields": {"poi_id": "String", "name": "String", "category": "String", "subtype": "String", "sources": "String", "attribution": "String", "licenses": "String", "importance": "Number"}}]
}
$json$;