package org.example.discoveryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Main application class for the Discovery Server (Eureka Server).
 * <p>
 * This service provides service discovery capabilities for the microservices architecture.
 * It maintains a registry of all available service instances and their locations, enabling
 * dynamic service-to-service communication and load balancing.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Maintaining a registry of service instances and their network locations</li>
 *   <li>Handling service registration and deregistration</li>
 *   <li>Providing service discovery for client applications</li>
 *   <li>Supporting health checks and instance status monitoring</li>
 *   <li>Enabling client-side load balancing through service names</li>
 * </ul>
 *
 * @author Discovery Server Team
 * @version 1.0
 * @since 2024
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    /**
     * Main method that starts the Eureka Discovery Server application.
     * <p>
     * This method initializes the Eureka server with service registry capabilities
     * and exposes endpoints for service registration and discovery operations.
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}