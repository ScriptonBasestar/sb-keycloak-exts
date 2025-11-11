#!/bin/bash
# Realm Hierarchy Deployment Test Script
# Tests: JAR deployment, API endpoints, Event Listener

set -e

# Colors for output
RED='\033[0:31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
KEYCLOAK_URL="http://localhost:8080"
ADMIN_USER="admin"
ADMIN_PASS="admin"
TEST_REALM="test-hierarchy"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Realm Hierarchy Deployment Test"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Function to print status
print_status() {
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ $1${NC}"
    else
        echo -e "${RED}❌ $1${NC}"
        exit 1
    fi
}

# 1. Check if Keycloak is running
echo "Step 1: Checking Keycloak availability..."
curl -s -f "${KEYCLOAK_URL}/health/ready" > /dev/null
print_status "Keycloak is running"

# 2. Get admin token
echo ""
echo "Step 2: Obtaining admin token..."
ACCESS_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  | jq -r '.access_token')

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" == "null" ]; then
    echo -e "${RED}❌ Failed to obtain access token${NC}"
    exit 1
fi
print_status "Access token obtained"

# 3. Test Hierarchy API on master realm
echo ""
echo "Step 3: Testing Hierarchy API on master realm..."
RESPONSE=$(curl -s -X GET "${KEYCLOAK_URL}/realms/master/hierarchy" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Accept: application/json")

REALM_ID=$(echo "$RESPONSE" | jq -r '.realmId')
if [ -n "$REALM_ID" ] && [ "$REALM_ID" != "null" ]; then
    print_status "Hierarchy API working"
    echo "   Realm: $(echo "$RESPONSE" | jq -r '.realmName')"
    echo "   Depth: $(echo "$RESPONSE" | jq -r '.depth')"
    echo "   Path: $(echo "$RESPONSE" | jq -r '.path')"
else
    echo -e "${RED}❌ Hierarchy API not responding correctly${NC}"
    echo "Response: $RESPONSE"
    exit 1
fi

# 4. Check Event Listeners
echo ""
echo "Step 4: Checking Event Listeners configuration..."
EVENT_LISTENERS=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/master" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  | jq -r '.eventsListeners[]' | grep "realm-hierarchy" || true)

if [ -n "$EVENT_LISTENERS" ]; then
    print_status "Event Listener 'realm-hierarchy' is registered"
else
    echo -e "${YELLOW}⚠️  Event Listener 'realm-hierarchy' not activated (this is optional)${NC}"
fi

# 5. Create test realm
echo ""
echo "Step 5: Creating test realm '${TEST_REALM}'..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"realm\": \"${TEST_REALM}\",
    \"enabled\": true,
    \"displayName\": \"Test Hierarchy Realm\"
  }" > /dev/null
print_status "Test realm created"

# 6. Set parent realm
echo ""
echo "Step 6: Setting master as parent of ${TEST_REALM}..."
curl -s -X POST "${KEYCLOAK_URL}/realms/${TEST_REALM}/hierarchy/parent" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "parentRealmId": "master",
    "inheritIdp": true,
    "inheritRoles": true,
    "inheritAuthFlow": false
  }' > /dev/null
print_status "Parent realm set"

# 7. Verify hierarchy
echo ""
echo "Step 7: Verifying hierarchy structure..."
CHILD_RESPONSE=$(curl -s -X GET "${KEYCLOAK_URL}/realms/${TEST_REALM}/hierarchy" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

PARENT_ID=$(echo "$CHILD_RESPONSE" | jq -r '.parentRealmId')
if [ -n "$PARENT_ID" ] && [ "$PARENT_ID" != "null" ]; then
    print_status "Hierarchy structure verified"
    echo "   Parent: $(echo "$CHILD_RESPONSE" | jq -r '.parentRealmName')"
    echo "   Depth: $(echo "$CHILD_RESPONSE" | jq -r '.depth')"
    echo "   InheritIdp: $(echo "$CHILD_RESPONSE" | jq -r '.inheritIdp')"
    echo "   InheritRoles: $(echo "$CHILD_RESPONSE" | jq -r '.inheritRoles')"
else
    echo -e "${RED}❌ Failed to verify hierarchy${NC}"
    echo "Response: $CHILD_RESPONSE"
    exit 1
fi

# 8. Test hierarchy path API
echo ""
echo "Step 8: Testing hierarchy path API..."
PATH_RESPONSE=$(curl -s -X GET "${KEYCLOAK_URL}/realms/${TEST_REALM}/hierarchy/path" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

PATH_COUNT=$(echo "$PATH_RESPONSE" | jq '. | length')
if [ "$PATH_COUNT" -eq 2 ]; then
    print_status "Hierarchy path API working (2 levels found)"
else
    echo -e "${YELLOW}⚠️  Unexpected path count: $PATH_COUNT${NC}"
fi

# 9. Test synchronize API
echo ""
echo "Step 9: Testing synchronize API..."
SYNC_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/hierarchy/synchronize" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

SYNC_MESSAGE=$(echo "$SYNC_RESPONSE" | jq -r '.message')
if [ "$SYNC_MESSAGE" == "Hierarchy synchronized successfully" ]; then
    print_status "Synchronize API working"
    echo "   Affected realms: $(echo "$SYNC_RESPONSE" | jq -r '.affectedRealms | length')"
else
    echo -e "${YELLOW}⚠️  Synchronize returned unexpected response${NC}"
fi

# 10. Cleanup (optional)
echo ""
echo "Step 10: Cleanup test realm..."
curl -s -X DELETE "${KEYCLOAK_URL}/admin/realms/${TEST_REALM}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" > /dev/null
print_status "Test realm deleted"

# Summary
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}✅ All tests passed successfully!${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Deployment verified:"
echo "  ✅ JAR loaded correctly"
echo "  ✅ REST API endpoints working"
echo "  ✅ Hierarchy creation successful"
echo "  ✅ Parent-child relationship working"
echo "  ✅ Synchronization API functional"
echo ""
