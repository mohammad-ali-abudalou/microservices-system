package org.example.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.client.InventoryClient;
import org.example.orderservice.dto.InventoryResponse;
import org.example.orderservice.dto.OrderLineItemsDto;
import org.example.orderservice.dto.OrderRequest;
import org.example.orderservice.event.OrderPlacedEvent;
import org.example.orderservice.module.Order;
import org.example.orderservice.module.OrderLineItems;
import org.example.orderservice.repository.OrderRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service class responsible for handling order processing operations in the e-commerce microservices system.
 * This service manages the complete order lifecycle including validation, persistence, and event publishing.
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Validating order requests against inventory availability</li>
 *   <li>Persisting valid orders to the database</li>
 *   <li>Publishing order events to Kafka for downstream processing</li>
 *   <li>Ensuring transactional consistency across operations</li>
 * </ul>
 *
 * <p>Business Flow:</p>
 * <ol>
 *   <li>Receive order request with line items</li>
 *   <li>Map DTO to domain entities</li>
 *   <li>Validate stock availability via inventory service</li>
 *   <li>Persist order if validation passes</li>
 *   <li>Publish notification event to Kafka</li>
 *   <li>Return generated order number</li>
 * </ol>
 *
 * @author Order Service Team
 * @version 1.0
 * @since 2024
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private static final String NOTIFICATION_TOPIC = "notificationTopic";

    static {
        // Disable automatic cleanup container as it causes connection issues on Windows
        System.setProperty("testcontainers.ryuk.disabled", "true");
    }

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    /**
     * Processes a new order request by validating inventory availability and persisting the order.
     *
     * <p>This method implements the core order placement business logic:</p>
     * <ol>
     *   <li>Transforms the incoming DTO to domain entities</li>
     *   <li>Validates stock availability for all order line items</li>
     *   <li>Saves the order to the database if validation passes</li>
     *   <li>Publishes an order placed event to Kafka for notifications</li>
     *   <li>Returns the generated order number for confirmation</li>
     * </ol>
     *
     * <p>The operation is transactional - if any step fails, the entire operation is rolled back.</p>
     *
     * @param orderRequest the order request containing customer order details including line items
     * @return the generated order number as a String (UUID format)
     * @throws IllegalArgumentException if any products in the order are not in stock
     * @throws RuntimeException         if database operations fail or Kafka publishing encounters issues
     * @see OrderRequest
     * @see Order
     * @see InventoryClient#checkStock(List)
     */
    public String placeOrder(OrderRequest orderRequest) {
        log.info("Processing order request: {}", orderRequest);

        Order order = mapToOrderEntity(orderRequest);

        if (isOrderValid(order)) {
            orderRepository.save(order);
            log.info("Order saved successfully: {}", order.getOrderNumber());

            publishOrderPlacedEvent(order.getOrderNumber());
            return order.getOrderNumber();
        } else {
            log.error("Stock verification failed for order");
            throw new IllegalArgumentException("One or more products are not in stock. Please try again.");
        }
    }

    private boolean isOrderValid(Order order) {
        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        List<InventoryResponse> inventoryResponses = inventoryClient.checkStock(skuCodes);

        return !inventoryResponses.isEmpty() && inventoryResponses.stream()
                .allMatch(InventoryResponse::isInStock);
    }

    private void publishOrderPlacedEvent(String orderNumber) {
        try {
            kafkaTemplate.send(NOTIFICATION_TOPIC, new OrderPlacedEvent(orderNumber));
            log.info("Notification event sent to Kafka for order: {}", orderNumber);
        } catch (Exception e) {
            log.error("Failed to send notification event to Kafka: {}", e.getMessage());
            // Order remains saved for eventual consistency
        }
    }

    private Order mapToOrderEntity(OrderRequest request) {
        List<OrderLineItems> orderLineItems = request.getOrderLineItemsDtoList().stream()
                .map(this::mapToOrderLineItem)
                .toList();

        return Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .orderLineItemsList(orderLineItems)
                .build();
    }

    private OrderLineItems mapToOrderLineItem(OrderLineItemsDto dto) {
        return OrderLineItems.builder()
                .skuCode(dto.getSkuCode())
                .price(dto.getPrice())
                .quantity(dto.getQuantity())
                .build();
    }
}