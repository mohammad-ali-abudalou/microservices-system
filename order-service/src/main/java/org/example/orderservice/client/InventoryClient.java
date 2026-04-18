package org.example.orderservice.client;

import org.example.orderservice.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "inventory-service")
public interface InventoryClient {
    // إرسال قائمة SkuCodes للفحص الجماعي
    @GetMapping("/api/inventory")
    List<InventoryResponse> checkStock(@RequestParam List<String> skuCode);
}