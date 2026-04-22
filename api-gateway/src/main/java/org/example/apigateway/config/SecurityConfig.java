package org.example.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for the API Gateway using Spring Security's reactive WebFlux support.
 * This configuration implements OAuth2/JWT authentication for all incoming requests while
 * allowing specific endpoints to remain publicly accessible for service discovery and health checks.
 *
 * <p>The gateway acts as a security boundary, validating JWT tokens issued by Keycloak
 * before forwarding requests to downstream microservices.</p>
 *
 * <p>JWT decoding is handled automatically by Spring Security using the configuration
 * in application.yaml, eliminating the need for manual JWT decoder beans.</p>
 *
 * @author API Gateway Team
 * @version 1.0
 * @since 2024
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain for reactive web requests.
     * Defines authorization rules and security policies for the API Gateway.
     *
     * <p>Security Rules:</p>
     * <ul>
     *   <li>Eureka service discovery endpoints are publicly accessible</li>
     *   <li>Actuator health check endpoints are publicly accessible</li>
     *   <li>Inventory API endpoints are permitted for health checks in containerized environments</li>
     *   <li>All other requests require valid JWT authentication</li>
     * </ul>
     *
     * @param serverHttpSecurity the reactive HTTP security configuration
     * @return configured security filter chain
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
        serverHttpSecurity
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/eureka/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/api/inventory/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(spec -> spec.jwt(Customizer.withDefaults()));

        return serverHttpSecurity.build();
    }
}