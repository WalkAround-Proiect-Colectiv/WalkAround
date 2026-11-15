package services.applicationapi.recommendation.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.applicationapi.recommendation.domain.ActivityCategoryRule;
import services.applicationapi.recommendation.domain.TemperatureBand;
import services.applicationapi.recommendation.domain.TimeOfDay;
import services.applicationapi.recommendation.domain.WeatherBucket;

@Repository
public class JdbcActivityRuleRepositoryImpl implements ActivityRuleRepository {

    private static final String SELECT_ALL_RULES_SQL = """
            SELECT
                c.code AS category_code,
                c.display_name,
                c.icon_key,
                r.indoor,
                r.weight,
                c.osm_filter,
                r.weather_bucket,
                r.temperature_band,
                r.time_of_day
            FROM weather_activity_rule r
            JOIN activity_category c ON c.code = r.activity_category
            ORDER BY r.weight DESC, c.code ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcActivityRuleRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ActivityCategoryRule> findAllRules() {
        return jdbcTemplate.query(SELECT_ALL_RULES_SQL, (rs, rowNum) -> {
            String tempBandStr = rs.getString("temperature_band");
            String timeOfDayStr = rs.getString("time_of_day");
            return new ActivityCategoryRule(
                    rs.getString("category_code"),
                    rs.getString("display_name"),
                    rs.getString("icon_key"),
                    rs.getBoolean("indoor"),
                    rs.getInt("weight"),
                    rs.getString("osm_filter"),
                    WeatherBucket.valueOf(rs.getString("weather_bucket")),
                    tempBandStr != null ? TemperatureBand.valueOf(tempBandStr) : null,
                    timeOfDayStr != null ? TimeOfDay.valueOf(timeOfDayStr) : null
            );
        });
    }
}
