package org.example.inventoryservice.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("#{'${security.oauth2.accepted-issuers:http://localhost:8080/realms/master,http://keycloak:8080/realms/master}'.split(',')}")
    private List<String> acceptedIssuers;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                token -> {
                    String issuer = token.getClaimAsString(JwtClaimNames.ISS);
                    if (acceptedIssuers.contains(issuer)) {
                        return OAuth2TokenValidatorResult.success();
                    }

                    log.error("JWT issuer validation failed. tokenIssuer={}, acceptedIssuers={}", issuer, acceptedIssuers);
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                            "invalid_token",
                            "The token issuer is not trusted by inventory-service",
                            null
                    ));
                }
        );
        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/**").permitAll() // Allow Prometheus to read the data
                        .anyRequest().authenticated() // No request is allowed without a token
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder()))
                        .authenticationEntryPoint((request, response, ex) -> {
                            log.error(
                                    "Authentication failed for {} {}: {}",
                                    request.getMethod(),
                                    request.getRequestURI(),
                                    ex.getMessage(),
                                    ex
                            );
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED).commence(request, response, ex);
                        }));

        return http.build();
    }
}
