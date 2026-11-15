package services.applicationapi.events.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import services.applicationapi.events.model.EventCoordinates;
import services.applicationapi.recommendation.config.RecommendationProperties;

/**
 * Queries {@code osm.features} in the PostGIS database by venue name, preferring exact matches and
 * proximity to the locality hint when coordinates are supplied.
 */
@Repository
public class PostgisEventVenueCoordinateRepositoryImpl implements EventVenueCoordinateRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgisEventVenueCoordinateRepositoryImpl.class);
    private static final double DEFAULT_SEARCH_RADIUS_METERS = 25_000.0;

    private static final String FIND_BY_NAME_NEAR_POINT_SQL = """
            WITH origin AS (
                SELECT
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS pt_4326,
                    ST_Transform(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), 3857) AS pt_3857
            )
            SELECT
                ST_Y(ST_Transform(ST_PointOnSurface(f.geom), 4326)) AS latitude,
                ST_X(ST_Transform(ST_PointOnSurface(f.geom), 4326)) AS longitude,
                CASE WHEN LOWER(BTRIM(f.name)) = LOWER(:exactName) THEN 0 ELSE 1 END AS match_rank,
                ST_Distance(
                    ST_Transform(ST_PointOnSurface(f.geom), 4326)::geography,
                    origin.pt_4326::geography
                ) AS distance_meters
            FROM osm.features f
            CROSS JOIN origin
            WHERE f.geom && ST_Expand(origin.pt_3857, :mercatorRadius)
              AND f.name IS NOT NULL
              AND BTRIM(f.name) <> ''
              AND (LOWER(BTRIM(f.name)) = LOWER(:exactName) OR f.name ILIKE :likePattern)
            ORDER BY match_rank ASC, distance_meters ASC
            LIMIT 1
            """;

    private static final String FIND_BY_NAME_SQL = """
            SELECT
                ST_Y(ST_Transform(ST_PointOnSurface(f.geom), 4326)) AS latitude,
                ST_X(ST_Transform(ST_PointOnSurface(f.geom), 4326)) AS longitude,
                CASE WHEN LOWER(BTRIM(f.name)) = LOWER(:exactName) THEN 0 ELSE 1 END AS match_rank
            FROM osm.features f
            WHERE f.name IS NOT NULL
              AND BTRIM(f.name) <> ''
              AND (LOWER(BTRIM(f.name)) = LOWER(:exactName) OR f.name ILIKE :likePattern)
            ORDER BY match_rank ASC, f.osm_id ASC
            LIMIT 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final HikariDataSource dedicatedDataSource;

    @Autowired
    public PostgisEventVenueCoordinateRepositoryImpl(
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
            config.setMaximumPoolSize(2);
            config.setConnectionTimeout(5000);
            config.setInitializationFailTimeout(-1);
            this.dedicatedDataSource = new HikariDataSource(config);
            this.jdbcTemplate = new NamedParameterJdbcTemplate(this.dedicatedDataSource);
        } else {
            this.dedicatedDataSource = null;
            this.jdbcTemplate = defaultJdbcTemplate;
        }
    }

    public PostgisEventVenueCoordinateRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
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
    public Optional<EventCoordinates> findVenueCoordinates(
            String venueName,
            Double hintLatitude,
            Double hintLongitude
    ) {
        if (venueName == null || venueName.isBlank()) {
            return Optional.empty();
        }
        String trimmed = venueName.strip();
        if (trimmed.length() < 2) {
            return Optional.empty();
        }
        String escapedLike = "%" + escapeLike(trimmed) + "%";

        try {
            if (hintLatitude != null && hintLongitude != null
                    && Double.isFinite(hintLatitude) && Double.isFinite(hintLongitude)) {
                double cosLat = Math.max(0.2, Math.cos(Math.toRadians(Math.abs(hintLatitude))));
                double mercatorRadius = (DEFAULT_SEARCH_RADIUS_METERS / cosLat) * 1.15;
                MapSqlParameterSource params = new MapSqlParameterSource()
                        .addValue("lat", hintLatitude)
                        .addValue("lon", hintLongitude)
                        .addValue("mercatorRadius", mercatorRadius)
                        .addValue("exactName", trimmed)
                        .addValue("likePattern", escapedLike);
                List<EventCoordinates> matches = jdbcTemplate.query(
                        FIND_BY_NAME_NEAR_POINT_SQL,
                        params,
                        (rs, rowNum) -> new EventCoordinates(rs.getDouble("latitude"), rs.getDouble("longitude"))
                );
                if (!matches.isEmpty()) {
                    return Optional.of(matches.getFirst());
                }
            }

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("exactName", trimmed)
                    .addValue("likePattern", escapedLike);
            List<EventCoordinates> matches = jdbcTemplate.query(
                    FIND_BY_NAME_SQL,
                    params,
                    (rs, rowNum) -> new EventCoordinates(rs.getDouble("latitude"), rs.getDouble("longitude"))
            );
            return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
        } catch (DataAccessException exception) {
            log.debug("PostGIS venue coordinate lookup unavailable: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
