package org.example.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.dto.ErrorResponse;
import org.example.orderservice.dto.OrderRequest;
import org.example.orderservice.dto.OrderResponse;
import org.example.orderservice.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for handling order-related HTTP requests.
 * <p>
 * This controller provides endpoints for order placement and management in the e-commerce system.
 * It validates incoming requests, processes orders through the service layer, and returns
 * appropriate responses. The controller integrates with Spring Security for authentication
 * and uses structured logging for request tracking.
 * <p>
 * Key features:
 * <ul>
 *   <li>JWT-based authentication for secure order placement</li>
 *   <li>Input validation using Bean Validation annotations</li>
 *   <li>Comprehensive error handling with custom error responses</li>
 *   <li>OpenAPI documentation for API specification</li>
 *   <li>Structured logging for request/response tracking</li>
 * </ul>
 *
 * @author Order Service Team
 * @version 1.0
 * @since 2024
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Management", description = "APIs for managing customer orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place a new order", description = "Creates a new customer order after validating inventory availability")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Order created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid order data or insufficient inventory",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest orderRequest) {
        log.info("REST request to place order: {}", orderRequest);

        String orderNumber = orderService.placeOrder(orderRequest);
        OrderResponse response = OrderResponse.builder()
                .orderNumber(orderNumber)
                .message("Order Processed Successfully")
                .status(HttpStatus.CREATED.value())
                .build();

        log.info("Order placed successfully with order number: {}", orderNumber);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}