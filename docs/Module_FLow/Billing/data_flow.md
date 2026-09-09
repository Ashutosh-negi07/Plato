# Billing & Payments Module — Complete Data Flow

> Every HTTP request through the Billing & Payments module traced step-by-step from HTTP wire to PostgreSQL and back.

---

## Architecture Overview

```
Customer Mobile Browser (X-Session-Token)       Cashier / Staff Terminal (Bearer JWT)
              │                                                │
              │                                                │
              ▼                                                ▼
CustomerBillingController.java                       StaffBillingController.java
(/api/v1/customer/billing)                           (/api/v1/restaurants/{id}/payments)
              │                                                │
              └───────────────────────┬────────────────────────┘
                                      │
                                      ▼
                             BillingService.java
                                      │
                                      ▼
                            BillingServiceImpl.java
              ┌───────────────────────┼───────────────────────┐
              ▼                       ▼                       ▼
      PaymentRepository        OrderService           CustomerSessionService
              │                       │                       │
              ▼                       ▼                       ▼
      payments table (V9)     orders table (V8)      customer_sessions table (V7)
                                                              │
                                                              ▼
                                                     restaurant_tables (V4)
```

---

## Table Relationships

```
restaurants
     │
     ├── restaurant_tables
     │        │
     │        └── customer_sessions
     │                 │
     │                 ├── orders (multiple orders per session)
     │                 │        └── order_items
     │                 │
     │                 └── payments (session_id FK, restaurant_id FK)
```

---

## Flow 1 — GET /api/v1/customer/billing/summary (Inspect Dynamic Bill)

### Request Example
```http
GET /api/v1/customer/billing/summary
X-Session-Token: a3f8c2b984d7e10f4433221100aabbccddeeff00112233445566778899aabbcc
```

### Step-by-Step Trace
1. **Token Validation & Heartbeat**: `sessionService.validateAndRefreshSession(sessionToken)` validates that the session is active and slides the inactivity window forward by 30 minutes.
2. **Order Aggregation**:
   - Fetches all orders belonging to this dining session: `orderService.getSessionOrders(sessionToken)`.
   - Filters out `CANCELLED` orders.
   - Sums order subtotals: $\text{ordersSubtotal} = \sum \text{order.subtotal}$.
   - Sums order taxes: $\text{taxTotal} = \sum \text{order.tax}$.
   - Sums discounts: $\text{discountTotal} = \sum \text{order.discount}$.
3. **Restaurant Service Charge Calculation**:
   - Queries `Restaurant` to read `restaurant.serviceCharge` (e.g. 2.50%).
   - Computes: $\text{serviceChargeAmount} = \text{ordersSubtotal} \times (\text{serviceCharge} / 100)$.
4. **Final Grand Total**:
   - $\text{grandTotal} = \text{ordersSubtotal} + \text{taxTotal} + \text{serviceChargeAmount} - \text{discountTotal}$.
5. **Pending Food Flag**: Checks if any active order is still `PENDING` or `PREPARING` and sets `hasPendingOrders = true`.
6. **Return 200 OK**:
   ```json
   {
     "success": true,
     "message": "Bill summary retrieved",
     "data": {
       "sessionId": "session-uuid-999",
       "restaurantId": "e3b0c442-...",
       "restaurantName": "The Tuscan Table",
       "tableId": "c9a0d8e7-...",
       "tableNumber": "T-12",
       "orders": [ ... ],
       "ordersSubtotal": 900.00,
       "taxTotal": 45.00,
       "serviceChargePercentage": 2.50,
       "serviceChargeAmount": 22.50,
       "discount": 0.00,
       "grandTotal": 967.50,
       "hasPendingOrders": false
     },
     "timestamp": "2026-09-09T16:30:00"
   }
   ```

---

## Flow 2 — POST /api/v1/customer/billing/request-bill (Request Payment)

### Request Example
```http
POST /api/v1/customer/billing/request-bill
X-Session-Token: a3f8c2b984d7e10f4433221100aabbccddeeff00112233445566778899aabbcc
Content-Type: application/json

{
  "paymentMethod": "UPI",
  "notes": "Please bring the QR scanner to Table 12"
}
```

### Step-by-Step Trace
1. Validates session token.
2. Checks restaurant payment method toggles:
   - If `paymentMethod == CASH` $\rightarrow$ verifies `restaurant.allowCashPayment == true`.
   - If `paymentMethod == CARD` $\rightarrow$ verifies `restaurant.allowCardPayment == true`.
   - If `paymentMethod == UPI` $\rightarrow$ verifies `restaurant.allowUpi == true`.
   - If disabled $\rightarrow$ throws `ValidationException("This restaurant does not accept UPI payments")`.
3. Verifies that the table has at least one non-cancelled order (cannot request bill for an empty session).
4. Persists a `Payment` record in PostgreSQL with status `PENDING`:
   ```sql
   INSERT INTO payments (id, session_id, restaurant_id, subtotal, tax, service_charge, discount, amount, payment_method, status, created_at, updated_at)
   VALUES (gen_random_uuid(), ?, ?, 900.00, 45.00, 22.50, 0.00, 967.50, 'UPI', 'PENDING', now(), now());
   ```
5. Returns `200 OK` with `PaymentResponse`. Signals the cashier that Table T-12 is ready for payment.

---

## Flow 3 — POST /api/v1/restaurants/{restaurantId}/payments/{paymentId}/complete (Staff Settlement & Table Release)

### Request Example
```http
POST /api/v1/restaurants/e3b0c442-.../payments/pay-uuid-101/complete
Authorization: Bearer eyJhbGciOi... (Staff / Cashier JWT)
Content-Type: application/json

{
  "transactionReference": "UPI-REF-987654321"
}
```

### Step-by-Step Trace
1. **Security & Staff Permission**:
   - `JwtAuthenticationFilter` verifies JWT.
   - `verifyStaffAccess()` checks caller is `SUPER_ADMIN`, restaurant `OWNER`, or assigned `EMPLOYEE`.
2. **Payment State Transition**:
   - Verifies payment status is `PENDING`.
   - Updates status: `payment.setStatus(PaymentStatus.COMPLETED)`.
   - Records completion timestamp: `payment.setPaidAt(now())`.
   - Records gateway/receipt reference: `payment.setTransactionReference(...)`.
   - Persists updated `Payment` entity.
3. **Automated Dining Loop Completion**:
   - Delegates to `sessionService.closeSession(payment.getSessionId(), callerId, callerRole)`.
   - Marks `customer_sessions` as `CLOSED` and stamps `ended_at = now()`.
   - Resets physical table occupancy: `restaurant_tables.status = AVAILABLE`.
4. **Returns 200 OK**:
   ```json
   {
     "success": true,
     "message": "Payment completed and table released successfully",
     "data": {
       "id": "pay-uuid-101",
       "sessionId": "session-uuid-999",
       "restaurantId": "e3b0c442-...",
       "subtotal": 900.00,
       "tax": 45.00,
       "serviceCharge": 22.50,
       "amount": 967.50,
       "paymentMethod": "UPI",
       "status": "COMPLETED",
       "transactionReference": "UPI-REF-987654321",
       "paidAt": "2026-09-09T16:45:00"
     },
     "timestamp": "2026-09-09T16:45:00"
   }
   ```

---

## Error Handling Matrix

| Scenario | Exception | HTTP Code | Response Message |
|---|---|:---:|---|
| Missing / invalid session token | `SessionExpiredException` | `401` | `"Invalid session token"` |
| Session has expired (inactivity) | `SessionExpiredException` | `401` | `"Your session has expired. Please scan the QR code again."` |
| Request bill for session with no orders | `ValidationException` | `400` | `"Cannot request bill for a session with no active orders"` |
| Restaurant does not accept chosen method | `ValidationException` | `400` | `"This restaurant does not accept cash payments"` |
| Re-completing an already completed payment | `ValidationException` | `400` | `"This payment has already been completed"` |
| Staff not assigned to restaurant | `UnauthorizedAccessException` | `403` | `"You do not have permission to access billing for this restaurant"` |
| Payment record not found | `ResourceNotFoundException` | `404` | `"Payment not found with id: ..."` |
