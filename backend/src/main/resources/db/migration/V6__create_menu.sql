-- ─────────────────────────────────────────────────────────────────────────────
-- V6 : Menu Categories & Menu Items
-- Each restaurant has categories (Starters, Mains, Drinks).
-- Each category has items (Paneer Tikka, Dal Makhani, etc.).
-- restaurant_id is stored on menu_items directly to allow cross-restaurant
-- isolation checks without joining through menu_categories.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE menu_categories (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID            NOT NULL REFERENCES restaurants(id),
    name            VARCHAR(100)    NOT NULL,
    description     TEXT,
    display_order   INT             NOT NULL DEFAULT 0,
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE menu_items (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     UUID            NOT NULL REFERENCES menu_categories(id),
    restaurant_id   UUID            NOT NULL REFERENCES restaurants(id),
    name            VARCHAR(150)    NOT NULL,
    description     TEXT,
    price           NUMERIC(10, 2)  NOT NULL,
    image_url       TEXT,
    is_available    BOOLEAN         NOT NULL DEFAULT true,
    display_order   INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX idx_menu_categories_restaurant_id ON menu_categories(restaurant_id);
CREATE INDEX idx_menu_items_category_id        ON menu_items(category_id);
CREATE INDEX idx_menu_items_restaurant_id      ON menu_items(restaurant_id);
