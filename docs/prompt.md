# PLATO MASTER CONTEXT
## PART 1 — PROJECT OVERVIEW & PRODUCT REQUIREMENTS

You are assisting in the design and development of a production-quality SaaS application called **Plato**.

Plato is a **QR-based Restaurant Management System** that allows customers to order food directly from their table without installing an app or creating an account, while giving restaurant owners a complete dashboard to manage their restaurants.

The project is intended to be **production-ready**, **secure**, **highly scalable**, **maintainable**, and **easily extensible**. Every recommendation should follow industry best practices used in modern SaaS applications.

---

# Project Goals

The objective of Plato is to digitize the complete dine-in restaurant ordering experience.

The platform should allow:

- Customers to order food by scanning a QR code.
- Restaurant staff to receive and manage orders in real time.
- Owners to manage restaurants from a centralized dashboard.
- The platform administrator to manage all restaurants and users.

The architecture should support hundreds or thousands of restaurants from a single deployment.

---

# Business Model

Plato is a **multi-tenant SaaS platform**.

A single backend and database instance serves multiple restaurants.

Every restaurant has completely isolated data.

Owners cannot access another owner's restaurants.

Employees cannot access restaurants they do not belong to.

Customers can only access the restaurant associated with the QR code they scanned.

---

# Target Users

There are four logical user roles.

## 1. Super Admin

Represents the Plato platform administrator.

Responsibilities:

- Create restaurant owners.
- Manage restaurant subscriptions.
- Suspend or activate restaurants.
- View platform-wide analytics.
- Manage the entire platform.
- Access every restaurant if necessary.

---

## 2. Owner

Represents a restaurant owner.

An owner may own one or many restaurants.

Responsibilities:

- Create restaurants.
- Manage restaurant information.
- Manage tables.
- Generate QR codes.
- Manage menus.
- Manage employees.
- View orders.
- View analytics.
- Manage business settings.

Owners should only access restaurants they own.

---

## 3. Employee

Employees belong to exactly one restaurant.

Possible employee roles include:

- Manager
- Chef
- Waiter
- Cashier

Employees authenticate using their own accounts.

Permissions depend on their assigned role.

---

## 4. Customer

Customers do not register.

Customers do not log in.

Customers simply scan a QR code and immediately begin ordering.

Customers are represented only by a temporary dining session.

---

# Core Customer Workflow

The customer journey should be simple.

1. Customer enters restaurant.
2. Customer sits at a table.
3. Customer scans the QR code.
4. Plato identifies:
   - Restaurant
   - Table
5. A temporary customer session is created.
6. Customer views the menu.
7. Customer adds items to the cart.
8. Customer places one or more orders.
9. Kitchen prepares food.
10. Customer receives live order status updates.
11. Customer pays the bill.
12. Customer leaves feedback.
13. Customer session is closed.

No login is ever required.

---

# Customer Sessions

Plato does **not** use customer accounts.

Instead, every QR scan creates a temporary **Customer Session**.

The customer session represents one complete dining visit.

A customer session stores:

- Restaurant
- Table
- Cart
- Orders
- Payment status
- Feedback
- Session status
- Timestamps

The frontend stores only a secure session token.

All business data remains on the server.

Customer sessions should automatically expire after inactivity or shortly after payment.

Expired sessions are retained for analytics and historical reporting.

---

# QR Code System

Every restaurant table has a unique QR code.

Each QR code identifies:

- Restaurant
- Table

Example:

```
https://plato.app/qr/7Kd92abLm
```

Scanning the QR should immediately start a customer session.

No manual table selection should be required.

---

# Menu Management

Restaurants should manage:

- Categories
- Menu items
- Prices
- Availability
- Images
- Descriptions
- Preparation time
- Vegetarian / Non-Vegetarian indicators
- Display order

The menu should update instantly for customers.

Unavailable items should not be orderable.

---

# Cart

Every customer session owns exactly one active cart.

Customers should be able to:

- Add items
- Remove items
- Update quantities
- Add special instructions

The cart must persist across:

- Browser refreshes
- Temporary connection loss
- Page navigation

The cart is stored in PostgreSQL.

The frontend only displays the current state.

---

# Ordering

A customer session may create multiple orders.

Example:

Order #1

- Pizza
- Fries

Later

Order #2

- Dessert
- Coffee

All orders belong to the same dining session.

Each order contains one or more order items.

---

# Order Status

Orders move through a defined lifecycle.

```
PLACED
    ↓
CONFIRMED
    ↓
PREPARING
    ↓
READY
    ↓
SERVED
    ↓
COMPLETED
```

Customers should receive real-time updates.

Employees should also receive live notifications.

---

# Payments

Initially support:

- UPI
- Credit/Debit Card
- Cash
- Online Payment

Assume one payment per dining session for the MVP.

The design should be extensible to support:

- Split bills
- Partial payments
- Refunds

---

# Feedback

After payment, customers may submit:

- Rating
- Review

Only one feedback should be allowed per customer session.

---

# Real-Time Features

Plato should support real-time communication.

Examples include:

- New order notifications
- Kitchen status updates
- Order progress
- Payment confirmation
- Dashboard updates

Real-time communication should use **WebSockets**.

---

# Functional Requirements

The system must support:

- Multi-restaurant management
- QR-based ordering
- Temporary customer sessions
- Restaurant dashboards
- Employee management
- Menu management
- Cart management
- Order management
- Payment tracking
- Feedback collection
- Role-based authorization
- Analytics

---

# Non-Functional Requirements

The system should be:

- Production-ready
- Highly scalable
- Secure
- Responsive
- RESTful
- Stateless (except temporary dining sessions)
- Cloud deployable
- Maintainable
- Modular
- Extensible
- Fault tolerant

The architecture should support horizontal scaling and future microservice migration without major redesign.

---

# Development Philosophy

Every design decision should prioritize:

- Simplicity
- Clean Architecture
- SOLID Principles
- Separation of Concerns
- High cohesion
- Low coupling
- Reusability
- Performance
- Security
- Readability
- Testability

Avoid unnecessary complexity while keeping the architecture flexible for future growth.

---

# AI Instructions

Whenever assisting with Plato:

- Always assume this is a real production SaaS application.
- Follow enterprise-level backend and database design practices.
- Prefer long-term maintainability over quick solutions.
- Design for scalability from the beginning.
- Normalize the PostgreSQL database unless there is a justified optimization.
- Follow Spring Boot best practices.
- Follow Next.js best practices.
- Keep frontend and backend responsibilities clearly separated.
- The backend is always the source of truth for business data.
- Think ahead before suggesting architectural changes to ensure they fit future expansion.
- If multiple solutions exist, explain the trade-offs and recommend the one most suitable for Plato's architecture.



# PLATO MASTER CONTEXT
## PART 2 — SYSTEM ARCHITECTURE & TECHNICAL DESIGN

This document describes the technical architecture, technology stack, backend structure, frontend structure, communication protocols, authentication strategy, and development standards for Plato.

The goal is to build a modern, scalable SaaS application that can serve thousands of restaurants from a single deployment while remaining modular and maintainable.

---

# Technology Stack

## Backend

- Java 21
- Spring Boot 3.x
- Spring Security 6
- Spring Data JPA (Hibernate)
- PostgreSQL
- Maven
- Bean Validation
- Lombok
- WebSocket (STOMP)
- JWT Authentication
- BCrypt Password Encoder

---

## Frontend

- Next.js (App Router)
- TypeScript
- Tailwind CSS
- Zustand (or Redux if complexity grows)
- Axios
- React Hook Form
- WebSocket Client (SockJS + STOMP)

---

## Database

- PostgreSQL

Primary goals:

- ACID compliance
- Strong relational integrity
- High performance
- Proper normalization
- Easy reporting
- Future scalability

---

## Deployment

Backend

Spring Boot

↓

Docker

↓

Cloud VM / Kubernetes

↓

PostgreSQL

↓

Redis (future)

↓

Nginx Reverse Proxy

Frontend

↓

Next.js

↓

Vercel / Docker / Nginx

---

# Overall Architecture

```
                   Browser
                        │
        ┌───────────────┴────────────────┐
        │                                │
        ▼                                ▼
     Next.js                      WebSocket Client
        │                                │
        └──────────────┬─────────────────┘
                       │
                 HTTPS / WSS
                       │
               Spring Boot API
                       │
     ┌─────────────────┼─────────────────┐
     │                 │                 │
     ▼                 ▼                 ▼
Authentication    Business Logic     WebSockets
     │                 │                 │
     └─────────────────┼─────────────────┘
                       │
                 Spring Data JPA
                       │
                  PostgreSQL
```

---

# Architecture Style

The backend follows a layered architecture.

```
Controller

↓

Service

↓

Repository

↓

Database
```

Business logic should **never** exist inside controllers.

Repositories should only perform database operations.

Services contain all business rules.

Controllers only:

- Validate requests
- Call services
- Return responses

---

# Backend Package Structure

```
com.plato

├── config
├── security
├── auth
├── user
├── restaurant
├── employee
├── table
├── menu
├── cart
├── session
├── order
├── payment
├── feedback
├── websocket
├── notification
├── exception
├── util
└── common
```

Every module should contain:

```
controller

service

repository

entity

dto

mapper

validator
```

---

# Frontend Structure

```
src

app/

components/

features/

hooks/

services/

lib/

store/

types/

utils/

constants/

styles/
```

Features should be separated by domain.

Example

```
features/

menu/

cart/

orders/

restaurant/

dashboard/

employees/
```

Avoid placing all components inside one folder.

---

# Authentication

Only platform users authenticate.

Authenticated users:

- Super Admin
- Owner
- Employee

Customers never authenticate.

---

# Authentication Flow

User

↓

Email + Password

↓

Spring Security

↓

AuthenticationManager

↓

JWT Generated

↓

JWT returned to client

↓

Stored in HttpOnly Cookie (preferred)

↓

Sent automatically on future requests

---

# Authorization

Role-based authorization.

Roles

```
SUPER_ADMIN

OWNER

EMPLOYEE
```

Employee permissions depend on employee_role.

Example

Chef

↓

Can only access

- Kitchen orders

Cannot

- Edit menu
- Manage employees

---

# Customer Sessions

Customers do not receive JWT authentication.

Instead

QR Scan

↓

Backend creates Customer Session

↓

Backend generates secure session token

↓

Frontend stores session token

↓

All customer requests include this token

The backend validates:

- Restaurant
- Table
- Session Status
- Expiration

---

# Session Expiration

Customer sessions use sliding expiration.

Example

30 minutes inactivity

Every request

↓

last_activity updated

↓

expires_at extended

If expired

↓

Session becomes EXPIRED

No further ordering allowed.

---

# Stateless Backend

Authenticated users use JWT.

Customer sessions are persisted in PostgreSQL.

Spring HttpSession should **not** be used.

Backend instances must remain stateless.

This allows horizontal scaling.

---

# REST API Design

Use REST principles.

Example

```
GET

POST

PUT

PATCH

DELETE
```

Resources

```
/restaurants

/tables

/menu

/cart

/orders

/payments

/employees

/feedback
```

Avoid verbs.

Bad

```
/createRestaurant
```

Good

```
POST /restaurants
```

---

# API Response Format

Every response follows a consistent format.

Success

```json
{
  "success": true,
  "message": "Restaurant created successfully",
  "data": {}
}
```

Error

```json
{
  "success": false,
  "message": "Restaurant not found",
  "errors": []
}
```

---

# DTO Usage

Never expose JPA entities directly.

Always use DTOs.

```
Entity

↓

Mapper

↓

DTO

↓

JSON
```

Same for incoming requests.

---

# Validation

Use Bean Validation.

Examples

```
@NotBlank

@NotNull

@Email

@Positive

@Size

@Pattern
```

Validation belongs in DTOs.

---

# Exception Handling

Use one global exception handler.

```
@RestControllerAdvice
```

Never return stack traces.

Every error should return structured JSON.

---

# Logging

Use SLF4J.

Log:

- Authentication
- Authorization failures
- Payments
- Order creation
- Unexpected exceptions

Never log:

- Passwords
- JWTs
- Sensitive customer information

---

# Password Storage

Passwords are stored using BCrypt.

Never store plaintext passwords.

Never decrypt passwords.

Only verify hashes.

---

# File Storage

Images should **not** be stored inside PostgreSQL.

Store only URLs.

Example

```
logo_url

image_url
```

Use cloud object storage.

Example

- Cloudinary
- AWS S3
- Google Cloud Storage

---

# WebSocket Architecture

Real-time communication is required.

Uses

- New orders
- Kitchen updates
- Order status
- Payment confirmation
- Dashboard refresh
- Notifications

Flow

```
Customer Places Order

↓

Order Saved

↓

Backend publishes event

↓

WebSocket

↓

Kitchen Dashboard Updates

↓

Customer Order Screen Updates
```

---

# Notification Strategy

Notifications are event-driven.

Examples

Order Created

↓

Kitchen notified

Order Ready

↓

Customer notified

Payment Complete

↓

Owner dashboard updated

---

# Transactions

Business-critical operations use transactions.

Examples

Place Order

↓

Create Order

↓

Create Order Items

↓

Clear Cart

↓

Commit

If one step fails

↓

Rollback

---

# Caching (Future)

Redis may be introduced for:

- Active sessions
- Frequently viewed menus
- QR lookups
- Analytics

PostgreSQL remains the source of truth.

---

# Security

Implement:

- JWT Authentication
- BCrypt Password Hashing
- Role-based authorization
- Method-level security
- HTTPS only
- CORS configuration
- CSRF disabled for REST APIs using JWT
- Rate limiting (future)
- Input validation
- SQL Injection prevention through JPA
- XSS protection on frontend

---

# Performance Goals

- Fast API responses
- Efficient database queries
- Lazy loading where appropriate
- Pagination for large datasets
- Proper indexing
- Connection pooling (HikariCP)

Avoid N+1 query problems.

---

# Scalability Goals

The system should support:

- Thousands of restaurants
- Hundreds of concurrent customer sessions
- Multiple backend instances
- Cloud deployment
- Future microservice migration

Business logic should remain independent of deployment architecture.

---

# Future Expansion

The architecture should allow adding:

- Inventory Management
- Reservations
- Loyalty Programs
- Coupons
- Delivery Integration
- Multi-Branch Restaurants
- Kitchen Display System (KDS)
- AI Analytics
- Push Notifications
- Mobile Applications

without major database or architectural redesign.

---

# Development Principles

Follow:

- SOLID Principles
- Clean Architecture
- DRY (Don't Repeat Yourself)
- KISS (Keep It Simple)
- Single Responsibility Principle
- Dependency Injection
- Constructor Injection
- Interface-based services
- Proper transaction management
- Clear separation of frontend and backend responsibilities

---

# AI Instructions

When suggesting architecture or code:

- Follow Spring Boot best practices.
- Follow Next.js best practices.
- Use PostgreSQL efficiently.
- Prefer maintainability over shortcuts.
- Never place business logic in controllers.
- Never expose JPA entities directly.
- Keep APIs RESTful and consistent.
- Design every feature as if Plato will eventually serve thousands of restaurants.
- Explain trade-offs when multiple technical approaches exist and recommend the one that best fits Plato's long-term architecture.


# PLATO MASTER CONTEXT
## PART 3 — DATABASE DESIGN (POSTGRESQL)

This document defines the complete database architecture for Plato.

The database must be production-ready, normalized, scalable, and optimized for a multi-tenant SaaS application.

The primary database is PostgreSQL.

The design should support future modules such as inventory management, reservations, loyalty programs, analytics, multi-branch restaurants, delivery integration, and AI features without requiring major schema redesign.

---

# Database Design Principles

The database should follow:

- Third Normal Form (3NF)
- ACID Transactions
- Foreign Key Constraints
- Proper Indexing
- UUID Primary Keys
- Audit Columns
- Soft Deletes where appropriate
- Minimal redundancy
- High read performance
- Easy reporting

The backend (Spring Boot) is the source of truth.

The frontend never owns business data.

---

# Multi-Tenant Architecture

Each restaurant is an isolated tenant.

Restaurant data must never mix.

Every business-related table should contain a restaurant reference whenever applicable.

Example

```
Restaurant A

Orders

Menu

Tables

Employees

Sessions

Feedback


Restaurant B

Orders

Menu

Tables

Employees

Sessions

Feedback
```

---

# Naming Convention

Tables

Plural

```
restaurants

users

orders

payments
```

Primary Keys

```
id
```

Foreign Keys

```
restaurant_id

owner_id

table_id

session_id

order_id
```

Timestamps

```
created_at

updated_at

deleted_at
```

---

# Primary Keys

Use UUID.

Example

```
id UUID PRIMARY KEY
```

Never expose sequential IDs publicly.

---

# Audit Columns

Almost every table should contain

```
created_at

updated_at
```

Some tables also include

```
deleted_at
```

for soft deletion.

---

# Core Tables

## 1. users

Purpose

Authentication for platform users.

Includes

- Super Admin
- Owners
- Employees

Columns

```
id

full_name

email

phone

password_hash

role

status

last_login

created_at

updated_at
```

Relationships

```
Owner

1 ------ N Restaurants

User

1 ------ 1 Employee Profile
```

---

## 2. restaurants

Purpose

Represents one restaurant.

Columns

```
id

owner_id

name

description

logo_url

phone

email

address

city

state

country

zipcode

currency

timezone

opening_time

closing_time

status

created_at

updated_at
```

Relationships

```
Restaurant

1 ------ N Tables

1 ------ N Employees

1 ------ N Categories

1 ------ N Sessions

1 ------ N Orders

1 ------ N Feedback
```

---

## 3. restaurant_settings

Purpose

Stores configurable settings instead of bloating the restaurant table.

Columns

```
id

restaurant_id

currency

timezone

tax_percentage

service_charge

allow_cash_payment

allow_card_payment

allow_upi

allow_online_payment

accepting_orders

auto_accept_orders

created_at

updated_at
```

Relationship

```
Restaurant

1 ------ 1 Settings
```

---

## 4. restaurant_tables

Purpose

Physical dining tables.

Columns

```
id

restaurant_id

table_number

capacity

qr_token

status

created_at

updated_at
```

Relationship

```
Restaurant

1 ------ N Tables
```

---

## 5. employees

Purpose

Restaurant-specific employee information.

Columns

```
id

user_id

restaurant_id

employee_role

joined_at

status

created_at

updated_at
```

Relationship

```
Restaurant

1 ------ N Employees

User

1 ------ 1 Employee
```

---

## 6. menu_categories

Purpose

Groups menu items.

Example

```
Starters

Pizza

Desserts

Drinks
```

Columns

```
id

restaurant_id

name

description

display_order

is_active

created_at

updated_at
```

Relationship

```
Restaurant

1 ------ N Categories
```

---

## 7. menu_items

Purpose

Restaurant menu.

Columns

```
id

restaurant_id

category_id

name

description

price

image_url

preparation_time

is_veg

is_available

display_order

created_at

updated_at
```

Relationship

```
Category

1 ------ N Menu Items
```

---

## 8. customer_sessions

Purpose

Represents one dining visit.

No login required.

Columns

```
id

restaurant_id

table_id

session_token

status

guest_count

started_at

last_activity

expires_at

ended_at

created_at

updated_at
```

Relationship

```
Restaurant

1 ------ N Sessions

Table

1 ------ N Sessions
```

---

## 9. carts

Purpose

One active cart per customer session.

Columns

```
id

session_id

status

created_at

updated_at
```

Relationship

```
CustomerSession

1 ------ 1 Active Cart
```

---

## 10. cart_items

Purpose

Items inside a cart.

Columns

```
id

cart_id

menu_item_id

quantity

price_at_time

special_request

created_at

updated_at
```

Relationship

```
Cart

1 ------ N Cart Items
```

---

## 11. orders

Purpose

Represents one order placed by the customer.

A customer session may create multiple orders.

Columns

```
id

restaurant_id

table_id

session_id

order_number

status

subtotal

tax

discount

grand_total

placed_at

completed_at

created_at

updated_at
```

Relationship

```
Session

1 ------ N Orders
```

---

## 12. order_items

Purpose

Individual items inside an order.

Columns

```
id

order_id

menu_item_id

quantity

unit_price

special_request

status

created_at

updated_at
```

Relationship

```
Order

1 ------ N Order Items
```

---

## 13. payments

Purpose

Stores payment information.

Columns

```
id

session_id

amount

payment_method

payment_status

transaction_reference

paid_at

created_at

updated_at
```

Relationship

```
Session

1 ------ 1 Payment
```

Future

```
Session

1 ------ N Payments
```

to support split bills.

---

## 14. feedback

Purpose

Customer feedback.

Columns

```
id

restaurant_id

session_id

rating

review

created_at
```

Relationship

```
Session

1 ------ 0..1 Feedback
```

---

## 15. refresh_tokens

Purpose

Stores refresh tokens for authenticated users.

Customers never use this table.

Columns

```
id

user_id

token

expires_at

revoked

created_at
```

Relationship

```
User

1 ------ N Refresh Tokens
```

---

# Complete Entity Relationships

```
Users

│

├────────────── Restaurants

│                    │

│                    ├──────── RestaurantSettings

│                    │

│                    ├──────── RestaurantTables

│                    │

│                    ├──────── Employees

│                    │

│                    ├──────── MenuCategories

│                    │             │

│                    │             ▼

│                    │        MenuItems

│                    │

│                    ├──────── CustomerSessions

│                    │              │

│                    │              ├──────── Carts

│                    │              │         │

│                    │              │         ▼

│                    │              │    CartItems

│                    │              │

│                    │              ├──────── Orders

│                    │              │          │

│                    │              │          ▼

│                    │              │     OrderItems

│                    │              │

│                    │              ├──────── Payments

│                    │              │

│                    │              └──────── Feedback

│

└──────── RefreshTokens
```

---

# PostgreSQL Enums

## UserRole

```
SUPER_ADMIN

OWNER

EMPLOYEE
```

---

## UserStatus

```
ACTIVE

SUSPENDED

DELETED
```

---

## RestaurantStatus

```
ACTIVE

INACTIVE

SUSPENDED
```

---

## EmployeeRole

```
MANAGER

CHEF

WAITER

CASHIER
```

---

## SessionStatus

```
ACTIVE

ORDERING

PAYMENT_PENDING

PAID

EXPIRED
```

---

## CartStatus

```
ACTIVE

ORDER_PLACED

ABANDONED
```

---

## OrderStatus

```
PLACED

CONFIRMED

PREPARING

READY

SERVED

COMPLETED

CANCELLED
```

---

## OrderItemStatus

```
PENDING

PREPARING

READY

SERVED
```

---

## PaymentMethod

```
UPI

CARD

CASH

ONLINE
```

---

## PaymentStatus

```
PENDING

SUCCESS

FAILED

REFUNDED
```

---

# Database Indexes

Create indexes on frequently queried columns.

Examples

```
users.email

restaurants.owner_id

restaurant_tables.restaurant_id

restaurant_tables.qr_token

customer_sessions.session_token

customer_sessions.restaurant_id

customer_sessions.table_id

orders.restaurant_id

orders.session_id

orders.status

payments.session_id

menu_items.restaurant_id

menu_items.category_id
```

---

# Database Constraints

Implement:

- Foreign Key Constraints
- NOT NULL where appropriate
- UNIQUE on email
- UNIQUE on qr_token
- UNIQUE on session_token
- CHECK constraints where useful
- Cascading rules only when safe
- Restrict deletes for historical business data

Orders and payments should never be physically deleted.

---

# AI Instructions

When designing or modifying the database:

- Maintain normalization unless denormalization is justified by performance.
- Use UUIDs for identifiers.
- Preserve historical records (orders, payments, sessions).
- Avoid duplicate business data.
- Keep monetary values as `NUMERIC(10,2)`.
- Store image URLs instead of binary files.
- Add indexes for frequently queried fields.
- Design every table with future expansion in mind.
- Explain trade-offs before suggesting schema changes.
- Never compromise data integrity for convenience.



# PLATO MASTER CONTEXT
## PART 4 — BUSINESS LOGIC, WORKFLOWS & APPLICATION BEHAVIOR

This document defines how Plato behaves from a business perspective.

It describes every major workflow, the rules governing each module, the responsibilities of each user role, and how the backend should process requests.

The backend is always the source of truth.

The frontend is responsible only for presentation and user interaction.

---

# Core Business Philosophy

Plato is designed around one principle:

> Every customer interaction belongs to a temporary dining session.

Unlike e-commerce applications, Plato does not use customer accounts.

Instead, every customer is represented by a Customer Session that begins when a QR code is scanned and ends after payment or expiration.

---

# Primary Actors

There are four actors.

## Super Admin

Responsibilities

- Manage owners
- Manage restaurants
- Suspend restaurants
- View platform analytics
- Manage subscriptions
- Resolve platform issues

Super Admin has unrestricted access.

---

## Owner

Responsibilities

- Manage restaurants
- Manage employees
- Manage menu
- Manage categories
- Manage tables
- View active sessions
- View orders
- View revenue
- View analytics

Owners cannot access restaurants they do not own.

---

## Employee

Employee permissions depend on employee_role.

### Manager

Can

- View orders
- Manage employees
- Edit menu
- Manage tables

Cannot

- Access platform settings

---

### Chef

Can

- View incoming orders
- Update order item status
- Mark food ready

Cannot

- Edit menu
- Process payments
- Manage employees

---

### Waiter

Can

- View table sessions
- View customer requests
- Mark orders served

Cannot

- Modify menu
- Manage employees

---

### Cashier

Can

- Generate bill
- Accept payment
- Complete session

Cannot

- Modify menu

---

# Customer Workflow

Step 1

Customer scans QR.

Backend validates QR token.

If valid

↓

Customer Session created.

If active session already exists for the table, backend follows the restaurant's configured policy (join existing session, create a new session after payment, or require staff intervention).

---

Step 2

Customer receives

- Restaurant information
- Menu
- Categories
- Active cart

---

Step 3

Customer browses menu.

Customer may

- Add item
- Remove item
- Change quantity
- Add special request

Every change immediately updates PostgreSQL.

The frontend never becomes the permanent source of truth.

---

Step 4

Customer places order.

Backend performs

Validate session

↓

Validate menu items

↓

Validate availability

↓

Calculate totals

↓

Create order

↓

Create order items

↓

Update cart status

↓

Publish WebSocket event

↓

Return success

Entire operation occurs inside one transaction.

---

Step 5

Kitchen receives notification instantly.

Kitchen dashboard refreshes automatically.

---

Step 6

Chef updates order status.

Example

```
PLACED

↓

CONFIRMED

↓

PREPARING

↓

READY
```

Every update generates a WebSocket event.

Customer receives real-time updates.

---

Step 7

Waiter serves order.

Order status

```
READY

↓

SERVED
```

---

Step 8

Customer may continue ordering.

One customer session can create multiple orders.

Example

```
Order 1

Burger

Pizza

Later

Order 2

Coffee

Cake

Later

Order 3

Ice Cream
```

All belong to the same dining session.

---

Step 9

Customer requests bill.

Backend calculates

```
Order Total

+

Tax

+

Service Charge

-

Discount

=

Grand Total
```

Bill always reflects the latest database state.

---

Step 10

Cashier processes payment.

Backend

Creates payment

↓

Updates session

↓

Marks payment complete

↓

Closes session

↓

Notifies dashboard

↓

Allows feedback

---

Step 11

Customer submits feedback.

Only one feedback per session.

Feedback cannot be edited after submission (unless future requirements change).

---

# Cart Rules

Each customer session owns exactly one active cart.

Rules

- Cannot add unavailable items.
- Cannot exceed restaurant-defined quantity limits (if configured).
- Price is captured when the item is added to preserve consistency during checkout.
- Cart persists until converted into an order or abandoned.

If a menu item's price changes after being added to the cart, the backend should follow the restaurant's pricing policy (recalculate or honor the captured price). The chosen policy should be consistent across the application.

---

# Order Rules

Each order belongs to

- One restaurant
- One table
- One customer session

Each order contains

One or more order items.

Orders are immutable once completed.

Historical orders should never be modified.

---

# Order Status Rules

Allowed transitions

```
PLACED

↓

CONFIRMED

↓

PREPARING

↓

READY

↓

SERVED

↓

COMPLETED
```

Cancellation

```
PLACED

↓

CANCELLED
```

After PREPARING

Cancellation policy depends on restaurant settings.

---

# Payment Rules

Every payment belongs to one customer session.

Payment methods

- UPI
- Card
- Cash
- Online

Once payment succeeds

Session

↓

PAID

↓

CLOSED

Customer cannot create new orders.

---

# Customer Session Lifecycle

```
QR Scan

↓

ACTIVE

↓

ORDERING

↓

PAYMENT_PENDING

↓

PAID

↓

CLOSED

↓

EXPIRED
```

Inactive sessions automatically expire after the configured timeout.

Expired sessions remain stored.

---

# Session Expiration

Every request updates

```
last_activity
```

Backend recalculates

```
expires_at
```

Sliding expiration.

Example

```
30 minutes

↓

Every request extends

↓

Another 30 minutes
```

If expired

Return

```
401

Session Expired
```

---

# Restaurant Workflow

Owner

↓

Creates restaurant

↓

Creates tables

↓

QR codes generated

↓

Adds categories

↓

Adds menu items

↓

Adds employees

↓

Restaurant begins accepting orders

---

# Employee Workflow

Employee logs in.

Backend determines

- Restaurant
- Role
- Permissions

Dashboard is generated according to role.

---

# Super Admin Workflow

Super Admin

↓

Creates owner

↓

Owner creates restaurants

↓

Restaurant becomes active

↓

Platform analytics update

---

# Notifications

Events generating notifications

- New order
- Order confirmed
- Order preparing
- Order ready
- Payment completed
- New employee
- Restaurant suspended

Notification delivery uses WebSockets.

---

# Real-Time Dashboard

Owner dashboard updates automatically.

Examples

Current active tables

Today's orders

Current revenue

Kitchen workload

Live sessions

No manual refresh required.

---

# Kitchen Display System (Future)

Kitchen sees

```
Incoming Orders

↓

Preparing

↓

Ready

↓

Completed
```

Kitchen never edits payment data.

Kitchen never edits customer information.

---

# Error Handling Rules

Examples

Expired session

↓

Return

401

Restaurant closed

↓

Return

403

Menu item unavailable

↓

Return

409

Invalid QR

↓

Return

404

Unexpected error

↓

Return

500

Always return structured JSON.

---

# Business Constraints

Customers cannot

- Access another restaurant
- Modify completed orders
- Modify payment
- Submit multiple feedback entries

Employees cannot

- Access other restaurants
- Escalate privileges

Owners cannot

- View other owners' restaurants

---

# Audit Rules

Record

Restaurant creation

Employee creation

Order placement

Payment

Restaurant suspension

Role changes

Login events

Audit logs should never be editable.

---

# Future Business Modules

Architecture should support

Inventory

Reservations

Coupons

Loyalty Programs

Memberships

Gift Cards

Subscriptions

AI Recommendations

Dynamic Pricing

Delivery Integration

Multiple Kitchen Stations

Split Bills

Table Reservations

Waitlist

without redesigning the business layer.

---

# AI Instructions

Whenever implementing or discussing Plato's business logic:

- Treat the backend as the authoritative source.
- Preserve transactional integrity.
- Never bypass business validation.
- Ensure workflows are deterministic and consistent.
- Consider concurrent users and race conditions.
- Design for multiple restaurants sharing one platform.
- Keep business rules inside the Service layer.
- Use WebSockets only for real-time notifications and state updates, not for business logic.
- Prefer extensible workflows over hardcoded behavior.
- If a business rule is ambiguous, recommend the approach that best supports long-term scalability and maintainability.



# PLATO MASTER CONTEXT
## PART 5 — DEVELOPMENT STANDARDS, CODING CONVENTIONS & AI INSTRUCTIONS

This document defines the engineering standards, coding conventions, project structure, and development philosophy for Plato.

All future code, architecture, APIs, database changes, and feature implementations should follow these standards.

The objective is to keep the codebase clean, modular, scalable, maintainable, and production-ready.

---

# General Philosophy

Every feature should be built as if Plato will eventually serve:

- Thousands of restaurants
- Millions of orders
- Multiple backend instances
- Multiple frontend instances
- Cloud deployment
- Kubernetes deployment
- Future microservices

Avoid quick fixes.

Prefer maintainability over shortcuts.

---

# Tech Stack

## Backend

- Java 21
- Spring Boot 3.x
- Spring Security 6
- Spring Data JPA
- Hibernate
- PostgreSQL
- Maven
- Lombok
- Bean Validation
- JWT
- BCrypt
- WebSockets (STOMP)

---

## Frontend

- Next.js (App Router)
- TypeScript
- TailwindCSS
- Zustand
- Axios
- React Hook Form
- SockJS + STOMP

---

# Backend Folder Structure

```
com.plato

config

security

common

exception

auth

users

restaurants

employees

tables

menu

sessions

cart

orders

payments

feedback

notifications

websocket

analytics

utils
```

Every module should contain

```
controller

service

repository

entity

dto

mapper

validator
```

---

# Layer Responsibilities

## Controller

Responsible for

- Receiving HTTP requests
- Request validation
- Calling services
- Returning responses

Controllers should NEVER contain

- Business logic
- Database queries
- Complex calculations

---

## Service

Responsible for

- Business logic
- Validation
- Transactions
- Authorization
- Communication between modules

All business rules belong here.

---

## Repository

Responsible only for

Database operations.

No business logic.

---

## Entity

Represents database tables.

Should not contain business logic.

---

## DTO

Entities should NEVER be exposed directly.

Always

```
Entity

↓

Mapper

↓

DTO

↓

JSON
```

Incoming requests should also use DTOs.

---

# Dependency Injection

Always use

Constructor Injection.

Never use field injection.

Example

Good

```
private final UserService userService;

public RestaurantController(UserService userService){
    this.userService = userService;
}
```

Bad

```
@Autowired

private UserService service;
```

---

# Transactions

Business operations should be transactional.

Example

Place Order

```
Validate

↓

Create Order

↓

Create Order Items

↓

Update Cart

↓

Publish Event

↓

Commit
```

If any step fails

↓

Rollback.

---

# Exception Handling

Use

```
@RestControllerAdvice
```

Create custom exceptions.

Examples

```
RestaurantNotFoundException

SessionExpiredException

UnauthorizedRestaurantAccessException

MenuItemUnavailableException

PaymentFailedException
```

Never throw generic RuntimeException in business code.

---

# Logging

Use SLF4J.

Log

- Login attempts
- Payments
- Order creation
- Errors
- Security events

Never log

- Passwords
- JWT
- Session tokens
- Sensitive customer information

---

# Validation

Use Bean Validation.

Examples

```
@NotBlank

@NotNull

@Positive

@Email

@Size

@Pattern
```

Validation belongs in DTOs.

---

# REST API Standards

Correct

```
GET /restaurants

POST /restaurants

PUT /restaurants/{id}

DELETE /restaurants/{id}
```

Avoid

```
/createRestaurant

/updateRestaurant
```

---

# API Versioning

Prepare for versioning.

Example

```
/api/v1/restaurants

/api/v1/orders
```

Future

```
/api/v2/
```

should not require major restructuring.

---

# API Response Structure

Every API should return a consistent response.

Success

```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {}
}
```

Error

```json
{
  "success": false,
  "message": "Menu item unavailable",
  "errors": []
}
```

Avoid inconsistent response formats.

---

# Naming Conventions

Classes

```
RestaurantService

OrderController

PaymentRepository
```

Methods

```
createRestaurant()

findRestaurantById()

placeOrder()

updateOrderStatus()
```

Variables

camelCase

```
restaurantId

customerSession

menuItem
```

Constants

UPPER_CASE

```
MAX_CART_ITEMS

DEFAULT_SESSION_TIMEOUT
```

---

# Database Standards

Use UUID primary keys.

Use

```
NUMERIC(10,2)
```

for monetary values.

Never store

- Images
- Files

inside PostgreSQL.

Store URLs only.

Always use

Foreign Keys.

Always create indexes for

- email
- qr_token
- session_token
- order_number

---

# Security Standards

Use

JWT

↓

Spring Security

↓

BCrypt

↓

Role-based Authorization

↓

Method Security

Never store passwords in plaintext.

Never trust frontend input.

Every request must be validated.

---

# WebSocket Standards

Use WebSockets only for

- Notifications
- Order updates
- Dashboard updates
- Kitchen updates

Never perform business logic inside WebSocket handlers.

Business logic always starts with REST APIs.

---

# Frontend Responsibilities

Frontend is responsible for

- Rendering UI
- Form validation
- Calling APIs
- Managing UI state
- Showing notifications

Frontend is NOT responsible for

- Business validation
- Security
- Calculating bills
- Authorizing users
- Persisting business data

Backend remains the source of truth.

---

# State Management

Use Zustand.

Store only

- Theme
- UI state
- Filters
- Temporary selections

Do NOT permanently store

- Orders
- Payments
- Sessions
- Business data

Always synchronize with backend.

---

# Git Standards

Branch naming

```
feature/menu

feature/orders

bugfix/payment

refactor/security

hotfix/login
```

Commit messages

```
feat: add order placement

fix: resolve session timeout issue

refactor: simplify menu service

docs: update API documentation
```

Avoid vague commits like

```
updated code

changes

fixes
```

---

# Testing Strategy

Unit Tests

Service Layer

Integration Tests

Repository

API Tests

Controllers

End-to-End Tests

Customer Ordering Flow

Future

Load Testing

Security Testing

---

# Performance Guidelines

Avoid

N+1 queries.

Always paginate

Large datasets.

Use projections when needed.

Lazy load where appropriate.

Optimize joins.

Use HikariCP.

Avoid unnecessary API calls.

---

# Future Scalability

The architecture should support

- Redis
- Kafka
- RabbitMQ
- Elasticsearch
- Kubernetes
- Docker Swarm
- AWS
- Azure
- Google Cloud
- Microservices

without major code rewrites.

---

# Documentation Standards

Every API should be documented.

Use

OpenAPI / Swagger.

Document

- Request
- Response
- Errors
- Authentication
- Examples

Every module should include

README documentation.

---

# Code Quality

Follow

SOLID

DRY

KISS

Clean Code

Meaningful naming

Small methods

Single Responsibility

Dependency Injection

Composition over inheritance

Avoid code duplication.

---

# Definition of Done

A feature is considered complete only when:

✓ Backend implemented

✓ Database updated

✓ DTOs created

✓ Validation added

✓ Authorization implemented

✓ Exceptions handled

✓ Logging added

✓ Tests written

✓ API documented

✓ Frontend integrated

✓ WebSocket events added (if required)

✓ Code reviewed

---

# AI Instructions

Whenever assisting with Plato:

- Assume the project is production-bound.
- Never recommend shortcuts that compromise maintainability.
- Explain architectural trade-offs before suggesting alternatives.
- Follow Spring Boot, Next.js, and PostgreSQL best practices.
- Keep the backend authoritative for all business logic.
- Design features to be modular and reusable.
- Prioritize readability and clean architecture over clever implementations.
- Suggest scalable solutions that can support enterprise growth.
- When adding new features, ensure they fit the existing architecture without violating SOLID principles or database normalization.




# PLATO MASTER CONTEXT
## PART 6 — API SPECIFICATION, MODULE CONTRACTS & COMMUNICATION STANDARDS

This document defines how every module communicates with each other.

It serves as the contract between:

- Backend
- Frontend
- Database
- WebSocket Layer

All APIs should follow these standards.

The backend is the source of truth.

---

# API Base URL

```
/api/v1
```

Future versions

```
/api/v2
```

should coexist without breaking previous clients.

---

# Authentication

## Platform Users

Uses

- JWT Access Token
- Refresh Token
- Spring Security

Authentication Header

```
Authorization: Bearer <access_token>
```

Applies to

- Super Admin
- Owner
- Employee

---

## Customer

Customers never authenticate.

Instead, every request contains a session token.

Example

```
X-Session-Token

3f8e9d93-a2...
```

Backend validates

- Session exists
- Session active
- Session belongs to restaurant
- Session belongs to table

---

# Standard Response

Success

```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {}
}
```

Error

```json
{
  "success": false,
  "message": "Menu item unavailable",
  "errors": []
}
```

---

# Standard HTTP Status Codes

```
200 OK

201 CREATED

204 NO CONTENT

400 BAD REQUEST

401 UNAUTHORIZED

403 FORBIDDEN

404 NOT FOUND

409 CONFLICT

422 VALIDATION ERROR

500 INTERNAL SERVER ERROR
```

---

# Module Overview

```
Authentication

↓

Users

↓

Restaurants

↓

Tables

↓

Menu

↓

Customer Sessions

↓

Cart

↓

Orders

↓

Payments

↓

Feedback

↓

Notifications

↓

Analytics
```

Every module should remain independent.

---

# AUTH MODULE

Responsibilities

- Login
- Refresh Token
- Logout

Endpoints

```
POST

/auth/login
```

```
POST

/auth/refresh
```

```
POST

/auth/logout
```

---

# USER MODULE

Only Super Admin.

Endpoints

```
GET /users

GET /users/{id}

POST /users

PUT /users/{id}

DELETE /users/{id}
```

---

# RESTAURANT MODULE

Owner

```
GET /restaurants

GET /restaurants/{id}

POST /restaurants

PUT /restaurants/{id}

DELETE /restaurants/{id}
```

Restaurant Settings

```
GET

/restaurants/{id}/settings

PUT

/restaurants/{id}/settings
```

---

# TABLE MODULE

```
GET

/restaurants/{id}/tables
```

```
POST

/restaurants/{id}/tables
```

```
PUT

/tables/{id}
```

```
DELETE

/tables/{id}
```

Generate QR

```
POST

/tables/{id}/generate-qr
```

---

# MENU CATEGORY MODULE

```
GET

/restaurants/{id}/categories
```

```
POST

/restaurants/{id}/categories
```

```
PUT

/categories/{id}
```

```
DELETE

/categories/{id}
```

---

# MENU ITEM MODULE

```
GET

/restaurants/{id}/menu
```

```
GET

/menu-items/{id}
```

```
POST

/menu-items
```

```
PUT

/menu-items/{id}
```

```
DELETE

/menu-items/{id}
```

---

# CUSTOMER SESSION MODULE

Customer

QR Scan

↓

Backend

↓

Creates Session

Endpoints

```
POST

/sessions/start
```

Returns

```
Session Token

Restaurant

Table

Cart

Menu
```

---

Get Session

```
GET

/sessions/current
```

---

Close Session

```
POST

/sessions/end
```

---

# CART MODULE

Get Cart

```
GET

/cart
```

Add Item

```
POST

/cart/items
```

Update Quantity

```
PATCH

/cart/items/{id}
```

Remove Item

```
DELETE

/cart/items/{id}
```

Clear Cart

```
DELETE

/cart
```

---

# ORDER MODULE

Place Order

```
POST

/orders
```

Get Order

```
GET

/orders/{id}
```

Restaurant Orders

```
GET

/restaurants/{id}/orders
```

Update Status

```
PATCH

/orders/{id}/status
```

---

# PAYMENT MODULE

Generate Bill

```
GET

/sessions/{id}/bill
```

Create Payment

```
POST

/payments
```

Payment Status

```
GET

/payments/{id}
```

---

# FEEDBACK MODULE

Submit Feedback

```
POST

/feedback
```

Restaurant Feedback

```
GET

/restaurants/{id}/feedback
```

---

# EMPLOYEE MODULE

```
GET

/restaurants/{id}/employees
```

```
POST

/restaurants/{id}/employees
```

```
PUT

/employees/{id}
```

```
DELETE

/employees/{id}
```

---

# ANALYTICS MODULE

Dashboard

```
GET

/restaurants/{id}/dashboard
```

Revenue

```
GET

/restaurants/{id}/analytics/revenue
```

Orders

```
GET

/restaurants/{id}/analytics/orders
```

Popular Items

```
GET

/restaurants/{id}/analytics/menu
```

---

# Dashboard Data

Owner Dashboard

Should display

- Active Sessions
- Active Tables
- Current Orders
- Revenue Today
- Revenue This Month
- Popular Menu Items
- Employees Online
- Kitchen Load

---

# WebSocket Communication

Transport

```
STOMP

over

WebSocket
```

Endpoint

```
/ws
```

---

# Topics

Restaurant Updates

```
/topic/restaurants/{restaurantId}
```

Orders

```
/topic/orders/{restaurantId}
```

Kitchen

```
/topic/kitchen/{restaurantId}
```

Table

```
/topic/tables/{tableId}
```

Customer Session

```
/topic/session/{sessionId}
```

Notifications

```
/topic/notifications/{userId}
```

---

# Events

New Order

```
ORDER_CREATED
```

Order Confirmed

```
ORDER_CONFIRMED
```

Preparing

```
ORDER_PREPARING
```

Ready

```
ORDER_READY
```

Served

```
ORDER_SERVED
```

Payment Completed

```
PAYMENT_COMPLETED
```

Session Closed

```
SESSION_CLOSED
```

Restaurant Suspended

```
RESTAURANT_SUSPENDED
```

---

# DTO Naming

Request

```
CreateRestaurantRequest

CreateMenuItemRequest

PlaceOrderRequest
```

Response

```
RestaurantResponse

MenuItemResponse

OrderResponse
```

Avoid exposing Entity classes.

---

# Mapper Naming

```
RestaurantMapper

OrderMapper

MenuMapper

PaymentMapper
```

---

# Service Naming

```
RestaurantService

OrderService

PaymentService

SessionService
```

Interfaces

```
RestaurantService

OrderService
```

Implementation

```
RestaurantServiceImpl

OrderServiceImpl
```

---

# Repository Naming

```
RestaurantRepository

OrderRepository

MenuRepository
```

---

# Validation Rules

Restaurant

```
Name required

Phone valid

Email valid
```

Menu Item

```
Name required

Price > 0

Category exists
```

Order

```
Cart not empty

Session active

Items available
```

Payment

```
Session unpaid

Amount valid
```

Feedback

```
Rating between 1 and 5

Only one feedback per session
```

---

# Security Rules

Owner

↓

Can access only owned restaurants.

Employee

↓

Can access only assigned restaurant.

Customer

↓

Can access only active session.

Super Admin

↓

Can access everything.

Every request must verify ownership before performing business logic.

---

# Module Communication

```
Customer

↓

Cart Service

↓

Order Service

↓

Payment Service

↓

Feedback Service
```

Restaurant Management

```
Restaurant

↓

Tables

↓

Menu

↓

Employees
```

Authentication

```
Login

↓

JWT

↓

Authorization

↓

Business Module
```

---

# AI Coding Guidelines

Whenever generating code:

- Generate production-ready code.
- Follow Spring Boot best practices.
- Use constructor injection.
- Use DTOs.
- Use JPA repositories.
- Use UUIDs.
- Use transactions where required.
- Add validation.
- Add exception handling.
- Add logging.
- Never expose entities directly.
- Keep controllers thin.
- Keep services responsible for business logic.
- Design for future scalability.

---

# Project Vision

Plato should be developed as if it were a commercial SaaS product competing with platforms such as Toast, Square POS, Petpooja, and GloriaFood.

Every design decision should prioritize:

- Scalability
- Maintainability
- Security
- Performance
- Clean Architecture
- SOLID Principles
- Production readiness

The system should be capable of evolving into a complete restaurant operating platform without requiring major architectural redesign.