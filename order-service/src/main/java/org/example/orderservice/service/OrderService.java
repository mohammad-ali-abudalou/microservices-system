package org.example.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.client.InventoryClient;
import org.example.orderservice.dto.InventoryResponse;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private static final String NOTIFICATION_TOPIC = "notificationTopic";
    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    static {
        // تعطيل حاوية التنظيف التلقائي لأنها تسبب مشاكل اتصال في ويندوز
        System.setProperty("testcontainers.ryuk.disabled", "true");
    }

    public String placeOrder(OrderRequest orderRequest) {
        log.info("Processing order request: {}", orderRequest);

        // 1. Map DTO to Entity
        Order order = mapToOrderEntity(orderRequest);

        // 2. Bulk Inventory Check (Performance Optimized)
        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        List<InventoryResponse> inventoryResponses = inventoryClient.checkStock(skuCodes);

        boolean allProductsInStock = inventoryResponses.stream()
                .allMatch(InventoryResponse::isInStock);

        if (Boolean.TRUE.equals(allProductsInStock) && !inventoryResponses.isEmpty()) {
            orderRepository.save(order);
            log.info("Order saved successfully: {}", order.getOrderNumber());

            publishOrderEvent(order.getOrderNumber());
            return order.getOrderNumber();
        } else {
            log.error("Stock verification failed for order");
            throw new IllegalArgumentException("One or more products are not in stock. Please try again.");
        }
    }

    private void publishOrderEvent(String orderNumber) {
        try {
            kafkaTemplate.send(NOTIFICATION_TOPIC, new OrderPlacedEvent(orderNumber));
            log.info("Notification event sent to Kafka for order: {}", orderNumber);
        } catch (Exception e) {
            log.error("Kafka delivery failure: {}", e.getMessage());
            // We keep the order saved even if notification fails (Eventual Consistency)
        }
    }

    private Order mapToOrderEntity(OrderRequest request) {
        List<OrderLineItems> items = request.getOrderLineItemsDtoList().stream()
                .map(dto -> OrderLineItems.builder()
                        .skuCode(dto.getSkuCode())
                        .price(dto.getPrice())
                        .quantity(dto.getQuantity())
                        .build())
                .toList();

        return Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .orderLineItemsList(items)
                .build();
    }
}