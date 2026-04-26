# Keycloak OAuth2 Client Setup Script (PowerShell)
# This script configures Keycloak with the necessary OAuth2 client for Postman testing

param(
    [string]$KeycloakUrl = "http://localhost:8080",
    [string]$Realm = "master",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "admin"
)

$ClientId = "postman-client"
$ClientSecret = "postman-client-secret-123"

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "Keycloak OAuth2 Client Setup Script" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Keycloak URL: $KeycloakUrl"
Write-Host "  Realm: $Realm"
Write-Host "  Client ID: $ClientId"
Write-Host "  Client Secret: $ClientSecret"
Write-Host ""

# Wait for Keycloak to be ready
Write-Host "Waiting for Keycloak to be ready..." -ForegroundColor Yellow
$maxAttempts = 30
$attempt = 0

while ($attempt -lt $maxAttempts) {
    $attempt++
    try {
        $response = Invoke-WebRequest -Uri "$KeycloakUrl/health/ready" -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "✓ Keycloak is ready!" -ForegroundColor Green
            break
        }
    }
    catch {
        Write-Host "  Waiting... (attempt $attempt/$maxAttempts)" -ForegroundColor Gray
        Start-Sleep -Seconds 2
    }

    if ($attempt -eq $maxAttempts) {
        Write-Host "✗ Keycloak did not start in time. Please check if Keycloak is running." -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "Step 1: Getting admin token..." -ForegroundColor Yellow

# Get admin token
try {
    $tokenUri = "$KeycloakUrl/realms/master/protocol/openid-connect/token"
    $tokenBody = @{
        client_id = "admin-cli"
        username = $AdminUsername
        password = $AdminPassword
        grant_type = "password"
    }

    $tokenResponse = Invoke-RestMethod -Uri $tokenUri -Method Post -Body $tokenBody -ContentType "application/x-www-form-urlencoded"
    $adminToken = $tokenResponse.access_token

    if (-not $adminToken) {
        Write-Host "✗ Failed to get admin token" -ForegroundColor Red
        exit 1
    }

    Write-Host "✓ Admin token obtained" -ForegroundColor Green
}
catch {
    Write-Host "✗ Failed to get admin token: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Step 2: Creating OAuth2 client '$ClientId'..." -ForegroundColor Yellow

# Create the client
try {
    $clientUri = "$KeycloakUrl/admin/realms/$Realm/clients"
    $headers = @{
        Authorization = "Bearer $adminToken"
        "Content-Type" = "application/json"
    }

    $clientBody = @{
        clientId = $ClientId
        enabled = $true
        publicClient = $false
        clientAuthenticatorType = "client-secret-basic"
        secret = $ClientSecret
        redirectUris = @(
            "http://localhost",
            "http://localhost:*/*",
            "http://localhost:3000/callback",
            "http://localhost:3000/*"
        )
        webOrigins = @("*")
        standardFlowEnabled = $true
        implicitFlowEnabled = $true
        directAccessGrantsEnabled = $true
        serviceAccountsEnabled = $true
        defaultClientScopes = @(
            "web-origins",
            "profile",
            "email",
            "acl",
            "roles"
        )
        optionalClientScopes = @(
            "address",
            "phone",
            "offline_access",
            "microprofile-jwt"
        )
    } | ConvertTo-Json

    $createResponse = Invoke-RestMethod -Uri $clientUri -Method Post -Headers $headers -Body $clientBody
    Write-Host "✓ Client created successfully" -ForegroundColor Green
}
catch {
    if ($_.Exception.Response.StatusCode -eq "Conflict") {
        Write-Host "ℹ Client already exists" -ForegroundColor Cyan

        # Try to get the existing client
        try {
            $getClientsUri = "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$ClientId"
            $clients = Invoke-RestMethod -Uri $getClientsUri -Method Get -Headers $headers

            if ($clients.Count -gt 0) {
                Write-Host "✓ Client configuration verified" -ForegroundColor Green
            }
        }
        catch {
            Write-Host "✗ Failed to verify client: $_" -ForegroundColor Red
        }
    }
    else {
        Write-Host "✗ Failed to create client: $_" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "Step 3: Creating test users..." -ForegroundColor Yellow

# Create test user
try {
    $userUri = "$KeycloakUrl/admin/realms/$Realm/users"
    $headers = @{
        Authorization = "Bearer $adminToken"
        "Content-Type" = "application/json"
    }

    $userBody = @{
        username = "testuser"
        email = "testuser@example.com"
        enabled = $true
        firstName = "Test"
        lastName = "User"
        credentials = @(
            @{
                type = "password"
                value = "testuser123"
                temporary = $false
            }
        )
    } | ConvertTo-Json

    $userResponse = Invoke-RestMethod -Uri $userUri -Method Post -Headers $headers -Body $userBody
    Write-Host "✓ Test user 'testuser' created" -ForegroundColor Green
}
catch {
    if ($_.Exception.Response.StatusCode -eq "Conflict") {
        Write-Host "ℹ Test user 'testuser' already exists" -ForegroundColor Cyan
    }
    else {
        Write-Host "⚠ Warning: Could not create test user: $_" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "✓ Keycloak OAuth2 Configuration Complete!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "OAuth2 Client Credentials:" -ForegroundColor Yellow
Write-Host "  Client ID: $ClientId" -ForegroundColor White
Write-Host "  Client Secret: $ClientSecret" -ForegroundColor White
Write-Host "  Realm: $Realm" -ForegroundColor White
Write-Host ""
Write-Host "Test Credentials:" -ForegroundColor Yellow
Write-Host "  Username: testuser" -ForegroundColor White
Write-Host "  Password: testuser123" -ForegroundColor White
Write-Host ""
Write-Host "Admin Credentials:" -ForegroundColor Yellow
Write-Host "  Username: $AdminUsername" -ForegroundColor White
Write-Host "  Password: $AdminPassword" -ForegroundColor White
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "1. Update your Postman collection variables:" -ForegroundColor Cyan
Write-Host "   - client_id: $ClientId"
Write-Host "   - client_secret: $ClientSecret"
Write-Host "   - username: testuser (or admin)"
Write-Host "   - password: testuser123 (or admin)"
Write-Host ""
Write-Host "2. In Postman, go to 'Get Access Token (Client Credentials)' and click 'Send'" -ForegroundColor Cyan
Write-Host "3. Use the token in other API requests" -ForegroundColor Cyan
Write-Host ""
Write-Host "Token Endpoints:" -ForegroundColor Yellow
Write-Host "  Authorization: $KeycloakUrl/realms/$Realm/protocol/openid-connect/auth"
Write-Host "  Token: $KeycloakUrl/realms/$Realm/protocol/openid-connect/token"
Write-Host "  JWK Set: $KeycloakUrl/realms/$Realm/protocol/openid-connect/certs"
Write-Host ""

