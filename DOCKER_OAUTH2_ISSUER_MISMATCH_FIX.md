# Advanced Spring Security OAuth2 Configuration for Docker OAuth2 Issuer Mismatch

## Problem Analysis

**The Core Issue:** 
- Your Keycloak token's `iss` claim: `http://localhost:8080/realms/master`
- Gateway tries to validate against: `http://keycloak:8080/realms/master` (Docker internal)
- Spring's default JwtDecoder silently rejects the mismatch
- No logs appear because rejection happens in Security Filter Chain before routing

**Why Silent:**
- `issuer-uri` property is set during JwtDecoder bean initialization
- Validation happens early in security filter chain
- By default, Spring Security logs are at WARN/ERROR level for this flow
- The error is caught and converted to 401 without details

---

## Solution 1: Custom OAuth2 Resource Server Configuration

**File:** `api-gateway/src/main/java/org/example/apigateway/config/CustomOAuth2ResourceServerConfig.java`

```java
package org.example.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Custom OAuth2 Resource Server Configuration for Docker environment.
 * 
 * This configuration handles the issuer mismatch problem where:
 * - Keycloak token has issuer: http://localhost:8080/realms/master
 * - Gateway connects to: http://keycloak:8080 (Docker internal)
 * 
 * Solution: Accept tokens from multiple issuer variations and validate JWK sets
 * from the Docker internal URL.
 */
@Configuration
public class CustomOAuth2ResourceServerConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Custom JwtDecoder that handles issuer mismatch:
     * 1. Fetches JWK set from Docker internal URL (keycloak:8080)
     * 2. Accepts tokens with multiple issuer variations
     * 3. Properly logs validation failures
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Step 1: Create NimbusJwtDecoder pointing to Docker internal JWK URL
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwtProcessingEndpoint(jwkSetUri)
                .build();

        // Step 2: Create custom token validators to accept issuer variations
        OAuth2TokenValidator<Jwt> issuerValidator = new DelegatingOAuth2TokenValidator<>(
                // Accept BOTH localhost and keycloak container name issuers
                new JwtIssuerValidator("http://localhost:8080/realms/master"),
                new JwtIssuerValidator("http://keycloak:8080/realms/master")
        );

        // Step 3: Add additional validators (expiration, etc.)
        OAuth2TokenValidator<Jwt> withDefaults = new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                new JwtTimestampValidator()
        );

        // Step 4: Set the composite validator
        jwtDecoder.setJwtValidator(withDefaults);

        return jwtDecoder;
    }

    /**
     * Custom Issuer Validator that accepts multiple issuer URIs.
     * This validator logs the validation process for debugging.
     */
    public static class JwtIssuerValidator implements OAuth2TokenValidator<Jwt> {
        private final String issuer;

        public JwtIssuerValidator(String issuer) {
            this.issuer = issuer;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            String tokenIssuer = token.getIssuerAsString();
            
            // Log validation attempt
            System.out.println("[JWT VALIDATION] Checking issuer:");
            System.out.println("  Token issuer: " + tokenIssuer);
            System.out.println("  Expected: " + issuer);
            System.out.println("  Match: " + issuer.equals(tokenIssuer));

            if (issuer.equals(tokenIssuer)) {
                return OAuth2TokenValidatorResult.success();
            }

            String message = String.format("Issuer '%s' mismatch. Expected: '%s'", tokenIssuer, issuer);
            System.out.println("[JWT VALIDATION ERROR] " + message);
            
            return OAuth2TokenValidatorResult.failure(
                    new JwtValidationException(message, null)
            );
        }
    }

    /**
     * WebFlux Security configuration for Spring Cloud Gateway.
     * Gateway uses WebFlux (reactive) stack, not WebMvc.
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf().disable()  // Gateway doesn't use CSRF
                .authorizeExchange(exchanges -> exchanges
                        // Allow health endpoints without auth
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/eureka/**").permitAll()
                        // All other routes require authentication
                        .anyExchange().authenticated()
                )
                // OAuth2 Resource Server - validates incoming tokens
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder())
                                .jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverter())
                        )
                        // Custom error handling with logging
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                )
                .cors();

        return http.build();
    }

    /**
     * CORS configuration for API Gateway
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:4200"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Custom entry point that logs authentication failures
     */
    public static class CustomAuthenticationEntryPoint 
            implements org.springframework.security.web.server.authentication.ServerAuthenticationEntryPoint {

        @Override
        public reactor.core.publisher.Mono<Void> commence(
                org.springframework.web.server.ServerWebExchange exchange,
                org.springframework.security.core.AuthenticationException ex) {

            // Log the authentication failure
            System.out.println("[SECURITY FILTER] Authentication failed:");
            System.out.println("  Path: " + exchange.getRequest().getPath());
            System.out.println("  Method: " + exchange.getRequest().getMethod());
            System.out.println("  Exception: " + ex.getClass().getSimpleName());
            System.out.println("  Message: " + ex.getMessage());

            // Log the cause chain
            Throwable cause = ex;
            int depth = 0;
            while (cause != null && depth < 5) {
                System.out.println("    Cause[" + depth + "]: " + cause.getClass().getSimpleName() 
                        + " - " + cause.getMessage());
                cause = cause.getCause();
                depth++;
            }

            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * Custom JWT Authentication Converter for reactive stack
     */
    public static class ReactiveJwtAuthenticationConverter 
            implements org.springframework.core.convert.converter.Converter<Jwt, 
                    reactor.core.publisher.Mono<org.springframework.security.authentication.AbstractAuthenticationToken>> {

        @Override
        public reactor.core.publisher.Mono<org.springframework.security.authentication.AbstractAuthenticationToken> convert(Jwt jwt) {
            // Extract roles from JWT
            var authorities = extractAuthorities(jwt)
                    .stream()
                    .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority(
                            role.startsWith("ROLE_") ? role : "ROLE_" + role
                    ))
                    .collect(java.util.stream.Collectors.toSet());

            var token = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    jwt.getSubject(),
                    "N/A",
                    authorities
            );
            token.setDetails(jwt);

            return reactor.core.publisher.Mono.just(token);
        }

        private java.util.Collection<String> extractAuthorities(Jwt jwt) {
            java.util.Set<String> authorities = new java.util.HashSet<>();
            Object realmAccess = jwt.getClaims().get("realm_access");
            if (realmAccess instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> realmAccessMap = (java.util.Map<String, Object>) realmAccess;
                Object roles = realmAccessMap.get("roles");
                if (roles instanceof java.util.Collection) {
                    @SuppressWarnings("unchecked")
                    java.util.Collection<String> rolesList = (java.util.Collection<String>) roles;
                    authorities.addAll(rolesList);
                }
            }
            return authorities;
        }
    }
}
```

---

## Solution 2: Logging Configuration for Security Events

**File:** `api-gateway/src/main/resources/logback-spring.xml` (CREATE NEW)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Appender for console output -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Appender for file output (optional) -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/api-gateway.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/api-gateway-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
    </appender>

    <!-- Spring Security Logging -->
    <logger name="org.springframework.security" level="DEBUG" />
    <logger name="org.springframework.security.oauth2" level="DEBUG" />
    <logger name="org.springframework.security.jwt" level="DEBUG" />
    <logger name="com.nimbusds.jwt" level="DEBUG" />

    <!-- Spring Cloud Gateway Logging -->
    <logger name="org.springframework.cloud.gateway" level="DEBUG" />
    <logger name="org.springframework.web.reactive.resource" level="DEBUG" />

    <!-- Application Logging -->
    <logger name="org.example.apigateway" level="DEBUG" />

    <!-- Root Logger -->
    <root level="INFO">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

---

## Solution 3: Optimized YAML Configuration

**File:** `api-gateway/src/main/resources/application.yml` (UPDATE docker profile)

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
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/order/**
          filters:
            - TokenRelay
            - name: CircuitBreaker
              args:
                name: order-service
                fallbackUri: forward:/orderFallback
            - name: Retry
              args:
                retries: 3
                methods: GET,POST,PUT,DELETE
        
        - id: inventory-service
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**
          filters:
            - TokenRelay
            - name: CircuitBreaker
              args:
                name: inventory-service
            - name: Retry
              args:
                retries: 3
                methods: GET,POST,PUT,DELETE

    config:
      import: "optional:configserver:${CONFIG_SERVER_URL:http://config-server:8888}"
      fail-fast: true
      retry:
        initial-interval: 1000
        max-interval: 2000
        multiplier: 1.1
        max-attempts: 10

  security:
    oauth2:
      resourceserver:
        jwt:
          # CRITICAL: Use Docker internal container name
          issuer-uri: ${KEYCLOAK_URL:http://keycloak:8080}/realms/master
          # CRITICAL: JWK Set URL must also use Docker container name
          jwk-set-uri: ${KEYCLOAK_URL:http://keycloak:8080}/realms/master/protocol/openid-connect/certs

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
  endpoint:
    health:
      show-details: always
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_URL:http://zipkin:9411/api/v2/spans}

  # Enable DEBUG logs for security
  logging:
    level:
      org.springframework.security: DEBUG
      org.springframework.security.oauth2: DEBUG
      org.springframework.cloud.gateway: DEBUG
```

---

## Solution 4: application.yml for Downstream Services

**File:** `order-service/src/main/resources/application.yml` (UPDATE docker profile)

```yaml
---
spring:
  config:
    activate:
      on-profile: docker

  application:
    name: order-service

  cloud:
    config:
      import: "optional:configserver:${CONFIG_SERVER_URL:http://config-server:8888}"
      fail-fast: true
      retry:
        initial-interval: 1000
        max-interval: 2000
        multiplier: 1.1
        max-attempts: 10

  # Database configuration
  datasource:
    url: jdbc:mysql://${DB_HOST:mysql-order}:3306/order_service?createDatabaseIfNotExist=true&autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=30000&socketTimeout=60000&serverTimezone=UTC
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      connection-timeout: 30000
      maximum-pool-size: 10
      minimum-idle: 5
      idle-timeout: 600000
      max-lifetime: 1800000

  jpa:
    hibernate:
      ddl-auto: update
    database-platform: org.hibernate.dialect.MySQL8Dialect

  # Security: OAuth2 Resource Server
  security:
    oauth2:
      resourceserver:
        jwt:
          # CRITICAL: Use Docker internal container name
          issuer-uri: ${KEYCLOAK_URL:http://keycloak:8080}/realms/master
          # CRITICAL: JWK Set URL must also use Docker container name
          jwk-set-uri: ${KEYCLOAK_URL:http://keycloak:8080}/realms/master/protocol/openid-connect/certs

server:
  port: 8082

eureka:
  instance:
    hostname: order-service
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://discovery-server:8761/eureka/}

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,tracing
  endpoint:
    health:
      show-details: always
  tracing:
    sampling:
      probability: 0.1
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_URL:http://zipkin:9411/api/v2/spans}

  # Enable DEBUG logs for security
  logging:
    level:
      org.springframework.security: DEBUG
      org.springframework.security.oauth2: DEBUG
```

---

## Solution 5: Debugging Script

**File:** `debug-jwt-validation.ps1` (CREATE NEW)

```powershell
# JWT Validation Debugging Script for Docker OAuth2 Issues

param(
    [string]$Token = "",
    [string]$KeycloakUrl = "http://localhost:8080",
    [string]$GatewayUrl = "http://localhost:9000"
)

Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "JWT Validation Debugging Script" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

if ([string]::IsNullOrEmpty($Token)) {
    Write-Host "◆ Step 1: Get JWT Token" -ForegroundColor Yellow
    Write-Host "Getting token from Keycloak..." -ForegroundColor Gray
    
    $tokenResponse = Invoke-RestMethod -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
        -Method Post `
        -Headers @{ "Content-Type" = "application/x-www-form-urlencoded" } `
        -Body @{
            client_id = "postman-client"
            client_secret = "postman-client-secret-123"
            grant_type = "client_credentials"
        }
    
    $Token = $tokenResponse.access_token
    Write-Host "✓ Token obtained (expires in $($tokenResponse.expires_in) seconds)" -ForegroundColor Green
    Write-Host ""
}

Write-Host "◆ Step 2: Decode JWT Claims" -ForegroundColor Yellow

# Decode JWT (JWT structure: header.payload.signature)
$parts = $Token.Split('.')
if ($parts.Length -ne 3) {
    Write-Host "✗ Invalid JWT format" -ForegroundColor Red
    exit 1
}

# Decode with padding adjustment
$payload = $parts[1]
$payload = $payload.Replace('-', '+').Replace('_', '/')
while ($payload.Length % 4) { 
    $payload += '=' 
}

try {
    $decodedBytes = [System.Convert]::FromBase64String($payload)
    $decodedJson = [System.Text.Encoding]::UTF8.GetString($decodedBytes)
    $claims = $decodedJson | ConvertFrom-Json
    
    Write-Host "JWT Claims:" -ForegroundColor Green
    Write-Host "  iss (Issuer): $($claims.iss)" -ForegroundColor White
    Write-Host "  sub (Subject): $($claims.sub)" -ForegroundColor White
    Write-Host "  exp (Expiration): $($claims.exp) ($(Get-Date -UnixTimeSeconds $claims.exp))" -ForegroundColor White
    Write-Host "  iat (Issued At): $($claims.iat) ($(Get-Date -UnixTimeSeconds $claims.iat))" -ForegroundColor White
    if ($claims.realm_access) {
        Write-Host "  roles: $($claims.realm_access.roles -join ', ')" -ForegroundColor White
    }
    Write-Host ""
} catch {
    Write-Host "✗ Failed to decode JWT: $_" -ForegroundColor Red
    exit 1
}

Write-Host "◆ Step 3: Verify Token with Keycloak" -ForegroundColor Yellow

try {
    $introspectResponse = Invoke-RestMethod -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token/introspect" `
        -Method Post `
        -Headers @{
            "Content-Type" = "application/x-www-form-urlencoded"
            "Authorization" = "Basic $(([System.Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes('postman-client:postman-client-secret-123'))))"
        } `
        -Body "token=$Token"
    
    if ($introspectResponse.active) {
        Write-Host "✓ Token is valid and active" -ForegroundColor Green
    } else {
        Write-Host "✗ Token is not active (revoked or expired)" -ForegroundColor Red
    }
    Write-Host ""
} catch {
    Write-Host "⚠ Could not verify token with Keycloak: $_" -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "◆ Step 4: Test API Gateway" -ForegroundColor Yellow

try {
    Write-Host "Sending request to: $GatewayUrl/api/order/all" -ForegroundColor Gray
    
    $headers = @{
        "Authorization" = "Bearer $Token"
        "Content-Type" = "application/json"
    }
    
    $response = Invoke-WebRequest -Uri "$GatewayUrl/api/order/all" `
        -Method Get `
        -Headers $headers `
        -ErrorAction Stop
    
    Write-Host "✓ Request successful (Status: $($response.StatusCode))" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Gray
    Write-Host ($response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 3) -ForegroundColor White
} catch {
    $response = $_.Exception.Response
    $statusCode = $response.StatusCode.value__
    
    if ($statusCode -eq 401) {
        Write-Host "✗ 401 Unauthorized - Gateway rejected the token" -ForegroundColor Red
        Write-Host "Checking gateway logs for details..." -ForegroundColor Yellow
        
        # Try to get gateway logs
        Write-Host "Gateway logs (last 50 lines):" -ForegroundColor Gray
        docker logs --tail 50 gateway-service 2>&1 | Select-Object -Last 50
        
    } elseif ($statusCode -eq 403) {
        Write-Host "✗ 403 Forbidden - Token valid but not authorized" -ForegroundColor Red
    } else {
        Write-Host "✗ Request failed (Status: $statusCode)" -ForegroundColor Red
        Write-Host $_.Exception.Response.StatusDescription -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "◆ Step 5: Verify Docker Networking" -ForegroundColor Yellow

Write-Host "Testing connectivity from gateway container..." -ForegroundColor Gray

# Check gateway can reach Keycloak
$keycloakTest = & docker exec gateway-service curl -s -o /dev/null -w "%{http_code}" http://keycloak:8080/health/ready
Write-Host "  Gateway → Keycloak connection: HTTP $keycloakTest" -ForegroundColor $(if ($keycloakTest -eq "200") { "Green" } else { "Red" })

# Check Keycloak is returning JWK set
$jwkTest = & docker exec gateway-service curl -s http://keycloak:8080/realms/master/protocol/openid-connect/certs
if ($jwkTest | Select-String "keys" -Quiet) {
    Write-Host "  Keycloak JWK Set endpoint: ✓ Accessible" -ForegroundColor Green
} else {
    Write-Host "  Keycloak JWK Set endpoint: ✗ Not accessible" -ForegroundColor Red
}

Write-Host ""
Write-Host "◆ Troubleshooting Checklist" -ForegroundColor Yellow
Write-Host "  [ ] Token issuer ($($claims.iss)) matches gateway configuration" -ForegroundColor White
Write-Host "  [ ] Token is not expired" -ForegroundColor White
Write-Host "  [ ] Gateway can reach Keycloak on internal network" -ForegroundColor White
Write-Host "  [ ] spring.security.oauth2.resourceserver.jwt.issuer-uri uses \${KEYCLOAK_URL}" -ForegroundColor White
Write-Host "  [ ] spring.security.oauth2.resourceserver.jwt.jwk-set-uri uses \${KEYCLOAK_URL}" -ForegroundColor White
Write-Host "  [ ] TokenRelay filter is in gateway routes" -ForegroundColor White
Write-Host "  [ ] Docker environment variable KEYCLOAK_URL=http://keycloak:8080" -ForegroundColor White
Write-Host ""

Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
```

---

## Solution 6: Docker Compose Security Fix

**File:** `docker-compose.yml` (UPDATE docker health checks and logging)

```yaml
api-gateway:
  image: gateway-service:v1
  container_name: gateway-service
  build: ./api-gateway
  ports:
    - "9000:9000"
  depends_on:
    discovery-server:
      condition: service_healthy
    config-server:
      condition: service_healthy
    keycloak:
      condition: service_healthy
  environment:
    - CONFIG_SERVER_URL=http://config-server:8888
    - EUREKA_SERVER_URL=http://discovery-server:8761/eureka/
    - ZIPKIN_URL=http://zipkin:9411/api/v2/spans
    - KEYCLOAK_URL=http://keycloak:8080
    - MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
    - SPRING_CONFIG_IMPORT=optional:configserver:http://config-server:8888
    - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka
    - SPRING_PROFILES_ACTIVE=docker
    # Enable debug logging for security
    - LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG
    - LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY_OAUTH2=DEBUG
    - LOGGING_LEVEL_COM_NIMBUSDS_JWT=DEBUG
  networks:
    - spring-network
  # Important: allocate enough memory for Spring Boot + security processing
  deploy:
    resources:
      limits:
        memory: 512M
      reservations:
        memory: 256M
```

---

## Key Diagnostic Commands

```bash
# 1. Check JWT claims directly
JWT_TOKEN="your_token_here"
# Go to https://jwt.io and paste the token

# 2. Test Keycloak JWK endpoint from gateway container
docker exec gateway-service curl -v http://keycloak:8080/realms/master/protocol/openid-connect/certs

# 3. Check issuer configuration
docker exec gateway-service curl http://keycloak:8080/realms/master/.well-known/openid-configuration | jq '.issuer'

# 4. Monitor gateway security logs in real-time
docker logs -f gateway-service 2>&1 | grep -E "SECURITY|JWT|Bearer|Authorization|issuer"

# 5. Test token validation explicitly
docker exec gateway-service curl \
  -H "Authorization: Bearer $JWT_TOKEN" \
  http://localhost:9000/actuator/health

# 6. Check if gateway can reach Keycloak internally
docker exec gateway-service curl http://keycloak:8080/health/ready

# 7. View detailed Spring Security debug output
docker logs gateway-service 2>&1 | grep "SecurityFilterChain\|JwtDecoder\|JwtValidator"
```

---

## Verification Steps

1. **Update api-gateway SecurityConfig** → Use custom OAuth2 config
2. **Update YAML files** → Both gateway and services use Docker container names
3. **Enable logging** → Add logback-spring.xml
4. **Rebuild Docker image** → `docker-compose build --no-cache api-gateway`
5. **Restart gateway** → `docker-compose restart api-gateway`
6. **Test with debug script** → `.\debug-jwt-validation.ps1`
7. **Monitor logs** → `docker logs -f gateway-service | grep -i security`

---


