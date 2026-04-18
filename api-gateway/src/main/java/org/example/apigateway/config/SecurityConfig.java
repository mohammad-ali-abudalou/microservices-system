package org.example.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
        serverHttpSecurity
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/eureka/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        // السماح بفحص الصحة (Health Checks) بدون توكن لضمان عمل Kubernetes/Docker
                        .pathMatchers("/api/inventory/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(spec -> spec.jwt(Customizer.withDefaults()));

        return serverHttpSecurity.build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // هذا الرابط يجب أن يكون متاحاً من داخل حاوية الـ Gateway
        return NimbusReactiveJwtDecoder.withJwkSetUri("http://keycloak:8080/realms/master/protocol/openid-connect/certs").build();
    }
}