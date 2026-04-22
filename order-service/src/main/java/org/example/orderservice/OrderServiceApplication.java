package org.example.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Main application class for the Order Service microservice.
 * <p>
 * This service handles order processing operations in the e-commerce system, including
 * order creation, inventory validation, and event publishing. It integrates with inventory
 * service for stock checks and notification service for order confirmations via Kafka.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Processing customer order requests with validation</li>
 *   <li>Checking inventory availability before order confirmation</li>
 *   <li>Persisting orders to the database with transactional consistency</li>
 *   <li>Publishing order events to Kafka for downstream processing</li>
 *   <li>Providing REST API endpoints for order management</li>
 * </ul>
 * <p>
 * Integration points:
 * <ul>
 *   <li>Eureka for service discovery</li>
 *   <li>OpenFeign for declarative REST client to inventory service</li>
 *   <li>Kafka for event-driven communication with notification service</li>
 *   <li>MySQL for order data persistence</li>
 *   <li>Keycloak for OAuth2/JWT authentication</li>
 * </ul>
 *
 * @author Order Service Team
 * @version 1.0
 * @since 2024
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class OrderServiceApplication {

    /**
     * Main method that starts the Order Service application.
     * <p>
     * This method bootstraps the service with all necessary integrations including
     * service discovery, Feign clients, Kafka producers, and database connectivity.
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}