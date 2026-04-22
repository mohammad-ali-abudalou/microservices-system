package org.example.discoveryserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the Eureka Discovery Server.
 * <p>
 * This configuration secures the Eureka dashboard and API endpoints while allowing
 * necessary access for service registration and health monitoring. The server needs
 * to be accessible to microservices for registration/deregistration operations.
 * <p>
 * Security approach:
 * <ul>
 *   <li>Allows public access to Eureka API endpoints for service communication</li>
 *   <li>Permits actuator endpoints for monitoring and health checks</li>
 *   <li>Allows access to static resources (CSS, JS, images) for the dashboard</li>
 *   <li>Enables HTTP Basic authentication for administrative access</li>
 *   <li>Disables CSRF protection as it's not applicable for API-based registration</li>
 * </ul>
 *
 * @author Discovery Server Team
 * @version 1.0
 * @since 2024
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain for the Eureka Discovery Server.
     * <p>
     * This method sets up HTTP security rules that protect the Eureka dashboard
     * while ensuring microservices can register and discover services. The configuration
     * balances security with operational requirements of the service registry.
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
                        .requestMatchers("/eureka/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/lastn/**", "/js/**", "/css/**", "/fonts/**", "/images/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults());


        return http.build();
    }
}