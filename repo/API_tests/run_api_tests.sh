#!/bin/bash
###############################################################################
# API Interface Functional Test Runner
# Tests all major API endpoints against a running application instance.
#
# Prerequisites:
#   - The application must be running at BASE_URL (default: http://localhost:8080)
#   - A PostgreSQL database must be available and seeded
#
# Usage:
#   ./run_api_tests.sh                     # use default URL
#   BASE_URL=http://localhost:9090 ./run_api_tests.sh  # custom URL
###############################################################################

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
API="${BASE_URL}/api/v1"

TOTAL=0
PASSED=0
FAILED=0
FAILURES=""

echo "=============================================="
echo "  API INTERFACE FUNCTIONAL TESTS"
echo "=============================================="
echo "Target: $BASE_URL"
echo "Started at: $(date)"
echo "=============================================="

###############################################################################
# Helper functions
###############################################################################

assert_status() {
    local test_name="$1"
    local expected="$2"
    local actual="$3"
    local body="$4"
    TOTAL=$((TOTAL + 1))

    if [ "$actual" -eq "$expected" ]; then
        PASSED=$((PASSED + 1))
        echo "  [PASS] $test_name (HTTP $actual)"
    else
        FAILED=$((FAILED + 1))
        FAILURES="${FAILURES}\n  - ${test_name}: expected HTTP ${expected}, got HTTP ${actual}"
        echo "  [FAIL] $test_name — expected HTTP $expected, got HTTP $actual"
        echo "         Response: $(echo "$body" | head -c 200)"
    fi
}

assert_json_field() {
    local test_name="$1"
    local body="$2"
    local field="$3"
    local expected="$4"
    TOTAL=$((TOTAL + 1))

    local actual
    actual=$(echo "$body" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d${field})" 2>/dev/null || echo "__PARSE_ERROR__")

    if [ "$actual" = "$expected" ]; then
        PASSED=$((PASSED + 1))
        echo "  [PASS] $test_name"
    else
        FAILED=$((FAILED + 1))
        FAILURES="${FAILURES}\n  - ${test_name}: expected '${expected}', got '${actual}'"
        echo "  [FAIL] $test_name — expected '$expected', got '$actual'"
    fi
}

http_get() {
    local url="$1"
    local token="${2:-}"
    local headers=(-s -w "\n%{http_code}" -H "Content-Type: application/json")
    if [ -n "$token" ]; then
        headers+=(-H "Authorization: Bearer $token")
    fi
    curl "${headers[@]}" "$url"
}

http_post() {
    local url="$1"
    local data="$2"
    local token="${3:-}"
    local extra_headers="${4:-}"
    local headers=(-s -w "\n%{http_code}" -H "Content-Type: application/json")
    if [ -n "$token" ]; then
        headers+=(-H "Authorization: Bearer $token")
    fi
    if [ -n "$extra_headers" ]; then
        headers+=(-H "$extra_headers")
    fi
    curl "${headers[@]}" -X POST -d "$data" "$url"
}

http_put() {
    local url="$1"
    local data="$2"
    local token="${3:-}"
    local headers=(-s -w "\n%{http_code}" -H "Content-Type: application/json")
    if [ -n "$token" ]; then
        headers+=(-H "Authorization: $token")
    fi
    curl "${headers[@]}" -X PUT -d "$data" "$url"
}

http_delete() {
    local url="$1"
    local token="${2:-}"
    local headers=(-s -w "\n%{http_code}" -H "Content-Type: application/json")
    if [ -n "$token" ]; then
        headers+=(-H "Authorization: Bearer $token")
    fi
    curl "${headers[@]}" -X DELETE "$url"
}

parse_response() {
    local raw="$1"
    BODY=$(echo "$raw" | sed '$d')
    STATUS=$(echo "$raw" | tail -1)
}

extract_token() {
    echo "$1" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null
}

extract_id() {
    echo "$1" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null
}

###############################################################################
# 1. AUTH TESTS
###############################################################################

echo ""
echo "--- 1. Authentication API ---"

# 1.1 Register with valid data
UNIQUE_EMAIL="apitest_$(date +%s)@example.com"
parse_response "$(http_post "$API/auth/register" "{\"email\":\"$UNIQUE_EMAIL\",\"password\":\"T3st!Pass\",\"displayName\":\"API Test User\"}")"
assert_status "POST /auth/register — valid registration" 201 "$STATUS" "$BODY"
assert_json_field "POST /auth/register — returns MEMBER role" "$BODY" "['data']['role']" "MEMBER"

# 1.2 Register with duplicate email
parse_response "$(http_post "$API/auth/register" "{\"email\":\"$UNIQUE_EMAIL\",\"password\":\"T3st!Pass\",\"displayName\":\"Duplicate\"}")"
assert_status "POST /auth/register — duplicate email rejected" 409 "$STATUS" "$BODY"

# 1.3 Register with weak password
parse_response "$(http_post "$API/auth/register" "{\"email\":\"weak_$(date +%s)@example.com\",\"password\":\"weak\",\"displayName\":\"Weak\"}")"
assert_status "POST /auth/register — weak password rejected" 400 "$STATUS" "$BODY"

# 1.4 Register with missing fields
parse_response "$(http_post "$API/auth/register" "{}")"
assert_status "POST /auth/register — missing fields rejected" 400 "$STATUS" "$BODY"

# 1.5 Login with valid credentials
parse_response "$(http_post "$API/auth/login" "{\"email\":\"$UNIQUE_EMAIL\",\"password\":\"T3st!Pass\"}")"
assert_status "POST /auth/login — valid credentials" 200 "$STATUS" "$BODY"
assert_json_field "POST /auth/login — returns Bearer type" "$BODY" "['data']['tokenType']" "Bearer"
MEMBER_TOKEN=$(extract_token "$BODY")

# 1.6 Login with invalid password
parse_response "$(http_post "$API/auth/login" "{\"email\":\"$UNIQUE_EMAIL\",\"password\":\"WrongPass1!\"}")"
assert_status "POST /auth/login — invalid password" 401 "$STATUS" "$BODY"

# 1.7 Login with nonexistent email
parse_response "$(http_post "$API/auth/login" "{\"email\":\"nobody_$(date +%s)@example.com\",\"password\":\"T3st!Pass\"}")"
assert_status "POST /auth/login — nonexistent email" 401 "$STATUS" "$BODY"

# 1.8 Get current user
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_get "$API/auth/me" "$MEMBER_TOKEN")"
    assert_status "GET /auth/me — authenticated user" 200 "$STATUS" "$BODY"
fi

# 1.9 Get current user without token
parse_response "$(http_get "$API/auth/me")"
assert_status "GET /auth/me — no token rejected" 401 "$STATUS" "$BODY"

# 1.10 Refresh token
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_post "$API/auth/refresh" "{}" "$MEMBER_TOKEN")"
    assert_status "POST /auth/refresh — token refresh" 200 "$STATUS" "$BODY"
    MEMBER_TOKEN=$(extract_token "$BODY")
fi

###############################################################################
# 2. LISTING TESTS
###############################################################################

echo ""
echo "--- 2. Listings API ---"

# Try to login as a seller (uses seeded data)
parse_response "$(http_post "$API/auth/login" "{\"email\":\"seller@heritage.local\",\"password\":\"S3ller!Pass\"}")"
SELLER_TOKEN=$(extract_token "$BODY")

# 2.1 Search listings (public, no auth needed)
parse_response "$(http_get "$API/listings")"
assert_status "GET /listings — search without auth" 200 "$STATUS" "$BODY"

# 2.2 Search listings with filters
parse_response "$(http_get "$API/listings?keyword=desk&priceMin=10&priceMax=500&page=0&pageSize=5")"
assert_status "GET /listings — search with filters" 200 "$STATUS" "$BODY"

# 2.3 Trending listings
parse_response "$(http_get "$API/listings/trending?limit=5")"
assert_status "GET /listings/trending — trending endpoint" 200 "$STATUS" "$BODY"

# 2.4 Create listing without auth
parse_response "$(http_post "$API/listings" "{\"title\":\"Test\",\"description\":\"Desc\",\"category\":\"furniture\",\"price\":100}")"
assert_status "POST /listings — create without auth rejected" 401 "$STATUS" "$BODY"

# 2.5 Create listing with SELLER token (if seller login succeeded)
if [ -n "${SELLER_TOKEN:-}" ]; then
    parse_response "$(http_post "$API/listings" "{\"title\":\"API Test Listing\",\"description\":\"Test description for API\",\"category\":\"furniture\",\"price\":199.99,\"tags\":[\"vintage\"],\"neighborhood\":\"Downtown\"}" "$SELLER_TOKEN")"
    assert_status "POST /listings — seller creates listing" 201 "$STATUS" "$BODY"
    LISTING_ID=$(extract_id "$BODY")

    # 2.6 Get listing detail
    if [ -n "${LISTING_ID:-}" ]; then
        parse_response "$(http_get "$API/listings/$LISTING_ID" "$SELLER_TOKEN")"
        assert_status "GET /listings/{id} — listing detail" 200 "$STATUS" "$BODY"
    fi
fi

# 2.7 Get nonexistent listing
parse_response "$(http_get "$API/listings/00000000-0000-0000-0000-000000000000")"
assert_status "GET /listings/{id} — nonexistent returns 404" 404 "$STATUS" "$BODY"

# 2.8 Create listing with member token (should fail authorization)
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_post "$API/listings" "{\"title\":\"No\",\"description\":\"No\",\"category\":\"furniture\",\"price\":10}" "$MEMBER_TOKEN")"
    assert_status "POST /listings — member cannot create listing" 403 "$STATUS" "$BODY"
fi

###############################################################################
# 3. ORDER TESTS
###############################################################################

echo ""
echo "--- 3. Orders API ---"

# 3.1 Create order without auth
parse_response "$(http_post "$API/orders" "{\"items\":[{\"listingId\":\"00000000-0000-0000-0000-000000000001\",\"quantity\":1}]}")"
assert_status "POST /orders — create without auth rejected" 401 "$STATUS" "$BODY"

# 3.2 Create order without idempotency key
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_post "$API/orders" "{\"items\":[{\"listingId\":\"00000000-0000-0000-0000-000000000001\",\"quantity\":1}]}" "$MEMBER_TOKEN")"
    assert_status "POST /orders — missing idempotency key" 400 "$STATUS" "$BODY"
fi

# 3.3 Get my orders
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_get "$API/orders/me" "$MEMBER_TOKEN")"
    assert_status "GET /orders/me — list member orders" 200 "$STATUS" "$BODY"
fi

# 3.4 Get my orders with status filter
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_get "$API/orders/me?status=CONFIRMED&page=0&pageSize=10" "$MEMBER_TOKEN")"
    assert_status "GET /orders/me — filter by status" 200 "$STATUS" "$BODY"
fi

# 3.5 Get nonexistent order
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_get "$API/orders/00000000-0000-0000-0000-000000000000" "$MEMBER_TOKEN")"
    assert_status "GET /orders/{id} — nonexistent returns 404" 404 "$STATUS" "$BODY"
fi

###############################################################################
# 4. TICKET TESTS
###############################################################################

echo ""
echo "--- 4. Tickets API ---"

# 4.1 Create ticket without auth
parse_response "$(http_post "$API/tickets" "{\"type\":\"DELIVERY_DISPUTE\",\"severity\":\"MEDIUM\",\"description\":\"Test ticket\"}")"
assert_status "POST /tickets — create without auth rejected" 401 "$STATUS" "$BODY"

# 4.2 Create ticket with member token
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_post "$API/tickets" "{\"type\":\"DELIVERY_DISPUTE\",\"severity\":\"MEDIUM\",\"locationAddress\":\"123 Main St\",\"description\":\"Package was damaged\"}" "$MEMBER_TOKEN")"
    assert_status "POST /tickets — member creates ticket" 201 "$STATUS" "$BODY"
    TICKET_ID=$(extract_id "$BODY")
    assert_json_field "POST /tickets — status is OPEN" "$BODY" "['data']['status']" "OPEN"
fi

# 4.3 List tickets
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_get "$API/tickets?page=0&pageSize=10" "$MEMBER_TOKEN")"
    assert_status "GET /tickets — list tickets" 200 "$STATUS" "$BODY"
fi

# 4.4 Get ticket detail
if [ -n "${TICKET_ID:-}" ] && [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_get "$API/tickets/$TICKET_ID" "$MEMBER_TOKEN")"
    assert_status "GET /tickets/{id} — ticket detail" 200 "$STATUS" "$BODY"
fi

# 4.5 Create ticket with missing required fields
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_post "$API/tickets" "{}" "$MEMBER_TOKEN")"
    assert_status "POST /tickets — missing fields rejected" 400 "$STATUS" "$BODY"
fi

###############################################################################
# 5. INVENTORY TESTS
###############################################################################

echo ""
echo "--- 5. Inventory API ---"

# 5.1 List warehouses without auth
parse_response "$(http_get "$API/warehouses")"
assert_status "GET /warehouses — without auth rejected" 401 "$STATUS" "$BODY"

# 5.2 List warehouses with seller token
if [ -n "${SELLER_TOKEN:-}" ]; then
    parse_response "$(http_get "$API/warehouses" "$SELLER_TOKEN")"
    assert_status "GET /warehouses — seller lists warehouses" 200 "$STATUS" "$BODY"
fi

# 5.3 List inventory
if [ -n "${SELLER_TOKEN:-}" ]; then
    parse_response "$(http_get "$API/inventory" "$SELLER_TOKEN")"
    assert_status "GET /inventory — seller lists inventory" 200 "$STATUS" "$BODY"
fi

# 5.4 Get low stock alerts
if [ -n "${SELLER_TOKEN:-}" ]; then
    parse_response "$(http_get "$API/inventory/alerts/low-stock" "$SELLER_TOKEN")"
    assert_status "GET /inventory/alerts/low-stock — low stock alerts" 200 "$STATUS" "$BODY"
fi

# 5.5 Create warehouse with member token (should fail)
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_post "$API/warehouses" "{\"name\":\"Test WH\",\"address\":\"Test\",\"status\":\"ACTIVE\"}" "$MEMBER_TOKEN")"
    assert_status "POST /warehouses — member cannot create warehouse" 403 "$STATUS" "$BODY"
fi

###############################################################################
# 6. APPEAL TESTS
###############################################################################

echo ""
echo "--- 6. Appeals API ---"

# 6.1 List appeals without auth
parse_response "$(http_get "$API/appeals")"
assert_status "GET /appeals — without auth rejected" 401 "$STATUS" "$BODY"

# 6.2 List appeals with member token
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_get "$API/appeals?page=0&pageSize=10" "$MEMBER_TOKEN")"
    assert_status "GET /appeals — member lists appeals" 200 "$STATUS" "$BODY"
fi

# 6.3 Get nonexistent appeal
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_get "$API/appeals/00000000-0000-0000-0000-000000000000" "$MEMBER_TOKEN")"
    assert_status "GET /appeals/{id} — nonexistent returns 404" 404 "$STATUS" "$BODY"
fi

###############################################################################
# 7. RISK TESTS
###############################################################################

echo ""
echo "--- 7. Risk API ---"

# Try to login as admin (uses seeded data)
parse_response "$(http_post "$API/auth/login" "{\"email\":\"admin@heritage.local\",\"password\":\"Adm1n!Pass\"}")"
ADMIN_TOKEN=$(extract_token "$BODY")

# 7.1 Risk dashboard without auth
parse_response "$(http_get "$API/risk/dashboard")"
assert_status "GET /risk/dashboard — without auth rejected" 401 "$STATUS" "$BODY"

# 7.2 Risk dashboard with member (should fail authorization)
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_get "$API/risk/dashboard" "$MEMBER_TOKEN")"
    assert_status "GET /risk/dashboard — member cannot access" 403 "$STATUS" "$BODY"
fi

# 7.3 Risk dashboard with admin
if [ -n "${ADMIN_TOKEN:-}" ]; then
    parse_response "$(http_get "$API/risk/dashboard" "$ADMIN_TOKEN")"
    assert_status "GET /risk/dashboard — admin access" 200 "$STATUS" "$BODY"
fi

# 7.4 Risk flags with admin
if [ -n "${ADMIN_TOKEN:-}" ]; then
    parse_response "$(http_get "$API/risk/flags?page=0&pageSize=10" "$ADMIN_TOKEN")"
    assert_status "GET /risk/flags — admin lists flags" 200 "$STATUS" "$BODY"
fi

###############################################################################
# 8. AUDIT LOGS TESTS
###############################################################################

echo ""
echo "--- 8. Audit Logs API ---"

# 8.1 Audit logs without auth
parse_response "$(http_get "$API/audit-logs")"
assert_status "GET /audit-logs — without auth rejected" 401 "$STATUS" "$BODY"

# 8.2 Audit logs with member (should fail)
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_get "$API/audit-logs" "$MEMBER_TOKEN")"
    assert_status "GET /audit-logs — member cannot access" 403 "$STATUS" "$BODY"
fi

# 8.3 Audit logs with admin
if [ -n "${ADMIN_TOKEN:-}" ]; then
    parse_response "$(http_get "$API/audit-logs?page=0&pageSize=10" "$ADMIN_TOKEN")"
    assert_status "GET /audit-logs — admin access" 200 "$STATUS" "$BODY"
fi

###############################################################################
# 9. LOGOUT TEST
###############################################################################

echo ""
echo "--- 9. Logout ---"

# 9.1 Logout
if [ -n "$MEMBER_TOKEN" ]; then
    parse_response "$(http_post "$API/auth/logout" "{}" "" "Authorization: Bearer $MEMBER_TOKEN")"
    assert_status "POST /auth/logout — successful logout" 200 "$STATUS" "$BODY"

    # 9.2 Verify token is invalidated
    parse_response "$(http_get "$API/auth/me" "$MEMBER_TOKEN")"
    assert_status "GET /auth/me — after logout rejected" 401 "$STATUS" "$BODY"
fi

###############################################################################
# SUMMARY
###############################################################################

echo ""
echo "=============================================="
echo "  API TEST RESULTS SUMMARY"
echo "=============================================="
echo "  Total:   $TOTAL"
echo "  Passed:  $PASSED"
echo "  Failed:  $FAILED"
echo "  Finished at: $(date)"
echo "=============================================="

if [ "$FAILED" -gt 0 ]; then
    echo ""
    echo "  Failed tests:"
    echo -e "$FAILURES"
    echo ""
fi

if [ "$FAILED" -gt 0 ]; then
    exit 1
else
    exit 0
fi
