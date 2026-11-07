package services.applicationapi.recommendation.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import services.applicationapi.recommendation.config.RecommendationProperties;
import services.applicationapi.recommendation.domain.PoiCandidate;

@Repository
public class PostgisPoiRepositoryImpl implements PoiRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgisPoiRepositoryImpl.class);
    private static final Set<String> EMPTY_SENTINEL = Set.of("__NONE__");

    private static final String FIND_NEARBY_POIS_SQL = """
            WITH origin AS (
                SELECT
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS pt_4326,
                    ST_Transform(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), 3857) AS pt_3857
            )
            SELECT
                f.osm_type || ':' || f.osm_id AS id,
                BTRIM(f.name) AS name,
                f.kind AS osm_kind,
                f.class AS osm_class,
                ST_Y(ST_Transform(ST_PointOnSurface(f.geom), 4326)) AS latitude,
                ST_X(ST_Transform(ST_PointOnSurface(f.geom), 4326)) AS longitude,
                ST_Distance(
                    ST_Transform(ST_PointOnSurface(f.geom), 4326)::geography,
                    origin.pt_4326::geography
                ) AS distance_meters
            FROM osm.features f
            CROSS JOIN origin
            WHERE f.geom && ST_Expand(origin.pt_3857, :mercatorRadius)
              AND ST_DWithin(
                  ST_Transform(ST_PointOnSurface(f.geom), 4326)::geography,
                  origin.pt_4326::geography,
                  :radiusMeters
              )
              AND f.name IS NOT NULL
              AND BTRIM(f.name) <> ''
              AND (
                  (f.kind = 'poi' AND f.class IN (:poiClasses))
                  OR (f.kind = 'landcover' AND f.class IN (:landcoverClasses))
                  OR (f.kind = 'water' AND f.class IN (:waterClasses))
              )
            ORDER BY distance_meters ASC, f.osm_type ASC, f.osm_id ASC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final HikariDataSource dedicatedDataSource;

    @Autowired
    public PostgisPoiRepositoryImpl(
            RecommendationProperties properties,
            NamedParameterJdbcTemplate defaultJdbcTemplate
    ) {
        if (properties.poiDatabaseUrl() != null && !properties.poiDatabaseUrl().isBlank()) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(properties.poiDatabaseUrl());
            if (properties.poiDatabaseUsername() != null && !properties.poiDatabaseUsername().isBlank()) {
                config.setUsername(properties.poiDatabaseUsername());
            }
            if (properties.poiDatabasePassword() != null) {
                config.setPassword(properties.poiDatabasePassword());
            }
            config.setReadOnly(true);
            config.setMaximumPoolSize(4);
            config.setConnectionTimeout(5000);
            config.setInitializationFailTimeout(-1);
            this.dedicatedDataSource = new HikariDataSource(config);
            this.jdbcTemplate = new NamedParameterJdbcTemplate(this.dedicatedDataSource);
        } else {
            this.dedicatedDataSource = null;
            this.jdbcTemplate = defaultJdbcTemplate;
        }
    }

    public PostgisPoiRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.dedicatedDataSource = null;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PreDestroy
    void closeDedicatedDataSource() {
        if (dedicatedDataSource != null && !dedicatedDataSource.isClosed()) {
            dedicatedDataSource.close();
        }
    }

    @Override
    public List<PoiCandidate> findNearbyPois(
            double latitude,
            double longitude,
            int radiusMeters,
            int limit,
            Set<String> poiClasses,
            Set<String> landcoverClasses,
            Set<String> waterClasses
    ) {
        if ((poiClasses == null || poiClasses.isEmpty())
                && (landcoverClasses == null || landcoverClasses.isEmpty())
                && (waterClasses == null || waterClasses.isEmpty())) {
            return List.of();
        }

        double cosLat = Math.max(0.2, Math.cos(Math.toRadians(Math.abs(latitude))));
        double mercatorRadius = (radiusMeters / cosLat) * 1.15;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lat", latitude)
                .addValue("lon", longitude)
                .addValue("radiusMeters", radiusMeters)
                .addValue("mercatorRadius", mercatorRadius)
                .addValue("limit", limit)
                .addValue("poiClasses", (poiClasses == null || poiClasses.isEmpty()) ? EMPTY_SENTINEL : poiClasses)
                .addValue("landcoverClasses", (landcoverClasses == null || landcoverClasses.isEmpty()) ? EMPTY_SENTINEL : landcoverClasses)
                .addValue("waterClasses", (waterClasses == null || waterClasses.isEmpty()) ? EMPTY_SENTINEL : waterClasses);

        try {
            return jdbcTemplate.query(FIND_NEARBY_POIS_SQL, params, (rs, rowNum) -> new PoiCandidate(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("osm_kind"),
                    rs.getString("osm_class"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    rs.getDouble("distance_meters")
            ));
        } catch (DataAccessException exception) {
            log.warn("PostGIS POI lookup unavailable: {}", exception.getClass().getSimpleName());
            return List.of();
        }
    }
}
