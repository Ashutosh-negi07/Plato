# Order Module — Concepts, Annotations & Interview Prep

---

## File-by-File Breakdown

---

### Order.java — The Aggregate Root Entity

**What it is**: A JPA entity mapping to `orders`. Serves as the domain aggregate root for a customer order containing line items.

**Key design decisions**:

```java
@Column(name = "restaurant_id", nullable = false)
private UUID restaurantId;

@Column(name = "table_id", nullable = false)
private UUID tableId;

@Column(name = "session_id", nullable = false)
private UUID sessionId;
// Direct foreign keys to avoid loading entire Restaurant, Table, or CustomerSession objects.

@Column(name = "order_number", nullable = false, unique = true, length = 32)
private String orderNumber;
// Human-readable reference (e.g., ORD-20260909-1234) used by kitchen staff and customer receipts.

@Enumerated(EnumType.STRING)
@Column(nullable = false, columnDefinition = "order_status")
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Builder.Default
private OrderStatus status = OrderStatus.PENDING;
// Hibernate 6 custom enum binding to PostgreSQL's named enum 'order_status'.

@Column(nullable = false, precision = 10, scale = 2)
private BigDecimal subtotal;

@Column(nullable = false, precision = 10, scale = 2)
private BigDecimal tax;

@Column(name = "grand_total", nullable = false, precision = 10, scale = 2)
private BigDecimal grandTotal;
// Accurate monetary calculations using BigDecimal.

@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
@Builder.Default
private List<OrderItem> items = new ArrayList<>();
// Bidirectional parent-child relationship.
// CascadeType.ALL ensures persisting the Order automatically persists its line items.
// orphanRemoval = true ensures removing an item from the list deletes the record from DB.
```

#### Domain Helper Methods:
```java
public void addItem(OrderItem item) {
    items.add(item);
    item.setOrder(this);
}

public void removeItem(OrderItem item) {
    items.remove(item);
    item.setOrder(null);
}

public void recalculateTotals(BigDecimal taxPercentage) {
    BigDecimal calculatedSubtotal = BigDecimal.ZERO;
    for (OrderItem item : items) {
        if (item.getStatus() != OrderItemStatus.CANCELLED) {
            calculatedSubtotal = calculatedSubtotal.add(item.getSubtotal());
        }
    }
    this.subtotal = calculatedSubtotal.setScale(2, RoundingMode.HALF_UP);
    BigDecimal effectiveTaxPercentage = (taxPercentage != null) ? taxPercentage : BigDecimal.ZERO;
    this.tax = this.subtotal.multiply(effectiveTaxPercentage)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    this.grandTotal = this.subtotal.add(this.tax).subtract(this.discount).max(BigDecimal.ZERO);
}
```
*Encapsulates domain business logic and financial calculations directly inside the aggregate root.*

---

### OrderItem.java — The Line Item Entity

**What it is**: A JPA entity mapping to `order_items`. Represents an individual dish ordered with its quantity and customer notes.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "order_id", nullable = false)
private Order order;

@Column(name = "menu_item_id", nullable = false)
private UUID menuItemId;

@Column(nullable = false)
private Integer quantity;

@Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
private BigDecimal unitPrice;
// PRICE SNAPSHOT: Captured from menu_items.price at order placement time.
// Guarantees immutable historical order billing.

@Enumerated(EnumType.STRING)
@Column(nullable = false, columnDefinition = "order_item_status")
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Builder.Default
private OrderItemStatus status = OrderItemStatus.PENDING;
```

---

### V8__create_orders.sql — Flyway Migration

```sql
CREATE TABLE orders (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID            NOT NULL REFERENCES restaurants(id),
    table_id        UUID            NOT NULL REFERENCES restaurant_tables(id),
    session_id      UUID            NOT NULL REFERENCES customer_sessions(id),
    order_number    VARCHAR(32)     NOT NULL UNIQUE,
    status          order_status    NOT NULL DEFAULT 'PENDING',
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

CREATE INDEX idx_orders_restaurant_id      ON orders(restaurant_id);
CREATE INDEX idx_orders_session_id         ON orders(session_id);
CREATE INDEX idx_orders_table_id           ON orders(table_id);
CREATE INDEX idx_orders_status             ON orders(status);
CREATE INDEX idx_orders_placed_at          ON orders(placed_at);
CREATE INDEX idx_order_items_order_id      ON order_items(order_id);
```

#### Indexing Strategy Rationale:
1. **`idx_orders_session_id`**: Fast $O(1)$ query when customer requests all orders for their active table session.
2. **`idx_orders_restaurant_id` & `status`**: Powers the kitchen display screen filtering incoming orders by status (`PENDING`, `PREPARING`).
3. **`idx_orders_placed_at`**: Kitchen orders are sorted ascending (`ORDER BY placed_at ASC`) for First-In, First-Out (FIFO) food preparation.
4. **`idx_order_items_order_id`**: Speeds up join lookups when rendering line items.

---

### OrderServiceImpl.java — Business Logic & Rules

#### 1. Price Snapshotting Mechanism
```java
OrderItem orderItem = OrderItem.builder()
        .menuItemId(menuItem.getId())
        .quantity(itemReq.quantity())
        .unitPrice(menuItem.getPrice()) // price snapshot
        .specialRequest(itemReq.specialRequest())
        .status(OrderItemStatus.PENDING)
        .build();
```
- The application queries the current price from `menu_items`.
- It writes this value directly into `order_items.unit_price`.
- Any future price hikes or promotions on `menu_items` will not retroactively alter the price on placed orders.

#### 2. Auto-Accept Logic
```java
OrderStatus initialStatus = Boolean.TRUE.equals(restaurant.getAutoAcceptOrders())
        ? OrderStatus.ACCEPTED
        : OrderStatus.PENDING;
```
- Quick-service restaurants can configure `autoAcceptOrders = true` so orders bypass the pending approval step and enter the preparation queue immediately.
- Fine-dining restaurants configure `autoAcceptOrders = false` so kitchen or waitstaff verify capacity before accepting.

#### 3. In-Flight Order Modification Safeguards
```java
if (order.getStatus() != OrderStatus.PENDING) {
    throw new ValidationException("Order cannot be cancelled because it is already " + order.getStatus());
}
```
- Once kitchen status moves to `ACCEPTED` or `PREPARING`, diners cannot cancel or remove items without staff intervention, preventing food waste.

---

## Architectural & Interview Deep Dives

### Q1: Why do we snapshot `unit_price` in `order_items` rather than joining `menu_items`?
**Answer**:
> "If we did not snapshot `unit_price` and instead calculated totals via `JOIN menu_items`, any time a restaurant owner changed a dish's price (e.g. Biryani from ₹300 to ₹350), all historical receipts, completed orders, and accounting reports from past weeks would retroactively change to ₹350. 
> Storing `unit_price` on `order_items` provides an immutable audit trail and guarantees financial integrity."

---

### Q2: Why use `BigDecimal` instead of `double` or `float` for currency and tax?
**Answer**:
> "Binary floating-point numbers (`double`/`float` based on IEEE 754) represent fractions in powers of 2. Decimal numbers like `0.1` or `0.05` cannot be represented precisely in binary floating-point, causing rounding errors like `0.1 + 0.2 = 0.30000000000000004`.
> In financial calculations, rounding errors accumulate into noticeable discrepancies. `BigDecimal` represents arbitrary-precision decimal numbers, guaranteeing exact arithmetic down to the exact cent or paisa."

---

### Q3: Why is a separate `carts` table not needed for our dining flow?
**Answer**:
> "In an e-commerce platform like Amazon, shoppers keep items in a cart for days or weeks across sessions.
> In a QR-code restaurant dining experience, diners browse, pick dishes, and immediately submit the order to the kitchen.
> Modeling an order with status `PENDING` directly satisfies all cart requirements:
> - Diners can add items with special requests.
> - Diners can remove items while `PENDING`.
> - Diners can cancel the entire order while `PENDING`.
> Eliminating an intermediate `cart_items` table avoids data synchronization complexity, deletes, and extra table joins."

---

### Q4: How do you prevent the N+1 query problem when retrieving orders with items and dish names?
**Answer**:
> "When fetching a list of orders, if we lazily traversed `order.getItems()` and called `menuItemRepository.findById(...)` inside a loop, it would trigger $1 + N + M$ queries.
> We solve this in `OrderServiceImpl` through two optimizations:
> 1. In `fetchMenuItemNames()`, we extract all distinct `menuItemId`s across the order list and perform a single bulk query: `menuItemRepository.findAllById(menuItemIds)`.
> 2. We construct an in-memory map `Map<UUID, String> menuItemNames` to hydrate dish names in $O(1)$ time."

---

### Q5: Why track both `OrderStatus` and `OrderItemStatus`?
**Answer**:
> "In a restaurant kitchen, different dishes in the same order are prepared at different stations (e.g., drinks at the bar, appetizers at the fryer, steaks at the grill).
> - `OrderItemStatus` lets individual chefs mark items as `PREPARING` or `READY` independently.
> - `OrderStatus` represents the aggregate order lifecycle (`PENDING` $\rightarrow$ `ACCEPTED` $\rightarrow$ `PREPARING` $\rightarrow$ `READY` $\rightarrow$ `SERVED`).
> When all items are ready, the runner brings the full order to the table and marks the order `SERVED`."
