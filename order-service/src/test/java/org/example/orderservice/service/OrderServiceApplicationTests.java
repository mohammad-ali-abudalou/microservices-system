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
 * These tests verify the complete order placement workflow with real infrastructure components
 * including MySQL database, Kafka message broker, and mocked external services. The tests
 * ensure that all integrations work correctly in a containerized environment.
 * <p>
 * Test infrastructure:
 * <ul>
 *   <li>MySQL container for database operations</li>
 *   <li>Kafka container for event publishing</li>
 *   <li>MockWebServer for simulating inventory service responses</li>
 *   <li>Spring Boot test context with full application configuration</li>
 * </ul>
 * <p>
 * Note: These are integration tests that run slower than unit tests but provide
 * higher confidence in system behavior.
 *
 * @author Order Service Team
 * @version 1.0
 * @since 2024
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
        // Mock inventory service response
        mockWebServer.enqueue(new MockResponse()
                .setBody("true")
                .addHeader("Content-Type", "application/json"));

        // Build order request
        OrderLineItemsDto itemsDto = OrderLineItemsDto.builder()
                .skuCode("iphone_15")
                .price(BigDecimal.valueOf(1200))
                .quantity(1)
                .build();

        OrderRequest orderRequest = OrderRequest.builder()
                .orderLineItemsDtoList(Collections.singletonList(itemsDto))
                .build();

        // Execute order placement
        orderService.placeOrder(orderRequest);

        // Verify order persisted to database
        assertEquals(1, orderRepository.findAll().size());
    }
}
