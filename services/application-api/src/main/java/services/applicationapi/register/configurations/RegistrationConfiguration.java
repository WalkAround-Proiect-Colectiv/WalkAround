package services.applicationapi.register.configurations;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.web.cors.*;
import java.util.List;

@Configuration
@EnableConfigurationProperties(RegistrationProperties.class)
public class RegistrationConfiguration {
    @Bean
    JwtDecoder googleIdTokenDecoder(RegistrationProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.google().jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new GoogleTokenValidator(properties.google())));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(RegistrationProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/registrations/google", cors);
        return source;
    }
}
