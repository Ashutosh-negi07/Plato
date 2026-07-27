-- =============================================================================
-- V1__create_enums.sql
--
-- All PostgreSQL custom types (enums) used across the entire schema.
-- Must be the FIRST migration because every table that follows references
-- these types. Dropping or renaming an enum requires a new migration.
--
-- Naming convention: snake_case, no prefix.
-- =============================================================================


-- ── Users ─────────────────────────────────────────────────────────────────

-- Roles for platform users (employees/owners/admins).
-- Customers are NOT users — they use customer_sessions instead.
CREATE TYPE user_role AS ENUM (
    'SUPER_ADMIN',   -- manages the entire platform
    'OWNER',         -- manages one or more restaurants
    'EMPLOYEE'       -- works inside a restaurant (manager, chef, waiter, cashier)
);

-- Account lifecycle states.
CREATE TYPE user_status AS ENUM (
    'ACTIVE',        -- can log in and use the platform
    'SUSPENDED',     -- temporarily blocked by Super Admin
    'DELETED'        -- soft-deleted; row stays but account is inaccessible
);


-- ── Restaurants ───────────────────────────────────────────────────────────

CREATE TYPE restaurant_status AS ENUM (
    'ACTIVE',        -- visible and operational
    'INACTIVE',      -- hidden from customers, owner can still manage it
    'SUSPENDED'      -- blocked by Super Admin (e.g. policy violation)
);


-- ── Employees ─────────────────────────────────────────────────────────────

-- Fine-grained role within a restaurant (separate from user_role which is platform-level).
CREATE TYPE employee_role AS ENUM (
    'MANAGER',       -- full access to restaurant settings and reports
    'CHEF',          -- sees kitchen orders, updates order item status
    'WAITER',        -- takes and manages orders at the table
    'CASHIER'        -- processes payments and closes sessions
);


-- ── Customer Sessions ─────────────────────────────────────────────────────

-- Lifecycle of a customer's dining visit.
CREATE TYPE session_status AS ENUM (
    'ACTIVE',        -- session is live; customer can browse, add to cart, order
    'CLOSED',        -- payment completed; session ended normally
    'EXPIRED'        -- no activity for 30 minutes; session auto-closed by scheduler
);


-- ── Orders ────────────────────────────────────────────────────────────────

CREATE TYPE order_status AS ENUM (
    'PENDING',       -- placed by customer, waiting for kitchen to accept
    'ACCEPTED',      -- kitchen accepted; food is being prepared
    'PREPARING',     -- kitchen is actively preparing the order
    'READY',         -- food is ready to be served
    'SERVED',        -- delivered to the table
    'CANCELLED'      -- cancelled by customer or staff before preparation
);

-- Status of each individual item within an order.
CREATE TYPE order_item_status AS ENUM (
    'PENDING',
    'PREPARING',
    'READY',
    'SERVED',
    'CANCELLED'
);


-- ── Payments ──────────────────────────────────────────────────────────────

CREATE TYPE payment_method AS ENUM (
    'CASH',
    'CARD',
    'UPI',
    'ONLINE'         -- payment gateway (future integration)
);

CREATE TYPE payment_status AS ENUM (
    'PENDING',       -- bill requested, payment not yet received
    'COMPLETED',     -- payment confirmed; session will be closed
    'FAILED',        -- payment attempt failed (relevant for online payments)
    'REFUNDED'       -- payment reversed after completion
);
