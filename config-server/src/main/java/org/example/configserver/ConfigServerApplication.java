package org.example.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Main application class for the Config Server microservice.
 * <p>
 * This service acts as a centralized configuration management system for all microservices
 * in the architecture. It provides externalized configuration using Git as the backend store,
 * enabling dynamic configuration updates without service restarts.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Serving configuration properties from Git repositories</li>
 *   <li>Supporting multiple profiles (dev, prod, etc.) and environments</li>
 *   <li>Providing encrypted sensitive configuration values</li>
 *   <li>Enabling configuration refresh for runtime property updates</li>
 * </ul>
 *
 * @author Config Server Team
 * @version 1.0
 * @since 2024
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    /**
     * Main method that starts the Spring Boot Config Server application.
     * <p>
     * This method initializes the config server with Git repository integration
     * and exposes configuration endpoints for other microservices.
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}