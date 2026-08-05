-- =============================================================================
-- V3__create_restaurants.sql
--
-- Restaurant table with settings embedded directly.
-- No separate restaurant_settings table — settings are columns on this table.
-- This avoids a join on every request and keeps the data model flat.
--
-- Dependencies: V1__create_enums.sql (restaurant_status)
--               V2__create_users.sql (users.id — owner_id FK)
-- =============================================================================

CREATE TABLE restaurants (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            UUID            NOT NULL REFERENCES users(id),

    -- ── Identity ──────────────────────────────────────────────────────────
    name                VARCHAR(255)    NOT NULL,
    description         TEXT,
    logo_url            TEXT,
    phone               VARCHAR(20),
    email               VARCHAR(255),

    -- ── Location ──────────────────────────────────────────────────────────
    address             TEXT,
    city                VARCHAR(100),
    state               VARCHAR(100),
    country             VARCHAR(100),
    zipcode             VARCHAR(20),

    -- ── Operations ────────────────────────────────────────────────────────
    timezone            VARCHAR(50),
    opening_time        TIME,
    closing_time        TIME,
    status              restaurant_status NOT NULL DEFAULT 'ACTIVE',

    -- ── Settings (embedded — no separate table) ───────────────────────────
    -- WHY EMBEDDED: Settings are always loaded with the restaurant.
    -- A JOIN would be wasteful for data that is always needed together.
    tax_percentage      NUMERIC(5,2)    NOT NULL DEFAULT 0.00,
    service_charge      NUMERIC(5,2)    NOT NULL DEFAULT 0.00,
    allow_cash_payment  BOOLEAN         NOT NULL DEFAULT true,
    allow_card_payment  BOOLEAN         NOT NULL DEFAULT true,
    allow_upi           BOOLEAN         NOT NULL DEFAULT true,
    allow_online_payment BOOLEAN        NOT NULL DEFAULT false,
    accepting_orders    BOOLEAN         NOT NULL DEFAULT true,
    auto_accept_orders  BOOLEAN         NOT NULL DEFAULT false,

    -- ── Audit ─────────────────────────────────────────────────────────────
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Owner queries their own restaurants
CREATE INDEX idx_restaurants_owner_id ON restaurants(owner_id);

-- Super Admin filters by status
CREATE INDEX idx_restaurants_status ON restaurants(status);
