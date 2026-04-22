package org.example.orderservice.service;

import org.example.orderservice.client.InventoryClient;
import org.example.orderservice.dto.InventoryResponse;
import org.example.orderservice.dto.OrderLineItemsDto;
import org.example.orderservice.dto.OrderRequest;
import org.example.orderservice.module.Order;
import org.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderService#placeOrder(OrderRequest)} method.
 * <p>
 * This test class uses JUnit 5 and Mockito to test order placement functionality
 * in isolation, focusing on inventory stock availability validation.
 * <p>
 * Test Scenarios:
 * - Single item in stock (order placed successfully)
 * - Single item out of stock (order rejected)
 * - Multiple items all in stock
 * - Multiple items with mixed stock status
 * - Multiple items all out of stock
 * - Inventory service returns empty response
 * - Successful Kafka event publishing
 * - Failed Kafka event publishing (resilience)
 * - Order persistence in database
 * - Transaction rollback on validation failure
 *
 * @author QA Team
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    private OrderRequest validOrderRequest;
    private OrderLineItemsDto iphone15Item;
    private OrderLineItemsDto pixelItem;

    @BeforeEach
    void setUp() {
        // Initialize test data
        iphone15Item = OrderLineItemsDto.builder()
                .skuCode("iphone_15")
                .price(BigDecimal.valueOf(1200))
                .quantity(1)
                .build();

        pixelItem = OrderLineItemsDto.builder()
                .skuCode("pixel_8")
                .price(BigDecimal.valueOf(800))
                .quantity(2)
                .build();

        validOrderRequest = OrderRequest.builder()
                .orderLineItemsDtoList(Collections.singletonList(iphone15Item))
                .build();
    }

    // ===================== SUCCESS SCENARIOS - ITEMS IN STOCK =====================

    /**
     * Test: Single item in stock returns order number successfully.
     */
    @Test
    @DisplayName("Should place order successfully when single item is in stock")
    void testPlaceOrder_SingleItemInStock_Success() {
        // Given - Item is in stock
        InventoryResponse inStockResponse = InventoryResponse.builder()
                .skuCode("iphone_15")
                .isInStock(true)
                .build();
        when(inventoryClient.checkStock(Arrays.asList("iphone_15")))
                .thenReturn(Collections.singletonList(inStockResponse));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        String orderNumber = orderService.placeOrder(validOrderRequest);

        // Then
        assertNotNull(orderNumber);
        assertNotEquals("", orderNumber);
        verify(inventoryClient, times(1)).checkStock(anyList());
        verify(orderRepository, times(1)).save(any());
        verify(kafkaTemplate, times(1)).send(anyString(), any());
    }

    /**
     * Test: Multiple items all in stock returns order number successfully.
     */
    @Test
    @DisplayName("Should place order successfully when multiple items are in stock")
    void testPlaceOrder_MultipleItemsInStock_Success() {
        // Given - Multiple items in stock
        OrderRequest multiItemRequest = OrderRequest.builder()
                .orderLineItemsDtoList(Arrays.asList(iphone15Item, pixelItem))
                .build();

        List<InventoryResponse> inStockResponses = Arrays.asList(
                InventoryResponse.builder().skuCode("iphone_15").isInStock(true).build(),
                InventoryResponse.builder().skuCode("pixel_8").isInStock(true).build()
        );
        when(inventoryClient.checkStock(Arrays.asList("iphone_15", "pixel_8")))
                .thenReturn(inStockResponses);
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        String orderNumber = orderService.placeOrder(multiItemRequest);

        // Then
        assertNotNull(orderNumber);
        verify(inventoryClient, times(1)).checkStock(Arrays.asList("iphone_15", "pixel_8"));
        verify(orderRepository, times(1)).save(any());
        verify(kafkaTemplate, times(1)).send(anyString(), any());
    }

    /**
     * Test: Order is persisted with correct data when items are in stock.
     */
    @Test
    @DisplayName("Should persist order with correct data when items are in stock")
    void testPlaceOrder_PersistOrderData_Success() {
        // Given
        InventoryResponse inStockResponse = InventoryResponse.builder()
                .skuCode("iphone_15")
                .isInStock(true)
                .build();
        when(inventoryClient.checkStock(Arrays.asList("iphone_15")))
                .thenReturn(Collections.singletonList(inStockResponse));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        orderService.placeOrder(validOrderRequest);

        // Then - Verify order persistence
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertNotNull(savedOrder.getOrderNumber());
        assertNotNull(savedOrder.getOrderLineItemsList());
        assertEquals(1, savedOrder.getOrderLineItemsList().size());
        assertEquals("iphone_15", savedOrder.getOrderLineItemsList().get(0).getSkuCode());
        assertEquals(BigDecimal.valueOf(1200), savedOrder.getOrderLineItemsList().get(0).getPrice());
        assertEquals(1, savedOrder.getOrderLineItemsList().get(0).getQuantity());
    }

    // ===================== FAILURE SCENARIOS - ITEMS OUT OF STOCK =====================

    /**
     * Test: Single item out of stock throws IllegalArgumentException.
     */
    @Test
    @DisplayName("Should throw exception when single item is out of stock")
    void testPlaceOrder_SingleItemOutOfStock_ThrowsException() {
        // Given - Item is out of stock
        InventoryResponse outOfStockResponse = InventoryResponse.builder()
                .skuCode("iphone_15")
                .isInStock(false)
                .build();
        when(inventoryClient.checkStock(Arrays.asList("iphone_15")))
                .thenReturn(Collections.singletonList(outOfStockResponse));

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(validOrderRequest)
        );

        assertEquals("One or more products are not in stock. Please try again.", exception.getMessage());
        verify(inventoryClient, times(1)).checkStock(anyList());
        verify(orderRepository, never()).save(any());  // Order not saved
        verify(kafkaTemplate, never()).send(anyString(), any());  // No event published
    }

    /**
     * Test: One of multiple items out of stock throws exception.
     */
    @Test
    @DisplayName("Should throw exception when one of multiple items is out of stock")
    void testPlaceOrder_OneOfMultipleItemsOutOfStock_ThrowsException() {
        // Given - One item out of stock
        OrderRequest multiItemRequest = OrderRequest.builder()
                .orderLineItemsDtoList(Arrays.asList(iphone15Item, pixelItem))
                .build();

        List<InventoryResponse> mixedResponses = Arrays.asList(
                InventoryResponse.builder().skuCode("iphone_15").isInStock(true).build(),
                InventoryResponse.builder().skuCode("pixel_8").isInStock(false).build()
        );
        when(inventoryClient.checkStock(Arrays.asList("iphone_15", "pixel_8")))
                .thenReturn(mixedResponses);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(multiItemRequest)
        );

        assertNotNull(exception.getMessage());
        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    /**
     * Test: All items out of stock throws exception.
     */
    @Test
    @DisplayName("Should throw exception when all items are out of stock")
    void testPlaceOrder_AllItemsOutOfStock_ThrowsException() {
        // Given - All items out of stock
        OrderRequest multiItemRequest = OrderRequest.builder()
                .orderLineItemsDtoList(Arrays.asList(iphone15Item, pixelItem))
                .build();

        List<InventoryResponse> outOfStockResponses = Arrays.asList(
                InventoryResponse.builder().skuCode("iphone_15").isInStock(false).build(),
                InventoryResponse.builder().skuCode("pixel_8").isInStock(false).build()
        );
        when(inventoryClient.checkStock(Arrays.asList("iphone_15", "pixel_8")))
                .thenReturn(outOfStockResponses);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(multiItemRequest));

        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    // ===================== EDGE CASE SCENARIOS =====================

    /**
     * Test: Inventory service returns empty list (item not found).
     */
    @Test
    @DisplayName("Should throw exception when inventory service returns no items")
    void testPlaceOrder_InventoryReturnsEmpty_ThrowsException() {
        // Given - Empty response from inventory
        when(inventoryClient.checkStock(Arrays.asList("iphone_15")))
                .thenReturn(Collections.emptyList());

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.placeOrder(validOrderRequest)
        );

        assertNotNull(exception.getMessage());
        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    /**
     * Test: Inventory service throws exception (network error).
     */
    @Test
    @DisplayName("Should throw exception when inventory service fails")
    void testPlaceOrder_InventoryServiceFails_ThrowsException() {
        // Given - Inventory service throws exception
        when(inventoryClient.checkStock(anyList()))
                .thenThrow(new RuntimeException("Inventory service unavailable"));

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orderService.placeOrder(validOrderRequest)
        );

        assertEquals("Inventory service unavailable", exception.getMessage());
        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    /**
     * Test: Kafka publishing fails but order is still persisted (resilience).
     */
    @Test
    @DisplayName("Should persist order even if Kafka publishing fails (eventual consistency)")
    void testPlaceOrder_KafkaFails_OrderStillPersisted() {
        // Given - Items in stock but Kafka fails
        InventoryResponse inStockResponse = InventoryResponse.builder()
                .skuCode("iphone_15")
                .isInStock(true)
                .build();
        when(inventoryClient.checkStock(Arrays.asList("iphone_15")))
                .thenReturn(Collections.singletonList(inStockResponse));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        when(kafkaTemplate.send(anyString(), any()))
                .thenThrow(new RuntimeException("Kafka unavailable"));

        // When - Order placed despite Kafka failure (should be caught internally)
        // The service catches exceptions and logs them for eventual consistency
        orderService.placeOrder(validOrderRequest);

        // Then - Order persisted (Kafka exception handled gracefully)
        verify(orderRepository, times(1)).save(any());
        verify(kafkaTemplate, times(1)).send(anyString(), any());
    }

    // ===================== VERIFICATION SCENARIOS =====================

    /**
     * Test: Correct SKU codes sent to inventory client.
     */
    @Test
    @DisplayName("Should send correct SKU codes to inventory client")
    void testPlaceOrder_CorrectSkuCodesSentToInventory() {
        // Given
        OrderRequest multiItemRequest = OrderRequest.builder()
                .orderLineItemsDtoList(Arrays.asList(iphone15Item, pixelItem))
                .build();

        List<InventoryResponse> inStockResponses = Arrays.asList(
                InventoryResponse.builder().skuCode("iphone_15").isInStock(true).build(),
                InventoryResponse.builder().skuCode("pixel_8").isInStock(true).build()
        );
        when(inventoryClient.checkStock(anyList()))
                .thenReturn(inStockResponses);
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        ArgumentCaptor<List<String>> skuCaptor = ArgumentCaptor.forClass(List.class);

        // When
        orderService.placeOrder(multiItemRequest);

        // Then - Verify SKU codes sent to inventory
        verify(inventoryClient).checkStock(skuCaptor.capture());
        List<String> sentSkuCodes = skuCaptor.getValue();

        assertEquals(2, sentSkuCodes.size());
        assertTrue(sentSkuCodes.contains("iphone_15"));
        assertTrue(sentSkuCodes.contains("pixel_8"));
    }

    /**
     * Test: Generated order number is unique (UUID format).
     */
    @Test
    @DisplayName("Should generate unique order numbers for each order")
    void testPlaceOrder_GenerateUniqueOrderNumbers() {
        // Given
        InventoryResponse inStockResponse = InventoryResponse.builder()
                .skuCode("iphone_15")
                .isInStock(true)
                .build();
        when(inventoryClient.checkStock(anyList()))
                .thenReturn(Collections.singletonList(inStockResponse));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        String orderNumber1 = orderService.placeOrder(validOrderRequest);
        String orderNumber2 = orderService.placeOrder(validOrderRequest);

        // Then - Different order numbers generated
        assertNotEquals(orderNumber1, orderNumber2);
        assertTrue(orderNumber1.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
        assertTrue(orderNumber2.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }

    /**
     * Test: Order repository save method called exactly once.
     */
    @Test
    @DisplayName("Should call repository save method exactly once on successful order placement")
    void testPlaceOrder_RepositoryCalledOnce() {
        // Given
        InventoryResponse inStockResponse = InventoryResponse.builder()
                .skuCode("iphone_15")
                .isInStock(true)
                .build();
        when(inventoryClient.checkStock(anyList()))
                .thenReturn(Collections.singletonList(inStockResponse));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        orderService.placeOrder(validOrderRequest);

        // Then
        verify(orderRepository, times(1)).save(any());
        verify(orderRepository, never()).findAll();
        verify(orderRepository, never()).delete(any());
    }

    /**
     * Test: Kafka event published with order number.
     */
    @Test
    @DisplayName("Should publish Kafka event with correct topic and order number")
    void testPlaceOrder_KafkaEventPublished() {
        // Given
        InventoryResponse inStockResponse = InventoryResponse.builder()
                .skuCode("iphone_15")
                .isInStock(true)
                .build();
        when(inventoryClient.checkStock(anyList()))
                .thenReturn(Collections.singletonList(inStockResponse));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        String orderNumber = orderService.placeOrder(validOrderRequest);

        // Then - Verify Kafka event
        verify(kafkaTemplate, times(1)).send(eq("notificationTopic"), any());
    }

    /**
     * Test: No order saved when stock validation fails.
     */
    @Test
    @DisplayName("Should not persist order when stock validation fails")
    void testPlaceOrder_NoOrderSavedOnValidationFailure() {
        // Given
        InventoryResponse outOfStockResponse = InventoryResponse.builder()
                .skuCode("iphone_15")
                .isInStock(false)
                .build();
        when(inventoryClient.checkStock(anyList()))
                .thenReturn(Collections.singletonList(outOfStockResponse));

        // When
        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(validOrderRequest));

        // Then - Repository should never be called
        verify(orderRepository, never()).save(any());
        verify(orderRepository, never()).findAll();
    }

    /**
     * Test: Order with large quantity handled correctly with stock validation.
     */
    @Test
    @DisplayName("Should validate orders with large quantities successfully")
    void testPlaceOrder_LargeQuantity_StockValidation() {
        // Given - Large quantity item
        OrderLineItemsDto largeQuantityItem = OrderLineItemsDto.builder()
                .skuCode("iphone_15")
                .price(BigDecimal.valueOf(1200))
                .quantity(100)
                .build();

        OrderRequest largeOrderRequest = OrderRequest.builder()
                .orderLineItemsDtoList(Collections.singletonList(largeQuantityItem))
                .build();

        InventoryResponse inStockResponse = InventoryResponse.builder()
                .skuCode("iphone_15")
                .isInStock(true)
                .build();
        when(inventoryClient.checkStock(anyList()))
                .thenReturn(Collections.singletonList(inStockResponse));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        String orderNumber = orderService.placeOrder(largeOrderRequest);

        // Then
        assertNotNull(orderNumber);
        verify(orderRepository, times(1)).save(any());
    }

    /**
     * Test: Multiple successive orders are independent.
     */
    @Test
    @DisplayName("Should handle multiple successive orders independently")
    void testPlaceOrder_MultipleSuccessiveOrders_Independent() {
        // Given
        InventoryResponse inStockResponse = InventoryResponse.builder()
                .skuCode("iphone_15")
                .isInStock(true)
                .build();
        when(inventoryClient.checkStock(anyList()))
                .thenReturn(Collections.singletonList(inStockResponse));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        String orderNumber1 = orderService.placeOrder(validOrderRequest);
        String orderNumber2 = orderService.placeOrder(validOrderRequest);
        String orderNumber3 = orderService.placeOrder(validOrderRequest);

        // Then - All orders placed
        assertNotNull(orderNumber1);
        assertNotNull(orderNumber2);
        assertNotNull(orderNumber3);
        assertNotEquals(orderNumber1, orderNumber2);
        assertNotEquals(orderNumber2, orderNumber3);
        assertNotEquals(orderNumber1, orderNumber3);

        verify(orderRepository, times(3)).save(any());
        verify(inventoryClient, times(3)).checkStock(anyList());
    }
}