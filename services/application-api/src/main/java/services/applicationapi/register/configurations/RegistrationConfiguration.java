package services.applicationapi.register.configurations;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

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

}
