CREATE INDEX IF NOT EXISTS features_minzoom_idx ON osm.features (minzoom);

CREATE FUNCTION tiles.basemap(z integer, x integer, y integer)
RETURNS bytea
LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE
AS $function$
DECLARE
    bounds geometry;
    buffered geometry;
    result bytea;
BEGIN
    IF z < 4 OR z > 15 OR x < 0 OR y < 0 OR x >= (1 << z) OR y >= (1 << z) THEN
        RETURN ''::bytea;
    END IF;
    bounds := ST_TileEnvelope(z, x, y);
    buffered := ST_TileEnvelope(z, x, y, margin => 64.0 / 4096);
    SELECT ST_AsMVT(tile, 'features', 4096, 'geom') INTO result
    FROM (
        SELECT kind, class, name,
            ST_AsMVTGeom(geom, bounds, 4096, 64, true) AS geom
        FROM osm.features
        WHERE geom && buffered AND minzoom <= z
        ORDER BY kind, osm_type, osm_id, class
    ) AS tile
    WHERE tile.geom IS NOT NULL;
    RETURN COALESCE(result, ''::bytea);
END;
$function$;

REVOKE ALL ON FUNCTION tiles.basemap(integer, integer, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION tiles.basemap(integer, integer, integer) TO map_tiles;

COMMENT ON FUNCTION tiles.basemap(integer, integer, integer) IS $json$
{
  "description": "WalkAround OSM basemap v1",
  "attribution": "<a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>",
  "vector_layers": [{"id": "features", "fields": {"kind": "String", "class": "String", "name": "String"}}]
}
$json$;