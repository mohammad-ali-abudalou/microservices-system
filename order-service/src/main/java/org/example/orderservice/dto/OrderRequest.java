package org.example.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object for order placement requests.
 * <p>
 * This class represents the incoming request payload when a client places a new order.
 * It contains the list of order line items with their details and uses Bean Validation
 * to ensure data integrity before processing.
 * <p>
 * Validation rules:
 * <ul>
 *   <li>Order line items list cannot be empty</li>
 *   <li>Each order line item must be valid according to its own constraints</li>
 * </ul>
 *
 * @author Order Service Team
 * @version 1.0
 * @since 2024
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderRequest {
    @NotEmpty(message = "Order line items cannot be empty")
    @Valid
    private List<OrderLineItemsDto> orderLineItemsDtoList;
}