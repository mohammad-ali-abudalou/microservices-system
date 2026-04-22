package org.example.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event class representing an order placement notification.
 * <p>
 * This event is published to Kafka when an order is successfully placed and persisted.
 * It contains the order number and is consumed by the notification service to send
 * confirmation emails or other notifications to customers.
 * <p>
 * Event flow:
 * <ol>
 *   <li>OrderService places order and persists to database</li>
 *   <li>OrderPlacedEvent is published to Kafka topic</li>
 *   <li>NotificationService consumes event and sends notifications</li>
 * </ol>
 *
 * @author Order Service Team
 * @version 1.0
 * @since 2024
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderPlacedEvent {
    private String orderNumber;
}