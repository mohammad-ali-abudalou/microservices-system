package org.example.inventoryservice.repository;

import org.example.inventoryservice.module.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing inventory data persistence operations.
 * Provides methods for querying inventory items by SKU codes with optimized
 * database access patterns to minimize the number of queries.
 *
 * @author Inventory Service Team
 * @version 1.0
 * @since 2024
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Finds a single inventory item by its SKU code.
     * Used for individual product lookups.
     *
     * @param skuCode the unique product identifier
     * @return Optional containing the inventory item if found
     */
    Optional<Inventory> findBySkuCode(String skuCode);

    /**
     * Performs a bulk search for multiple inventory items by their SKU codes.
     * This method optimizes database access by executing a single query
     * instead of multiple individual queries, significantly improving performance
     * when checking stock availability for multiple products simultaneously.
     *
     * @param skuCode list of SKU codes to search for
     * @return list of matching inventory items (may be empty if none found)
     */
    List<Inventory> findBySkuCodeIn(List<String> skuCode);
}