-- =============================================================================
-- V7__create_customer_sessions.sql
--
-- Tracks customer dining visits at a table. Created when a guest scans a QR code.
-- Carries the session_token used by the guest's mobile device for all ordering,
-- cart, and payment actions.
--
-- Dependencies:
--   - V1__create_enums.sql (session_status enum: 'ACTIVE', 'CLOSED', 'EXPIRED')
--   - V3__create_restaurants.sql (restaurants.id)
--   - V4__create_restaurant_tables.sql (restaurant_tables.id)
-- =============================================================================

CREATE TABLE customer_sessions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID            NOT NULL REFERENCES restaurants(id),
    table_id        UUID            NOT NULL REFERENCES restaurant_tables(id),

    -- ── Authentication Token ──────────────────────────────────────────────────
    -- 64-character random hex token passed in X-Session-Token header.
    -- Opaque, unguessable, and separate from the internal UUID.
    session_token   VARCHAR(128)    NOT NULL UNIQUE,

    -- ── State & Metadata ──────────────────────────────────────────────────────
    status          session_status  NOT NULL DEFAULT 'ACTIVE',
    guest_count     INT             NOT NULL DEFAULT 1,

    -- ── Lifecycle Timestamps ──────────────────────────────────────────────────
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_activity   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NOT NULL,               -- sliding 30-min window
    ended_at        TIMESTAMPTZ,                            -- populated on CLOSED/EXPIRED

    -- ── Audit ─────────────────────────────────────────────────────────────────
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Indexes ──────────────────────────────────────────────────────────────────
-- Critical: session_token is queried on EVERY authenticated customer HTTP request
CREATE INDEX idx_customer_sessions_token          ON customer_sessions(session_token);
CREATE INDEX idx_customer_sessions_restaurant_id  ON customer_sessions(restaurant_id);
CREATE INDEX idx_customer_sessions_table_id       ON customer_sessions(table_id);
CREATE INDEX idx_customer_sessions_status         ON customer_sessions(status);
