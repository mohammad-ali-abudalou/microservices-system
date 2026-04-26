# Docker Microservices OAuth2 Troubleshooting & Quick Reference

## Quick Start (5 Minutes)

### Prerequisites
- Docker and Docker Compose installed
- Windows PowerShell or Bash terminal
- Git Bash or similar for Windows users (optional but recommended)

### Step 1: Start Docker Containers
```bash
cd D:\workspaces\microservices-system
docker-compose up -d
```

### Step 2: Wait for Services to Be Ready
```bash
# Monitor Keycloak logs
docker logs -f keycloak

# In another terminal, check health
curl http://localhost:8080/health/ready
```

Look for: `"status":"UP"` response

### Step 3: Setup Keycloak OAuth2 Client
```powershell
# PowerShell - Run from workspace directory
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
.\setup-keycloak-oauth2.ps1
```

### Step 4: Import Postman Collection
1. Open Postman
2. Click **Import** → choose `postman-collection.json`
3. Update variables:
   - `client_id`: `postman-client`
   - `client_secret`: `postman-client-secret-123`
4. Go to "Authentication" → "Get Access Token (Client Credentials)" → Send
5. Now test API endpoints!

---

## Common Issues & Solutions

### Issue 1: 401 Unauthorized from Gateway

**Error Message:**
```
{
  "timestamp": "2026-04-23T10:00:00",
  "status": 401,
  "error": "Unauthorized"
}
```

**Root Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| No Authorization header | Add header: `Authorization: Bearer <token>` |
| Invalid token format | Use: `Bearer ` (with space) before token |
| Token expired | Get new token using "Get Access Token" request |
| Keycloak not ready | Wait 60+ seconds, check `curl http://localhost:8080/health/ready` |
| Wrong Keycloak URL | Verify KEYCLOAK_URL env var in docker-compose.yml |
| JWT validation fails | Check issuer-uri matches Keycloak realm issuer |

**Verification Steps:**
```bash
# 1. Check if token is valid
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token/introspect \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=postman-client" \
  -d "client_secret=postman-client-secret-123" \
  -d "token=YOUR_TOKEN"

# Expected response should have: "active": true

# 2. Decode token at https://jwt.io
# Paste your token to see claims and expiration

# 3. Check Gateway logs
docker logs -f gateway-service 2>&1 | grep -i "unauthorized\|bearer\|jwt"
```

---

### Issue 2: 503 Service Unavailable - Keycloak Connection Failed

**Error Message:**
```
Failed to load JWK set from http://keycloak:8080/realms/master/protocol/openid-connect/certs
Connection refused
```

**Root Causes:**
- Keycloak container not running
- Keycloak still starting up (needs >30 seconds)
- Services started before Keycloak was ready
- Network connectivity issue

**Solutions:**
```bash
# 1. Check if Keycloak is running
docker ps | grep keycloak
docker logs keycloak

# Expected log output:
# "Listening on: http://0.0.0.0:8080"
# "HTTP server is ready"

# 2. Wait for health check
docker ps keycloak  # Look at "(healthy)" status

# 3. Test from inside container
docker exec order-service curl http://keycloak:8080/health/ready

# 4. Restart services in correct order
docker-compose restart keycloak
sleep 60  # Wait full minute for Keycloak initialization
docker-compose restart api-gateway
docker-compose restart order-service
docker-compose restart inventory-service
```

---

### Issue 3: Token Not Being Passed to Downstream Services

**Symptoms:**
- Services receive null Authentication
- PreAuthorize annotations fail
- Downstream services return 401/403

**Root Cause:** TokenRelay filter missing or misconfigured

**Solution - Verify Gateway Configuration:**

```bash
# Check if TokenRelay is in gateway config
grep -r "TokenRelay" *.yaml */*.yaml

# Expected to see in api-gateway application.yaml:
# filters:
#   - TokenRelay

# Verify in docker config as well:
grep -r "SPRING_PROFILES_ACTIVE=docker" docker-compose.yml
```

**Gateway Route Configuration (api-gateway/src/main/resources/application.yaml):**
```yaml
# CORRECT - with TokenRelay
routes:
  - id: order-service
    uri: lb://order-service
    predicates:
      - Path=/api/order/**
    filters:
      - TokenRelay  # <- THIS IS ESSENTIAL
      - name: CircuitBreaker
        args:
          name: order-service

# WRONG - without TokenRelay
routes:
  - id: order-service
    uri: lb://order-service
    predicates:
      - Path=/api/order/**
    # Missing TokenRelay!
```

**Verify Token is Being Passed:**
```bash
# Enable debug logging
docker exec api-gateway curl \
  -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:9000/api/order/all -v

# Check gateway logs for Authorization header
docker logs gateway-service 2>&1 | tail -50
```

---

### Issue 4: Issuer Mismatch - JWT Validation Failed

**Error Message:**
```
Jwt issuer "http://localhost:8080/realms/master" does not match 
"http://keycloak:8080/realms/master"
```

**Root Cause:** 
- Token was created with different issuer than what service expects
- Configuration issuer-uri doesn't match token issuer claim

**Solution:**

```bash
# 1. Check what issuer token claims
# Decode token at https://jwt.io and look for "iss" claim

# 2. Check what service expects
docker exec order-service cat spring-config/order-service.properties | grep issuer-uri

# 3. Fix - must match!
# If token says: "iss": "http://keycloak:8080/realms/master"
# Then config must be: 
# issuer-uri: http://keycloak:8080/realms/master

# 4. Verify Keycloak realm issuer
curl http://localhost:8080/realms/master/.well-known/openid-configuration | \
  grep '"issuer"'

# Output should be:
# "issuer": "http://keycloak:8080/realms/master"
```

**Configuration Check:**
```yaml
# order-service/src/main/resources/application.yml (docker profile)
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Must use environment variable
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8080}/realms/master
```

```bash
# docker-compose.yml must set:
environment:
  - KEYCLOAK_URL=http://keycloak:8080
```

---

### Issue 5: Services Can't Reach Each Other

**Error Message:**
```
Can't resolve keycloak: Name or service not known
Cannot connect to order-service:8082
```

**Root Cause:** Services on different networks or container names don't resolve

**Solution:**

```bash
# 1. Verify network
docker network ls | grep spring-network

# 2. Check which network containers are on
docker inspect keycloak | grep -A 5 "Networks"
docker inspect order-service | grep -A 5 "Networks"

# Should both show: "spring-network"

# 3. Test connectivity from inside container
docker exec order-service ping keycloak
docker exec order-service curl http://keycloak:8080/health

# 4. If not working, check docker-compose.yml networks section:
networks:
  spring-network:
    driver: bridge

# And all services must have:
networks:
  - spring-network
```

---

### Issue 6: Keycloak Client Creation Failed in Setup Script

**Error Message:**
```
Failed to create client: 409 Conflict
or
Failed to get admin token
```

**Solutions:**

```bash
# 1. If token error - check Keycloak admin auth
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password"

# Should return access_token

# 2. If conflict error - client already exists (OK!)
# You can use it, or delete and recreate:
docker exec keycloak /opt/keycloak/bin/kcadm.sh delete clients/CLIENT_ID \
  -r master \
  -u admin \
  -p admin

# 3. Manually create via Keycloak admin console:
# - Open http://localhost:8080/admin
# - Login as: admin / admin
# - Clients → Create client
# - Fill in: postman-client
# - Continue, set credentials, save
```

---

### Issue 7: Gateway Returns 502 Bad Gateway

**Error Message:**
```
502 Bad Gateway
or
LoadBalancer does not have available servers
```

**Root Causes:**
- Services not registered with Eureka
- Services crashed/unhealthy
- Network issues

**Solution:**

```bash
# 1. Check Eureka dashboard
curl http://localhost:8761/eureka/apps | grep -A 5 "order-service"

# 2. Check if services are running
docker ps | grep -E "order|inventory|api-gateway"

# 3. Check service health
curl http://localhost:8082/actuator/health  # Order Service
curl http://localhost:8081/actuator/health  # Inventory Service

# Should return: "status": "UP"

# 4. Check service logs for errors
docker logs order-service 2>&1 | tail -100 | grep -i error
docker logs inventory-service 2>&1 | tail -100 | grep -i error

# 5. If services crashed, restart them
docker-compose restart order-service
docker-compose restart inventory-service
```

---

## Health Check Commands

Use these to verify system is running correctly:

```bash
# Keycloak Ready
curl http://localhost:8080/health/ready

# Keycloak Realm Config
curl http://localhost:8080/realms/master/.well-known/openid-configuration

# Discovery Server (Eureka)
curl http://localhost:8761/eureka/apps

# Config Server Health
curl http://localhost:8888/actuator/health

# API Gateway Health
curl http://localhost:9000/actuator/health

# Order Service Health
curl http://localhost:8082/actuator/health \
  -H "Authorization: Bearer YOUR_TOKEN"

# Inventory Service Health
curl http://localhost:8081/actuator/health \
  -H "Authorization: Bearer YOUR_TOKEN"

# Get Prometheus Metrics
curl http://localhost:9000/actuator/prometheus | head -50

# Check Kafka
docker exec kafka kafka-broker-api-versions.sh \
  --bootstrap-server kafka-1:9092

# Check MySQL Databases
docker exec mysql-inventory mysql -uroot -proot -e "SHOW DATABASES;"
docker exec mysql-order mysql -uroot -proot -e "SHOW DATABASES;"
```

---

## Debugging Commands

### Enable Debug Logging

```bash
# For order-service
docker exec order-service curl -X POST \
  http://localhost:8082/actuator/loggers/org.springframework.security \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'

# For gateway
docker exec api-gateway curl -X POST \
  http://localhost:9000/actuator/loggers/org.springframework.security \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'
```

### View Real-Time Logs

```bash
# Watch all service logs
docker-compose logs -f

# Watch specific service with filter
docker logs -f order-service | grep -i "security\|jwt\|token"
docker logs -f api-gateway | grep -i "security\|jwt\|token\|bearer"
docker logs -f keycloak | grep -i "error\|warn"

# Stream with timestamps
docker logs -f --timestamps order-service
```

### Database Inspection

```bash
# Connect to MySQL
docker exec -it mysql-order mysql -uroot -proot

# Inside MySQL:
USE order_service;
SHOW TABLES;
SELECT * FROM orders LIMIT 5;

# Or from host:
mysql -h 127.0.0.1 -P 3308 -uroot -proot -e "USE order_service; SHOW TABLES;"
```

---

## Configuration Verification Checklist

Run these commands to verify everything is configured correctly:

```bash
# 1. Check environment variables in running containers
docker exec order-service printenv | grep -E "KEYCLOAK|CONFIG_SERVER|EUREKA"

# 2. Verify application.yml is using correct values
docker exec order-service cat /app/app/application.yml | grep -A 3 "oauth2"

# 3. Check Config Server has correct properties
curl -s "http://admin:admin@localhost:8888/order-service/docker" | jq '.propertySources'

# 4. Verify Keycloak realm is master
curl http://localhost:8080/realms/master | jq '.realm'

# 5. Verify client exists in Keycloak
curl -X GET http://localhost:8080/admin/realms/master/clients \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -H "Content-Type: application/json" | jq '.[] | select(.clientId=="postman-client")'
```

---

## Performance & Monitoring

### Check System Performance

```bash
# Docker resource usage
docker stats

# Container uptime
docker ps --format "table {{.Names}}\t{{.Status}}"

# Monitor Prometheus metrics
curl http://localhost:9000/actuator/prometheus | grep -E "http_server_requests_seconds|jvm"
```

### Access Monitoring Dashboards

- **Eureka Dashboard:** http://localhost:8761
- **Keycloak Admin:** http://localhost:8080/admin (admin/admin)
- **Prometheus:** http://localhost:9090
- **Grafana:** http://localhost:3000
- **Zipkin (Tracing):** http://localhost:9411
- **Kafdrop (Kafka):** http://localhost:9010

---

## Common Configuration Mistakes

| Mistake | Impact | Fix |
|---------|--------|-----|
| `KEYCLOAK_URL=localhost:8080` in docker | Services can't reach Keycloak | Use: `http://keycloak:8080` |
| `issuer-uri: http://localhost:8080/realms/master` | JWT validation fails | Use env var: `${KEYCLOAK_URL}/realms/master` |
| Missing `TokenRelay` in gateway routes | Tokens not passed to services | Add: `- TokenRelay` in filters |
| `SPRING_PROFILES_ACTIVE` not set | Wrong config file loaded | Set in docker-compose env |
| `kafka-1:9092` in docker services | Kafka connection fails | Use: `kafka-1:29092` (internal) |
| Services restart before dependencies | Services fail to start | Check `depends_on` with `condition: service_healthy` |

---

## Getting Help

### Check Application Logs
```bash
# Get last 100 lines from service
docker logs --tail 100 order-service

# Follow logs in real-time
docker logs -f order-service

# With timestamps
docker logs -f --timestamps order-service

# Filter for errors
docker logs order-service 2>&1 | grep -i "error\|exception"
```

### Useful Information to Gather

When reporting issues, collect:
1. Docker logs: `docker logs service-name > logs.txt`
2. Configuration: `curl http://config-server:8888/order-service/docker`
3. Health status: `curl http://localhost:8082/actuator/health`
4. Token claims: Paste token at https://jwt.io

---

## Quick Reset

If everything is broken, restart cleanly:

```bash
# Stop all containers
docker-compose down

# Remove volumes (if you want clean database)
docker-compose down -v

# Rebuild images (if config changed)
docker-compose build --no-cache

# Start fresh
docker-compose up -d

# Wait for Keycloak to be ready (60+ seconds)
docker logs -f keycloak

# Once ready, setup Keycloak
.\setup-keycloak-oauth2.ps1

# Check all services are healthy
docker ps

# Test a call
curl -X GET http://localhost:9000/actuator/health
```

---

## References

- Spring Cloud Gateway: https://spring.io/projects/spring-cloud-gateway
- OAuth2 Resource Server: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html
- Keycloak: https://www.keycloak.org/documentation.html
- JWT.io: https://jwt.io (for token debugging)
- Postman: https://learning.postman.com/docs/sending-requests/authorization/oauth-20/


