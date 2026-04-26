# 🚀 Quick Reference Card - OAuth2 Keycloak Setup

## 5-Minute Quick Start

### Step 1: Build & Start Docker
```bash
cd D:\workspaces\microservices-system
docker-compose build --no-cache
docker-compose up -d
```

### Step 2: Wait for Keycloak (60 seconds!)
```bash
# Monitor startup
docker logs -f keycloak

# Test health
curl http://localhost:8080/health/ready
```

### Step 3: Setup OAuth2 Client
```powershell
# PowerShell (Windows)
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
.\setup-keycloak-oauth2.ps1
```

**Output:** Client Secret: `postman-client-secret-123`

### Step 4: Get Token
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=postman-client" \
  -d "client_secret=postman-client-secret-123" \
  -d "grant_type=client_credentials" | jq -r '.access_token')
echo $TOKEN
```

### Step 5: Test API
```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:9000/api/order/all
```

---

## 🔑 Key URLs & Credentials

| Service | URL | Credentials |
|---------|-----|-------------|
| **Keycloak** | http://localhost:8080 | admin / admin |
| **API Gateway** | http://localhost:9000 | Token required |
| **Order Service** | http://localhost:8082 | Token required |
| **Inventory Service** | http://localhost:8081 | Token required |
| **Config Server** | http://localhost:8888 | admin / admin |
| **Eureka** | http://localhost:8761 | None |
| **Prometheus** | http://localhost:9090 | None |
| **Grafana** | http://localhost:3000 | admin / admin |

## 🔐 OAuth2 Credentials

```
Client ID: postman-client
Client Secret: postman-client-secret-123
Realm: master
Token URL: http://localhost:8080/realms/master/protocol/openid-connect/token
Auth URL: http://localhost:8080/realms/master/protocol/openid-connect/auth
JWK Set URL: http://localhost:8080/realms/master/protocol/openid-connect/certs
```

## 👥 Test Users

```
Username: admin              Username: testuser
Password: admin              Password: testuser123
Roles: admin                 Roles: user
```

---

## ✅ Health Checks

```bash
# Keycloak
curl http://localhost:8080/health/ready

# Config Server
curl http://localhost:8888/actuator/health

# API Gateway
curl http://localhost:9000/actuator/health

# Order Service (with token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/actuator/health

# Eureka
curl http://localhost:8761/eureka/apps

# Prometheus
curl http://localhost:9090/-/healthy
```

---

## 🧪 API Testing

### Postman Import
1. Open Postman → **Import**
2. Select `postman-collection.json`
3. Update variables with credentials
4. Use "Get Access Token (Client Credentials)"
5. All endpoints now available!

### Manual cURL Testing

**Get Token:**
```bash
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=postman-client&client_secret=postman-client-secret-123&grant_type=client_credentials"
```

**Call Protected API:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:9000/api/order/all
```

**Verify Token:**
```bash
# Visit https://jwt.io and paste $TOKEN
# Or use Keycloak introspection endpoint:
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token/introspect \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=$TOKEN&client_id=postman-client&client_secret=postman-client-secret-123"
```

---

## 🐛 Quick Troubleshooting

| Problem | Solution |
|---------|----------|
| 401 Unauthorized | 1. Add `Authorization: Bearer <token>` header 2. Check token not expired |
| 503 Keycloak Error | Wait 60+ seconds for Keycloak to start: `docker logs -f keycloak` |
| Connection Refused | Services on different network: `docker network inspect spring-network` |
| Issuer Mismatch | Check: `curl http://localhost:8080/realms/master/.well-known/openid-configuration` |
| Token Not Relayed | Verify `TokenRelay` filter in `api-gateway.yaml` routes |
| Config Server Failed | Restart: `docker-compose restart config-server` |

---

## 📁 Critical Files

```
docker-compose.yml               ← Keycloak health check added
order-service/application.yml    ← Fixed KEYCLOAK_URL (line 120)
inventory-service/bootstrap.yml  ← NEW: Config retry logic
api-gateway/application.yaml     ← TokenRelay already configured
config-repo/*.properties         ← Using env variables

setup-keycloak-oauth2.ps1        ← Run this for OAuth2 setup
postman-collection.json          ← Import for testing
OAUTH2_KEYCLOAK_SETUP_GUIDE.md   ← Detailed configuration
TROUBLESHOOTING_QUICK_REFERENCE.md ← Common issues & fixes
```

---

## 🔄 Docker Commands

```bash
# Start services
docker-compose up -d

# Stop services  
docker-compose down

# View logs
docker logs -f service-name
docker-compose logs -f

# Rebuild images
docker-compose build --no-cache

# Check status
docker ps
docker stats

# Execute commands in container
docker exec order-service curl http://keycloak:8080/health/ready

# Full clean restart
docker-compose down -v
docker-compose build --no-cache
docker-compose up -d
```

---

## 📊 Configuration Map

```
┌─────────────────────────────────────┐
│   Environment Variables             │
│  (set in docker-compose.yml)        │
├─────────────────────────────────────┤
│ KEYCLOAK_URL=http://keycloak:8080   │
│ CONFIG_SERVER_URL=...               │
│ EUREKA_SERVER_URL=...               │
│ DB_HOST=mysql-{order,inventory}     │
│ KAFKA_BOOTSTRAP_SERVERS=kafka:29092 │
└──────────────┬──────────────────────┘
               ↓
┌─────────────────────────────────────┐
│   bootstrap.yml                      │
│  (Config Server connection)          │
├─────────────────────────────────────┤
│ Retry: 1s → 2s @ 1.1x (max 10x)    │
└──────────────┬──────────────────────┘
               ↓
┌─────────────────────────────────────┐
│   application.yml                    │
│  (Service configuration)             │
├─────────────────────────────────────┤
│ OAuth2: issuer-uri, jwk-set-uri     │
│ Security: @PreAuthorize ready       │
│ Actuator: health, metrics endpoints │
└─────────────────────────────────────┘
```

---

## 🔒 Security Checklist

- [ ] KEYCLOAK_URL uses container name (keycloak:8080)
- [ ] issuer-uri uses environment variable
- [ ] TokenRelay filter in gateway routes  
- [ ] All services on spring-network
- [ ] Health checks on startup dependencies
- [ ] No hardcoded credentials
- [ ] JWT validation enabled in all services
- [ ] Role-based authorization configured

---

## 📝 Postman Variables

```
keycloak_url      = http://localhost:8080
api_gateway_url   = http://localhost:9000
client_id         = postman-client
client_secret     = postman-client-secret-123
username          = admin or testuser
password          = admin or testuser123
access_token      = (auto-filled by Get Token request)
order_id          = 1
sku_code          = ITEM-001
```

---

## 🎯 Success Criteria

✅ All containers running: `docker ps | wc -l` ≥ 10  
✅ Keycloak healthy: `curl http://localhost:8080/health/ready` → UP  
✅ Token generated: cURL returns valid JWT  
✅ Token decoded: https://jwt.io shows claims  
✅ API accessible: `curl -H "Bearer $TOKEN" http://localhost:9000/api/order/all` → 200  
✅ Services healthy: All actuator/health endpoints → UP  
✅ No 401 errors: Requests with token succeed  

---

## 📞 Emergency Commands

```bash
# Reset everything
docker-compose down -v && docker-compose build --no-cache && docker-compose up -d

# Monitor all logs
docker-compose logs -f

# Check service connectivity
docker exec order-service curl http://keycloak:8080/health

# Verify token issuer
curl http://localhost:8080/realms/master/.well-known/openid-configuration | jq .issuer

# Check service registration  
curl http://localhost:8761/eureka/apps | grep -A 5 "order-service"

# View running processes in container
docker exec order-service ps aux

# Check mounted volumes
docker inspect order-service | grep -A 10 Mounts
```

---

## 🚨 Common Error Messages & Fixes

```
Error: Bearer token not found
Fix: Add Authorization header: Bearer <token>

Error: Invalid issuer in token
Fix: Ensure token issuer matches issuer-uri in config

Error: Service Unavailable from Keycloak
Fix: Wait 60+ seconds, check: docker logs keycloak

Error: LoadBalancer does not have available servers  
Fix: Service not in Eureka, restart: docker-compose restart order-service

Error: Cannot connect to config server
Fix: Config Server not ready, restart: docker-compose restart config-server

Error: Failed to load JWK set
Fix: Keycloak not healthy, check: curl http://localhost:8080/health/ready
```

---

## 💡 Pro Tips

1. **Always wait 60+ seconds** after `docker-compose up` before testing
2. **Use Postman Collection** instead of manual cURL - much faster
3. **Check `docker logs -f keycloak`** first for any startup errors
4. **Decode tokens at https://jwt.io** to debug claims/expiration
5. **Copy TOKEN to environment** for multiple curl attempts: `TOKEN=$(curl ... | jq -r '.access_token')`
6. **Monitor multiple services** simultaneously: `docker-compose logs -f`
7. **Health check status** in `docker ps` shows service readiness
8. **Use container names** inside Docker (keycloak, config-server, etc.)

---

## 📚 Documentation Index

| Document | Purpose | Read When |
|----------|---------|-----------|
| OAUTH2_KEYCLOAK_SETUP_GUIDE.md | Complete configuration guide | Setting up for first time |
| SECURITY_IMPLEMENTATION_GUIDE.md | Java implementation examples | Implementing in services |
| TROUBLESHOOTING_QUICK_REFERENCE.md | Common issues & solutions | Something breaks |
| README_OAUTH2_IMPLEMENTATION.md | Overview of all changes | Understanding the system |
| IMPLEMENTATION_VERIFICATION.md | What was changed and why | Verification & audit |

---

**Last Updated: April 23, 2026**  
**Status: Ready for Production** ✅


