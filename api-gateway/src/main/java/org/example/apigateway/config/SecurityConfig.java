package org.example.apigateway.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

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

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("#{'${security.oauth2.accepted-issuers:http://localhost:8080/realms/master,http://keycloak:8080/realms/master}'.split(',')}")
    private List<String> acceptedIssuers;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Creates a global filter that logs incoming requests for debugging purposes.
     * This filter executes after routing decisions and provides visibility into
     * request flow through the gateway.
     *
     * @return a GlobalFilter for request logging
     */
    @Bean
    public GlobalFilter postGlobalFilter() {
        return (exchange, chain) -> {
            log.debug("Gateway request received: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath());
            return chain.filter(exchange);
        };
    }

    /**
     * Creates a reactive JWT decoder with custom validation for issuer checking.
     * The decoder validates JWT tokens against accepted issuers and ensures
     * token timestamps are valid. This provides an additional security layer
     * beyond standard JWT validation.
     *
     * @return a ReactiveJwtDecoder configured with custom validators
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
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
                            "The token issuer is not trusted by the gateway",
                            null
                    ));
                }
        );
        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    /**
     * Creates a converter that transforms JWT tokens into Spring Security authentication tokens.
     * Extracts user roles from the JWT's realm_access claim and converts them to granted authorities.
     * Ensures role names are prefixed with "ROLE_" for proper Spring Security integration.
     *
     * @return a Converter for JWT to authentication token conversion
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return jwt -> {
            Collection<SimpleGrantedAuthority> authorities = extractRoles(jwt).stream()
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());

            return Mono.just(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
        };
    }

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
                .oauth2ResourceServer(spec -> spec
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((exchange, ex) -> {
                            log.error(
                                    "Authentication failed for {} {}: {}",
                                    exchange.getRequest().getMethod(),
                                    exchange.getRequest().getPath(),
                                    ex.getMessage(),
                                    ex
                            );
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }));

        return serverHttpSecurity.build();
    }

    /**
     * Extracts user roles from the JWT token's realm_access claim.
     * Safely navigates the nested claim structure to retrieve role information
     * from Keycloak JWT tokens, handling cases where the claim may be missing or malformed.
     *
     * @param jwt the JWT token containing user claims
     * @return a collection of role names, or empty list if roles cannot be extracted
     */
    private Collection<String> extractRoles(Jwt jwt) {
        return Optional.ofNullable(jwt.getClaims().get("realm_access"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(realmAccess -> realmAccess.get("roles"))
                .filter(Collection.class::isInstance)
                .map(Collection.class::cast)
                .orElse(List.of())
                .stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
