package org.example.inventoryservice.service;

import org.example.inventoryservice.dto.InventoryResponse;
import org.example.inventoryservice.module.Inventory;
import org.example.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InventoryService#isInStock(List)} method.
 * <p>
 * This test class uses JUnit 5 and Mockito to test inventory stock checking functionality
 * in isolation, without requiring a full Spring Boot context or database.
 * <p>
 * Test Scenarios:
 * - Items in stock (quantity > 0)
 * - Items out of stock (quantity = 0)
 * - Mixed stock status (some in, some out)
 * - Empty inventory (no items found)
 * - Multiple items with various quantities
 *
 * @author QA Team
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Unit Tests")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory iphone15;
    private Inventory samsungS24;
    private Inventory pixelPhone;

    @BeforeEach
    void setUp() {
        // Initialize test data
        iphone15 = Inventory.builder()
                .id(1L)
                .skuCode("iphone_15")
                .quantity(50)
                .build();

        samsungS24 = Inventory.builder()
                .id(2L)
                .skuCode("samsung_s24")
                .quantity(0)  // Out of stock
                .build();

        pixelPhone = Inventory.builder()
                .id(3L)
                .skuCode("pixel_8")
                .quantity(25)
                .build();
    }

    // ===================== SUCCESS SCENARIOS =====================

    /**
     * Test: Single item in stock returns correct InventoryResponse.
     */
    @Test
    @DisplayName("Should return 'in stock' when single item exists with quantity > 0")
    void testIsInStock_ItemExists_ReturnInStock() {
        // Given
        List<String> skuCodes = Collections.singletonList("iphone_15");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(Collections.singletonList(iphone15));

        // When
        List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("iphone_15", responses.get(0).getSkuCode());
        assertTrue(responses.get(0).isInStock());
        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
    }

    /**
     * Test: Single item out of stock returns correct InventoryResponse.
     */
    @Test
    @DisplayName("Should return 'out of stock' when single item exists with quantity = 0")
    void testIsInStock_ItemOutOfStock_ReturnNotInStock() {
        // Given
        List<String> skuCodes = Collections.singletonList("samsung_s24");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(Collections.singletonList(samsungS24));

        // When
        List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("samsung_s24", responses.get(0).getSkuCode());
        assertFalse(responses.get(0).isInStock());
        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
    }

    /**
     * Test: Multiple items with mixed stock status.
     */
    @Test
    @DisplayName("Should return mixed stock status for multiple items")
    void testIsInStock_MultipleItems_ReturnMixedStatus() {
        // Given
        List<String> skuCodes = Arrays.asList("iphone_15", "samsung_s24", "pixel_8");
        List<Inventory> inventoryList = Arrays.asList(iphone15, samsungS24, pixelPhone);
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(inventoryList);

        // When
        List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

        // Then
        assertNotNull(responses);
        assertEquals(3, responses.size());

        // Verify each item's stock status
        InventoryResponse iphone = responses.stream()
                .filter(r -> r.getSkuCode().equals("iphone_15"))
                .findFirst()
                .orElseThrow();
        assertTrue(iphone.isInStock());

        InventoryResponse samsung = responses.stream()
                .filter(r -> r.getSkuCode().equals("samsung_s24"))
                .findFirst()
                .orElseThrow();
        assertFalse(samsung.isInStock());

        InventoryResponse pixel = responses.stream()
                .filter(r -> r.getSkuCode().equals("pixel_8"))
                .findFirst()
                .orElseThrow();
        assertTrue(pixel.isInStock());

        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
    }

    // ===================== EDGE CASE SCENARIOS =====================

    /**
     * Test: Item not found in inventory returns empty list.
     */
    @Test
    @DisplayName("Should return empty list when item doesn't exist in inventory")
    void testIsInStock_ItemNotFound_ReturnEmptyList() {
        // Given
        List<String> skuCodes = Collections.singletonList("nonexistent_sku");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(Collections.emptyList());

        // When
        List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

        // Then
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
    }

    /**
     * Test: Empty SKU code list returns empty response list.
     */
    @Test
    @DisplayName("Should return empty list when empty SKU code list is provided")
    void testIsInStock_EmptySkuList_ReturnEmptyList() {
        // Given
        List<String> skuCodes = Collections.emptyList();
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(Collections.emptyList());

        // When
        List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

        // Then
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
    }

    /**
     * Test: Large quantity item returns in stock status.
     */
    @Test
    @DisplayName("Should return 'in stock' for item with large quantity")
    void testIsInStock_LargeQuantity_ReturnInStock() {
        // Given
        Inventory highStockItem = Inventory.builder()
                .id(4L)
                .skuCode("popular_product")
                .quantity(1000)
                .build();
        List<String> skuCodes = Collections.singletonList("popular_product");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(Collections.singletonList(highStockItem));

        // When
        List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertTrue(responses.get(0).isInStock());
        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
    }

    /**
     * Test: Item with quantity 1 returns in stock status.
     */
    @Test
    @DisplayName("Should return 'in stock' when quantity equals 1")
    void testIsInStock_QuantityOne_ReturnInStock() {
        // Given
        Inventory singleItem = Inventory.builder()
                .id(5L)
                .skuCode("last_item")
                .quantity(1)
                .build();
        List<String> skuCodes = Collections.singletonList("last_item");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(Collections.singletonList(singleItem));

        // When
        List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertTrue(responses.get(0).isInStock());
        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
    }

    /**
     * Test: InventoryResponse mapping preserves SKU code and stock status.
     */
    @Test
    @DisplayName("Should correctly map Inventory to InventoryResponse")
    void testIsInStock_CorrectMapping_VerifyDtoProperties() {
        // Given
        List<String> skuCodes = Collections.singletonList("iphone_15");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(Collections.singletonList(iphone15));

        // When
        List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());

        InventoryResponse response = responses.get(0);
        assertEquals("iphone_15", response.getSkuCode());
        assertTrue(response.isInStock());
        assertTrue(response.isInStock()); // Verify isInStock is called correctly (quantity > 0)
        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
    }

    // ===================== VERIFICATION SCENARIOS =====================

    /**
     * Test: Repository is called exactly once with correct parameters.
     */
    @Test
    @DisplayName("Should call repository method exactly once with correct parameters")
    void testIsInStock_VerifyRepositoryInteraction() {
        // Given
        List<String> skuCodes = Arrays.asList("iphone_15", "samsung_s24");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(Arrays.asList(iphone15, samsungS24));

        // When
        inventoryService.isInStock(skuCodes);

        // Then
        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
        verify(inventoryRepository, never()).findAll();
        verify(inventoryRepository, never()).findBySkuCode(anyString());
    }

    /**
     * Test: Response maintains order from repository.
     */
    @Test
    @DisplayName("Should maintain order of responses matching repository order")
    void testIsInStock_MaintainOrder() {
        // Given
        List<String> skuCodes = Arrays.asList("pixel_8", "iphone_15", "samsung_s24");
        List<Inventory> inventoryList = Arrays.asList(pixelPhone, iphone15, samsungS24);
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(inventoryList);

        // When
        List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

        // Then
        assertNotNull(responses);
        assertEquals(3, responses.size());
        assertEquals("pixel_8", responses.get(0).getSkuCode());
        assertEquals("iphone_15", responses.get(1).getSkuCode());
        assertEquals("samsung_s24", responses.get(2).getSkuCode());
        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
    }

    /**
     * Test: Response list is not null even with empty repository result.
     */
    @Test
    @DisplayName("Should return non-null empty list when no items found")
    void testIsInStock_ReturnNonNull_WhenEmpty() {
        // Given
        List<String> skuCodes = Arrays.asList("unknown_1", "unknown_2");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(Collections.emptyList());

        // When
        List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

        // Then
        assertNotNull(responses, "Response should not be null");
        assertEquals(0, responses.size());
        assertFalse(responses.getClass().equals(ArrayList.class) && responses.isEmpty() == false);
        verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
    }
}

