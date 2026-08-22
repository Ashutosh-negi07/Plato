-- ── Employee Roles already defined in V1 (employee_role enum) ────────────

CREATE TABLE employees (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL REFERENCES users(id),
    restaurant_id   UUID            NOT NULL REFERENCES restaurants(id),
    role            employee_role   NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    UNIQUE(user_id, restaurant_id)
);

CREATE INDEX idx_employees_restaurant_id ON employees(restaurant_id);
CREATE INDEX idx_employees_user_id ON employees(user_id);
