CREATE INDEX IF NOT EXISTS features_poi_geom_idx
    ON osm.features USING gist (geom) WHERE kind = 'poi';

CREATE OR REPLACE FUNCTION tiles.basemap(z integer, x integer, y integer)
RETURNS bytea
LANGUAGE plpgsql STABLE STRICT PARALLEL SAFE
AS $function$
DECLARE
    bounds geometry;
    buffered geometry;
    cell_size double precision;
    result bytea;
BEGIN
    IF z < 4 OR z > 15 OR x < 0 OR y < 0 OR x >= (1 << z) OR y >= (1 << z) THEN
        RETURN ''::bytea;
    END IF;

    bounds := ST_TileEnvelope(z, x, y);
    buffered := ST_TileEnvelope(z, x, y, margin => 64.0 / 4096);
    cell_size := (ST_XMax(bounds) - ST_XMin(bounds)) / 8;

    SELECT ST_AsMVT(tile, 'features', 4096, 'geom') INTO result
    FROM (
        SELECT kind, class, name, NULL::integer AS point_count,
            ST_AsMVTGeom(geom, bounds, 4096, 64, true) AS geom
        FROM osm.features
        WHERE geom && buffered AND minzoom <= z
            AND (kind <> 'poi' OR z >= 15)

        UNION ALL

        SELECT kind, class, name, point_count,
            ST_AsMVTGeom(geom, bounds, 4096, 0, true) AS geom
        FROM (
            SELECT CASE WHEN count(*) = 1 THEN 'poi' ELSE 'poi_cluster' END AS kind,
                CASE WHEN count(*) = 1 THEN min(class) END AS class,
                CASE WHEN count(*) = 1 THEN min(name) END AS name,
                count(*)::integer AS point_count,
                ST_SetSRID(ST_MakePoint(avg(ST_X(geom)), avg(ST_Y(geom))), 3857) AS geom
            FROM osm.features
            WHERE z BETWEEN 10 AND 14 AND kind = 'poi' AND minzoom <= z AND geom && bounds
            GROUP BY floor(ST_X(geom) / cell_size), floor(ST_Y(geom) / cell_size)
        ) AS grouped_pois
    ) AS tile
    WHERE tile.geom IS NOT NULL;

    RETURN COALESCE(result, ''::bytea);
END;
$function$;

COMMENT ON FUNCTION tiles.basemap(integer, integer, integer) IS $json$
{
  "description": "WalkAround OSM basemap v2 with zoom-aware POI density",
  "attribution": "<a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>",
  "vector_layers": [{"id": "features", "fields": {"kind": "String", "class": "String", "name": "String", "point_count": "Number"}}]
}
$json$;