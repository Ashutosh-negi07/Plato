-- =============================================================================
-- V2__create_users.sql
--
-- Platform users: Super Admin, Owners, and Employees.
-- Customers are NOT stored here — they use customer_sessions.
--
-- Dependencies: V1__create_enums.sql (user_role, user_status)
-- =============================================================================

CREATE TABLE users (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    phone         VARCHAR(20),
    password_hash VARCHAR(255)  NOT NULL,               -- BCrypt hash only, never plaintext
    role          user_role     NOT NULL,
    status        user_status   NOT NULL DEFAULT 'ACTIVE',
    last_login    TIMESTAMPTZ,                           -- updated on every successful login
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Fast lookup for login (email is the login identifier)
CREATE INDEX idx_users_email  ON users(email);

-- Filter users by role (e.g. Super Admin listing all owners)
CREATE INDEX idx_users_role   ON users(role);

-- Filter users by status (e.g. listing only active employees)
CREATE INDEX idx_users_status ON users(status);
