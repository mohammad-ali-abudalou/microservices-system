package org.example.inventoryservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.inventoryservice.dto.InventoryResponse;
import org.example.inventoryservice.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory Management", description = "APIs for checking product inventory availability")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Check inventory availability",
            description = "Checks if the specified products are in stock")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Inventory check completed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InventoryResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<InventoryResponse>> checkInventory(
            @Parameter(description = "List of SKU codes to check", required = true)
            @RequestParam List<String> skuCode) {

        log.info("REST request to check inventory for SKU codes: {}", skuCode);

        List<InventoryResponse> responses = inventoryService.isInStock(skuCode);

        log.info("Inventory check completed for {} items", responses.size());
        return ResponseEntity.ok(responses);
    }
}