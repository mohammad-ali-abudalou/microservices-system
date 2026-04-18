package org.example.orderservice.service;

import org.example.orderservice.dto.OrderLineItemsDto;
import org.example.orderservice.dto.OrderRequest;
import org.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Assertions;
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

import java.math.BigDecimal;
import java.util.Collections;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderServiceApplicationTests {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Autowired
    private OrderRepository orderRepository;

    // --- هذا هو السطر الذي كان ينقصك ---
    @Autowired
    private OrderService orderService;
    // ---------------------------------

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        // 1. بناء عنصر الطلب (Line Item)
        OrderLineItemsDto itemsDto = OrderLineItemsDto.builder()
                .skuCode("iphone_15")
                .price(BigDecimal.valueOf(1200))
                .quantity(1)
                .build();

        // 2. وضع العنصر داخل OrderRequest
        OrderRequest orderRequest = OrderRequest.builder()
                .orderLineItemsDtoList(Collections.singletonList(itemsDto))
                .build();

        // 3. تنفيذ العملية (الآن orderService أصبح معرفاً)
        orderService.placeOrder(orderRequest);

        // 4. التحقق من النتائج
        Assertions.assertEquals(1, orderRepository.findAll().size());
    }
}