package services.routingservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration
{
    @Bean(name = "applicationJwtDecoder")
    public org.springframework.security.oauth2.jwt.JwtDecoder applicationJwtDecoder(
            @org.springframework.beans.factory.annotation.Value("${app.login.signing-secret}") String secret)
    {
        byte[] keyBytes = java.util.Base64.getDecoder().decode(secret);
        javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA256");
        return org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   @Qualifier("applicationJwtDecoder") JwtDecoder jwtDecoder) {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/registrations/google").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/sessions/google", "/api/sessions/refresh", "/api/sessions/logout",
                                "/api/mobile/sessions/google", "/api/mobile/sessions/refresh", "/api/mobile/sessions/logout").permitAll()
                        .requestMatchers(HttpMethod.GET,"/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/dangerous-zones").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/routes/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.decoder(jwtDecoder)));

        return http.build();
    }
}
