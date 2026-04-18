package org.example.inventoryservice.repository;

import org.example.inventoryservice.module.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findBySkuCode(String skuCode);

    // ميثود للبحث الجماعي لتقليل الـ Queries
    List<Inventory> findBySkuCodeIn(List<String> skuCode);
}