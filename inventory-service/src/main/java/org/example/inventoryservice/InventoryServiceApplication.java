package org.example.inventoryservice;

import org.example.inventoryservice.module.Inventory;
import org.example.inventoryservice.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableDiscoveryClient
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner loadData(InventoryRepository inventoryRepository) {
        return args -> {
            inventoryRepository.deleteAll();
            inventoryRepository.save(Inventory.builder().skuCode("iphone_15").quantity(100).build());
            inventoryRepository.save(Inventory.builder().skuCode("iphone_15_pro").quantity(0).build());
        };
    }
}