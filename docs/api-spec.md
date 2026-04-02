# Heritage Marketplace Operations Management System — REST API Specification

## Base URL

```
https://<host>/api/v1
```

All endpoints are served over TLS on the local network. Responses use `application/json` unless otherwise noted.

---

## Common Headers

| Header | Description |
|--------|-------------|
| `Authorization` | `Bearer <signed-jwt-token>` — Required for all authenticated endpoints |
| `Content-Type` | `application/json` (default) or `multipart/form-data` (file uploads) |
| `X-Idempotency-Key` | Client-generated UUID — Required for mutating payment/order operations |
| `X-Request-Id` | Client-generated UUID for request tracing |

## Common Response Envelope

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "meta": {
    "page": 1,
    "pageSize": 20,
    "totalItems": 142,
    "totalPages": 8
  }
}
```

## Error Response

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "STOCK_UNAVAILABLE",
    "message": "Requested quantity is no longer available.",
    "details": { "listing_id": "...", "available": 0 }
  }
}
```

## Standard HTTP Status Codes

| Code | Usage |
|------|-------|
| `200` | Success |
| `201` | Resource created |
| `204` | Success, no content |
| `400` | Validation error / bad request |
| `401` | Unauthenticated |
| `403` | Forbidden (insufficient role) |
| `404` | Resource not found |
| `409` | Conflict (e.g., concurrent stock reservation) |
| `423` | Account locked |
| `429` | Rate limit exceeded (includes `Retry-After` header) |
| `500` | Internal server error |

---

## 1. Authentication & Session

### 1.1 Register

```
POST /auth/register
```

**Access**: Public

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "SecureP@ss123",
  "display_name": "Jane Doe",
  "phone": "555-123-4567"
}
```

**Validation**:
- `email`: Required, valid email format, unique
- `password`: Required, min 8 chars, must include uppercase, lowercase, digit, special char
- `display_name`: Required, 2–100 chars
- `phone`: Optional, E.164 or US format

**Response** `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "user@example.com",
    "display_name": "Jane Doe",
    "role": "MEMBER",
    "created_at": "2026-04-01T10:00:00Z"
  }
}
```

---

### 1.2 Login

```
POST /auth/login
```

**Access**: Public

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "SecureP@ss123"
}
```

**Rate Limit**: 10 attempts/hour per account. Exceeding triggers 15-minute lockout.

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGci...",
    "expires_at": "2026-04-01T11:00:00Z",
    "user": {
      "id": "uuid",
      "email": "user@example.com",
      "display_name": "Jane Doe",
      "role": "MEMBER"
    }
  }
}
```

**Error** `423 Locked`:
```json
{
  "success": false,
  "error": {
    "code": "ACCOUNT_LOCKED",
    "message": "Too many failed attempts. Try again in 15 minutes.",
    "details": { "lockout_until": "2026-04-01T10:15:00Z" }
  }
}
```

---

### 1.3 Logout

```
POST /auth/logout
```

**Access**: Authenticated

**Response** `204 No Content`

---

### 1.4 Refresh Token

```
POST /auth/refresh
```

**Access**: Authenticated (with valid, non-expired token)

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGci...",
    "expires_at": "2026-04-01T12:00:00Z"
  }
}
```

---

### 1.5 Get Current User

```
GET /auth/me
```

**Access**: Authenticated

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "user@example.com",
    "display_name": "Jane Doe",
    "phone": "555-***-4567",
    "role": "MEMBER",
    "status": "ACTIVE",
    "created_at": "2026-04-01T10:00:00Z"
  }
}
```

> **Note**: Phone is returned masked. Full phone visible only to `ADMIN`.

---

## 2. User Management

### 2.1 List Users

```
GET /users?role={role}&status={status}&page={page}&pageSize={pageSize}
```

**Access**: `ADMIN`

**Query Parameters**:
| Param | Type | Description |
|-------|------|-------------|
| `role` | string | Filter by role |
| `status` | string | Filter by status (`ACTIVE`, `LOCKED`, `PENDING_DELETION`) |
| `page` | int | Page number (default: 1) |
| `pageSize` | int | Items per page (default: 20, max: 100) |

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "email": "u***@example.com",
      "display_name": "Jane Doe",
      "role": "MEMBER",
      "status": "ACTIVE",
      "created_at": "2026-04-01T10:00:00Z"
    }
  ],
  "meta": { "page": 1, "pageSize": 20, "totalItems": 45, "totalPages": 3 }
}
```

---

### 2.2 Get User by ID

```
GET /users/{userId}
```

**Access**: `ADMIN`

---

### 2.3 Update User Role

```
PATCH /users/{userId}/role
```

**Access**: `ADMIN`

**Request Body**:
```json
{
  "role": "SELLER"
}
```

**Response** `200 OK`

---

### 2.4 Request Account Deletion

```
POST /users/me/deletion-request
```

**Access**: Authenticated (own account)

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "deletion_requested_at": "2026-04-01T10:00:00Z",
    "deletion_effective_at": "2026-05-01T10:00:00Z",
    "message": "Your account will be permanently anonymized after the 30-day cooling-off period."
  }
}
```

---

### 2.5 Cancel Account Deletion

```
DELETE /users/me/deletion-request
```

**Access**: Authenticated (own account, during cooling-off period)

**Response** `200 OK`

---

## 3. Membership Tiers & Benefits

### 3.1 Get My Membership

```
GET /memberships/me
```

**Access**: `MEMBER`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "tier": {
      "id": "uuid",
      "name": "Silver",
      "rank": 2,
      "spend_threshold_min": 500.00,
      "spend_threshold_max": 1499.99
    },
    "total_spend": 872.50,
    "tier_valid_until": "2027-04-01",
    "benefits": [
      {
        "id": "uuid",
        "name": "Silver 10% Discount",
        "type": "PERCENTAGE_DISCOUNT",
        "value": 10.00,
        "scope_category": "Crafts",
        "stackable": true,
        "mutual_exclusion_group": null
      },
      {
        "id": "uuid",
        "name": "Free Shipping",
        "type": "FREE_SHIPPING",
        "value": null,
        "stackable": true,
        "mutual_exclusion_group": null
      }
    ]
  }
}
```

---

### 3.2 List Tier Configurations

```
GET /tiers
```

**Access**: `ADMIN`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    { "id": "uuid", "name": "Bronze", "rank": 1, "spend_threshold_min": 0.00, "spend_threshold_max": 499.99 },
    { "id": "uuid", "name": "Silver", "rank": 2, "spend_threshold_min": 500.00, "spend_threshold_max": 1499.99 },
    { "id": "uuid", "name": "Gold", "rank": 3, "spend_threshold_min": 1500.00, "spend_threshold_max": null }
  ]
}
```

---

### 3.3 Create Tier Configuration

```
POST /tiers
```

**Access**: `ADMIN`

**Request Body**:
```json
{
  "name": "Platinum",
  "spend_threshold_min": 3000.00,
  "spend_threshold_max": null,
  "rank": 4
}
```

**Response** `201 Created`

---

### 3.4 Update Tier Configuration

```
PUT /tiers/{tierId}
```

**Access**: `ADMIN`

---

### 3.5 List Benefit Packages

```
GET /tiers/{tierId}/benefits
```

**Access**: `ADMIN`

---

### 3.6 Create Benefit Package

```
POST /tiers/{tierId}/benefits
```

**Access**: `ADMIN`

**Request Body**:
```json
{
  "name": "Gold Exclusive Pricing - Pottery",
  "type": "EXCLUSIVE_PRICE",
  "value": 45.00,
  "scope_category": "Pottery",
  "scope_seller_id": null,
  "scope_date_start": "2026-04-01",
  "scope_date_end": "2026-06-30",
  "stackable": false,
  "mutual_exclusion_group": "pricing_override",
  "priority": 10
}
```

**Response** `201 Created`

---

### 3.7 Update Benefit Package

```
PUT /tiers/{tierId}/benefits/{benefitId}
```

**Access**: `ADMIN`

---

### 3.8 Delete Benefit Package

```
DELETE /tiers/{tierId}/benefits/{benefitId}
```

**Access**: `ADMIN`

**Response** `204 No Content`

---

### 3.9 Preview Checkout Benefits

```
POST /memberships/me/preview-benefits
```

**Access**: `MEMBER`

**Description**: Given a cart, returns which benefits apply, which are blocked, and why.

**Request Body**:
```json
{
  "items": [
    { "listing_id": "uuid", "quantity": 2 }
  ]
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "applied": [
      {
        "benefit_id": "uuid",
        "name": "Silver 10% Discount",
        "type": "PERCENTAGE_DISCOUNT",
        "discount_amount": 12.50
      }
    ],
    "blocked": [
      {
        "benefit_id": "uuid",
        "name": "Free Shipping",
        "reason": "Not eligible for items in category 'Experiences'."
      }
    ],
    "subtotal": 125.00,
    "total_discount": 12.50,
    "final_total": 112.50
  }
}
```

---

## 4. Listings

### 4.1 Search Listings

```
GET /listings?q={keyword}&neighborhood={neighborhood}&radius={miles}&lat={lat}&lng={lng}&priceMin={min}&priceMax={max}&sqftMin={min}&sqftMax={max}&tags={tag1,tag2}&availFrom={date}&availTo={date}&sort={sort}&page={page}&pageSize={pageSize}
```

**Access**: Public (Guest and above)

**Query Parameters**:
| Param | Type | Description |
|-------|------|-------------|
| `q` | string | Keyword search (full-text) |
| `neighborhood` | string | Filter by neighborhood |
| `radius` | float | Radius in miles (requires `lat` and `lng`) |
| `lat` | float | User latitude for distance calc |
| `lng` | float | User longitude for distance calc |
| `priceMin` | decimal | Minimum price |
| `priceMax` | decimal | Maximum price |
| `sqftMin` | decimal | Minimum square footage |
| `sqftMax` | decimal | Maximum square footage |
| `tags` | string | Comma-separated tag list |
| `availFrom` | date | Availability window start (ISO 8601) |
| `availTo` | date | Availability window end (ISO 8601) |
| `sort` | string | `newest`, `price_asc`, `price_desc`, `distance`, `popularity` |
| `page` | int | Default: 1 |
| `pageSize` | int | Default: 20, max: 100 |

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "title": "Handcrafted Ethiopian Coffee Set",
      "category": "Crafts",
      "price": 65.00,
      "neighborhood": "Historic District",
      "tags": ["coffee", "handmade", "traditional"],
      "distance_miles": 2.4,
      "trending": true,
      "availability_start": "2026-04-05",
      "availability_end": "2026-04-30",
      "seller": {
        "id": "uuid",
        "display_name": "Artisan Crafts Co."
      },
      "created_at": "2026-03-28T14:00:00Z"
    }
  ],
  "meta": { "page": 1, "pageSize": 20, "totalItems": 87, "totalPages": 5 }
}
```

---

### 4.2 Get Listing Detail

```
GET /listings/{listingId}
```

**Access**: Public

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "title": "Handcrafted Ethiopian Coffee Set",
    "description": "A beautifully crafted traditional coffee set...",
    "category": "Crafts",
    "price": 65.00,
    "tags": ["coffee", "handmade", "traditional"],
    "neighborhood": "Historic District",
    "latitude": 38.9072,
    "longitude": -77.0369,
    "layout_sqft": null,
    "availability_start": "2026-04-05",
    "availability_end": "2026-04-30",
    "view_count": 342,
    "trending_score": 58.2,
    "status": "ACTIVE",
    "seller": {
      "id": "uuid",
      "display_name": "Artisan Crafts Co."
    },
    "stock_summary": {
      "total_available": 24,
      "warehouses": [
        { "warehouse_id": "uuid", "name": "Main Warehouse", "available": 15 },
        { "warehouse_id": "uuid", "name": "Downtown Store", "available": 9 }
      ]
    },
    "tier_pricing": {
      "has_exclusive_price": true,
      "exclusive_price": 55.00,
      "applicable_tier": "Gold",
      "note": "Gold members get exclusive pricing on this item."
    },
    "created_at": "2026-03-28T14:00:00Z"
  }
}
```

> **Note**: `tier_pricing` section only included for authenticated `MEMBER` requests. `stock_summary` only included for `SELLER`, `WAREHOUSE_STAFF`, and `ADMIN`.

---

### 4.3 Get Trending Listings

```
GET /listings/trending?limit={limit}
```

**Access**: Public

**Query Parameters**:
| Param | Type | Description |
|-------|------|-------------|
| `limit` | int | Number of trending listings to return (default: 10, max: 50) |

**Response** `200 OK`: Array of listing summaries sorted by `trending_score DESC`.

---

### 4.4 Create Listing

```
POST /listings
```

**Access**: `SELLER`, `ADMIN`

**Request Body**:
```json
{
  "title": "Handcrafted Basket",
  "description": "Traditional woven basket made from natural fibers.",
  "category": "Crafts",
  "price": 35.00,
  "tags": ["basket", "handmade"],
  "neighborhood": "Art Quarter",
  "latitude": 38.9100,
  "longitude": -77.0200,
  "layout_sqft": null,
  "availability_start": "2026-04-10",
  "availability_end": "2026-05-10"
}
```

**Validation**:
- `title`: Required, 3–255 chars
- `price`: Required, > 0
- `category`: Required
- `latitude`/`longitude`: Optional, valid coordinate ranges

**Response** `201 Created`

---

### 4.5 Update Listing

```
PUT /listings/{listingId}
```

**Access**: `SELLER` (own listings), `ADMIN`

---

### 4.6 Delete / Deactivate Listing

```
DELETE /listings/{listingId}
```

**Access**: `SELLER` (own listings), `ADMIN`

Sets `status` to `REMOVED`. Does not hard-delete for audit trail.

**Response** `204 No Content`

---

### 4.7 Get Recent Searches

```
GET /listings/recent-searches
```

**Access**: Authenticated

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "query": "pottery",
      "filters": { "neighborhood": "Historic District", "priceMax": 100 },
      "searched_at": "2026-04-01T09:30:00Z"
    }
  ]
}
```

Returns last 20 searches for the current user.

---

### 4.8 Clear Recent Searches

```
DELETE /listings/recent-searches
```

**Access**: Authenticated

**Response** `204 No Content`

---

## 5. Inventory Management

### 5.1 List Warehouses

```
GET /warehouses
```

**Access**: `SELLER`, `WAREHOUSE_STAFF`, `ADMIN`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "Main Warehouse",
      "address": "123 Heritage Ln",
      "latitude": 38.9072,
      "longitude": -77.0369,
      "status": "ACTIVE"
    }
  ]
}
```

---

### 5.2 Create Warehouse

```
POST /warehouses
```

**Access**: `ADMIN`

**Request Body**:
```json
{
  "name": "Downtown Store",
  "address": "456 Market St",
  "latitude": 38.9150,
  "longitude": -77.0300
}
```

**Response** `201 Created`

---

### 5.3 Update Warehouse

```
PUT /warehouses/{warehouseId}
```

**Access**: `ADMIN`

---

### 5.4 Get Inventory for Listing

```
GET /inventory?listingId={listingId}&warehouseId={warehouseId}
```

**Access**: `SELLER`, `WAREHOUSE_STAFF`, `ADMIN`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "listing_id": "uuid",
      "warehouse_id": "uuid",
      "warehouse_name": "Main Warehouse",
      "available_qty": 15,
      "reserved_qty": 3,
      "low_stock_threshold": 5,
      "is_low_stock": false
    }
  ]
}
```

---

### 5.5 Update Low Stock Threshold

```
PATCH /inventory/{inventoryId}/threshold
```

**Access**: `SELLER`, `ADMIN`

**Request Body**:
```json
{
  "low_stock_threshold": 10
}
```

**Response** `200 OK`

---

### 5.6 Create Inventory Document (Inbound / Outbound)

```
POST /inventory/documents
```

**Access**: `SELLER`, `WAREHOUSE_STAFF`, `ADMIN`

**Request Body**:
```json
{
  "type": "INBOUND",
  "warehouse_id": "uuid",
  "items": [
    {
      "listing_id": "uuid",
      "quantity": 50,
      "notes": "Restocking from supplier batch #2024-Q2"
    }
  ]
}
```

**Validation**:
- `type`: Required, `INBOUND` or `OUTBOUND`
- `quantity`: > 0 for inbound; for outbound, must not exceed `available_qty`

**Response** `201 Created`:
```json
{
  "success": true,
  "data": {
    "document_ref": "INB-20260401-001",
    "type": "INBOUND",
    "warehouse_id": "uuid",
    "items_processed": 1,
    "movements": [
      {
        "inventory_id": "uuid",
        "quantity_change": 50,
        "new_available_qty": 65
      }
    ]
  }
}
```

---

### 5.7 Run Stocktake

```
POST /inventory/stocktake
```

**Access**: `WAREHOUSE_STAFF`, `ADMIN`

**Request Body**:
```json
{
  "warehouse_id": "uuid",
  "counts": [
    { "listing_id": "uuid", "actual_count": 42 }
  ]
}
```

**Description**: Adjusts `available_qty` to match actual counts. Creates adjustment movement records.

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "document_ref": "STK-20260401-001",
    "adjustments": [
      {
        "listing_id": "uuid",
        "previous_qty": 45,
        "actual_count": 42,
        "adjustment": -3
      }
    ]
  }
}
```

---

### 5.8 Get Inventory Movement History

```
GET /inventory/{inventoryId}/movements?page={page}&pageSize={pageSize}
```

**Access**: `SELLER`, `WAREHOUSE_STAFF`, `ADMIN`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "document_type": "INBOUND",
      "document_ref": "INB-20260401-001",
      "quantity_change": 50,
      "operator": { "id": "uuid", "display_name": "John Smith" },
      "warehouse": { "id": "uuid", "name": "Main Warehouse" },
      "notes": "Restocking from supplier",
      "created_at": "2026-04-01T08:00:00Z"
    }
  ],
  "meta": { "page": 1, "pageSize": 20, "totalItems": 156, "totalPages": 8 }
}
```

---

### 5.9 Get Low Stock Alerts

```
GET /inventory/alerts/low-stock?warehouseId={warehouseId}
```

**Access**: `SELLER`, `WAREHOUSE_STAFF`, `ADMIN`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "inventory_id": "uuid",
      "listing_id": "uuid",
      "listing_title": "Handcrafted Basket",
      "warehouse_name": "Main Warehouse",
      "available_qty": 3,
      "low_stock_threshold": 5,
      "severity": "critical"
    }
  ]
}
```

---

## 6. Orders & Checkout

### 6.1 Create Order (Reserve Stock)

```
POST /orders
```

**Access**: `MEMBER`

**Headers**: `X-Idempotency-Key: <uuid>` (required)

**Request Body**:
```json
{
  "items": [
    { "listing_id": "uuid", "quantity": 2 }
  ],
  "delivery_address": "789 Heritage Blvd",
  "delivery_lat": 38.9100,
  "delivery_lng": -77.0250
}
```

**Processing**:
1. Validates stock availability with row-level locking.
2. Selects optimal fulfillment warehouse (nearest with stock).
3. Reserves stock (30-minute hold).
4. Applies tier benefits.
5. Creates order with `RESERVED` status.

**Response** `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "status": "RESERVED",
    "items": [
      {
        "listing_id": "uuid",
        "title": "Handcrafted Ethiopian Coffee Set",
        "quantity": 2,
        "unit_price": 65.00,
        "applied_benefit": {
          "name": "Silver 10% Discount",
          "discount": 13.00
        }
      }
    ],
    "subtotal": 130.00,
    "discount_amount": 13.00,
    "final_amount": 117.00,
    "fulfillment_warehouse": "Main Warehouse",
    "reservation_expires_at": "2026-04-01T10:30:00Z"
  }
}
```

**Error** `409 Conflict`:
```json
{
  "success": false,
  "error": {
    "code": "STOCK_UNAVAILABLE",
    "message": "Insufficient stock for one or more items.",
    "details": {
      "unavailable_items": [
        { "listing_id": "uuid", "requested": 2, "available": 0 }
      ]
    }
  }
}
```

---

### 6.2 Confirm Order (Submit Payment)

```
POST /orders/{orderId}/confirm
```

**Access**: `MEMBER` (order owner)

**Headers**: `X-Idempotency-Key: <uuid>` (required)

**Request Body**:
```json
{
  "payment_method": "INTERNAL",
  "reconciliation_ref": "PAY-20260401-001"
}
```

**Processing**:
1. Validates reservation is not expired.
2. Converts reserved stock to permanent deduction.
3. Creates `internal_tender_record` with `COMPLETED` status.
4. Updates order status to `CONFIRMED`.
5. Recalculates member `total_spend` for tier evaluation.

**Response** `200 OK`

---

### 6.3 Cancel Order

```
POST /orders/{orderId}/cancel
```

**Access**: `MEMBER` (order owner), `ADMIN`

**Processing**:
1. Releases reserved stock (or rolls back confirmed deduction).
2. If payment was made, creates refund tender record.
3. Updates order status to `CANCELLED`.

**Response** `200 OK`

---

### 6.4 List My Orders

```
GET /orders/me?status={status}&page={page}&pageSize={pageSize}
```

**Access**: `MEMBER`

---

### 6.5 Get Order Detail

```
GET /orders/{orderId}
```

**Access**: `MEMBER` (own order), `ADMIN`

---

### 6.6 Mark Order Fulfilled

```
POST /orders/{orderId}/fulfill
```

**Access**: `WAREHOUSE_STAFF`, `ADMIN`

**Processing**: Updates status to `FULFILLED`, records fulfillment timestamp.

**Response** `200 OK`

---

## 7. Incidents & Ticketing

### 7.1 Create Ticket

```
POST /tickets
```

**Access**: `MEMBER`, `SELLER`, `WAREHOUSE_STAFF`

**Request Body**:
```json
{
  "type": "DELIVERY_DISPUTE",
  "severity": "HIGH",
  "description": "Package was delivered to wrong address. Contents are missing.",
  "location_address": "789 Heritage Blvd",
  "location_cross_street": "Corner of Heritage Blvd and Oak St",
  "related_order_id": "uuid"
}
```

**Validation**:
- `type`: Required, one of `DELIVERY_DISPUTE`, `SAFETY_CONCERN`, `PICKUP_ISSUE`, `OTHER`
- `severity`: Required, one of `LOW`, `MEDIUM`, `HIGH`
- `description`: Required, 10–2000 chars

**Response** `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "type": "DELIVERY_DISPUTE",
    "severity": "HIGH",
    "status": "OPEN",
    "sla_acknowledge_by": "2026-04-01T10:15:00Z",
    "sla_resolve_by": "2026-04-02T10:00:00Z",
    "created_at": "2026-04-01T10:00:00Z"
  }
}
```

---

### 7.2 List Tickets

```
GET /tickets?status={status}&severity={severity}&type={type}&assignedTo={userId}&page={page}&pageSize={pageSize}
```

**Access**:
- `MEMBER`, `SELLER`, `WAREHOUSE_STAFF`: Own tickets only
- `MODERATOR`, `ADMIN`: All tickets

---

### 7.3 Get Ticket Detail

```
GET /tickets/{ticketId}
```

**Access**: Ticket reporter, assigned handler, `MODERATOR`, `ADMIN`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "type": "DELIVERY_DISPUTE",
    "severity": "HIGH",
    "status": "OPEN",
    "reporter": { "id": "uuid", "display_name": "Jane Doe" },
    "assigned_to": null,
    "description": "Package was delivered to wrong address.",
    "location_address": "789 Heritage Blvd",
    "location_cross_street": "Corner of Heritage Blvd and Oak St",
    "sla_acknowledge_by": "2026-04-01T10:15:00Z",
    "sla_resolve_by": "2026-04-02T10:00:00Z",
    "acknowledged_at": null,
    "resolved_at": null,
    "escalated_at": null,
    "closure_code": null,
    "closure_notes": null,
    "follow_ups": [],
    "created_at": "2026-04-01T10:00:00Z"
  }
}
```

---

### 7.4 Acknowledge Ticket

```
POST /tickets/{ticketId}/acknowledge
```

**Access**: `MODERATOR`, `ADMIN`

**Processing**: Sets `status` to `ACKNOWLEDGED`, records `acknowledged_at`, assigns handler.

**Response** `200 OK`

---

### 7.5 Update Ticket Status

```
PATCH /tickets/{ticketId}/status
```

**Access**: `MODERATOR`, `ADMIN`

**Request Body**:
```json
{
  "status": "IN_PROGRESS"
}
```

---

### 7.6 Resolve Ticket

```
POST /tickets/{ticketId}/resolve
```

**Access**: `MODERATOR`, `ADMIN`

**Request Body**:
```json
{
  "closure_code": "RESOLVED_REDELIVERY",
  "closure_notes": "Arranged for redelivery to correct address. Confirmed with customer."
}
```

**Response** `200 OK`

---

### 7.7 Add Follow-Up Note

```
POST /tickets/{ticketId}/follow-ups
```

**Access**: Ticket reporter, assigned handler, `MODERATOR`, `ADMIN`

**Request Body**:
```json
{
  "message": "Customer confirmed the correct address is 790 Heritage Blvd, Apt 3B."
}
```

**Response** `201 Created`

---

### 7.8 Get Ticket Follow-Ups

```
GET /tickets/{ticketId}/follow-ups
```

**Access**: Ticket reporter, assigned handler, `MODERATOR`, `ADMIN`

---

## 8. Appeals

### 8.1 Submit Appeal

```
POST /appeals
```

**Access**: `MEMBER`, `SELLER`, `WAREHOUSE_STAFF`

**Content-Type**: `multipart/form-data`

**Form Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `ticket_id` | UUID | Required. Related ticket |
| `reason` | string | Required. Appeal justification (10–5000 chars) |
| `evidence[]` | file | Optional. Up to 5 files, each max 10 MB |

**Allowed MIME Types**: `image/jpeg`, `image/png`, `application/pdf`

**Response** `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "ticket_id": "uuid",
    "status": "SUBMITTED",
    "evidence_count": 2,
    "created_at": "2026-04-01T11:00:00Z"
  }
}
```

**Error** `400 Bad Request` (validation):
```json
{
  "success": false,
  "error": {
    "code": "EVIDENCE_VALIDATION_FAILED",
    "message": "File upload validation failed.",
    "details": {
      "errors": [
        { "file": "document.exe", "reason": "Unsupported MIME type. Allowed: image/jpeg, image/png, application/pdf" },
        { "file": "large_photo.jpg", "reason": "File exceeds 10 MB limit (12.4 MB)" }
      ]
    }
  }
}
```

---

### 8.2 List Appeals

```
GET /appeals?status={status}&page={page}&pageSize={pageSize}
```

**Access**:
- `MEMBER`, `SELLER`, `WAREHOUSE_STAFF`: Own appeals only
- `MODERATOR`, `ADMIN`: All appeals

---

### 8.3 Get Appeal Detail

```
GET /appeals/{appealId}
```

**Access**: Appellant, `MODERATOR`, `ADMIN`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "ticket_id": "uuid",
    "appellant": { "id": "uuid", "display_name": "Jane Doe" },
    "reason": "The delivery was marked as missed but I was present...",
    "status": "UNDER_REVIEW",
    "reviewer": { "id": "uuid", "display_name": "Mod Smith" },
    "admin_reviewer": null,
    "decision_notes": null,
    "evidence": [
      {
        "id": "uuid",
        "file_name": "receipt_photo.jpg",
        "mime_type": "image/jpeg",
        "file_size_bytes": 2456789,
        "uploaded_at": "2026-04-01T11:00:00Z"
      }
    ],
    "created_at": "2026-04-01T11:00:00Z",
    "decided_at": null
  }
}
```

---

### 8.4 Download Appeal Evidence

```
GET /appeals/{appealId}/evidence/{evidenceId}/download
```

**Access**: Appellant, `MODERATOR`, `ADMIN`

**Response**: Binary file download with appropriate `Content-Type` and `Content-Disposition` headers.

---

### 8.5 Review Appeal (Moderator)

```
POST /appeals/{appealId}/review
```

**Access**: `MODERATOR`

**Request Body**:
```json
{
  "decision": "DENIED",
  "decision_notes": "Evidence does not support the claim. Delivery logs confirm no one was present at the address."
}
```

**Valid decisions**: `APPROVED`, `DENIED`, `ESCALATED_TO_ADMIN`

**Response** `200 OK`

---

### 8.6 Final Review (Admin)

```
POST /appeals/{appealId}/final-review
```

**Access**: `ADMIN`

**Request Body**:
```json
{
  "decision": "APPROVED",
  "decision_notes": "After reviewing additional security footage, the delivery was indeed mishandled."
}
```

**Valid decisions**: `APPROVED`, `DENIED`

**Response** `200 OK`

---

## 9. Risk Analytics

### 9.1 Get Risk Dashboard Summary

```
GET /risk/dashboard
```

**Access**: `MODERATOR`, `ADMIN`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "open_tickets": { "LOW": 12, "MEDIUM": 8, "HIGH": 3 },
    "avg_resolution_hours": 6.4,
    "escalation_rate_percent": 18.5,
    "active_risk_flags": 7,
    "top_flagged_sellers": [
      {
        "seller_id": "uuid",
        "display_name": "QuickSell Crafts",
        "flag_type": "REPEAT_INCIDENTS",
        "incident_count": 5,
        "window": "2026-03-02 to 2026-04-01"
      }
    ],
    "top_flagged_members": [
      {
        "member_id": "uuid",
        "display_name": "John Doe",
        "flag_type": "MISSED_CHECKINS",
        "incident_count": 4,
        "window": "2026-03-02 to 2026-04-01"
      }
    ]
  }
}
```

---

### 9.2 List Risk Flags

```
GET /risk/flags?entityType={SELLER|MEMBER|STAFF}&flagType={type}&page={page}&pageSize={pageSize}
```

**Access**: `MODERATOR`, `ADMIN`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "entity_type": "SELLER",
      "entity_id": "uuid",
      "entity_name": "QuickSell Crafts",
      "flag_type": "REPEAT_INCIDENTS",
      "incident_count": 5,
      "window_start": "2026-03-02",
      "window_end": "2026-04-01",
      "created_at": "2026-04-01T03:00:00Z"
    }
  ],
  "meta": { "page": 1, "pageSize": 20, "totalItems": 7, "totalPages": 1 }
}
```

---

### 9.3 Get Risk Detail for Entity

```
GET /risk/entity/{entityId}?entityType={SELLER|MEMBER|STAFF}
```

**Access**: `MODERATOR`, `ADMIN`

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "entity_id": "uuid",
    "entity_type": "SELLER",
    "entity_name": "QuickSell Crafts",
    "flags": [ ... ],
    "recent_tickets": [ ... ],
    "recent_appeals": [ ... ],
    "risk_score": 78,
    "recommendation": "Review seller activity. Consider temporary suspension."
  }
}
```

---

## 10. Audit Logs

### 10.1 Query Audit Logs

```
GET /audit-logs?entityType={type}&entityId={id}&action={action}&actorId={userId}&from={datetime}&to={datetime}&page={page}&pageSize={pageSize}
```

**Access**: `ADMIN`

**Query Parameters**:
| Param | Type | Description |
|-------|------|-------------|
| `entityType` | string | Filter by entity type (e.g., `ORDER`, `TICKET`, `USER`) |
| `entityId` | UUID | Filter by specific entity |
| `action` | string | Filter by action (e.g., `CREATE`, `UPDATE`, `DELETE`) |
| `actorId` | UUID | Filter by who performed the action |
| `from` | datetime | Start of date range (ISO 8601) |
| `to` | datetime | End of date range (ISO 8601) |
| `page` | int | Default: 1 |
| `pageSize` | int | Default: 50, max: 200 |

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": 12345,
      "entity_type": "ORDER",
      "entity_id": "uuid",
      "action": "UPDATE",
      "actor": { "id": "uuid", "display_name": "System Scheduler" },
      "changes": {
        "before": { "status": "RESERVED" },
        "after": { "status": "CANCELLED" }
      },
      "ip_address": "192.168.1.50",
      "created_at": "2026-04-01T10:31:00Z"
    }
  ],
  "meta": { "page": 1, "pageSize": 50, "totalItems": 8453, "totalPages": 170 }
}
```

---

## 11. Rate Limiting

All authenticated endpoints enforce the following rate limits:

| Scope | Limit | Window | Response on Breach |
|-------|-------|--------|-------------------|
| Per-user API calls | 60 requests | 1 minute | `429` with `Retry-After` header |
| Login attempts | 10 attempts | 1 hour | `423` with lockout duration |

**Rate Limit Headers** (included in every response):
```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 42
X-RateLimit-Reset: 1711929600
```

---

## 12. Pagination

All list endpoints support cursor-based or offset pagination:

| Param | Type | Default | Max |
|-------|------|---------|-----|
| `page` | int | 1 | — |
| `pageSize` | int | 20 | 100 (200 for audit logs) |

Pagination metadata is included in the `meta` field of every list response.

---

## 13. API Endpoint Summary

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `POST` | `/auth/register` | Public | Register new user |
| `POST` | `/auth/login` | Public | Login |
| `POST` | `/auth/logout` | Auth | Logout |
| `POST` | `/auth/refresh` | Auth | Refresh session token |
| `GET` | `/auth/me` | Auth | Get current user profile |
| `GET` | `/users` | Admin | List users |
| `GET` | `/users/{id}` | Admin | Get user by ID |
| `PATCH` | `/users/{id}/role` | Admin | Update user role |
| `POST` | `/users/me/deletion-request` | Auth | Request account deletion |
| `DELETE` | `/users/me/deletion-request` | Auth | Cancel deletion request |
| `GET` | `/memberships/me` | Member | Get my tier & benefits |
| `POST` | `/memberships/me/preview-benefits` | Member | Preview checkout benefits |
| `GET` | `/tiers` | Admin | List tier configs |
| `POST` | `/tiers` | Admin | Create tier config |
| `PUT` | `/tiers/{id}` | Admin | Update tier config |
| `GET` | `/tiers/{id}/benefits` | Admin | List benefits for tier |
| `POST` | `/tiers/{id}/benefits` | Admin | Create benefit package |
| `PUT` | `/tiers/{id}/benefits/{id}` | Admin | Update benefit package |
| `DELETE` | `/tiers/{id}/benefits/{id}` | Admin | Delete benefit package |
| `GET` | `/listings` | Public | Search listings |
| `GET` | `/listings/trending` | Public | Get trending listings |
| `GET` | `/listings/{id}` | Public | Get listing detail |
| `POST` | `/listings` | Seller, Admin | Create listing |
| `PUT` | `/listings/{id}` | Seller, Admin | Update listing |
| `DELETE` | `/listings/{id}` | Seller, Admin | Deactivate listing |
| `GET` | `/listings/recent-searches` | Auth | Get recent searches |
| `DELETE` | `/listings/recent-searches` | Auth | Clear recent searches |
| `GET` | `/warehouses` | Seller, Staff, Admin | List warehouses |
| `POST` | `/warehouses` | Admin | Create warehouse |
| `PUT` | `/warehouses/{id}` | Admin | Update warehouse |
| `GET` | `/inventory` | Seller, Staff, Admin | Query inventory |
| `PATCH` | `/inventory/{id}/threshold` | Seller, Admin | Set low-stock threshold |
| `POST` | `/inventory/documents` | Seller, Staff, Admin | Create inbound/outbound doc |
| `POST` | `/inventory/stocktake` | Staff, Admin | Run stocktake |
| `GET` | `/inventory/{id}/movements` | Seller, Staff, Admin | Movement history |
| `GET` | `/inventory/alerts/low-stock` | Seller, Staff, Admin | Low stock alerts |
| `POST` | `/orders` | Member | Create order (reserve) |
| `POST` | `/orders/{id}/confirm` | Member | Confirm order (pay) |
| `POST` | `/orders/{id}/cancel` | Member, Admin | Cancel order |
| `GET` | `/orders/me` | Member | List my orders |
| `GET` | `/orders/{id}` | Member, Admin | Get order detail |
| `POST` | `/orders/{id}/fulfill` | Staff, Admin | Mark fulfilled |
| `POST` | `/tickets` | Member, Seller, Staff | Create incident ticket |
| `GET` | `/tickets` | Varies | List tickets |
| `GET` | `/tickets/{id}` | Varies | Ticket detail |
| `POST` | `/tickets/{id}/acknowledge` | Mod, Admin | Acknowledge ticket |
| `PATCH` | `/tickets/{id}/status` | Mod, Admin | Update status |
| `POST` | `/tickets/{id}/resolve` | Mod, Admin | Resolve ticket |
| `POST` | `/tickets/{id}/follow-ups` | Varies | Add follow-up |
| `GET` | `/tickets/{id}/follow-ups` | Varies | Get follow-ups |
| `POST` | `/appeals` | Member, Seller, Staff | Submit appeal |
| `GET` | `/appeals` | Varies | List appeals |
| `GET` | `/appeals/{id}` | Varies | Appeal detail |
| `GET` | `/appeals/{id}/evidence/{id}/download` | Varies | Download evidence |
| `POST` | `/appeals/{id}/review` | Mod | Moderator review |
| `POST` | `/appeals/{id}/final-review` | Admin | Admin final review |
| `GET` | `/risk/dashboard` | Mod, Admin | Risk dashboard |
| `GET` | `/risk/flags` | Mod, Admin | List risk flags |
| `GET` | `/risk/entity/{id}` | Mod, Admin | Entity risk detail |
| `GET` | `/audit-logs` | Admin | Query audit logs |
