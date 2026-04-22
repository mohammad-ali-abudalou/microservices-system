package org.example.inventoryservice.exception;

/**
 * Exception thrown when inventory for a specific SKU code is not found.
 */
public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(String skuCode) {
        super("Inventory not found for SKU code: " + skuCode);
    }

    public InventoryNotFoundException(String skuCode, Throwable cause) {
        super("Inventory not found for SKU code: " + skuCode, cause);
    }
}