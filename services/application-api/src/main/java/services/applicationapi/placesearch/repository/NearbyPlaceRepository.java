package services.applicationapi.placesearch.repository;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import services.applicationapi.configuration.MapPoiDatabase;
import services.applicationapi.placesearch.dto.NearbyPlaceResult;

@Repository
public class NearbyPlaceRepository {

    private static final String FIND_POLICE = """
            WITH origin AS (
                SELECT ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS point
            )
            SELECT f.osm_type || ':' || f.osm_id AS id,
                   COALESCE(NULLIF(BTRIM(f.name), ''), 'Police station') AS name,
                   ST_Y(ST_Transform(f.geom, 4326)) AS latitude,
                   ST_X(ST_Transform(f.geom, 4326)) AS longitude,
                   ST_Distance(ST_Transform(f.geom, 4326)::geography, origin.point::geography)
                       AS distance_meters
            FROM osm.features f CROSS JOIN origin
            WHERE f.kind = 'poi' AND f.class = 'police'
              AND f.geom && ST_Expand(ST_Transform(origin.point, 3857), :mercatorRadius)
              AND ST_DWithin(ST_Transform(f.geom, 4326)::geography,
                             origin.point::geography, :radiusMeters)
            ORDER BY distance_meters, f.osm_type, f.osm_id
            LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public NearbyPlaceRepository(MapPoiDatabase database) {
        this.jdbc = database.jdbcTemplate();
    }

    public List<NearbyPlaceResult> findPolice(double latitude, double longitude, int radiusMeters,
                                              long offset, int limit) {
        double mercatorRadius = radiusMeters / Math.cos(Math.toRadians(latitude)) * 1.15;
        var parameters = new MapSqlParameterSource()
                .addValue("lat", latitude)
                .addValue("lon", longitude)
                .addValue("radiusMeters", radiusMeters)
                .addValue("mercatorRadius", mercatorRadius)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbc.query(FIND_POLICE, parameters, (rs, row) -> new NearbyPlaceResult(
                rs.getString("id"), rs.getString("name"),
                rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getDouble("distance_meters")));
    }
}
