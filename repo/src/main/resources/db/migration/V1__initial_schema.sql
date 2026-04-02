CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE user_role AS ENUM (
    'GUEST',
    'MEMBER',
    'SELLER',
    'WAREHOUSE_STAFF',
    'MODERATOR',
    'ADMIN'
);

CREATE TYPE user_status AS ENUM (
    'ACTIVE',
    'LOCKED',
    'PENDING_DELETION',
    'DELETED'
);

CREATE TYPE benefit_type AS ENUM (
    'EXCLUSIVE_PRICE',
    'PERCENTAGE_DISCOUNT',
    'FREE_SHIPPING'
);

CREATE TYPE listing_status AS ENUM (
    'ACTIVE',
    'INACTIVE',
    'REMOVED'
);

CREATE TYPE warehouse_status AS ENUM (
    'ACTIVE',
    'INACTIVE'
);

CREATE TYPE inventory_document_type AS ENUM (
    'INBOUND',
    'OUTBOUND',
    'STOCKTAKE',
    'RESERVATION',
    'RESERVATION_RELEASE',
    'ORDER_DEDUCTION',
    'CANCELLATION_ROLLBACK'
);

CREATE TYPE order_status AS ENUM (
    'PENDING',
    'RESERVED',
    'CONFIRMED',
    'FULFILLED',
    'CANCELLED',
    'FAILED'
);

CREATE TYPE tender_type AS ENUM (
    'PAYMENT',
    'REFUND'
);

CREATE TYPE tender_status AS ENUM (
    'PENDING',
    'COMPLETED',
    'FAILED',
    'COMPENSATED'
);

CREATE TYPE ticket_type AS ENUM (
    'DELIVERY_DISPUTE',
    'SAFETY_CONCERN',
    'PICKUP_ISSUE',
    'OTHER'
);

CREATE TYPE ticket_severity AS ENUM (
    'LOW',
    'MEDIUM',
    'HIGH'
);

CREATE TYPE ticket_status AS ENUM (
    'OPEN',
    'ACKNOWLEDGED',
    'IN_PROGRESS',
    'ESCALATED',
    'RESOLVED',
    'CLOSED'
);

CREATE TYPE appeal_status AS ENUM (
    'SUBMITTED',
    'UNDER_REVIEW',
    'ESCALATED_TO_ADMIN',
    'APPROVED',
    'DENIED'
);

CREATE TYPE risk_entity_type AS ENUM (
    'SELLER',
    'MEMBER',
    'STAFF'
);

CREATE TYPE risk_flag_type AS ENUM (
    'REPEAT_INCIDENTS',
    'MISSED_CHECKINS',
    'BUDDY_PUNCHING',
    'MISIDENTIFICATION'
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(255),
    display_name VARCHAR(100) NOT NULL,
    role user_role NOT NULL,
    status user_status NOT NULL DEFAULT 'ACTIVE',
    deletion_requested_at TIMESTAMP,
    failed_login_attempts INT NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    lockout_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE tier_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,
    spend_threshold_min NUMERIC(10,2) NOT NULL CHECK (spend_threshold_min >= 0),
    spend_threshold_max NUMERIC(10,2),
    rank INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tier_thresholds CHECK (
        spend_threshold_max IS NULL OR spend_threshold_max > spend_threshold_min
    ),
    CONSTRAINT uq_tier_rank UNIQUE (rank)
);

CREATE TABLE memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    tier_id UUID NOT NULL REFERENCES tier_configs(id),
    total_spend NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total_spend >= 0),
    tier_valid_until DATE NOT NULL,
    upgraded_at TIMESTAMP
);

CREATE TABLE benefit_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_id UUID NOT NULL REFERENCES tier_configs(id),
    name VARCHAR(100) NOT NULL,
    type benefit_type NOT NULL,
    value NUMERIC(10,2),
    scope_category VARCHAR(100),
    scope_seller_id UUID REFERENCES users(id),
    scope_date_start DATE,
    scope_date_end DATE,
    stackable BOOLEAN NOT NULL DEFAULT TRUE,
    mutual_exclusion_group VARCHAR(50),
    priority INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_benefit_value_non_negative CHECK (value IS NULL OR value >= 0),
    CONSTRAINT chk_benefit_scope_dates CHECK (
        scope_date_end IS NULL OR scope_date_start IS NULL OR scope_date_end >= scope_date_start
    ),
    CONSTRAINT chk_benefit_type_value CHECK (
        (type = 'FREE_SHIPPING' AND (value IS NULL OR value = 0))
        OR (type = 'PERCENTAGE_DISCOUNT' AND value IS NOT NULL AND value >= 0 AND value <= 100)
        OR (type = 'EXCLUSIVE_PRICE' AND value IS NOT NULL AND value >= 0)
    )
);

CREATE TABLE listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    price NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    tags TEXT[],
    neighborhood VARCHAR(100),
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    layout_sqft NUMERIC(8,2) CHECK (layout_sqft IS NULL OR layout_sqft >= 0),
    availability_start DATE,
    availability_end DATE,
    status listing_status NOT NULL DEFAULT 'ACTIVE',
    view_count INT NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    order_count_7d INT NOT NULL DEFAULT 0 CHECK (order_count_7d >= 0),
    trending_score NUMERIC(8,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_listing_availability_dates CHECK (
        availability_end IS NULL OR availability_start IS NULL OR availability_end >= availability_start
    )
);

CREATE TABLE warehouses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    status warehouse_status NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE inventory_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL REFERENCES listings(id),
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    available_qty INT NOT NULL CHECK (available_qty >= 0),
    reserved_qty INT NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    low_stock_threshold INT NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0),
    CONSTRAINT uq_inventory_listing_warehouse UNIQUE (listing_id, warehouse_id)
);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL REFERENCES inventory_records(id),
    document_type inventory_document_type NOT NULL,
    document_ref VARCHAR(100),
    quantity_change INT NOT NULL,
    operator_id UUID REFERENCES users(id),
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID NOT NULL REFERENCES users(id),
    status order_status NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    final_amount NUMERIC(12,2) NOT NULL CHECK (final_amount >= 0),
    fulfillment_warehouse_id UUID REFERENCES warehouses(id),
    reservation_expires_at TIMESTAMP,
    idempotency_key VARCHAR(100) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_discount_amount CHECK (final_amount <= total_amount)
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES listings(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
    applied_benefit_id UUID REFERENCES benefit_packages(id)
);

CREATE TABLE benefit_issuances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id UUID NOT NULL REFERENCES memberships(id),
    benefit_id UUID NOT NULL REFERENCES benefit_packages(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    applied_value NUMERIC(10,2) NOT NULL CHECK (applied_value >= 0),
    issued_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE internal_tender_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    type tender_type NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    status tender_status NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE,
    reconciliation_ref VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id),
    type ticket_type NOT NULL,
    severity ticket_severity NOT NULL,
    status ticket_status NOT NULL DEFAULT 'OPEN',
    assigned_to UUID REFERENCES users(id),
    location_address VARCHAR(255),
    location_cross_street VARCHAR(255),
    description TEXT NOT NULL,
    closure_code VARCHAR(50),
    closure_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    sla_acknowledge_by TIMESTAMP GENERATED ALWAYS AS (created_at + INTERVAL '15 minutes') STORED,
    sla_resolve_by TIMESTAMP GENERATED ALWAYS AS (created_at + INTERVAL '24 hours') STORED,
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    escalated_at TIMESTAMP
);

CREATE TABLE ticket_follow_ups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE appeals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tickets(id),
    appellant_id UUID NOT NULL REFERENCES users(id),
    reason TEXT NOT NULL,
    status appeal_status NOT NULL DEFAULT 'SUBMITTED',
    reviewer_id UUID REFERENCES users(id),
    admin_reviewer_id UUID REFERENCES users(id),
    decision_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    decided_at TIMESTAMP
);

CREATE TABLE appeal_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appeal_id UUID NOT NULL REFERENCES appeals(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes > 0 AND file_size_bytes <= 10485760),
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_appeal_evidence_mime_type CHECK (
        mime_type IN ('image/jpeg', 'image/png', 'application/pdf')
    )
);

CREATE TABLE recent_searches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    query VARCHAR(255) NOT NULL,
    filters JSONB,
    searched_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id BIGSERIAL NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id UUID REFERENCES users(id),
    changes JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

DO $$
DECLARE
    part_start DATE := (DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '12 months')::DATE;
    part_end DATE;
    part_name TEXT;
BEGIN
    WHILE part_start < (DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '24 months')::DATE LOOP
        part_end := (part_start + INTERVAL '1 month')::DATE;
        part_name := FORMAT('audit_logs_%s', TO_CHAR(part_start, 'YYYY_MM'));

        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
            part_name,
            part_start,
            part_end
        );

        part_start := part_end;
    END LOOP;
END $$;

CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

CREATE TABLE risk_flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type risk_entity_type NOT NULL,
    entity_id UUID NOT NULL,
    flag_type risk_flag_type NOT NULL,
    incident_count INT NOT NULL CHECK (incident_count >= 0),
    window_start DATE NOT NULL,
    window_end DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_risk_flag_window CHECK (window_end >= window_start)
);

CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();

CREATE TRIGGER trg_orders_set_updated_at
BEFORE UPDATE ON orders
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();

CREATE OR REPLACE FUNCTION prevent_row_modifications()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Table % is immutable and does not allow % operations', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER trg_inventory_movements_immutable
BEFORE UPDATE OR DELETE ON inventory_movements
FOR EACH ROW
EXECUTE FUNCTION prevent_row_modifications();

CREATE TRIGGER trg_benefit_issuances_immutable
BEFORE UPDATE OR DELETE ON benefit_issuances
FOR EACH ROW
EXECUTE FUNCTION prevent_row_modifications();

CREATE OR REPLACE FUNCTION enforce_recent_search_limit()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    DELETE FROM recent_searches
    WHERE id IN (
        SELECT id
        FROM recent_searches
        WHERE user_id = NEW.user_id
        ORDER BY searched_at DESC, id DESC
        OFFSET 20
    );

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_recent_searches_cap
AFTER INSERT ON recent_searches
FOR EACH ROW
EXECUTE FUNCTION enforce_recent_search_limit();

CREATE INDEX idx_listings_fts
    ON listings USING GIN (to_tsvector('english', COALESCE(title, '') || ' ' || COALESCE(description, '')));

CREATE INDEX idx_listings_neighborhood ON listings (neighborhood);
CREATE INDEX idx_listings_category ON listings (category);
CREATE INDEX idx_listings_trending_score_desc ON listings (trending_score DESC);

CREATE INDEX idx_inventory_records_listing_warehouse ON inventory_records (listing_id, warehouse_id);

CREATE INDEX idx_tickets_status_sla_ack_by ON tickets (status, sla_acknowledge_by);
CREATE INDEX idx_tickets_status_sla_resolve_by ON tickets (status, sla_resolve_by);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);

CREATE INDEX idx_orders_idempotency_key ON orders (idempotency_key);
CREATE INDEX idx_internal_tender_idempotency_key ON internal_tender_records (idempotency_key);

CREATE INDEX idx_recent_searches_user_time ON recent_searches (user_id, searched_at DESC);
