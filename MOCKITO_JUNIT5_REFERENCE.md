# Mockito & JUnit 5 Reference Guide

## Quick Reference for InventoryServiceTest

This document provides quick reference for Mockito and JUnit 5 patterns used in the comprehensive unit tests.

---

## JUnit 5 Annotations

### Test Execution Annotations

| Annotation           | Purpose                       | Example                                  |
|----------------------|-------------------------------|------------------------------------------|
| `@Test`              | Marks method as test          | `@Test void shouldReturnInStock()`       |
| `@BeforeEach`        | Runs before each test         | `@BeforeEach void setUp()`               |
| `@AfterEach`         | Runs after each test          | `@AfterEach void cleanup()`              |
| `@BeforeAll`         | Runs once before all tests    | `@BeforeAll static void initAll()`       |
| `@AfterAll`          | Runs once after all tests     | `@AfterAll static void tearDownAll()`    |
| `@DisplayName`       | Custom test name              | `@DisplayName("Should return in stock")` |
| `@Nested`            | Group related tests           | `@Nested class EdgeCases`                |
| `@ParameterizedTest` | Run test with multiple inputs | `@ParameterizedTest @ValueSource(...)`   |
| `@Disabled`          | Skip test                     | `@Disabled("Not ready")`                 |
| `@Tag`               | Label tests for filtering     | `@Tag("unit")`                           |

---

## Mockito Annotations

### Dependency Injection

| Annotation     | Purpose                  | Example                                  |
|----------------|--------------------------|------------------------------------------|
| `@Mock`        | Creates mock object      | `@Mock InventoryRepository repo;`        |
| `@InjectMocks` | Injects mocks into class | `@InjectMocks InventoryService service;` |
| `@Spy`         | Partial mock (spy)       | `@Spy List<String> list;`                |
| `@Captor`      | Capture arguments        | `@Captor ArgumentCaptor<String> captor;` |

### Extension

| Extension                             | Purpose                   |
|---------------------------------------|---------------------------|
| `@ExtendWith(MockitoExtension.class)` | Enable Mockito in JUnit 5 |

---

## Mockito Core Methods

### 1. Stubbing with `when()`

**Basic Stubbing:**

```java
when(inventoryRepository.findBySkuCodeIn(skuCodes))
        .

thenReturn(Collections.singletonList(iphone15));
```

**Returning Different Values on Multiple Calls:**

```java
when(inventoryRepository.findBySkuCodeIn(skuCodes))
        .

thenReturn(list1)
    .

thenReturn(list2)
    .

thenReturn(list3);
```

**Throwing Exceptions:**

```java
when(inventoryRepository.findBySkuCodeIn(skuCodes))
        .

thenThrow(new RuntimeException("DB Error"));
```

**Using Answer for Custom Behavior:**

```java
when(inventoryRepository.findBySkuCodeIn(anyList()))
        .

thenAnswer(invocation ->{
List<String> args = invocation.getArgument(0);
        return args.

stream()
            .

map(sku ->Inventory.

builder().

skuCode(sku).

build())
        .

toList();
    });
```

---

### 2. Verification with `verify()`

**Basic Verification:**

```java
verify(inventoryRepository).

findBySkuCodeIn(skuCodes);
```

**Verify Call Count:**

```java
verify(inventoryRepository, times(1)).

findBySkuCodeIn(skuCodes);

verify(inventoryRepository, times(2)).

findBySkuCodeIn(skuCodes);

verify(inventoryRepository, never()).

findAll();
```

**Call Count Options:**
| Method | Meaning |
|--------|---------|
| `times(n)` | Called exactly n times |
| `never()` | Never called (times(0)) |
| `atLeastOnce()` | Called 1+ times |
| `atLeast(n)` | Called n+ times |
| `atMost(n)` | Called 0 to n times |
| `only()` | Only method called on mock |

**Verify Call Order:**

```java
InOrder inOrder = inOrder(repo1, repo2);
inOrder.

verify(repo1).

findBySkuCodeIn(list1);
inOrder.

verify(repo2).

findBySkuCode(sku);
```

---

### 3. Argument Matchers

**Exact Matching:**

```java
when(inventoryRepository.findBySkuCodeIn(
        Collections.singletonList("iphone_15")
)).

thenReturn(...);
```

**Flexible Matching:**

```java
when(inventoryRepository.findBySkuCodeIn(anyList()))
        .

thenReturn(...);

when(inventoryRepository.findBySkuCode(anyString()))
        .

thenReturn(...);

when(inventoryRepository.findBySkuCodeIn(argThat(list ->!list.

isEmpty())))
        .

thenReturn(...);
```

**Common Matchers:**
| Matcher | Type Matched |
|---------|------------|
| `any()` | Any object |
| `anyString()` | Any String |
| `anyInt()` | Any int |
| `anyList()` | Any List |
| `anyCollection()` | Any Collection |
| `eq(value)` | Exact value |
| `argThat(predicate)` | Custom predicate |
| `startsWith(prefix)` | String starts with |
| `contains(element)` | List contains |

---

### 4. Argument Capture

**Using ArgumentCaptor:**

```java

@Captor
ArgumentCaptor<List<String>> captor;

@Test
void testCaptureArguments() {
    service.isInStock(Arrays.asList("iphone_15", "samsung_s24"));

    verify(inventoryRepository).findBySkuCodeIn(captor.capture());
    List<String> capturedArgs = captor.getValue();

    assertEquals(2, capturedArgs.size());
    assertTrue(capturedArgs.contains("iphone_15"));
}
```

---

## Test Structure Patterns

### AAA Pattern (Arrange-Act-Assert)

```java

@Test
void testIsInStock_ItemExists_ReturnInStock() {
    // ARRANGE - Set up test data and mocks
    List<String> skuCodes = Collections.singletonList("iphone_15");
    when(inventoryRepository.findBySkuCodeIn(skuCodes))
            .thenReturn(Collections.singletonList(iphone15));

    // ACT - Execute the method under test
    List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

    // ASSERT - Verify the results
    assertNotNull(responses);
    assertEquals(1, responses.size());
    assertTrue(responses.get(0).isInStock());
    verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
}
```

---

## JUnit 5 Assertions

### Basic Assertions

```java
// Equality
assertEquals(expected, actual);

assertNotEquals(unexpected, actual);

// Boolean
assertTrue(condition);

assertFalse(condition);

// Null checks
assertNull(object);

assertNotNull(object);

// Same object reference
assertSame(expected, actual);

assertNotSame(notExpected, actual);

// Collections
assertTrue(responses.isEmpty());

assertEquals(1,responses.size());

assertTrue(responses.contains(item));
```

### Grouped Assertions

```java
assertAll(
    () ->

assertEquals(1,responses.size()),
        ()->

assertTrue(responses.get(0).

isInStock()),
        ()->

assertEquals("iphone_15",responses.get(0).

getSkuCode())
        );
```

### Exception Testing

```java

@Test
void testThrowsException() {
    IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> { /* code that should throw */ }
    );
    assertEquals("Expected message", exception.getMessage());
}
```

---

## Common Test Scenarios & Patterns

### Scenario 1: Happy Path (All Goes Well)

```java

@Test
@DisplayName("Should process successfully with valid input")
void testHappyPath() {
    // Given - Valid input
    List<String> skuCodes = Arrays.asList("iphone_15", "pixel_8");
    when(inventoryRepository.findBySkuCodeIn(skuCodes))
            .thenReturn(Arrays.asList(iphone15, pixelPhone));

    // When - Execute
    List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

    // Then - Verify success
    assertNotNull(responses);
    assertEquals(2, responses.size());
    assertTrue(responses.stream().allMatch(r -> r.isInStock()));
}
```

---

### Scenario 2: Not Found (Empty Result)

```java

@Test
@DisplayName("Should return empty list when items not found")
void testNotFound() {
    // Given - Item doesn't exist
    List<String> skuCodes = Collections.singletonList("nonexistent");
    when(inventoryRepository.findBySkuCodeIn(skuCodes))
            .thenReturn(Collections.emptyList());

    // When - Execute
    List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

    // Then - Verify empty
    assertNotNull(responses, "Response should not be null");
    assertTrue(responses.isEmpty(), "Response should be empty");
}
```

---

### Scenario 3: Boundary Condition (quantity = 0)

```java

@Test
@DisplayName("Should return out of stock when quantity is 0")
void testBoundaryCondition() {
    // Given - Exactly 0 quantity
    List<String> skuCodes = Collections.singletonList("samsung_s24");
    Inventory outOfStock = Inventory.builder()
            .skuCode("samsung_s24")
            .quantity(0)  // Boundary
            .build();
    when(inventoryRepository.findBySkuCodeIn(skuCodes))
            .thenReturn(Collections.singletonList(outOfStock));

    // When
    List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

    // Then
    assertFalse(responses.get(0).isInStock(), "Must be out of stock at qty=0");
}
```

---

### Scenario 4: Error Handling (Exception)

```java

@Test
@DisplayName("Should handle database exception gracefully")
void testErrorHandling() {
    // Given - Mock throws exception
    List<String> skuCodes = Collections.singletonList("iphone_15");
    when(inventoryRepository.findBySkuCodeIn(skuCodes))
            .thenThrow(new RuntimeException("Database connection failed"));

    // When & Then - Verify exception
    RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> inventoryService.isInStock(skuCodes)
    );
    assertEquals("Database connection failed", exception.getMessage());
}
```

---

### Scenario 5: Multiple Calls with Different Responses

```java

@Test
@DisplayName("Should handle different responses on different calls")
void testMultipleCalls() {
    // Given
    List<String> skus1 = Arrays.asList("iphone_15");
    List<String> skus2 = Arrays.asList("samsung_s24");

    when(inventoryRepository.findBySkuCodeIn(skus1))
            .thenReturn(Collections.singletonList(iphone15));
    when(inventoryRepository.findBySkuCodeIn(skus2))
            .thenReturn(Collections.singletonList(samsungS24));

    // When
    List<InventoryResponse> responses1 = inventoryService.isInStock(skus1);
    List<InventoryResponse> responses2 = inventoryService.isInStock(skus2);

    // Then
    assertTrue(responses1.get(0).isInStock());
    assertFalse(responses2.get(0).isInStock());
}
```

---

## Practical Code Examples

### From InventoryServiceTest

**Example 1: Basic Test with Mockito**

```java

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
```

---

**Example 2: Stream Processing with Verification**

```java

@Test
@DisplayName("Should correctly map Inventory to InventoryResponse")
void testIsInStock_CorrectMapping_VerifyDtoProperties() {
    // Given
    List<String> skuCodes = Collections.singletonList("iphone_15");
    when(inventoryRepository.findBySkuCodeIn(skuCodes))
            .thenReturn(Collections.singletonList(iphone15));

    // When
    List<InventoryResponse> responses = inventoryService.isInStock(skuCodes);

    // Then - Verify stream mapping worked
    InventoryResponse response = responses.get(0);
    assertEquals("iphone_15", response.getSkuCode());
    assertTrue(response.isInStock());

    // Verify repository called exactly once
    verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
}
```

---

**Example 3: Multiple Items with Stream Processing**

```java

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

    // Then - Verify mixed results
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
}
```

---

## Common Mistakes to Avoid

### ❌ Mistake 1: Forgetting to Mock

```java
// BAD - Will fail with NullPointerException
@Test
void testBad() {
    inventoryService.isInStock(skuCodes);  // No mock setup!
}

// GOOD - Mock is set up
@Test
void testGood() {
    when(inventoryRepository.findBySkuCodeIn(skuCodes))
            .thenReturn(inventories);
    inventoryService.isInStock(skuCodes);
}
```

---

### ❌ Mistake 2: Multiple Assertions on Same Check

```java
// BAD - Tests multiple things at once
@Test
void testBad() {
    assertEquals(1, responses.size());
    assertTrue(responses.get(0).isInStock());
    assertEquals("iphone_15", responses.get(0).getSkuCode());
}

// GOOD - Use assertAll or separate tests
@Test
void testResponseSize() {
    assertEquals(1, responses.size());
}

@Test
void testStockStatus() {
    assertTrue(responses.get(0).isInStock());
}
```

---

### ❌ Mistake 3: Not Verifying Mock Interactions

```java
// BAD - No verification of mock calls
@Test
void testBad() {
    inventoryService.isInStock(skuCodes);
    // Nothing verified about how repository was called
}

// GOOD - Verify the mock was called correctly
@Test
void testGood() {
    inventoryService.isInStock(skuCodes);
    verify(inventoryRepository, times(1)).findBySkuCodeIn(skuCodes);
}
```

---

### ❌ Mistake 4: Unclear Test Names

```java
// BAD - What does this test?
@Test
void test1() {
}

@Test
void testIsInStock() {
}

// GOOD - Crystal clear
@Test
@DisplayName("Should return 'in stock' when item exists with quantity > 0")
void testIsInStock_ItemExists_ReturnInStock() {
}
```

---

## Running Tests with Different Options

### Run All Unit Tests

```bash
./gradlew test --tests "*Test*"
```

### Run Specific Test Class

```bash
./gradlew test --tests "*InventoryServiceTest*"
```

### Run Specific Test Method

```bash
./gradlew test --tests "*InventoryServiceTest.testIsInStock_ItemExists_ReturnInStock"
```

### Run with Coverage

```bash
./gradlew test jacocoTestReport
```

### Run with Detailed Output

```bash
./gradlew test --info
```

### Run with Tags

```bash
./gradlew test -Dgroups=unit
```

---

## Useful Mockito & JUnit 5 Resources

- **Mockito Official:** https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html
- **JUnit 5 Guide:** https://junit.org/junit5/docs/current/user-guide/
- **Test Patterns:** https://www.baeldung.com/junit-5-tools
- **Spring Testing:** https://spring.io/guides/gs/testing-web/

---

**Status:** ✅ **Complete Reference Guide**  
**Created:** April 21, 2026  
**Updated:** April 21, 2026  
**Framework:** JUnit 5 + Mockito 4.x

