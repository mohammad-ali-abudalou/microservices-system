package org.example.orderservice.repository;

import org.example.orderservice.module.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for Order entity operations.
 * <p>
 * This interface extends JpaRepository to provide CRUD operations for Order entities.
 * It leverages Spring Data JPA to automatically generate implementation at runtime,
 * eliminating the need for boilerplate repository code.
 * <p>
 * Key features:
 * <ul>
 *   <li>Standard CRUD operations (save, findById, findAll, delete)</li>
 *   <li>Automatic query generation based on method naming conventions</li>
 *   <li>Transactional support through Spring's transaction management</li>
 *   <li>Type-safe query methods</li>
 * </ul>
 *
 * @author Order Service Team
 * @version 1.0
 * @since 2024
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
