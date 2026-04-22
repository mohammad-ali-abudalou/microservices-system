package org.example.orderservice.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.example.orderservice.dto.OrderLineItemsDto;
import org.example.orderservice.dto.OrderRequest;
import org.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for OrderService using Testcontainers.
 * <p>
 * These tests verify the complete order placement workflow with real database,
 * Kafka, and mocked external services.
 * <p>
 * Note: These are slower integration tests. Fast unit tests are in {@link OrderServiceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderServiceApplicationTests {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    private static MockWebServer mockWebServer;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @BeforeAll
    static void setup() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        // MySQL configuration
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        // Kafka configuration
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Route WebClient to MockWebServer instead of real inventory service
        registry.add("inventory.service.url", () -> "http://localhost:" + mockWebServer.getPort());
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        // 1. Mock inventory service response
        mockWebServer.enqueue(new MockResponse()
                .setBody("true")
                .addHeader("Content-Type", "application/json"));

        // 2. Build order request
        OrderLineItemsDto itemsDto = OrderLineItemsDto.builder()
                .skuCode("iphone_15")
                .price(BigDecimal.valueOf(1200))
                .quantity(1)
                .build();

        OrderRequest orderRequest = OrderRequest.builder()
                .orderLineItemsDtoList(Collections.singletonList(itemsDto))
                .build();

        // 3. Execute order placement
        orderService.placeOrder(orderRequest);

        // 4. Verify order persisted to database
        assertEquals(1, orderRepository.findAll().size());
    }
}

