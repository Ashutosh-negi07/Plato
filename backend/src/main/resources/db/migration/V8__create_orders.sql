-- =============================================================================
-- V8__create_orders.sql
--
-- Orders and line items placed by customers during an active dining session.
-- Captures price snapshots at time of order and tracks kitchen preparation states.
--
-- Dependencies:
--   - V1__create_enums.sql (order_status, order_item_status enums)
--   - V3__create_restaurants.sql (restaurants.id)
--   - V4__create_restaurant_tables.sql (restaurant_tables.id)
--   - V6__create_menu.sql (menu_items.id)
--   - V7__create_customer_sessions.sql (customer_sessions.id)
-- =============================================================================

CREATE TABLE orders (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID            NOT NULL REFERENCES restaurants(id),
    table_id        UUID            NOT NULL REFERENCES restaurant_tables(id),
    session_id      UUID            NOT NULL REFERENCES customer_sessions(id),

    -- Human-readable order code (e.g. ORD-20260909-A1B2)
    order_number    VARCHAR(32)     NOT NULL UNIQUE,

    status          order_status    NOT NULL DEFAULT 'PENDING',

    -- Financial calculations (price snapshot & totals)
    subtotal        NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    tax             NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    discount        NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    grand_total     NUMERIC(10,2)   NOT NULL DEFAULT 0.00,

    notes           VARCHAR(255),
    placed_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,

    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID                NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id    UUID                NOT NULL REFERENCES menu_items(id),

    quantity        INT                 NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(10,2)       NOT NULL,
    special_request VARCHAR(255),

    status          order_item_status   NOT NULL DEFAULT 'PENDING',

    created_at      TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT now()
);

-- ── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX idx_orders_restaurant_id      ON orders(restaurant_id);
CREATE INDEX idx_orders_session_id         ON orders(session_id);
CREATE INDEX idx_orders_table_id           ON orders(table_id);
CREATE INDEX idx_orders_status             ON orders(status);
CREATE INDEX idx_orders_placed_at          ON orders(placed_at);

CREATE INDEX idx_order_items_order_id      ON order_items(order_id);
CREATE INDEX idx_order_items_menu_item_id  ON order_items(menu_item_id);
