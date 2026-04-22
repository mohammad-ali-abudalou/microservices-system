package org.example.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Notification Service microservice.
 * <p>
 * This service handles asynchronous event processing for order notifications in the e-commerce system.
 * It consumes order placement events from Kafka and sends confirmation notifications to customers
 * via email or other communication channels.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Consuming order placed events from Kafka</li>
 *   <li>Processing notification requests asynchronously</li>
 *   <li>Sending order confirmation emails to customers</li>
 *   <li>Providing resilient event processing with error handling</li>
 * </ul>
 * <p>
 * Integration points:
 * <ul>
 *   <li>Kafka for event consumption</li>
 *   <li>Email service for notification delivery</li>
 *   <li>Order service for event publishing</li>
 * </ul>
 *
 * @author Notification Service Team
 * @version 1.0
 * @since 2024
 */
@SpringBootApplication
public class NotificationServiceApplication {

    /**
     * Main method that starts the Notification Service application.
     * <p>
     * This method bootstraps the service with Kafka consumer configurations
     * and notification processing capabilities.
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

}
