package services.applicationapi.configuration;

import org.springframework.context.annotation.*;
import org.springframework.web.cors.*;
import services.applicationapi.login.configurations.LoginProperties;
import services.applicationapi.register.configurations.RegistrationProperties;
import java.util.*;
import java.util.stream.Stream;

@Configuration
public class ApiCorsConfiguration {
    @Bean
    CorsConfigurationSource corsConfigurationSource(LoginProperties login, RegistrationProperties registration) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Stream.concat(login.allowedOrigins().stream(), registration.allowedOrigins().stream())
                .filter(origin -> !origin.isBlank()).distinct().toList());
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
