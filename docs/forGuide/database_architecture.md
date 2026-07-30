# Plato — Database Architecture

> **Project**: Plato — QR-Based Restaurant Management System
> **Stack**: PostgreSQL 16 · Spring Boot 3.5.5 · Flyway (versioned migrations) · JPA/Hibernate
> **Schema managed by**: Flyway — 11 versioned migration files (V1–V11), executed in order on startup.

---

## 1. System Overview

Plato is a multi-tenant restaurant management platform. Restaurants are independent tenants — no data leaks between them. The system serves two completely separate types of users with different authentication mechanisms:

| Actor | Authentication | How They Access the System |
|-------|---------------|---------------------------|
| **Super Admin** | Email + Password → 24h JWT | Manages the entire platform (restaurants, owners) |
| **Owner** | Email + Password → 24h JWT | Manages their own restaurant(s) |
| **Employee** | Email + Password → 24h JWT | Works within a restaurant (manager, chef, waiter, cashier) |
| **Customer** | **No account, no password** | Scans table QR code → gets a temporary session token |

> **Critical design choice**: Customers are never stored in the `users` table. They have no accounts. Their entire dining session is represented by a row in `customer_sessions`.

---

## 2. Database Hierarchy

The schema is structured as a strict hierarchy. Every table below a level belongs to and references the level above it.

```
Platform
│
├── users                        ← Super Admins, Owners, Employees (platform-level auth)
│
└── restaurants                  ← each owned by one Owner (user)
      │
      ├── restaurant_tables       ← physical tables in the restaurant; each has a QR token
      │
      ├── employees               ← links a user account to a restaurant + assigns role
      │
      ├── menu_categories         ← e.g. "Starters", "Main Course", "Beverages"
      │
      ├── menu_items              ← individual dishes under a category
      │
      └── customer_sessions       ← one session per customer visit (QR scan creates this)
            │
            ├── cart_items        ← items the customer adds before placing an order
            │
            ├── orders            ← placed orders (a session can have multiple orders)
            │     └── order_items ← individual items within an order
            │
            ├── payments          ← payment record for the session's total bill
            │
            └── feedback          ← post-payment rating and review (one per session)
```

---

## 3. PostgreSQL Enum Types (V1 Migration)

All enums are created first (V1) because every table that follows references them. Defined as native PostgreSQL `ENUM` types for data integrity — the database itself enforces valid values, not just the application.

| Enum Type | Values | Used By |
|-----------|--------|---------|
| `user_role` | `SUPER_ADMIN`, `OWNER`, `EMPLOYEE` | `users.role` |
| `user_status` | `ACTIVE`, `SUSPENDED`, `DELETED` | `users.status` |
| `restaurant_status` | `ACTIVE`, `INACTIVE`, `SUSPENDED` | `restaurants.status` |
| `employee_role` | `MANAGER`, `CHEF`, `WAITER`, `CASHIER` | `employees.employee_role` |
| `session_status` | `ACTIVE`, `CLOSED`, `EXPIRED` | `customer_sessions.status` |
| `order_status` | `PENDING`, `ACCEPTED`, `PREPARING`, `READY`, `SERVED`, `CANCELLED` | `orders.status` |
| `order_item_status` | `PENDING`, `PREPARING`, `READY`, `SERVED`, `CANCELLED` | `order_items.status` |
| `payment_method` | `CASH`, `CARD`, `UPI`, `ONLINE` | `payments.payment_method` |
| `payment_status` | `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED` | `payments.payment_status` |

---

## 4. Table Definitions

### 4.1 — `users`
**Migration**: `V2__create_users.sql`
**Purpose**: Stores all platform staff — Super Admins, Owners, Employees. Customers are never stored here.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY DEFAULT gen_random_uuid()` | DB-generated, non-sequential, non-guessable |
| `full_name` | `VARCHAR(100)` | `NOT NULL` | Display name |
| `email` | `VARCHAR(255)` | `NOT NULL UNIQUE` | Login identifier |
| `phone` | `VARCHAR(20)` | nullable | Optional contact |
| `password_hash` | `VARCHAR(255)` | `NOT NULL` | BCrypt hash only — never plaintext |
| `role` | `user_role` | `NOT NULL` | `SUPER_ADMIN` / `OWNER` / `EMPLOYEE` |
| `status` | `user_status` | `NOT NULL DEFAULT 'ACTIVE'` | Soft-delete via `DELETED` status |
| `last_login` | `TIMESTAMPTZ` | nullable | Updated on every successful JWT login |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Auto-set by JPA Auditing |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Auto-updated by JPA Auditing |

**Indexes**: `idx_users_email` (login lookup), `idx_users_role` (admin queries), `idx_users_status` (filtering)

---

### 4.2 — `restaurants`
**Migration**: `V3__create_restaurants.sql`
**Purpose**: Each restaurant owned by one Owner. Restaurant settings (tax, payment methods, ordering rules) are embedded directly — no separate settings table.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `owner_id` | `UUID` | `NOT NULL REFERENCES users(id)` | FK to the Owner's user account |
| `name` | `VARCHAR(255)` | `NOT NULL` | |
| `description` | `TEXT` | nullable | |
| `logo_url` | `TEXT` | nullable | |
| `phone` | `VARCHAR(20)` | nullable | |
| `email` | `VARCHAR(255)` | nullable | |
| `address` | `TEXT` | nullable | |
| `city`, `state`, `country`, `zipcode` | `VARCHAR` | nullable | |
| `timezone` | `VARCHAR(50)` | nullable | e.g. `Asia/Kolkata` |
| `opening_time`, `closing_time` | `TIME` | nullable | Operational hours |
| `status` | `restaurant_status` | `NOT NULL DEFAULT 'ACTIVE'` | |
| `tax_percentage` | `NUMERIC(5,2)` | `NOT NULL DEFAULT 0` | Applied on order total |
| `service_charge` | `NUMERIC(5,2)` | `NOT NULL DEFAULT 0` | |
| `allow_cash_payment` | `BOOLEAN` | `NOT NULL DEFAULT true` | |
| `allow_card_payment` | `BOOLEAN` | `NOT NULL DEFAULT true` | |
| `allow_upi` | `BOOLEAN` | `NOT NULL DEFAULT true` | |
| `allow_online_payment` | `BOOLEAN` | `NOT NULL DEFAULT false` | Gateway integration (future) |
| `accepting_orders` | `BOOLEAN` | `NOT NULL DEFAULT true` | Toggle by owner during rush |
| `auto_accept_orders` | `BOOLEAN` | `NOT NULL DEFAULT false` | Skip manual kitchen acceptance |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |

**Indexes**: `idx_restaurants_owner_id`

---

### 4.3 — `restaurant_tables`
**Migration**: `V4__create_restaurant_tables.sql`
**Purpose**: Physical dining tables in a restaurant. Each table has a unique QR token — scanning it creates a customer session.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `restaurant_id` | `UUID` | `NOT NULL REFERENCES restaurants(id)` | |
| `table_number` | `VARCHAR(20)` | `NOT NULL` | e.g. `"T-01"`, `"12"`, `"VIP-3"` |
| `capacity` | `INT` | nullable | Max guests |
| `qr_token` | `VARCHAR(64)` | `NOT NULL UNIQUE` | Secure random token for QR URL |
| `status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'AVAILABLE'` | `AVAILABLE` / `OCCUPIED` |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |

**Constraints**: `UNIQUE(restaurant_id, table_number)` — table numbers unique within a restaurant, not globally.
**Indexes**: `idx_tables_restaurant_id`, `idx_tables_qr_token` (critical — looked up on every QR scan)

---

### 4.4 — `employees`
**Migration**: `V5__create_employees.sql`
**Purpose**: Links a platform user account to a restaurant and assigns a fine-grained restaurant role. One user can only be an employee of one restaurant (`user_id UNIQUE`).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `user_id` | `UUID` | `NOT NULL UNIQUE REFERENCES users(id)` | One user = one restaurant |
| `restaurant_id` | `UUID` | `NOT NULL REFERENCES restaurants(id)` | |
| `employee_role` | `employee_role` | `NOT NULL` | `MANAGER` / `CHEF` / `WAITER` / `CASHIER` |
| `joined_at` | `DATE` | nullable | Employment start date |
| `status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'ACTIVE'` | |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |

**Indexes**: `idx_employees_restaurant_id`, `idx_employees_user_id`

---

### 4.5 — `menu_categories`
**Migration**: `V6__create_menu.sql`
**Purpose**: Groups menu items into categories (e.g. Starters, Main Course, Desserts).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `restaurant_id` | `UUID` | `NOT NULL REFERENCES restaurants(id)` | |
| `name` | `VARCHAR(100)` | `NOT NULL` | |
| `description` | `TEXT` | nullable | |
| `display_order` | `INT` | `DEFAULT 0` | Controls UI ordering |
| `is_active` | `BOOLEAN` | `DEFAULT true` | Hidden categories not shown to customers |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |

**Indexes**: `idx_menu_categories_restaurant_id`

---

### 4.6 — `menu_items`
**Migration**: `V6__create_menu.sql`
**Purpose**: Individual dishes. Each belongs to one category within one restaurant.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `restaurant_id` | `UUID` | `NOT NULL REFERENCES restaurants(id)` | Denormalised for fast queries |
| `category_id` | `UUID` | `NOT NULL REFERENCES menu_categories(id)` | |
| `name` | `VARCHAR(255)` | `NOT NULL` | |
| `description` | `TEXT` | nullable | |
| `price` | `NUMERIC(10,2)` | `NOT NULL CHECK (price >= 0)` | Price at time of menu creation |
| `image_url` | `TEXT` | nullable | |
| `preparation_time` | `INT` | nullable | Minutes estimate for kitchen |
| `is_veg` | `BOOLEAN` | `DEFAULT true` | Veg / non-veg flag |
| `is_available` | `BOOLEAN` | `DEFAULT true` | Toggle off when item is unavailable |
| `display_order` | `INT` | `DEFAULT 0` | |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |

**Indexes**: `idx_menu_items_restaurant_id`, `idx_menu_items_category_id`

---

### 4.7 — `customer_sessions`
**Migration**: `V7__create_customer_sessions.sql`
**Purpose**: Represents one dining visit. Created when a customer scans the QR code. Carries the `session_token` used as the customer's authentication header (`X-Session-Token`) for all subsequent requests.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `restaurant_id` | `UUID` | `NOT NULL REFERENCES restaurants(id)` | |
| `table_id` | `UUID` | `NOT NULL REFERENCES restaurant_tables(id)` | Which table was scanned |
| `session_token` | `VARCHAR(128)` | `NOT NULL UNIQUE` | Opaque token given to the customer's browser |
| `status` | `session_status` | `NOT NULL DEFAULT 'ACTIVE'` | `ACTIVE` / `CLOSED` / `EXPIRED` |
| `guest_count` | `INT` | `DEFAULT 1` | Number of people at the table |
| `started_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | When the session was created |
| `last_activity` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Reset on every customer request (sliding expiry) |
| `expires_at` | `TIMESTAMPTZ` | `NOT NULL` | `last_activity + 30 min`; auto-extended on activity |
| `ended_at` | `TIMESTAMPTZ` | nullable | Set when session is closed after payment |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |

**Indexes**: `idx_sessions_session_token` (on every authenticated customer request), `idx_sessions_restaurant_id`, `idx_sessions_table_id`

---

### 4.8 — `cart_items`
**Migration**: `V8__create_cart_items.sql`
**Purpose**: Items a customer has added to their cart before placing an order. There is no separate `carts` table — cart items are linked directly to the session. When an order is placed, the cart items are converted to order items and the cart is cleared.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `session_id` | `UUID` | `NOT NULL REFERENCES customer_sessions(id)` | |
| `menu_item_id` | `UUID` | `NOT NULL REFERENCES menu_items(id)` | |
| `quantity` | `INT` | `NOT NULL CHECK (quantity > 0)` | |
| `price_at_time` | `NUMERIC(10,2)` | `NOT NULL` | Captured when item is added; price changes do not affect the cart |
| `special_request` | `TEXT` | nullable | Customer notes (e.g. "no onions") |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |

**Constraints**: `UNIQUE(session_id, menu_item_id)` — same item appears once in the cart; duplicate adds increment quantity.
**Indexes**: `idx_cart_items_session_id`

---

### 4.9 — `orders`
**Migration**: `V9__create_orders.sql`
**Purpose**: A placed order within a session. One session can have multiple orders (e.g. customer orders starters, then orders mains separately). Totals are calculated and stored at order placement time.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `restaurant_id` | `UUID` | `NOT NULL REFERENCES restaurants(id)` | Denormalised for fast kitchen queries |
| `table_id` | `UUID` | `NOT NULL REFERENCES restaurant_tables(id)` | |
| `session_id` | `UUID` | `NOT NULL REFERENCES customer_sessions(id)` | |
| `order_number` | `VARCHAR(20)` | `NOT NULL UNIQUE` | Human-readable: `ORD-20260730-001` |
| `status` | `order_status` | `NOT NULL DEFAULT 'PENDING'` | Full lifecycle tracked |
| `subtotal` | `NUMERIC(10,2)` | `NOT NULL` | Sum of `unit_price x quantity` |
| `tax` | `NUMERIC(10,2)` | `NOT NULL DEFAULT 0` | Calculated from `restaurant.tax_percentage` |
| `discount` | `NUMERIC(10,2)` | `NOT NULL DEFAULT 0` | Future: promo codes |
| `grand_total` | `NUMERIC(10,2)` | `NOT NULL` | `subtotal + tax - discount` |
| `placed_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |
| `completed_at` | `TIMESTAMPTZ` | nullable | Set when status reaches `SERVED` |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |

**Indexes**: `idx_orders_restaurant_id`, `idx_orders_session_id`, `idx_orders_status`

---

### 4.10 — `order_items`
**Migration**: `V9__create_orders.sql`
**Purpose**: Individual items within a placed order. Each item has its own status so the kitchen can track progress item by item.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `order_id` | `UUID` | `NOT NULL REFERENCES orders(id)` | |
| `menu_item_id` | `UUID` | `NOT NULL REFERENCES menu_items(id)` | |
| `quantity` | `INT` | `NOT NULL CHECK (quantity > 0)` | |
| `unit_price` | `NUMERIC(10,2)` | `NOT NULL` | Snapshot from `price_at_time` in cart |
| `special_request` | `TEXT` | nullable | Carried over from cart item |
| `status` | `order_item_status` | `NOT NULL DEFAULT 'PENDING'` | Chef updates this per item |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |

**Indexes**: `idx_order_items_order_id`

---

### 4.11 — `payments`
**Migration**: `V10__create_payments.sql`
**Purpose**: Payment record for a session. One session has one payment record covering all orders. The bill is calculated by summing `grand_total` across all session orders.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `session_id` | `UUID` | `NOT NULL REFERENCES customer_sessions(id)` | |
| `amount` | `NUMERIC(10,2)` | `NOT NULL` | Total bill amount |
| `payment_method` | `payment_method` | `NOT NULL` | `CASH` / `CARD` / `UPI` / `ONLINE` |
| `payment_status` | `payment_status` | `NOT NULL DEFAULT 'PENDING'` | |
| `transaction_reference` | `VARCHAR(255)` | nullable | External gateway reference ID |
| `paid_at` | `TIMESTAMPTZ` | nullable | Set when `payment_status = COMPLETED` |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | |

**Indexes**: `idx_payments_session_id`

---

### 4.12 — `feedback`
**Migration**: `V11__create_feedback.sql`
**Purpose**: Post-payment review submitted by the customer. Strictly one per session (enforced by `UNIQUE` constraint on `session_id`). Can only be submitted after payment is completed.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | `PRIMARY KEY` | |
| `restaurant_id` | `UUID` | `NOT NULL REFERENCES restaurants(id)` | Denormalised for analytics queries |
| `session_id` | `UUID` | `NOT NULL UNIQUE REFERENCES customer_sessions(id)` | One feedback per visit, enforced at DB level |
| `rating` | `INT` | `NOT NULL CHECK (rating BETWEEN 1 AND 5)` | Star rating 1-5 |
| `review` | `TEXT` | nullable | Optional written review |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | No `updated_at` — feedback is immutable once submitted |

**Indexes**: `idx_feedback_restaurant_id`

---

## 5. Flyway Migration Sequence

Flyway runs these 11 files in version order on every application startup. Files are never modified after being committed — schema changes always go in a new migration file.

| Version | File | Creates | Depends On |
|---------|------|---------|-----------|
| V1 | `V1__create_enums.sql` | 9 PostgreSQL enum types | — |
| V2 | `V2__create_users.sql` | `users` | V1 |
| V3 | `V3__create_restaurants.sql` | `restaurants` | V2 |
| V4 | `V4__create_restaurant_tables.sql` | `restaurant_tables` | V3 |
| V5 | `V5__create_employees.sql` | `employees` | V2, V3 |
| V6 | `V6__create_menu.sql` | `menu_categories`, `menu_items` | V3 |
| V7 | `V7__create_customer_sessions.sql` | `customer_sessions` | V3, V4 |
| V8 | `V8__create_cart_items.sql` | `cart_items` | V6, V7 |
| V9 | `V9__create_orders.sql` | `orders`, `order_items` | V3, V4, V6, V7 |
| V10 | `V10__create_payments.sql` | `payments` | V7 |
| V11 | `V11__create_feedback.sql` | `feedback` | V3, V7 |

---

## 6. All Foreign Keys

| Table | Column | References | Notes |
|-------|--------|-----------|-------|
| `restaurants` | `owner_id` | `users(id)` | |
| `restaurant_tables` | `restaurant_id` | `restaurants(id)` | |
| `employees` | `user_id` | `users(id)` | UNIQUE — one user per restaurant |
| `employees` | `restaurant_id` | `restaurants(id)` | |
| `menu_categories` | `restaurant_id` | `restaurants(id)` | |
| `menu_items` | `restaurant_id` | `restaurants(id)` | |
| `menu_items` | `category_id` | `menu_categories(id)` | |
| `customer_sessions` | `restaurant_id` | `restaurants(id)` | |
| `customer_sessions` | `table_id` | `restaurant_tables(id)` | |
| `cart_items` | `session_id` | `customer_sessions(id)` | |
| `cart_items` | `menu_item_id` | `menu_items(id)` | |
| `orders` | `restaurant_id` | `restaurants(id)` | |
| `orders` | `table_id` | `restaurant_tables(id)` | |
| `orders` | `session_id` | `customer_sessions(id)` | |
| `order_items` | `order_id` | `orders(id)` | |
| `order_items` | `menu_item_id` | `menu_items(id)` | |
| `payments` | `session_id` | `customer_sessions(id)` | |
| `feedback` | `restaurant_id` | `restaurants(id)` | |
| `feedback` | `session_id` | `customer_sessions(id)` | UNIQUE — one feedback per session |

> No `ON DELETE CASCADE` anywhere. All deletes are soft (via status field). Full audit history is preserved.

---

## 7. Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **UUID primary keys** | Non-sequential (not guessable like integer IDs), DB-generated, globally unique across tables |
| **Customers have no user accounts** | QR-scan-based sessions are frictionless — customers never sign up or log in |
| **Restaurant settings embedded in `restaurants`** | Avoids a 1:1 join on every request; settings are part of the restaurant entity |
| **No separate `carts` table** | Cart items link directly to the session — simpler schema, fewer joins |
| **`price_at_time` in cart, `unit_price` in order** | Price snapshots prevent menu price changes from affecting in-progress orders |
| **`order_number` as human-readable string** | Staff and customers communicate using `ORD-20260730-001` instead of a UUID |
| **Soft deletes everywhere** | `status = DELETED/INACTIVE` instead of physical `DELETE`. Full audit history preserved. |
| **`session_status = EXPIRED`** | A `@Scheduled` job runs every 5 minutes to mark sessions inactive after 30 min of inactivity |
| **`feedback.session_id UNIQUE`** | Database-level enforcement of one review per visit — cannot be bypassed at application layer |
| **`TIMESTAMPTZ` for all timestamps** | Timezone-aware storage; safe for multi-region deployments |
| **`ddl-auto: validate`** | Hibernate only verifies entities match the schema — Flyway is the sole schema manager |
| **`restaurant_id` denormalised on `orders`, `feedback`** | Avoids multi-hop joins when querying all orders/feedback for a restaurant's dashboard |
