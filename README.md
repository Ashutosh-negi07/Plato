# Plato — Restaurant Management & QR Ordering Backend

> A production-ready REST API backend for a multi-tenant restaurant platform. Restaurant owners can manage their menus, staff, and tables. Customers scan a QR code at the table, browse the live menu, place orders, and pay — all from their phone with no app install required.

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [Running Locally](#running-locally)
- [API Reference](#api-reference)
- [Database Schema](#database-schema)
- [Security Architecture](#security-architecture)
- [Build Status](#build-status)

---

## Overview

Plato is a **multi-tenant SaaS backend** for restaurants. A single deployment serves multiple independent restaurant owners, each fully isolated from one another.

### Two types of users

| Actor | What they do |
|-------|-------------|
| **Restaurant Owner / Staff** | Login with JWT → manage restaurant, menu, tables, employees, orders |
| **Customer** | Scan QR at table → get a session token → browse menu → place orders → pay → rate |

### Core flows built so far

1. **Auth** — Staff JWT login, token validation, role-based access control
2. **Users** — Super admin manages all users (OWNER, EMPLOYEE, SUPER_ADMIN roles)
3. **Restaurants** — Owners create and configure restaurants, settings (tax %, payment methods), and lifecycle management

### Planned flows (in progress)

Tables & QR → Employee assignments → Menu management → Customer sessions → Cart → Orders → Payments → Feedback → WebSocket real-time kitchen updates

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.5 |
| Security | Spring Security 6 + JJWT 0.12.7 (HS384 signed JWTs) |
| Persistence | Spring Data JPA + Hibernate 6 |
| Database | PostgreSQL |
| Migrations | Flyway |
| Validation | Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Email`) |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI at `/swagger-ui.html`) |
| WebSocket | Spring WebSocket (STOMP over SockJS) |
| Build Tool | Maven (Maven Wrapper included) |
| Boilerplate | Lombok |

---

## Project Structure

```
backend/
└── src/
    ├── main/
    │   ├── java/com/miniproject/plato/
    │   │   ├── PlatoApplication.java
    │   │   │
    │   │   ├── auth/                          # JWT login
    │   │   │   ├── AuthController.java
    │   │   │   ├── AuthService.java
    │   │   │   ├── AuthServiceImpl.java
    │   │   │   └── dto/
    │   │   │       ├── LoginRequest.java
    │   │   │       └── LoginResponse.java
    │   │   │
    │   │   ├── user/                          # Staff user management
    │   │   │   ├── User.java
    │   │   │   ├── UserRole.java              # SUPER_ADMIN | OWNER | EMPLOYEE
    │   │   │   ├── UserStatus.java            # ACTIVE | SUSPENDED | DELETED
    │   │   │   ├── UserRepository.java
    │   │   │   ├── UserService.java
    │   │   │   ├── UserServiceImpl.java
    │   │   │   ├── UserMapper.java
    │   │   │   ├── UserController.java
    │   │   │   ├── DataInitializer.java       # Seeds SUPER_ADMIN on first boot
    │   │   │   └── dto/
    │   │   │       ├── CreateUserRequest.java
    │   │   │       ├── UpdateUserRequest.java
    │   │   │       └── UserResponse.java
    │   │   │
    │   │   ├── restaurant/                    # Restaurant management
    │   │   │   ├── Restaurant.java
    │   │   │   ├── RestaurantStatus.java      # ACTIVE | INACTIVE | SUSPENDED
    │   │   │   ├── RestaurantRepository.java
    │   │   │   ├── RestaurantService.java
    │   │   │   ├── RestaurantServiceImpl.java
    │   │   │   ├── RestaurantMapper.java
    │   │   │   ├── RestaurantController.java
    │   │   │   └── dto/
    │   │   │       ├── CreateRestaurantRequest.java
    │   │   │       ├── UpdateRestaurantRequest.java
    │   │   │       ├── RestaurantSettingsRequest.java
    │   │   │       └── RestaurantResponse.java
    │   │   │
    │   │   ├── common/                        # Shared building blocks
    │   │   │   ├── BaseEntity.java            # UUID PK + createdAt + updatedAt
    │   │   │   ├── ApiResponse.java           # Universal response envelope
    │   │   │   └── PagedResponse.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── AppConfig.java             # BCrypt PasswordEncoder bean
    │   │   │   └── SecurityConfig.java        # Filter chain, CORS, CSRF, session
    │   │   │
    │   │   ├── security/
    │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   ├── JwtTokenProvider.java      # Sign, validate, extract claims
    │   │   │   └── UserDetailsServiceImpl.java
    │   │   │
    │   │   └── exception/
    │   │       ├── GlobalExceptionHandler.java
    │   │       ├── PlatoException.java        # Base exception
    │   │       ├── ResourceNotFoundException.java   # 404
    │   │       ├── ConflictException.java           # 409
    │   │       ├── UnauthorizedAccessException.java # 403
    │   │       └── BadRequestException.java         # 400
    │   │
    │   └── resources/
    │       ├── application.yml                # Base config (uses ENV variables)
    │       ├── application-local.yml          # Local dev overrides (git-ignored)
    │       └── db/migration/
    │           ├── V1__create_enums.sql       # All PostgreSQL custom enum types
    │           ├── V2__create_users.sql
    │           └── V3__create_restaurants.sql
    │
    └── test/
        └── java/com/miniproject/plato/
            └── (unit tests — in progress)
```

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+ (or use `./mvnw` wrapper — no install needed)
- PostgreSQL 15+

### 1. Create the database

```bash
psql -U postgres -c "CREATE DATABASE plato;"
```

### 2. Clone the repo

```bash
git clone https://github.com/Ashutosh-negi07/Plato.git
cd Plato
```

### 3. Set up local config

The base `application.yml` reads from environment variables. For local development, create the override file:

```bash
cp backend/src/main/resources/application-local.yml.example \
   backend/src/main/resources/application-local.yml
```

Edit `application-local.yml` with your local PostgreSQL credentials (see [Environment Variables](#environment-variables)).

### 4. Run

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 5. Verify

```
http://localhost:8080/actuator/health       → {"status":"UP"}
http://localhost:8080/swagger-ui.html       → Interactive API explorer
```

On first boot, Flyway applies all migrations and `DataInitializer` seeds a Super Admin account using the credentials from your config.

---

## Environment Variables

| Variable | Required | Description | Local default |
|---|---|---|---|
| `DB_URL` | ✅ | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/plato` |
| `DB_USER` | ✅ | Database username | System username |
| `DB_PASSWORD` | ✅ | Database password | *(empty for Homebrew installs)* |
| `JWT_SECRET` | ✅ | HS384 signing secret (min 32 chars) | `local-dev-secret-do-not-use-in-...` |
| `ADMIN_EMAIL` | ✅ | Email for the seeded Super Admin account | `admin@plato.com` |
| `ADMIN_PASSWORD` | ✅ | Password for the seeded Super Admin account | `Admin@1234` |
| `ADMIN_NAME` | ❌ | Display name for the Super Admin | `Super Admin` |
| `QR_BASE_URL` | ❌ | Base URL prefix embedded in QR codes | `http://localhost:3000/qr` |

In production, set these as environment variables or Docker secrets. **Never commit `application-local.yml` to version control.**

---

## API Reference

All endpoints are also browsable at `http://localhost:8080/swagger-ui.html`.

Every response follows the same envelope shape:

```json
{
  "success": true,
  "message": "Restaurant created successfully",
  "data": { ... }
}
```

---

### Auth

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/login` | Public | Login with email + password → returns JWT |

**Request body**:
```json
{ "email": "admin@plato.com", "password": "Admin@1234" }
```

**Response**:
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGci...",
    "tokenType": "Bearer",
    "role": "SUPER_ADMIN",
    "fullName": "Super Admin"
  }
}
```

Pass the token on every subsequent request:
```
Authorization: Bearer eyJhbGci...
```

---

### Users

> All endpoints require `Authorization: Bearer <token>`

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/users` | SUPER_ADMIN | Paginated list of all users |
| `POST` | `/api/v1/users` | SUPER_ADMIN | Create a new user |
| `GET` | `/api/v1/users/{id}` | SUPER_ADMIN or self | Get user by ID |
| `PUT` | `/api/v1/users/{id}` | SUPER_ADMIN or self | Update user details |
| `PATCH` | `/api/v1/users/{id}/status` | SUPER_ADMIN | Activate / suspend / delete |
| `DELETE` | `/api/v1/users/{id}` | SUPER_ADMIN | Soft delete (sets status = DELETED) |

**Create user body**:
```json
{
  "fullName": "Alice Owner",
  "email": "alice@restaurant.com",
  "password": "Secret@123",
  "phone": "9876543210",
  "role": "OWNER"
}
```

**User roles**: `SUPER_ADMIN` | `OWNER` | `EMPLOYEE`

---

### Restaurants

> All endpoints require `Authorization: Bearer <token>`

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/restaurants` | OWNER | Create a restaurant |
| `GET` | `/api/v1/restaurants` | OWNER / SUPER_ADMIN | List (OWNER sees own, SUPER_ADMIN sees all) |
| `GET` | `/api/v1/restaurants/{id}` | OWNER / SUPER_ADMIN | Get one restaurant |
| `PUT` | `/api/v1/restaurants/{id}` | OWNER | Update name, location, contact info |
| `PATCH` | `/api/v1/restaurants/{id}/status` | SUPER_ADMIN | Change status (`?value=SUSPENDED`) |
| `GET` | `/api/v1/restaurants/{id}/settings` | OWNER | Get payment & order settings |
| `PUT` | `/api/v1/restaurants/{id}/settings` | OWNER | Update payment & order settings |

**Create restaurant body** (all fields except `name` are optional):
```json
{
  "name": "Spice Garden",
  "description": "Authentic Indian cuisine",
  "phone": "9876543210",
  "email": "contact@spicegarden.com",
  "address": "42 MG Road",
  "city": "Mumbai",
  "state": "Maharashtra",
  "country": "India",
  "zipcode": "400001",
  "timezone": "Asia/Kolkata",
  "openingTime": "09:00",
  "closingTime": "23:00",
  "taxPercentage": 5.00,
  "serviceCharge": 2.50,
  "allowCashPayment": true,
  "allowCardPayment": true,
  "allowUpi": true,
  "allowOnlinePayment": false,
  "acceptingOrders": true,
  "autoAcceptOrders": false
}
```

**Restaurant statuses**: `ACTIVE` | `INACTIVE` | `SUSPENDED`

---

### Upcoming Endpoints (in development)

| Module | Endpoints |
|--------|-----------|
| Tables & QR | `POST /restaurants/{id}/tables`, `GET /tables`, `POST /tables/{id}/qr/regenerate` |
| Employees | `POST /restaurants/{id}/employees`, `PATCH /employees/{id}/role`, `DELETE /employees/{id}` |
| Menu | `POST /restaurants/{id}/menu/categories`, `GET /restaurants/{id}/menu` (PUBLIC), `POST /menu/items` |
| Customer Sessions | `POST /sessions/start` (PUBLIC, QR token), `POST /sessions/{id}/close` |
| Cart | `GET /cart`, `POST /cart/items`, `PATCH /cart/items/{id}`, `DELETE /cart` |
| Orders | `POST /orders`, `GET /restaurants/{id}/orders`, `PATCH /orders/{id}/status` |
| Payments | `POST /payments/request-bill`, `GET /payments/{sessionId}/bill`, `PATCH /payments/{id}/complete` |
| Feedback | `POST /feedback`, `GET /restaurants/{id}/feedback/summary` |

---

## Database Schema

Migrations live in `backend/src/main/resources/db/migration/`. Flyway applies them in version order exactly once.

```
V1__create_enums.sql          # user_role, user_status, restaurant_status, employee_role,
                              # table_status, session_status, order_status, order_item_status,
                              # payment_method, payment_status

V2__create_users.sql          # users table

V3__create_restaurants.sql    # restaurants table (includes embedded settings columns)

V4__create_restaurant_tables  # restaurant_tables + qr_token (planned)
V5__create_employees          # employees with role per restaurant (planned)
V6__create_menu               # menu_categories + menu_items (planned)
V7__create_customer_sessions  # customer_sessions + session_token (planned)
V8__create_cart_items         # cart_items scoped to session (planned)
V9__create_orders             # orders + order_items with state machine (planned)
V10__create_payments          # payments with bill breakdown (planned)
V11__create_feedback          # feedback with rating 1–5 (planned)
```

---

## Security Architecture

```
HTTP Request
     │
     ▼
JwtAuthenticationFilter          Runs on EVERY request. Extracts and validates
     │                           JWT from Authorization header. Populates
     │                           SecurityContextHolder with userId + role.
     ▼
@PreAuthorize on Controller      Spring Security evaluates SpEL expression.
     │                           hasRole('OWNER') → checks for ROLE_OWNER authority.
     │                           Throws AccessDeniedException (403) if mismatch.
     ▼
Service Layer                    Re-validates ownership at business level:
     │                           restaurant.getOwnerId().equals(callerId)
     │                           Throws UnauthorizedAccessException (403) if mismatch.
     ▼
GlobalExceptionHandler           Catches ALL exceptions and maps them to
                                 consistent ApiResponse JSON bodies.
```

**Token lifetime**: 24 hours (configurable via `plato.jwt.expiration`)

**Algorithm**: HS384 (HMAC-SHA384)

**Principal stored in token**: `userId` (UUID string) — never email or PII

---

## Build Status

### Modules Complete ✅

- Foundation (Security, Config, Exception handling, Base entity)
- Auth (`POST /api/v1/auth/login`)
- User Module (all 6 CRUD endpoints)
- Restaurant Module (all 7 endpoints including settings)

### Modules In Progress 🔧

- Tables & QR Code
- Employees
- Menu
- Customer Sessions
- Cart
- Orders
- Payments
- Feedback
- WebSocket real-time updates
- Unit tests

### Run the tests

```bash
cd backend
./mvnw test
```

---

## License

This project is built as a learning portfolio for SDE-1/SDE-2 backend engineering roles.
