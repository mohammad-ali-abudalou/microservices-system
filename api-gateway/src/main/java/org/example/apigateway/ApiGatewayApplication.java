package org.example.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Main application class for the API Gateway microservice.
 * <p>
 * This gateway serves as the single entry point for all client requests in the microservices architecture.
 * It provides centralized routing, load balancing, and security enforcement using Spring Cloud Gateway.
 * The gateway integrates with Eureka for service discovery and uses OAuth2/JWT for authentication.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Routing requests to appropriate microservices based on path patterns</li>
 *   <li>Load balancing across multiple instances of services</li>
 *   <li>JWT token validation for secure API access</li>
 *   <li>Rate limiting and circuit breaker patterns</li>
 * </ul>
 *
 * @author API Gateway Team
 * @version 1.0
 * @since 2024
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    /**
     * Main method that starts the Spring Boot application.
     * <p>
     * This method bootstraps the API Gateway with all configured routes, security filters,
     * and service discovery capabilities.
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}