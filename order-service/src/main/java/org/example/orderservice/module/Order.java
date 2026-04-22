package org.example.orderservice.module;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

/**
 * JPA entity representing an Order in the e-commerce system.
 * <p>
 * This entity maps to the t_orders table in the database and represents a complete
 * customer order with all its line items. It uses JPA annotations for ORM mapping
 * and includes cascade operations for managing related entities.
 * <p>
 * Key relationships:
 * <ul>
 *   <li>One-to-many relationship with OrderLineItems (cascade all operations)</li>
 *   <li>Auto-generated primary key using IDENTITY strategy</li>
 * </ul>
 *
 * @author Order Service Team
 * @version 1.0
 * @since 2024
 */
@Entity
@Table(name = "t_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderNumber;

    @OneToMany(cascade = CascadeType.ALL)
    private List<OrderLineItems> orderLineItemsList;
}