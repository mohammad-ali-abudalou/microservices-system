package org.example.configserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the Config Server.
 * <p>
 * This configuration provides basic security for the config server endpoints while allowing
 * necessary access for monitoring and configuration retrieval. Since config servers are accessed
 * by other services programmatically, CSRF protection is disabled and authentication is minimal.
 * <p>
 * Security approach:
 * <ul>
 *   <li>Permits all requests by default for service-to-service communication</li>
 *   <li>Allows actuator endpoints for health monitoring</li>
 *   <li>Disables CSRF as services fetch config via HTTP GET requests</li>
 *   <li>Can be enhanced with Basic Auth or OAuth2 in production environments</li>
 * </ul>
 *
 * @author Config Server Team
 * @version 1.0
 * @since 2024
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain for the Config Server.
     * <p>
     * This method sets up HTTP security rules that balance accessibility for microservices
     * with basic monitoring capabilities. The configuration prioritizes service availability
     * over strict security since config servers are internal infrastructure components.
     *
     * @param http the HttpSecurity configuration to customize
     * @return the configured SecurityFilterChain
     * @throws Exception if security configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().permitAll());
        return http.build();
    }
}