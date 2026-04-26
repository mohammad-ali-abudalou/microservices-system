# Spring Boot 3.3 OAuth2/Keycloak Security Configuration Guide

## Complete Implementation Guide for OAuth2 JWT Validation & Token Relay

This guide provides complete code examples and configurations for implementing OAuth2 security in your microservices with Spring Cloud Gateway token relay.

---

## Table of Contents
1. [Security Configuration Classes](#security-configuration-classes)
2. [Service Authorization Examples](#service-authorization-examples)
3. [Gateway Token Relay Setup](#gateway-token-relay-setup)
4. [JWT Claim Extraction](#jwt-claim-extraction)
5. [Complete Bootstrap Configuration](#complete-bootstrap-configuration)

---

## Security Configuration Classes

### 1. API Gateway Security Configuration

**File:** `api-gateway/src/main/java/org/example/apigateway/config/SecurityConfig.java`

```java
package org.example.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures security for the API Gateway
     * - All requests must have a valid JWT token (except /actuator/health)
     * - Enables OAuth2 Resource Server with JWT validation
     * - Configures CORS to allow requests from frontend
     * - Uses stateless session (no cookies)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeHttpRequests(authorize -> authorize
                        // Allow health check without authentication
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Allow Eureka-related endpoints
                        .requestMatchers("/eureka/**").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                // Enable OAuth2 Resource Server with JWT bearer token validation
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                // Use default JWT decoder configuration from application properties
                                .jwtAuthenticationConverter(new CustomJwtAuthenticationConverter())
                        )
                )
                // Use stateless session (microservices don't store sessions)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .cors();

        return http.build();
    }

    /**
     * CORS Configuration - Allows requests from frontend applications
     * In production, restrict origins to specific domain
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:4200"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Custom JWT Decoder (optional - Spring handles this automatically)
     * Use only if you need custom JWT validation logic
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Spring Boot automatically creates this based on issuer-uri property
        // This bean is here for reference only
        return NimbusJwtDecoder.withJwtProcessingEndpoint("http://keycloak:8080/realms/master/protocol/openid-connect/certs")
                .build();
    }
}
```

### 2. Custom JWT Authentication Converter (Optional)

**File:** `api-gateway/src/main/java/org/example/apigateway/config/CustomJwtAuthenticationConverter.java`

```java
package org.example.apigateway.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Custom converter that extracts authorities from JWT token claims.
 * This allows Spring Security to recognize user roles from Keycloak.
 */
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Extract username from token claims
        String principalClaimValue = getPrincipalClaimValue(jwt);

        // Extract roles/authorities from token claims
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);

        return new UsernamePasswordAuthenticationToken(
                principalClaimValue,
                "N/A",
                authorities
        );
    }

    /**
     * Extracts the principal name from JWT claims.
     * Tries multiple claim names for compatibility with different OIDC providers.
     */
    private String getPrincipalClaimValue(Jwt jwt) {
        String claimName = "preferred_username";
        if (jwt.containsClaim(claimName)) {
            return jwt.getClaimAsString(claimName);
        }
        claimName = "sub";
        if (jwt.containsClaim(claimName)) {
            return jwt.getClaimAsString(claimName);
        }
        return jwt.getSubject();
    }

    /**
     * Extracts authorities (roles) from JWT token.
     * Supports multiple role claim structures:
     * - realm_access.roles (Keycloak standard)
     * - resource_access.{client_id}.roles (Keycloak client roles)
     * - roles (generic)
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        // Extract realm roles
        Collection<String> realmRoles = extractRealmRoles(jwt);
        realmRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .forEach(authorities::add);

        // Extract client roles
        // (Optional - uncomment if needed)
        // Collection<String> clientRoles = extractClientRoles(jwt);
        // clientRoles.stream()
        //         .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
        //         .forEach(authorities::add);

        return authorities;
    }

    /**
     * Extracts realm-level roles from JWT token
     */
    private Collection<String> extractRealmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> realmAccessMap = (Map<String, Object>) realmAccess;
            Object roles = realmAccessMap.get("roles");
            if (roles instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<String> rolesList = (Collection<String>) roles;
                return rolesList;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Extracts client-specific roles from JWT token (optional)
     */
    @SuppressWarnings("unchecked")
    private Collection<String> extractClientRoles(Jwt jwt) {
        Object resourceAccess = jwt.getClaims().get("resource_access");
        if (resourceAccess instanceof Map) {
            Map<String, Object> resourceAccessMap = (Map<String, Object>) resourceAccess;
            Object clientAccess = resourceAccessMap.get("postman-client");
            if (clientAccess instanceof Map) {
                Map<String, Object> clientAccessMap = (Map<String, Object>) clientAccess;
                Object roles = clientAccessMap.get("roles");
                if (roles instanceof Collection) {
                    return (Collection<String>) roles;
                }
            }
        }
        return Collections.emptyList();
    }
}
```

### 3. Order Service Security Configuration

**File:** `order-service/src/main/java/org/example/orderservice/config/SecurityConfig.java`

```java
package org.example.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enables @PreAuthorize, @PostAuthorize annotations
public class SecurityConfig {

    /**
     * Security configuration for Order Service
     * - All endpoints except /actuator/health require JWT
     * - Stateless session (no cookies)
     * - OAuth2 Resource Server validates JWT tokens from Keycloak
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                // Enable OAuth2 Resource Server with JWT validation
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }
}
```

### 4. Inventory Service Security Configuration

**File:** `inventory-service/src/main/java/org/example/inventoryservice/config/SecurityConfig.java`

```java
package org.example.inventoryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enables @PreAuthorize, @PostAuthorize annotations
public class SecurityConfig {

    /**
     * Security configuration for Inventory Service
     * Same as Order Service
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }
}
```

---

## Service Authorization Examples

### 1. Order Service Controller with JWT Authorization

**File:** `order-service/src/main/java/org/example/orderservice/controller/OrderController.java`

```java
package org.example.orderservice.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    /**
     * Get all orders - Requires authentication with valid JWT
     * Extracts user info from JWT token
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('user', 'admin')")  // Require user or admin role
    public ResponseEntity<?> getAllOrders(Authentication authentication) {
        // Get JWT token from authentication
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            String email = jwt.getClaimAsString("email");
            String subject = jwt.getSubject();
            
            String responseMsg = String.format(
                    "Orders retrieved for user: %s (email: %s, subject: %s)",
                    username, email, subject
            );
            return ResponseEntity.ok(responseMsg);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No authentication found");
    }

    /**
     * Place a new order - Requires user role
     * Only authenticated users can place orders
     */
    @PostMapping("/place-order")
    @PreAuthorize("hasRole('user')")  // Only users can place orders
    public ResponseEntity<?> placeOrder(
            @RequestBody OrderRequest orderRequest,
            Authentication authentication) {
        
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("preferred_username");
        
        // Business logic to place order
        String orderId = "ORD-" + System.currentTimeMillis();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
                "Order placed successfully. Order ID: " + orderId + 
                " for user: " + userId
        );
    }

    /**
     * Update order - Requires admin role or order owner
     * Administrative operation
     */
    @PutMapping("/{orderId}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<?> updateOrder(
            @PathVariable String orderId,
            @RequestBody OrderRequest orderRequest) {
        return ResponseEntity.ok("Order " + orderId + " updated");
    }

    /**
     * Delete order - Requires admin role
     * Administrative operation
     */
    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        return ResponseEntity.ok("Order " + orderId + " deleted");
    }

    /**
     * Get order by ID - Requires authentication
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("authenticated")
    public ResponseEntity<?> getOrderById(
            @PathVariable String orderId,
            Authentication authentication) {
        
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String username = jwt.getClaimAsString("preferred_username");
        
        return ResponseEntity.ok(
                "Order " + orderId + " retrieved for user: " + username
        );
    }
}
```

### 2. Order Request DTO

**File:** `order-service/src/main/java/org/example/orderservice/controller/OrderRequest.java`

```java
package org.example.orderservice.controller;

import java.util.List;
import java.math.BigDecimal;

public class OrderRequest {
    private String orderNumber;
    private String userId;
    private BigDecimal totalPrice;
    private List<OrderLineItem> orderLineItemsList;

    // Getters and Setters
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public List<OrderLineItem> getOrderLineItemsList() { return orderLineItemsList; }
    public void setOrderLineItemsList(List<OrderLineItem> orderLineItemsList) { 
        this.orderLineItemsList = orderLineItemsList; 
    }

    public static class OrderLineItem {
        private String skuCode;
        private BigDecimal price;
        private Integer quantity;

        // Getters and Setters
        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
```

### 3. Inventory Service Controller

**File:** `inventory-service/src/main/java/org/example/inventoryservice/controller/InventoryController.java`

```java
package org.example.inventoryservice.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    /**
     * Get all inventory items - Requires authentication
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('user', 'admin')")
    public ResponseEntity<?> getAllInventory(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String username = jwt.getClaimAsString("preferred_username");
        
        return ResponseEntity.ok("Inventory items for user: " + username);
    }

    /**
     * Check stock for a specific SKU - Requires authentication
     */
    @GetMapping("/{skuCode}")
    @PreAuthorize("authenticated")
    public ResponseEntity<?> checkStock(@PathVariable String skuCode) {
        return ResponseEntity.ok("Stock for SKU " + skuCode + " is available");
    }

    /**
     * Update inventory - Requires admin role
     */
    @PutMapping("/{skuCode}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<?> updateInventory(
            @PathVariable String skuCode,
            @RequestBody InventoryRequest request) {
        return ResponseEntity.ok("Inventory for SKU " + skuCode + " updated");
    }
}
```

---

## Gateway Token Relay Setup

### Complete Application Configuration

**File:** `api-gateway/src/main/resources/application.yaml` (docker profile)

```yaml
---
spring:
  config:
    activate:
      on-profile: docker

  application:
    name: api-gateway

  cloud:
    gateway:
      # Discovery locator to auto-register routes from Eureka
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true

      # Manual route definitions with token relay
      routes:
        # Route 1: Order Service
        - id: order-service
          uri: lb://order-service  # Load balance using Eureka
          predicates:
            - Path=/api/order/**
          filters:
            # IMPORTANT: TokenRelay filter passes JWT to downstream service
            - TokenRelay
            - name: CircuitBreaker
              args:
                name: order-service
                fallbackUri: forward:/orderFallback
            - name: Retry
              args:
                retries: 3
                methods: GET,POST,PUT,DELETE
            # Add request/response logging filter
            - name: RequestSize
              args:
                maxSize: 5000000  # 5MB max request size

        # Route 2: Inventory Service
        - id: inventory-service
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**
          filters:
            # IMPORTANT: TokenRelay filter passes JWT to downstream service
            - TokenRelay
            - name: CircuitBreaker
              args:
                name: inventory-service
                fallbackUri: forward:/inventoryFallback
            - name: Retry
              args:
                retries: 3
                methods: GET,POST,PUT,DELETE
            - name: RequestSize
              args:
                maxSize: 5000000

    config:
      import: "optional:configserver:${CONFIG_SERVER_URL:http://config-server:8888}"
      fail-fast: true
      retry:
        initial-interval: 1000
        max-interval: 2000
        multiplier: 1.1
        max-attempts: 10

  # OAuth2 Resource Server Configuration
  security:
    oauth2:
      resourceserver:
        jwt:
          # Issuer URI: Where to find the JWT issuer metadata
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8080}/realms/master
          # JWK Set URI: Where to find the public keys for JWT validation
          jwk-set-uri: ${KEYCLOAK_URL:http://keycloak:8080}/realms/master/protocol/openid-connect/certs

  main:
    allow-bean-definition-overriding: true

server:
  port: 9000

eureka:
  instance:
    hostname: api-gateway
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://discovery-server:8761/eureka/}

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,tracing
  tracing:
    sampling:
      probability: 0.1  # Sample 10% of requests in production
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_URL:http://zipkin:9411/api/v2/spans}

# Resilience4j Configuration
resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        waitDurationInOpenState: 5s
        failureRateThreshold: 50

  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 100ms

  ratelimiter:
    configs:
      default:
        limitForPeriod: 100
        limitRefreshPeriod: 1m
```

---

## JWT Claim Extraction

### Utility Class for JWT Access

**File:** `order-service/src/main/java/org/example/orderservice/util/JwtUtil.java`

```java
package org.example.orderservice.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class JwtUtil {

    /**
     * Extract username from JWT token
     */
    public static String getUsername(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return null;
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaimAsString("preferred_username");
    }

    /**
     * Extract email from JWT token
     */
    public static String getEmail(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return null;
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaimAsString("email");
    }

    /**
     * Extract user ID from JWT token
     */
    public static String getUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return null;
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getSubject();
    }

    /**
     * Extract roles from JWT token
     */
    public static Set<String> getRoles(Authentication authentication) {
        Set<String> roles = new LinkedHashSet<>();
        
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return roles;
        }
        
        Jwt jwt = (Jwt) authentication.getPrincipal();
        
        // Try to extract roles from realm_access
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> realmAccessMap = 
                    (java.util.Map<String, Object>) realmAccess;
            Object rolesObj = realmAccessMap.get("roles");
            if (rolesObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<String> rolesList = (Collection<String>) rolesObj;
                roles.addAll(rolesList);
            }
        }
        
        return roles;
    }

    /**
     * Check if user has a specific role
     */
    public static boolean hasRole(Authentication authentication, String role) {
        return getRoles(authentication).contains(role);
    }

    /**
     * Get all claims from JWT token
     */
    public static java.util.Map<String, Object> getAllClaims(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return new java.util.HashMap<>();
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaims();
    }
}
```

---

## Complete Bootstrap Configuration

### Order Service Bootstrap Configuration

**File:** `order-service/src/main/resources/bootstrap.yml`

```yaml
spring:
  application:
    name: order-service
  cloud:
    config:
      uri: ${CONFIG_SERVER_URL:http://localhost:8888}
      fail-fast: true
      retry:
        initial-interval: 1000
        max-interval: 2000
        multiplier: 1.1
        max-attempts: 10
      username: ${CONFIG_SERVER_USERNAME:admin}
      password: ${CONFIG_SERVER_PASSWORD:admin}
```

### Inventory Service Bootstrap Configuration

**File:** `inventory-service/src/main/resources/bootstrap.yml`

```yaml
spring:
  application:
    name: inventory-service
  cloud:
    config:
      uri: ${CONFIG_SERVER_URL:http://localhost:8888}
      fail-fast: true
      retry:
        initial-interval: 1000
        max-interval: 2000
        multiplier: 1.1
        max-attempts: 10
      username: ${CONFIG_SERVER_USERNAME:admin}
      password: ${CONFIG_SERVER_PASSWORD:admin}
```

---

## Testing the Configuration

### 1. Start Docker Containers
```bash
cd D:\workspaces\microservices-system
docker-compose up -d
```

### 2. Run Keycloak Setup Script
```powershell
# PowerShell
.\setup-keycloak-oauth2.ps1

# Or with parameters
.\setup-keycloak-oauth2.ps1 -KeycloakUrl "http://localhost:8080"
```

### 3. Get Access Token
```bash
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=postman-client" \
  -d "client_secret=postman-client-secret-123" \
  -d "grant_type=client_credentials"
```

### 4. Call Protected Endpoint
```bash
# Replace TOKEN with actual token from previous step
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:9000/api/order/all
```

---

## Troubleshooting Checklist

- [ ] Keycloak container is running and healthy
- [ ] Keycloak health endpoint returns 200: `http://localhost:8080/health/ready`
- [ ] OAuth2 client is created in Keycloak with correct redirect URIs
- [ ] KEYCLOAK_URL environment variable is set to `http://keycloak:8080` in docker-compose
- [ ] issuer-uri and jwk-set-uri use environment variable in application.yml
- [ ] TokenRelay filter is configured in gateway routes
- [ ] Services are on the same Docker network (spring-network)
- [ ] JWT token is passed in Authorization header as `Bearer <token>`
- [ ] Token is not expired (check expiration at jwt.io)

---

## Summary of Changes

1. ✅ Updated all configuration files to use environment variables for Keycloak URL
2. ✅ Added retry logic for Config Server connections
3. ✅ Added health checks for Keycloak in docker-compose.yml
4. ✅ Configured TokenRelay filter in API Gateway
5. ✅ Created comprehensive security configurations for all services
6. ✅ Added JWT claim extraction utilities
7. ✅ Implemented role-based authorization with @PreAuthorize annotations


