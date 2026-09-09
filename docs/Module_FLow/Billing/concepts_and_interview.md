# Billing & Payments Module — Concepts, Annotations & Interview Prep

---

## File-by-File Breakdown

---

### Payment.java — The Entity

**What it is**: A JPA entity mapping to `payments`. Tracks the final financial settlement for a customer's dining session.

**Key design decisions**:

```java
@Column(name = "session_id", nullable = false)
private UUID sessionId;

@Column(name = "restaurant_id", nullable = false)
private UUID restaurantId;
// Direct UUIDs to preserve isolation and avoid eager fetching.

@Column(nullable = false, precision = 10, scale = 2)
private BigDecimal subtotal;

@Column(nullable = false, precision = 10, scale = 2)
private BigDecimal tax;

@Column(name = "service_charge", nullable = false, precision = 10, scale = 2)
private BigDecimal serviceCharge;

@Column(nullable = false, precision = 10, scale = 2)
private BigDecimal amount;
// Full financial breakdown captured at time of settlement.

@Enumerated(EnumType.STRING)
@Column(name = "payment_method", nullable = false, columnDefinition = "payment_method")
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
private PaymentMethod paymentMethod;

@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false, columnDefinition = "payment_status")
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
private PaymentStatus status = PaymentStatus.PENDING;

@Column(name = "transaction_reference")
private String transactionReference;

@Column(name = "paid_at")
private LocalDateTime paidAt;
```

---

### V9__create_payments.sql — Flyway Migration

```sql
CREATE TABLE payments (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id              UUID            NOT NULL REFERENCES customer_sessions(id),
    restaurant_id           UUID            NOT NULL REFERENCES restaurants(id),
    subtotal                NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    tax                     NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    service_charge          NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    discount                NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    amount                  NUMERIC(10,2)   NOT NULL DEFAULT 0.00,
    payment_method          payment_method  NOT NULL,
    status                  payment_status  NOT NULL DEFAULT 'PENDING',
    transaction_reference   VARCHAR(255),
    paid_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_session_id     ON payments(session_id);
CREATE INDEX idx_payments_restaurant_id  ON payments(restaurant_id);
CREATE INDEX idx_payments_status         ON payments(status);
```

---

### BillingServiceImpl.java — Core Logic

#### 1. Dynamic Order Aggregation
```java
List<OrderResponse> allOrders = orderService.getSessionOrders(sessionToken);
List<OrderResponse> activeOrders = allOrders.stream()
        .filter(o -> o.status() != OrderStatus.CANCELLED)
        .toList();

for (OrderResponse order : activeOrders) {
    ordersSubtotal = ordersSubtotal.add(order.subtotal());
    taxTotal = taxTotal.add(order.tax());
}
```
- Rather than duplicating line items into a redundant "invoice items" table, Plato aggregates live orders placed under this session.
- Excludes cancelled orders so customers are never billed for rejected food.

#### 2. Service Charge Application
```java
BigDecimal serviceChargeAmount = ordersSubtotal.multiply(serviceChargePercentage)
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
```
- Calculated against the net orders subtotal using standard commercial financial rounding (`RoundingMode.HALF_UP`).

#### 3. Automated Table Release & Session Termination
```java
payment.setStatus(PaymentStatus.COMPLETED);
payment.setPaidAt(LocalDateTime.now());
paymentRepository.save(payment);

// Closes session and resets table to AVAILABLE in one unified transaction
sessionService.closeSession(payment.getSessionId(), callerId, callerRole);
```
- Once staff confirms payment, the table state machine transitions `OCCUPIED` $\rightarrow$ `AVAILABLE` without requiring cashiers to navigate to a separate floor map screen.

---

## Architectural & Interview Deep Dives

### Q1: Why do we aggregate orders dynamically rather than copying line items to a bill table?
**Answer**:
> "In restaurant dining, orders are placed incrementally across a meal (appetizers, drinks, mains, desserts). 
> Each order already maintains its own immutable price snapshot (`unit_price`) and tax breakdown.
> Duplicating these items into an `invoice_items` table would introduce data synchronization risks and unnecessary storage overhead. 
> Storing the aggregated financial totals (`subtotal`, `tax`, `service_charge`, `amount`) on the `payments` table preserves the immutable financial record while referencing the existing orders."

---

### Q2: How do you prevent double-charging or race conditions during payment completion?
**Answer**:
> "1. In `completePayment()`, we verify that `payment.getStatus() == PaymentStatus.PENDING`. If another cashier attempts to complete the same payment simultaneously, the first transaction commits `COMPLETED` and the second is rejected with `ValidationException('This payment has already been completed')`.
> 2. For database-level safety, we can apply an optimistic lock (`@Version` on `Payment`) or a pessimistic write lock (`PESSIMISTIC_WRITE`) during settlement."

---

### Q3: How are payment methods validated against restaurant policies?
**Answer**:
> "Each restaurant configures its allowed payment methods (`allowCashPayment`, `allowCardPayment`, `allowUpi`, `allowOnlinePayment`) via `PATCH /restaurants/{id}/settings`. 
> When a guest submits a bill request (`POST /customer/billing/request-bill`), the service checks these toggles. If a diner requests `CASH` at a cashless digital venue, the request is rejected with a descriptive `400 Bad Request`."

---

### Q4: Why is table status reset during payment completion rather than order delivery?
**Answer**:
> "A table remains physically occupied until guests settle their bill and leave. If the table were marked `AVAILABLE` when the last dish was served (`SERVED`), host staff or new arriving walk-ins might be assigned to a table where diners are still seated drinking coffee or waiting for the check."
