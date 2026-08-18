-- =============================================================================
-- V4__create_restaurant_tables.sql
--
-- Physical tables inside a restaurant, each with a unique QR token.
-- The QR token is the public identifier customers scan — it's separate from
-- the internal UUID so it can be regenerated without breaking foreign keys.
--
-- Dependencies: V3__create_restaurants.sql (restaurants.id)
-- =============================================================================

-- Table operational status (not in V1 — added here since it belongs to this module)
CREATE TYPE table_status AS ENUM (
    'AVAILABLE',     -- table is free, no active session
    'OCCUPIED',      -- customer session is active at this table
    'RESERVED',      -- reserved in advance (future feature)
    'MAINTENANCE'    -- table is out of service
);

CREATE TABLE restaurant_tables (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID            NOT NULL REFERENCES restaurants(id),

    -- ── Identity ──────────────────────────────────────────────────────────
    table_number    VARCHAR(20)     NOT NULL,   -- e.g. "T1", "T12", "VIP-3"
    capacity        INT,                        -- max seats at this table
    label           VARCHAR(100),               -- optional display name, e.g. "Window Seat"

    -- ── QR Code ───────────────────────────────────────────────────────────
    -- qr_token is a random, URL-safe 32-char string (not the UUID).
    -- Kept separate so regenerating a QR code doesn't affect foreign keys.
    qr_token        VARCHAR(64)     NOT NULL UNIQUE,

    -- ── Status ────────────────────────────────────────────────────────────
    status          table_status    NOT NULL DEFAULT 'AVAILABLE',

    -- ── Audit ─────────────────────────────────────────────────────────────
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- A restaurant cannot have two tables with the same number
    UNIQUE(restaurant_id, table_number)
);

-- Index for the most common query pattern: "give me all tables for restaurant X"
CREATE INDEX idx_tables_restaurant_id ON restaurant_tables(restaurant_id);

-- Index for QR scan lookup: "find the table with this qr_token"
-- This is called on EVERY customer QR scan — must be fast
CREATE INDEX idx_tables_qr_token ON restaurant_tables(qr_token);
