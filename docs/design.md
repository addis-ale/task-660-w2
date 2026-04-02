# Heritage Marketplace Operations Management System — Design Document

## 1. Overview

The Heritage Marketplace Operations Management System is a fully **offline-capable**, **self-hosted** web application for managing a cultural crafts and experiences marketplace. It supports tiered membership, listing discovery, inventory operations across multiple warehouses, incident and SLA management, appeal workflows, and risk analytics — all without any external or cloud dependencies.

### 1.1 Key Design Goals

| Goal | Description |
|------|-------------|
| **Offline-First** | Zero reliance on third-party login, messaging, maps, cloud storage, or online payment providers. All services run on-premises. |
| **Role-Based Security** | Six distinct roles (Guest, Member, Seller, Warehouse Staff, Moderator, Administrator) with fine-grained permissions enforced at the API layer. |
| **Auditability** | Immutable audit logs retained for 2 years; every inventory movement and tier redemption is traceable by operator, timestamp, document, and warehouse. |
| **Data Privacy** | Minimal data collection, PII masking (e.g., phone `555-***-1234`), at-rest encryption for sensitive fields, TLS on the local network, and GDPR-style account deletion with a 30-day cooling-off period. |
| **Idempotent State Transitions** | All mutating operations (payments, refunds, stock movements) are idempotent, supporting safe retries and manual exception compensation. |

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     React Frontend                      │
│  (SPA — Search, Listings, Checkout, Dashboards, Admin)  │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTPS / TLS (local network)
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   Spring Boot Backend                   │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ Auth &  │ │ Listing  │ │Inventory │ │ Ticketing  │  │
│  │ Session │ │ & Search │ │ & Warehs │ │ & Incidents│  │
│  └─────────┘ └──────────┘ └──────────┘ └────────────┘  │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │  Tier & │ │ Payment  │ │  Appeal  │ │   Risk &   │  │
│  │Benefits │ │ (Internal│ │ Workflow │ │ Analytics  │  │
│  └─────────┘ └──────────┘ └──────────┘ └────────────┘  │
│  ┌────────────────────────────────────────────────────┐  │
│  │       Rate Limiter  ·  Scheduler  ·  Audit Log    │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────┘
                           │ JDBC
                           ▼
┌─────────────────────────────────────────────────────────┐
│                      PostgreSQL                         │
│  (Members, Tiers, Listings, Inventory, Tickets, Audit)  │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React (SPA), React Router, Axios |
| Backend | Spring Boot 3.x (Java 17+), Spring Security, Spring Data JPA |
| Database | PostgreSQL 15+ |
| Session Management | Signed JWT session tokens (locally issued, no external IdP) |
| Scheduling | Spring `@Scheduled` tasks (reservation expiry, tier recalculation, SLA escalation) |
| File Storage | Local filesystem (evidence uploads) |
| Transport Security | TLS via self-signed or internal CA certificates |

### 2.3 Module Decomposition

```
com.heritage.marketplace
├── auth/              # Authentication, session tokens, rate limiting
├── user/              # User accounts, roles, profile, data deletion
├── tier/              # Membership tiers, benefits, stacking rules
├── listing/           # Listings CRUD, search, trending
├── inventory/         # Warehouses, stock, documents, reservations
├── order/             # Order lifecycle, fulfillment, internal payments
├── ticket/            # Incidents, SLA, escalation
├── appeal/            # Exception-and-appeal loop
├── risk/              # Risk analytics, dashboards
├── audit/             # Immutable audit log
├── scheduler/         # Background jobs
└── common/            # Shared DTOs, utilities, encryption helpers
```

---

## 3. Data Model

### 3.1 Entity-Relationship Overview

```
┌───────────┐     ┌──────────────┐     ┌───────────┐
│   User    │────▶│  Membership  │────▶│   Tier    │
│           │     │   (1:1)      │     │  Config   │
└─────┬─────┘     └──────────────┘     └─────┬─────┘
      │                                      │
      │ 1:N                                  │ 1:N
      ▼                                      ▼
┌───────────┐     ┌──────────────┐     ┌───────────┐
│  Listing  │     │   Benefit    │────▶│  Benefit  │
│           │     │  Issuance    │     │  Package  │
└─────┬─────┘     └──────────────┘     └───────────┘
      │ 1:N
      ▼
┌───────────┐     ┌──────────────┐     ┌───────────┐
│ Inventory │────▶│  Warehouse   │     │  Ticket   │
│  Record   │     │              │     │           │
└───────────┘     └──────────────┘     └─────┬─────┘
                                             │ 1:N
                                             ▼
                                       ┌───────────┐
                                       │  Appeal   │
                                       └───────────┘
```

### 3.2 Core Tables

#### 3.2.1 `users`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | Encrypted at rest |
| `password_hash` | VARCHAR(255) | NOT NULL | BCrypt |
| `phone` | VARCHAR(20) | | Encrypted; displayed masked (`555-***-1234`) |
| `display_name` | VARCHAR(100) | NOT NULL | |
| `role` | ENUM | NOT NULL | `GUEST`, `MEMBER`, `SELLER`, `WAREHOUSE_STAFF`, `MODERATOR`, `ADMIN` |
| `status` | ENUM | NOT NULL | `ACTIVE`, `LOCKED`, `PENDING_DELETION`, `DELETED` |
| `deletion_requested_at` | TIMESTAMP | | 30-day cooling-off start |
| `failed_login_attempts` | INT | DEFAULT 0 | Reset on success |
| `lockout_until` | TIMESTAMP | | 15-min lockout after 10 attempts/hr |
| `created_at` | TIMESTAMP | NOT NULL | |
| `updated_at` | TIMESTAMP | NOT NULL | |

#### 3.2.2 `tier_configs`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `name` | VARCHAR(50) | UNIQUE, NOT NULL | e.g. Bronze, Silver, Gold |
| `spend_threshold_min` | DECIMAL(10,2) | NOT NULL | e.g. 0.00, 500.00, 1500.00 |
| `spend_threshold_max` | DECIMAL(10,2) | | NULL = unlimited |
| `rank` | INT | NOT NULL | For ordering / priority |
| `created_at` | TIMESTAMP | NOT NULL | |

#### 3.2.3 `memberships`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | FK → `users`, UNIQUE | |
| `tier_id` | UUID | FK → `tier_configs` | |
| `total_spend` | DECIMAL(12,2) | DEFAULT 0 | Running total for tier calc |
| `tier_valid_until` | DATE | NOT NULL | Yearly reset boundary |
| `upgraded_at` | TIMESTAMP | | Last tier change |

#### 3.2.4 `benefit_packages`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `tier_id` | UUID | FK → `tier_configs` | |
| `name` | VARCHAR(100) | NOT NULL | |
| `type` | ENUM | NOT NULL | `EXCLUSIVE_PRICE`, `PERCENTAGE_DISCOUNT`, `FREE_SHIPPING` |
| `value` | DECIMAL(10,2) | | Discount % or fixed price |
| `scope_category` | VARCHAR(100) | | Listing category filter |
| `scope_seller_id` | UUID | FK → `users` | Seller-specific benefit |
| `scope_date_start` | DATE | | |
| `scope_date_end` | DATE | | |
| `stackable` | BOOLEAN | DEFAULT true | |
| `mutual_exclusion_group` | VARCHAR(50) | | Benefits in same group cannot stack |
| `priority` | INT | DEFAULT 0 | Higher wins when non-stackable |

#### 3.2.5 `benefit_issuances`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | Immutable record |
| `membership_id` | UUID | FK → `memberships` | |
| `benefit_id` | UUID | FK → `benefit_packages` | |
| `order_id` | UUID | FK → `orders` | |
| `applied_value` | DECIMAL(10,2) | NOT NULL | Actual discount applied |
| `issued_at` | TIMESTAMP | NOT NULL | |

#### 3.2.6 `listings`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `seller_id` | UUID | FK → `users` | |
| `title` | VARCHAR(255) | NOT NULL | Full-text indexed |
| `description` | TEXT | | Full-text indexed |
| `category` | VARCHAR(100) | NOT NULL | |
| `price` | DECIMAL(10,2) | NOT NULL | |
| `tags` | TEXT[] | | PostgreSQL array |
| `neighborhood` | VARCHAR(100) | | |
| `latitude` | DECIMAL(10,7) | | For Haversine distance sort |
| `longitude` | DECIMAL(10,7) | | |
| `layout_sqft` | DECIMAL(8,2) | | Square footage (where applicable) |
| `availability_start` | DATE | | |
| `availability_end` | DATE | | |
| `status` | ENUM | NOT NULL | `ACTIVE`, `INACTIVE`, `REMOVED` |
| `view_count` | INT | DEFAULT 0 | |
| `order_count_7d` | INT | DEFAULT 0 | Maintained by scheduler |
| `trending_score` | DECIMAL(8,2) | DEFAULT 0 | `views*0.4 + orders*0.6` over 7 days |
| `created_at` | TIMESTAMP | NOT NULL | |

#### 3.2.7 `warehouses`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `name` | VARCHAR(100) | NOT NULL | |
| `address` | VARCHAR(255) | NOT NULL | |
| `latitude` | DECIMAL(10,7) | | |
| `longitude` | DECIMAL(10,7) | | |
| `status` | ENUM | NOT NULL | `ACTIVE`, `INACTIVE` |

#### 3.2.8 `inventory_records`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `listing_id` | UUID | FK → `listings` | |
| `warehouse_id` | UUID | FK → `warehouses` | |
| `available_qty` | INT | NOT NULL, CHECK ≥ 0 | |
| `reserved_qty` | INT | DEFAULT 0, CHECK ≥ 0 | |
| `low_stock_threshold` | INT | DEFAULT 5 | Configurable per product |

#### 3.2.9 `inventory_movements`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `inventory_id` | UUID | FK → `inventory_records` | |
| `document_type` | ENUM | NOT NULL | `INBOUND`, `OUTBOUND`, `STOCKTAKE`, `RESERVATION`, `RESERVATION_RELEASE`, `ORDER_DEDUCTION`, `CANCELLATION_ROLLBACK` |
| `document_ref` | VARCHAR(100) | | Reference to inbound/outbound document |
| `quantity_change` | INT | NOT NULL | Positive or negative |
| `operator_id` | UUID | FK → `users` | |
| `warehouse_id` | UUID | FK → `warehouses` | |
| `notes` | TEXT | | |
| `created_at` | TIMESTAMP | NOT NULL | Immutable |

#### 3.2.10 `orders`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `member_id` | UUID | FK → `users` | |
| `status` | ENUM | NOT NULL | `PENDING`, `RESERVED`, `CONFIRMED`, `FULFILLED`, `CANCELLED`, `FAILED` |
| `total_amount` | DECIMAL(12,2) | NOT NULL | |
| `discount_amount` | DECIMAL(12,2) | DEFAULT 0 | |
| `final_amount` | DECIMAL(12,2) | NOT NULL | |
| `fulfillment_warehouse_id` | UUID | FK → `warehouses` | |
| `reservation_expires_at` | TIMESTAMP | | 30-minute hold |
| `idempotency_key` | VARCHAR(100) | UNIQUE | For safe retries |
| `created_at` | TIMESTAMP | NOT NULL | |
| `updated_at` | TIMESTAMP | NOT NULL | |

#### 3.2.11 `order_items`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `order_id` | UUID | FK → `orders` | |
| `listing_id` | UUID | FK → `listings` | |
| `quantity` | INT | NOT NULL | |
| `unit_price` | DECIMAL(10,2) | NOT NULL | |
| `applied_benefit_id` | UUID | FK → `benefit_packages` | |

#### 3.2.12 `internal_tender_records`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `order_id` | UUID | FK → `orders` | |
| `type` | ENUM | NOT NULL | `PAYMENT`, `REFUND` |
| `amount` | DECIMAL(12,2) | NOT NULL | |
| `status` | ENUM | NOT NULL | `PENDING`, `COMPLETED`, `FAILED`, `COMPENSATED` |
| `idempotency_key` | VARCHAR(100) | UNIQUE | |
| `reconciliation_ref` | VARCHAR(100) | | |
| `created_at` | TIMESTAMP | NOT NULL | |

#### 3.2.13 `tickets`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `reporter_id` | UUID | FK → `users` | |
| `type` | ENUM | NOT NULL | `DELIVERY_DISPUTE`, `SAFETY_CONCERN`, `PICKUP_ISSUE`, `OTHER` |
| `severity` | ENUM | NOT NULL | `LOW`, `MEDIUM`, `HIGH` |
| `status` | ENUM | NOT NULL | `OPEN`, `ACKNOWLEDGED`, `IN_PROGRESS`, `ESCALATED`, `RESOLVED`, `CLOSED` |
| `assigned_to` | UUID | FK → `users` | Current handler |
| `location_address` | VARCHAR(255) | | Optional |
| `location_cross_street` | VARCHAR(255) | | Optional |
| `description` | TEXT | NOT NULL | |
| `closure_code` | VARCHAR(50) | | |
| `closure_notes` | TEXT | | |
| `sla_acknowledge_by` | TIMESTAMP | | created_at + 15 min |
| `sla_resolve_by` | TIMESTAMP | | created_at + 24 hr |
| `acknowledged_at` | TIMESTAMP | | |
| `resolved_at` | TIMESTAMP | | |
| `escalated_at` | TIMESTAMP | | |
| `created_at` | TIMESTAMP | NOT NULL | |

#### 3.2.14 `ticket_follow_ups`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `ticket_id` | UUID | FK → `tickets` | |
| `author_id` | UUID | FK → `users` | |
| `message` | TEXT | NOT NULL | |
| `created_at` | TIMESTAMP | NOT NULL | |

#### 3.2.15 `appeals`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `ticket_id` | UUID | FK → `tickets` | |
| `appellant_id` | UUID | FK → `users` | |
| `reason` | TEXT | NOT NULL | |
| `status` | ENUM | NOT NULL | `SUBMITTED`, `UNDER_REVIEW`, `ESCALATED_TO_ADMIN`, `APPROVED`, `DENIED` |
| `reviewer_id` | UUID | FK → `users` | Moderator (first-level) |
| `admin_reviewer_id` | UUID | FK → `users` | Admin (final decision) |
| `decision_notes` | TEXT | | |
| `created_at` | TIMESTAMP | NOT NULL | |
| `decided_at` | TIMESTAMP | | |

#### 3.2.16 `appeal_evidence`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `appeal_id` | UUID | FK → `appeals` | |
| `file_name` | VARCHAR(255) | NOT NULL | |
| `file_path` | VARCHAR(500) | NOT NULL | Local filesystem path |
| `mime_type` | VARCHAR(100) | NOT NULL | Restricted allowlist |
| `file_size_bytes` | BIGINT | NOT NULL | Max 10 MB |
| `uploaded_at` | TIMESTAMP | NOT NULL | |

Constraint: max 5 evidence files per appeal (enforced at application layer).

#### 3.2.17 `recent_searches`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | FK → `users` | |
| `query` | VARCHAR(255) | NOT NULL | |
| `filters` | JSONB | | Serialized filter state |
| `searched_at` | TIMESTAMP | NOT NULL | |

Constraint: retain last 20 per user (on insertion, delete the oldest if count exceeds 20).

#### 3.2.18 `audit_logs`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGSERIAL | PK | |
| `entity_type` | VARCHAR(50) | NOT NULL | e.g. `ORDER`, `TICKET`, `USER` |
| `entity_id` | UUID | NOT NULL | |
| `action` | VARCHAR(50) | NOT NULL | e.g. `CREATE`, `UPDATE`, `DELETE` |
| `actor_id` | UUID | | FK → `users` |
| `changes` | JSONB | | Before/after snapshots |
| `ip_address` | VARCHAR(45) | | |
| `created_at` | TIMESTAMP | NOT NULL | Immutable |

Retention: 2 years. Partitioned by month for efficient pruning.

#### 3.2.19 `risk_flags`
| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `entity_type` | ENUM | NOT NULL | `SELLER`, `MEMBER`, `STAFF` |
| `entity_id` | UUID | NOT NULL | |
| `flag_type` | ENUM | NOT NULL | `REPEAT_INCIDENTS`, `MISSED_CHECKINS`, `BUDDY_PUNCHING`, `MISIDENTIFICATION` |
| `incident_count` | INT | NOT NULL | |
| `window_start` | DATE | NOT NULL | |
| `window_end` | DATE | NOT NULL | |
| `created_at` | TIMESTAMP | NOT NULL | |

---

## 4. Business Logic Design

### 4.1 Membership Tiers & Benefits

#### Tier Lifecycle
1. **Upgrade** — Triggered immediately when a confirmed order pushes `total_spend` past a threshold boundary. The `OrderCompletionEvent` listener recalculates the tier in real time.
2. **Downgrade / Reset** — Tiers are evaluated on a yearly basis. A scheduled job runs daily, and for memberships where `tier_valid_until < today`, the system recalculates the tier based on spend within the new evaluation window. If spend no longer qualifies, the tier downgrades.
3. **`tier_valid_until`** — Set to 12 months from the date of first qualifying purchase in the current cycle.

#### Benefit Application Flow
```
Order Checkout
  │
  ├─ Collect applicable benefits (by tier, category, seller, date scope)
  │
  ├─ Filter by mutual exclusion groups
  │    └─ Within each group, select benefit with highest priority (or value)
  │
  ├─ Determine stackability
  │    ├─ EXCLUSIVE_PRICE: replaces base price, blocks all other discounts
  │    ├─ PERCENTAGE_DISCOUNT: stackable only with FREE_SHIPPING
  │    └─ FREE_SHIPPING: stackable with any non-exclusive benefit
  │
  ├─ Apply benefits and compute final_amount
  │
  └─ Create immutable benefit_issuance records
```

**On-screen explanation**: When a benefit cannot be applied (e.g., mutual exclusion), the UI displays a clear message such as _"Your Gold 15% discount was not applied because an Exclusive Price is active for this item."_

### 4.2 Listing Discovery

#### Search
- **Keyword search**: PostgreSQL full-text search (`tsvector`/`tsquery`) on `title` and `description`.
- **Filters**: neighborhood, radius (Haversine formula using stored lat/long), price range, layout/square footage range, tags (array overlap), availability date window.
- **Sort options**: newest (`created_at DESC`), price (ASC/DESC), distance (from user-inputted address coordinates via Haversine), popularity (`trending_score DESC`).

#### Distance Calculation (Offline)
Since no external map API is allowed:
1. Each listing stores `latitude` and `longitude` (entered manually or via local geocoding database).
2. User provides an address which is resolved to lat/long locally.
3. Distance computed using the **Haversine formula**: `d = 2r × arcsin(√(sin²(Δlat/2) + cos(lat₁)·cos(lat₂)·sin²(Δlong/2)))`, returned in miles.

#### Recent Searches
- Stored per user, capped at 20 entries (FIFO eviction).
- Re-executing a recent search moves it to the top.

#### Trending This Week
- **Trending Score** = `(views × 0.4) + (orders × 0.6)` computed over a rolling 7-day window.
- Recalculated by a scheduled job (e.g., every hour).
- "Trending This Week" section shows top-N listings by score filtered to the user's locality.

### 4.3 Inventory Management

#### Stock Lifecycle
| Event | Effect on `available_qty` | Effect on `reserved_qty` |
|-------|--------------------------|-------------------------|
| Inbound document | +N | — |
| Order placement (reservation) | −N | +N |
| Reservation expiry (30 min) | +N | −N |
| Order confirmation | — | −N |
| Order cancellation | +N | −N |
| Outbound document | −N | — |
| Stocktake adjustment | ±Δ | — |

#### Reservation Flow
1. On checkout, the system attempts to reserve stock using **row-level locking** (`SELECT ... FOR UPDATE`).
2. If `available_qty >= requested`, the reservation succeeds. `available_qty -= N`, `reserved_qty += N`.
3. A `reservation_expires_at` timestamp is set (now + 30 minutes).
4. A **background scheduler** runs every minute, sweeping expired reservations: restores `available_qty`, clears `reserved_qty`, and cancels the order.
5. Concurrent requests for the last item: the first transaction to acquire the row lock wins; subsequent ones receive a "stock unavailable" response.

#### Multi-Warehouse Fulfillment
1. On order placement, query all warehouses with available stock for the requested listing(s).
2. Sort candidates by distance (Haversine from delivery/pickup address) and stock level.
3. Select the nearest warehouse with sufficient stock.
4. If no single warehouse can fulfill, reject the order (no split-shipment in v1).

#### Low Stock Alerts
- `low_stock_threshold` is configurable per product (default: 5).
- When `available_qty` falls below the threshold after any stock movement, a **red "Low Stock" alert** is generated.
- Alerts are visible on the Seller and Warehouse Staff dashboards.

### 4.4 Incident & Ticketing System

#### Ticket Lifecycle
```
OPEN ──(acknowledge)──▶ ACKNOWLEDGED ──(work)──▶ IN_PROGRESS ──(resolve)──▶ RESOLVED ──(close)──▶ CLOSED
  │                                                   │
  │  (15 min SLA breach)                              │  (24 hr SLA breach)
  └──(auto-escalate)──▶ ESCALATED ◀──(auto-escalate)──┘
```

#### SLA Rules
| SLA | Deadline | Action on Breach |
|-----|----------|-----------------|
| Acknowledge | 15 minutes from creation | Auto-escalate to Moderator |
| Resolve | 24 hours from creation | Auto-escalate to Moderator |

- Severity levels (`LOW`, `MEDIUM`, `HIGH`) can adjust SLA windows in future iterations; v1 uses uniform SLAs.
- On escalation, the Moderator becomes the **primary handler** (full ownership transfer).

#### SLA Enforcement
- A **scheduled job** runs every minute, scanning tickets where `status NOT IN ('RESOLVED', 'CLOSED')`.
- If `sla_acknowledge_by < now AND acknowledged_at IS NULL` → escalate.
- If `sla_resolve_by < now AND resolved_at IS NULL` → escalate.

### 4.5 Exception & Appeal Workflow

#### Anomaly Detection
The system flags anomalies such as:
- **Missed pickup check-ins**: Scheduled pickup not confirmed within the expected window.
- **Suspected buddy punching**: Staff confirmation from a device or location inconsistent with their assignment.
- **Misidentification**: Order item mismatch reported at delivery.

Flags are recorded in `risk_flags` and surface on Moderator/Admin dashboards.

#### Appeal Flow
```
SUBMITTED ──(Moderator review)──▶ UNDER_REVIEW
  ├──(approve)──▶ APPROVED
  ├──(deny)──▶ DENIED
  └──(escalate)──▶ ESCALATED_TO_ADMIN ──(Admin decision)──▶ APPROVED | DENIED
```

- **Evidence**: Up to 5 files, each max 10 MB. Allowed MIME types: `image/jpeg`, `image/png`, `application/pdf`.
- **Review authority**: Moderator handles first-level. If appellant disputes, Admin makes final decision.
- Closure notes are provided for transparency.

### 4.6 Risk Analytics

- **Computed locally** from historical tickets and exception flags — no external analytics services.
- **Repeat incidents**: If a seller accumulates > 3 incidents in 30 days, a `REPEAT_INCIDENTS` flag is created.
- **Dashboard metrics**: Open tickets by severity, average resolution time, escalation rate, top flagged sellers/members.
- Data is pre-aggregated by scheduled jobs and served from materialized views or summary tables.

---

## 5. Security Design

### 5.1 Authentication & Session Management

| Mechanism | Detail |
|-----------|--------|
| **Password storage** | BCrypt with cost factor 12 |
| **Session tokens** | Signed JWTs (HMAC-SHA256), issued on login, containing user ID, role, and expiry |
| **Token lifetime** | 1 hour (configurable), with sliding refresh |
| **Login rate limiting** | 10 attempts per hour per account; breach triggers 15-minute lockout |
| **API rate limiting** | 60 requests/minute per authenticated user (token bucket) |

### 5.2 Authorization Matrix

| Resource | Guest | Member | Seller | Warehouse Staff | Moderator | Admin |
|----------|:-----:|:------:|:------:|:---------------:|:---------:|:-----:|
| Browse listings | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| Search with filters | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| View tier/benefits | — | ✓ | — | — | — | ✓ |
| Place order | — | ✓ | — | — | — | — |
| Manage listings | — | — | ✓ | — | — | ✓ |
| Manage inventory | — | — | ✓ | ✓ | — | ✓ |
| Create incident | — | ✓ | ✓ | ✓ | — | — |
| Manage tickets | — | — | — | — | ✓ | ✓ |
| Submit appeal | — | ✓ | ✓ | ✓ | — | — |
| Review appeals | — | — | — | — | ✓ | ✓ |
| Risk dashboards | — | — | — | — | ✓ | ✓ |
| User management | — | — | — | — | — | ✓ |
| Audit logs | — | — | — | — | — | ✓ |

### 5.3 Data Privacy

| Requirement | Implementation |
|-------------|---------------|
| **Minimal collection** | Only fields essential to business operations are stored |
| **Phone masking** | Display as `555-***-1234`; full value only accessible to Admin |
| **Encryption at rest** | Sensitive columns (`email`, `phone`) encrypted via AES-256 using application-managed keys |
| **TLS** | All local network traffic encrypted via TLS 1.2+ |
| **Account deletion** | Soft-delete with 30-day cooling-off; after expiry, PII is replaced with anonymized hashes; transaction and audit records retained for compliance |
| **Audit retention** | 2-year retention; `audit_logs` table partitioned by month; old partitions dropped by scheduled job |

---

## 6. Background Jobs & Scheduling

| Job | Frequency | Description |
|-----|-----------|-------------|
| **Reservation Expiry Sweeper** | Every 1 minute | Releases expired stock reservations and cancels abandoned orders |
| **SLA Escalation Checker** | Every 1 minute | Scans tickets for SLA breaches and auto-escalates |
| **Trending Score Calculator** | Every 1 hour | Recalculates `trending_score` for all active listings |
| **Tier Recalculation** | Daily at 02:00 | Evaluates memberships past `tier_valid_until` for downgrade |
| **Risk Flag Aggregator** | Daily at 03:00 | Scans last 30 days of tickets per entity, creates/updates risk flags |
| **Audit Log Pruner** | Monthly on 1st | Drops partitions older than 2 years |
| **Deletion Finalizer** | Daily at 04:00 | Anonymizes accounts past the 30-day cooling-off period |
| **Recent Search Cleanup** | On insert trigger | Deletes oldest entry when user exceeds 20 recent searches |

---

## 7. Frontend Design

### 7.1 Page Structure

| Page | Accessible By | Key Features |
|------|--------------|-------------|
| **Home / Browse** | All | Keyword search bar, filter panel, sort dropdown, trending section, recent searches |
| **Listing Detail** | All | Product info, tier pricing badge, benefit explanation tooltips, add to cart |
| **Cart / Checkout** | Member | Tier card display, applied benefits breakdown, stacking explanation, payment summary |
| **My Orders** | Member | Order history with statuses, fulfillment tracking |
| **Seller Dashboard** | Seller | Listing management, inventory overview, low stock alerts |
| **Warehouse Console** | Warehouse Staff | Inbound/outbound documents, stocktake, movement log |
| **Incident Portal** | Member, Seller, Staff | Create ticket, track status, SLA countdown timers |
| **Appeal Center** | Member, Seller, Staff | Submit appeal with evidence upload, track status |
| **Moderation Hub** | Moderator | Ticket queue, escalation inbox, appeal reviews, risk dashboard |
| **Admin Panel** | Admin | User management, tier/benefit config, audit log viewer, analytics |

### 7.2 UX Highlights

- **Low Stock Alert**: Red badge overlay on inventory cards; real-time updates via polling.
- **SLA Countdown**: Animated countdown timers on ticket cards showing time until next SLA deadline.
- **Benefit Explanation**: Inline tooltips on checkout explaining why each benefit was or was not applied.
- **Trending Badge**: "🔥 Trending" badge on listing cards that appear in the trending section.
- **Distance Display**: Shows calculated distance in miles next to each listing when distance sort is active.

---

## 8. Error Handling & Resilience

| Scenario | Strategy |
|----------|---------|
| **Concurrent stock reservation** | Row-level DB locking; loser receives `409 Conflict` |
| **Idempotent payments** | `idempotency_key` on orders and tender records; duplicate requests return existing result |
| **Failed fulfillment** | Automatic stock rollback; order marked `FAILED`; manual compensation workflow available |
| **File upload failure** | Client-side retry with exponential backoff; incomplete uploads cleaned up by hourly sweep |
| **Rate limit exceeded** | `429 Too Many Requests` with `Retry-After` header |
| **Account lockout** | `423 Locked` response; auto-unlock after 15 minutes |

---

## 9. Deployment Topology (On-Premises)

```
┌─────────────────────────────────────────┐
│           On-Premises Server            │
│                                         │
│  ┌──────────┐  ┌──────────┐  ┌───────┐  │
│  │  Nginx   │─▶│ Spring   │─▶│ Pg DB │  │
│  │ (Reverse │  │  Boot    │  │       │  │
│  │  Proxy + │  │  App     │  │       │  │
│  │  TLS)    │  │          │  │       │  │
│  └──────────┘  └──────────┘  └───────┘  │
│  ┌──────────┐                           │
│  │  React   │  (served as static build  │
│  │  SPA     │   via Nginx)              │
│  └──────────┘                           │
│  ┌──────────┐                           │
│  │  Local   │  (evidence file storage)  │
│  │  FS      │                           │
│  └──────────┘                           │
└─────────────────────────────────────────┘
```

All components run on a single on-premises server. No cloud dependencies. No external APIs. Fully air-gappable.
