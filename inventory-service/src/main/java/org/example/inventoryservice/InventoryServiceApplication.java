package org.example.inventoryservice;

import org.example.inventoryservice.module.Inventory;
import org.example.inventoryservice.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

/**
 * Main application class for the Inventory Service microservice.
 * <p>
 * This service manages product inventory levels in the e-commerce system, providing
 * stock availability checks for order processing. It integrates with the order service
 * to validate product availability before order confirmation.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Maintaining inventory levels for all products</li>
 *   <li>Providing stock availability checks via REST API</li>
 *   <li>Supporting inventory updates and queries</li>
 *   <li>Integrating with order service for real-time stock validation</li>
 * </ul>
 * <p>
 * Integration points:
 * <ul>
 *   <li>Eureka for service discovery</li>
 *   <li>MySQL for inventory data persistence</li>
 *   <li>Order service for stock validation requests</li>
 *   <li>Keycloak for OAuth2/JWT authentication</li>
 * </ul>
 *
 * @author Inventory Service Team
 * @version 1.0
 * @since 2024
 */
@SpringBootApplication
@EnableDiscoveryClient
public class InventoryServiceApplication {

    /**
     * Main method that starts the Inventory Service application.
     * <p>
     * This method bootstraps the service with database connectivity,
     * service discovery, and security configurations.
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    /**
     * CommandLineRunner bean for loading initial inventory data.
     * <p>
     * This bean executes on application startup to populate the database
     * with sample inventory data for testing and demonstration purposes.
     * It clears existing data and inserts predefined inventory records.
     *
     * @param inventoryRepository the repository for inventory operations
     * @return CommandLineRunner that loads sample data
     */
    @Bean
    public CommandLineRunner loadData(InventoryRepository inventoryRepository) {
        return args -> {
            inventoryRepository.deleteAll();
            inventoryRepository.save(Inventory.builder().skuCode("iphone_15").quantity(100).build());
            inventoryRepository.save(Inventory.builder().skuCode("iphone_15_pro").quantity(0).build());
        };
    }
}