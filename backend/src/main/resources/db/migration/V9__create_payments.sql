-- =============================================================================
-- V9__create_payments.sql
--
-- Payments table capturing settlement for customer dining sessions.
-- Aggregates orders placed during a session, captures service charge, and tracks payment status.
--
-- Dependencies:
--   - V1__create_enums.sql (payment_method, payment_status enums)
--   - V3__create_restaurants.sql (restaurants.id)
--   - V7__create_customer_sessions.sql (customer_sessions.id)
-- =============================================================================

CREATE TABLE payments (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id              UUID            NOT NULL REFERENCES customer_sessions(id),
    restaurant_id           UUID            NOT NULL REFERENCES restaurants(id),

    -- Financial breakdown
    subtotal                NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    tax                     NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    service_charge          NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    discount                NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    amount                  NUMERIC(10,2)   NOT NULL DEFAULT 0.00, -- grand total

    payment_method          payment_method  NOT NULL,
    status                  payment_status  NOT NULL DEFAULT 'PENDING',

    transaction_reference   VARCHAR(255),
    paid_at                 TIMESTAMPTZ,

    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX idx_payments_session_id     ON payments(session_id);
CREATE INDEX idx_payments_restaurant_id  ON payments(restaurant_id);
CREATE INDEX idx_payments_status         ON payments(status);
CREATE INDEX idx_payments_created_at     ON payments(created_at);
