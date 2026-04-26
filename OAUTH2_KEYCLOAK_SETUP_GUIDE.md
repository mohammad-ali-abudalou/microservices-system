# OAuth2/Keycloak Security Configuration Guide for Docker Microservices

## Table of Contents
1. [Docker Configuration Issues & Solutions](#docker-configuration-issues--solutions)
2. [Correct YAML Configuration](#correct-yaml-configuration)
3. [Postman Token Configuration](#postman-token-configuration)
4. [Startup Retry Logic](#startup-retry-logic)
5. [Token Relay Implementation](#token-relay-implementation)
6. [Troubleshooting](#troubleshooting)

---

## Docker Configuration Issues & Solutions

### Problem 1: Keycloak URI Resolution in Docker
**Issue:** Services use `http://localhost:8080` but inside Docker containers, they should use `http://keycloak:8080` (container name).

**Solution:** Use environment variables with proper defaults for each environment.

```yaml
# WRONG - Will fail in Docker:
issuer-uri: http://localhost:8080/realms/master

# CORRECT - Uses environment variable:
issuer-uri: ${KEYCLOAK_URL:http://localhost:8080}/realms/master

# In docker-compose.yml, set:
environment:
  - KEYCLOAK_URL=http://keycloak:8080
```

### Problem 2: Service Startup Order
**Issue:** Services try to validate tokens with Keycloak before Keycloak is fully initialized.

**Solution:** Add health checks and proper dependency conditions.

```yaml
keycloak:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
    interval: 10s
    timeout: 5s
    retries: 10
    start_period: 60s
```

### Problem 3: JWT Validation Failures
**Issue:** `jwk-set-uri` endpoint may not be ready when application starts.

**Solution:** Configure retry logic in Spring Cloud Config client.

---

## Correct YAML Configuration

### API Gateway (docker profile)
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8080}/realms/master
          jwk-set-uri: ${KEYCLOAK_URL:http://keycloak:8080}/realms/master/protocol/openid-connect/certs
          
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/order/**
          filters:
            - TokenRelay  # Relay the JWT token from Gateway to services
            - name: CircuitBreaker
              args:
                name: order-service
                fallbackUri: forward:/orderFallback
            - name: Retry
              args:
                retries: 3
                methods: GET,POST,PUT,DELETE
```

### Order Service & Inventory Service (docker profile)
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8080}/realms/master
          jwk-set-uri: ${KEYCLOAK_URL:http://keycloak:8080}/realms/master/protocol/openid-connect/certs
  
  cloud:
    config:
      retry:
        max-attempts: 10
        max-interval: 2000
        initial-interval: 1000
        multiplier: 1.1
```

---

## Postman Token Configuration

### Step 1: Configure OAuth2 in Postman

1. **Create a new Request** in Postman
2. **Go to Authorization tab**
3. **Select OAuth 2.0** as the Type
4. **Configure the following:**

```
Grant Type: Authorization Code
Callback URL: http://localhost:3000/callback (or any valid/unused port)
Auth URL: http://localhost:8080/realms/master/protocol/openid-connect/auth
Access Token URL: http://localhost:8080/realms/master/protocol/openid-connect/token
Client ID: postman-client (must be created in Keycloak)
Client Secret: your-client-secret
Scope: openid profile email
State: random
```

### Step 2: Create OAuth2 Client in Keycloak

1. **Access Keycloak Admin Console:** http://localhost:8080/admin
2. **Login with:** admin / admin
3. **Select Realm:** master
4. **Go to Clients** → **Create client**
5. **Configure:**
   - Client ID: `postman-client`
   - Client authentication: ON
   - Authorization: ON
   - Valid redirect URIs: `http://localhost`, `http://localhost:*/*`
   - Web origins: `*`

6. **Go to Credentials tab** and copy the Client Secret
7. **Go to Service Accounts Roles** and assign roles if needed

### Step 3: Get Token in Postman

1. Click **Get New Access Token**
2. Postman will redirect to Keycloak login
3. Enter credentials and authorize
4. Token will be automatically added to requests

### Step 4: Test API Calls

```bash
# Get token
GET /realms/master/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

client_id=postman-client&
client_secret=YOUR_SECRET&
grant_type=client_credentials&
scope=openid

# Response:
{
  "access_token": "eyJ...",
  "expires_in": 300,
  "token_type": "Bearer"
}

# Call API with token
GET http://localhost:9000/api/order/all
Authorization: Bearer eyJ...
```

---

## Startup Retry Logic

### Spring Cloud Config Retry Configuration

Add to `application.yml`:

```yaml
spring:
  cloud:
    config:
      retry:
        initial-interval: 1000      # Start with 1 second
        max-interval: 2000          # Max 2 seconds between retries
        multiplier: 1.1             # Increase by 10% each time
        max-attempts: 10            # Try 10 times
      fail-fast: true              # Fail fast if config server not available
```

### Keylock JWT Cache Configuration

Add to services that validate JWT:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL}/realms/master
          jwk-set-uri: ${KEYCLOAK_URL}/realms/master/protocol/openid-connect/certs
          # Cache JWK set for 5 minutes to reduce calls to Keycloak
          jws-algorithms: RS256

server:
  servlet:
    session:
      timeout: 30m
```

### Docker Health Check Implementation

```yaml
depends_on:
  keycloak:
    condition: service_healthy
  discovery-server:
    condition: service_healthy
  config-server:
    condition: service_healthy
```

---

## Token Relay Implementation

### How TokenRelay Works

The `TokenRelay` filter in Spring Cloud Gateway:
1. Extracts JWT from incoming request's `Authorization` header
2. Validates the JWT using Keycloak's public key
3. Forwards the SAME token to downstream services
4. Downstream services validate the token independently

### Configuration

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/order/**
          filters:
            - TokenRelay  # This is the key filter
```

### Downstream Service Configuration

```yaml
# order-service, inventory-service configs
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL}/realms/master
          jwk-set-uri: ${KEYCLOAK_URL}/realms/master/protocol/openid-connect/certs
```

### Extracting User Information in Services

```java
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @GetMapping
    @PreAuthorize("hasAnyRole('user', 'admin')")
    public ResponseEntity<?> getOrders(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            String email = jwt.getClaimAsString("email");
            // Use username and email in business logic
        }
        return ResponseEntity.ok("Orders");
    }
}
```

---

## Troubleshooting

### Issue 1: 401 Unauthorized from Gateway

**Symptoms:**
```
{
  "timestamp": "2026-04-23T10:00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Bearer token not found"
}
```

**Solutions:**
1. Verify token is sent in `Authorization: Bearer <token>` header
2. Check token is not expired: `https://jwt.io`
3. Verify token issuer matches `issuer-uri` in configuration

**Check:**
```bash
# Decode token to see claims
curl -X POST http://keycloak:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=postman-client&client_secret=SECRET&grant_type=client_credentials"
```

### Issue 2: 503 Service Unavailable - Keycloak Not Ready

**Symptoms:**
```
Failed to load JWK set from http://keycloak:8080/realms/master/protocol/openid-connect/certs
Connection refused
```

**Solutions:**
1. Verify Keycloak container is running:
   ```bash
   docker ps | grep keycloak
   docker logs keycloak
   ```

2. Test Keycloak health:
   ```bash
   curl http://localhost:8080/health/ready
   curl http://localhost:8080/auth/realms/master/.well-known/openid-configuration
   ```

3. Increase startup delay:
   ```yaml
   keycloak:
     healthcheck:
       start_period: 90s  # Increase if still failing
   ```

### Issue 3: JWT Validation Failed - Invalid Issuer

**Symptoms:**
```
Invalid issuer in token. Expected: http://keycloak:8080/realms/master, 
Got: http://localhost:8080/realms/master
```

**Solutions:**
1. Ensure Keycloak is configured with correct issuer:
   ```bash
   curl http://keycloak:8080/realms/master/.well-known/openid-configuration | jq .issuer
   ```

2. Update `issuer-uri` to match exactly
3. If issuer is hardcoded in Keycloak, update Keycloak realm settings

### Issue 4: Services Can't Reach Keycloak

**Symptoms:**
```
Failed to connect to keycloak:8080 (Name or service not known)
```

**Solutions:**
1. Verify services are on same Docker network:
   ```bash
   docker network inspect spring-network
   ```

2. Verify docker-compose.yml has correct network definition
3. Check Keycloak service name matches environment variable
4. Test connectivity:
   ```bash
   docker exec order-service curl http://keycloak:8080/health
   ```

### Issue 5: Token Not Being Relayed to Downstream Services

**Symptoms:**
- Services receive requests without `Authorization` header
- `PreAuthorize` annotations fail in downstream services

**Solutions:**
1. Verify `TokenRelay` filter is in gateway routes:
   ```yaml
   filters:
     - TokenRelay  # Must be present
   ```

2. Check gateway logs for security processing
3. Verify downstream services have OAuth2 resource server configured

---

## Security Best Practices

1. **Never hardcode credentials** - Use environment variables
2. **Rotate Keycloak passwords** in production
3. **Use HTTPS** in production environments
4. **Limit token lifetime** - use short-lived access tokens with refresh tokens
5. **Implement rate limiting** on auth endpoints
6. **Monitor token validation failures** for security audits
7. **Use proper role-based access control (RBAC)** in all services
8. **Enable CORS properly** - don't use wildcard in production

---

## Environment Variables Summary

| Variable | Default | Docker Value | Purpose |
|----------|---------|--------------|---------|
| KEYCLOAK_URL | http://localhost:8080 | http://keycloak:8080 | Keycloak server URL |
| CONFIG_SERVER_URL | http://localhost:8888 | http://config-server:8888 | Config server URL |
| EUREKA_SERVER_URL | http://localhost:8761/eureka/ | http://discovery-server:8761/eureka/ | Service discovery URL |
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 | kafka-1:29092 | Kafka broker URL (internal) |
| DB_HOST | localhost | mysql-inventory / mysql-order | Database host |
| ZIPKIN_URL | http://localhost:9411 | http://zipkin:9411 | Distributed tracing URL |

---

## Quick Start Command for Docker

```bash
# Navigate to workspace
cd D:\workspaces\microservices-system

# Build all services
docker-compose build

# Start all services
docker-compose up -d

# Check logs
docker-compose logs -f keycloak      # Watch Keycloak initialization
docker-compose logs -f api-gateway    # Watch Gateway logs
docker-compose logs -f order-service  # Watch Order Service logs

# Verify health
curl http://localhost:8080/health/ready          # Keycloak
curl http://localhost:9000/actuator/health      # Gateway
curl http://localhost:8082/actuator/health      # Order Service
```

---

## Next Steps

1. ✅ Apply configuration fixes from this guide
2. ✅ Update docker-compose.yml with health checks
3. ✅ Restart Docker containers
4. ✅ Test with Postman using the configuration guide
5. ✅ Monitor logs for any JWT validation errors

