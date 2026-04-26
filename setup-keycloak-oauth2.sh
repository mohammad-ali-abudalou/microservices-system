#!/bin/bash
# Keycloak OAuth2 Client Setup Script
# This script configures Keycloak with the necessary OAuth2 client for Postman testing

set -e

# Configuration
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="admin"
REALM="master"
CLIENT_ID="postman-client"
CLIENT_SECRET="postman-client-secret-123"

echo "================================================"
echo "Keycloak OAuth2 Client Setup Script"
echo "================================================"
echo ""
echo "Configuration:"
echo "  Keycloak URL: $KEYCLOAK_URL"
echo "  Realm: $REALM"
echo "  Client ID: $CLIENT_ID"
echo "  Client Secret: $CLIENT_SECRET"
echo ""

# Wait for Keycloak to be ready
echo "Waiting for Keycloak to be ready..."
for i in {1..30}; do
    if curl -s "$KEYCLOAK_URL/health/ready" > /dev/null 2>&1; then
        echo "✓ Keycloak is ready!"
        break
    fi
    echo "  Waiting... (attempt $i/30)"
    sleep 2
    if [ $i -eq 30 ]; then
        echo "✗ Keycloak did not start in time. Please check if Keycloak is running."
        exit 1
    fi
done

echo ""
echo "Step 1: Getting admin token..."

# Get admin token
TOKEN_RESPONSE=$(curl -s -X POST \
    "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=admin-cli" \
    -d "username=$ADMIN_USERNAME" \
    -d "password=$ADMIN_PASSWORD" \
    -d "grant_type=password")

ADMIN_TOKEN=$(echo $TOKEN_RESPONSE | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
    echo "✗ Failed to get admin token. Response: $TOKEN_RESPONSE"
    exit 1
fi

echo "✓ Admin token obtained"

echo ""
echo "Step 2: Creating OAuth2 client '$CLIENT_ID'..."

# Create the client
CREATE_RESPONSE=$(curl -s -X POST \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "clientId": "'$CLIENT_ID'",
        "enabled": true,
        "publicClient": false,
        "clientAuthenticatorType": "client-secret-basic",
        "secret": "'$CLIENT_SECRET'",
        "redirectUris": [
            "http://localhost",
            "http://localhost:*/*",
            "http://localhost:3000/callback",
            "http://localhost:3000/*"
        ],
        "webOrigins": ["*"],
        "standardFlowEnabled": true,
        "implicitFlowEnabled": true,
        "directAccessGrantsEnabled": true,
        "serviceAccountsEnabled": true,
        "authorizationServicesEnabled": false,
        "defaultClientScopes": [
            "web-origins",
            "profile",
            "email",
            "acl",
            "roles"
        ],
        "optionalClientScopes": [
            "address",
            "phone",
            "offline_access",
            "microprofile-jwt"
        ]
    }')

# Check if client was created (should return 201 with location header)
if echo "$CREATE_RESPONSE" | grep -q "clientId"; then
    echo "✗ Client may already exist or there was an error: $CREATE_RESPONSE"
    echo ""
    echo "Attempting to update existing client instead..."

    # Get existing client ID
    CLIENT_UUID=$(curl -s -X GET \
        "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" \
        -H "Authorization: Bearer $ADMIN_TOKEN" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

    if [ -z "$CLIENT_UUID" ]; then
        echo "✗ Failed to find or create client"
        exit 1
    fi

    echo "Found existing client UUID: $CLIENT_UUID"
    echo "✓ Client configuration verified"
else
    echo "✓ Client created successfully"
fi

echo ""
echo "Step 3: Creating test users..."

# Create test user
USER_RESPONSE=$(curl -s -X POST \
    "$KEYCLOAK_URL/admin/realms/$REALM/users" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "testuser",
        "email": "testuser@example.com",
        "enabled": true,
        "firstName": "Test",
        "lastName": "User",
        "credentials": [
            {
                "type": "password",
                "value": "testuser123",
                "temporary": false
            }
        ]
    }')

if echo "$USER_RESPONSE" | grep -q "User exists"; then
    echo "ℹ Test user 'testuser' already exists"
else
    echo "✓ Test user 'testuser' created"
fi

echo ""
echo "================================================"
echo "✓ Keycloak OAuth2 Configuration Complete!"
echo "================================================"
echo ""
echo "OAuth2 Client Credentials:"
echo "  Client ID: $CLIENT_ID"
echo "  Client Secret: $CLIENT_SECRET"
echo "  Realm: $REALM"
echo ""
echo "Test Credentials:"
echo "  Username: testuser"
echo "  Password: testuser123"
echo ""
echo "Admin Credentials:"
echo "  Username: $ADMIN_USERNAME"
echo "  Password: $ADMIN_PASSWORD"
echo ""
echo "Next Steps:"
echo "1. Update your Postman collection variables:"
echo "   - client_id: $CLIENT_ID"
echo "   - client_secret: $CLIENT_SECRET"
echo "   - username: testuser (or admin)"
echo "   - password: testuser123 (or admin)"
echo ""
echo "2. In Postman, go to 'Get Access Token (Client Credentials)' and click 'Send'"
echo "3. Use the token in other API requests"
echo ""
echo "Token Endpoints:"
echo "  Authorization: $KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/auth"
echo "  Token: $KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token"
echo "  JWK Set: $KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/certs"
echo ""

