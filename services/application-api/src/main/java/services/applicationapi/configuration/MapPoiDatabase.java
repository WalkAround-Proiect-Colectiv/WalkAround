package services.applicationapi.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import services.applicationapi.recommendation.config.RecommendationProperties;

@Component
public class MapPoiDatabase {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final HikariDataSource dedicatedDataSource;

    public MapPoiDatabase(RecommendationProperties properties, NamedParameterJdbcTemplate defaultJdbcTemplate) {
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
            this.jdbcTemplate = new NamedParameterJdbcTemplate(dedicatedDataSource);
        } else {
            this.dedicatedDataSource = null;
            this.jdbcTemplate = defaultJdbcTemplate;
        }
    }

    public NamedParameterJdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    @PreDestroy
    void close() {
        if (dedicatedDataSource != null) dedicatedDataSource.close();
    }
}
