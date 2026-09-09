package com.miniproject.plato.modules.order;

import com.miniproject.plato.exception.ResourceNotFoundException;
import com.miniproject.plato.exception.UnauthorizedAccessException;
import com.miniproject.plato.exception.ValidationException;
import com.miniproject.plato.modules.employee.EmployeeRepository;
import com.miniproject.plato.modules.menu.MenuItem;
import com.miniproject.plato.modules.menu.MenuItemRepository;
import com.miniproject.plato.modules.order.dto.OrderItemRequest;
import com.miniproject.plato.modules.order.dto.OrderResponse;
import com.miniproject.plato.modules.order.dto.PlaceOrderRequest;
import com.miniproject.plato.modules.restaurant.Restaurant;
import com.miniproject.plato.modules.restaurant.RestaurantRepository;
import com.miniproject.plato.modules.restaurant.RestaurantStatus;
import com.miniproject.plato.modules.session.CustomerSession;
import com.miniproject.plato.modules.session.CustomerSessionService;
import com.miniproject.plato.modules.table.RestaurantTable;
import com.miniproject.plato.modules.table.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerSessionService sessionService;
    private final RestaurantRepository restaurantRepository;
    private final TableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final EmployeeRepository employeeRepository;
    private final OrderMapper orderMapper;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── Helper methods ────────────────────────────────────────────────────────

    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String orderNumber;
        do {
            int randomSuffix = SECURE_RANDOM.nextInt(9000) + 1000; // 1000 - 9999
            orderNumber = "ORD-" + datePart + "-" + randomSuffix;
        } while (orderRepository.existsByOrderNumber(orderNumber));
        return orderNumber;
    }

    private Map<UUID, String> fetchMenuItemNames(List<Order> orders) {
        Set<UUID> menuItemIds = orders.stream()
                .filter(o -> o.getItems() != null)
                .flatMap(o -> o.getItems().stream())
                .map(OrderItem::getMenuItemId)
                .collect(Collectors.toSet());

        if (menuItemIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return menuItemRepository.findAllById(menuItemIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, MenuItem::getName));
    }

    private void verifyStaffAccess(UUID restaurantId, UUID callerId, String callerRole) {
        if ("SUPER_ADMIN".equals(callerRole)) {
            return;
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));

        if ("OWNER".equals(callerRole) && restaurant.getOwnerId().equals(callerId)) {
            return;
        }

        if ("EMPLOYEE".equals(callerRole) && employeeRepository.existsByUserIdAndRestaurantId(callerId, restaurantId)) {
            return;
        }

        throw new UnauthorizedAccessException("You do not have permission to access orders for this restaurant");
    }

    // ── Customer Operations ───────────────────────────────────────────────────

    @Override
    @Transactional
    public OrderResponse placeOrder(String sessionToken, PlaceOrderRequest request) {
        // 1. Validate session & slide inactivity window
        CustomerSession session = sessionService.validateAndRefreshSession(sessionToken);

        // 2. Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(session.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", session.getRestaurantId()));

        if (restaurant.getStatus() != RestaurantStatus.ACTIVE) {
            throw new ValidationException("This restaurant is not currently active");
        }
        if (Boolean.FALSE.equals(restaurant.getAcceptingOrders())) {
            throw new ValidationException("This restaurant is currently not accepting orders");
        }

        // 3. Resolve table
        RestaurantTable table = tableRepository.findById(session.getTableId()).orElse(null);
        String tableNumber = (table != null) ? table.getTableNumber() : null;

        // 4. Validate menu items and build line items with price snapshots
        List<UUID> requestedItemIds = request.items().stream()
                .map(OrderItemRequest::menuItemId)
                .toList();

        Map<UUID, MenuItem> menuItemMap = menuItemRepository.findAllById(requestedItemIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

        List<OrderItem> orderItems = new ArrayList<>();
        Map<UUID, String> menuItemNames = new HashMap<>();

        for (OrderItemRequest itemReq : request.items()) {
            MenuItem menuItem = menuItemMap.get(itemReq.menuItemId());
            if (menuItem == null) {
                throw new ResourceNotFoundException("MenuItem", itemReq.menuItemId());
            }

            if (!menuItem.getRestaurantId().equals(restaurant.getId())) {
                throw new ValidationException("Menu item '" + menuItem.getName() + "' does not belong to this restaurant");
            }

            if (!menuItem.isAvailable()) {
                throw new ValidationException("Menu item '" + menuItem.getName() + "' is currently unavailable");
            }

            menuItemNames.put(menuItem.getId(), menuItem.getName());

            OrderItem orderItem = OrderItem.builder()
                    .menuItemId(menuItem.getId())
                    .quantity(itemReq.quantity())
                    .unitPrice(menuItem.getPrice()) // price snapshot
                    .specialRequest(itemReq.specialRequest())
                    .status(OrderItemStatus.PENDING)
                    .build();

            orderItems.add(orderItem);
        }

        // 5. Determine initial order status
        OrderStatus initialStatus = Boolean.TRUE.equals(restaurant.getAutoAcceptOrders())
                ? OrderStatus.ACCEPTED
                : OrderStatus.PENDING;

        // 6. Build and save Order entity
        Order order = Order.builder()
                .restaurantId(restaurant.getId())
                .tableId(session.getTableId())
                .sessionId(session.getId())
                .orderNumber(generateOrderNumber())
                .status(initialStatus)
                .notes(request.notes())
                .placedAt(LocalDateTime.now())
                .build();

        for (OrderItem item : orderItems) {
            order.addItem(item);
        }

        order.recalculateTotals(restaurant.getTaxPercentage());

        Order savedOrder = orderRepository.save(order);
        log.info("Order placed: orderNumber={}, sessionId={}, status={}, grandTotal={}",
                savedOrder.getOrderNumber(), session.getId(), savedOrder.getStatus(), savedOrder.getGrandTotal());

        return orderMapper.toResponse(savedOrder, tableNumber, menuItemNames);
    }

    @Override
    public List<OrderResponse> getSessionOrders(String sessionToken) {
        CustomerSession session = sessionService.validateAndRefreshSession(sessionToken);

        RestaurantTable table = tableRepository.findById(session.getTableId()).orElse(null);
        String tableNumber = (table != null) ? table.getTableNumber() : null;

        List<Order> orders = orderRepository.findBySessionIdOrderByPlacedAtDesc(session.getId());
        Map<UUID, String> menuItemNames = fetchMenuItemNames(orders);

        return orders.stream()
                .map(order -> orderMapper.toResponse(order, tableNumber, menuItemNames))
                .toList();
    }

    @Override
    public OrderResponse getSessionOrderById(String sessionToken, UUID orderId) {
        CustomerSession session = sessionService.validateAndRefreshSession(sessionToken);

        Order order = orderRepository.findByIdAndSessionId(orderId, session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        RestaurantTable table = tableRepository.findById(order.getTableId()).orElse(null);
        String tableNumber = (table != null) ? table.getTableNumber() : null;
        Map<UUID, String> menuItemNames = fetchMenuItemNames(List.of(order));

        return orderMapper.toResponse(order, tableNumber, menuItemNames);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(String sessionToken, UUID orderId) {
        CustomerSession session = sessionService.validateAndRefreshSession(sessionToken);

        Order order = orderRepository.findByIdAndSessionId(orderId, session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ValidationException("Order cannot be cancelled because it is already " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        for (OrderItem item : order.getItems()) {
            item.setStatus(OrderItemStatus.CANCELLED);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order {} cancelled by customer", savedOrder.getOrderNumber());

        RestaurantTable table = tableRepository.findById(order.getTableId()).orElse(null);
        String tableNumber = (table != null) ? table.getTableNumber() : null;
        Map<UUID, String> menuItemNames = fetchMenuItemNames(List.of(savedOrder));

        return orderMapper.toResponse(savedOrder, tableNumber, menuItemNames);
    }

    @Override
    @Transactional
    public OrderResponse removeOrderItem(String sessionToken, UUID orderId, UUID orderItemId) {
        CustomerSession session = sessionService.validateAndRefreshSession(sessionToken);

        Order order = orderRepository.findByIdAndSessionId(orderId, session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ValidationException("Items can only be removed while the order is pending confirmation");
        }

        OrderItem targetItem = order.getItems().stream()
                .filter(item -> item.getId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", orderItemId));

        order.removeItem(targetItem);

        Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", order.getRestaurantId()));

        if (order.getItems().isEmpty()) {
            order.setStatus(OrderStatus.CANCELLED);
        } else {
            order.recalculateTotals(restaurant.getTaxPercentage());
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Item {} removed from order {}. Updated total={}", orderItemId, savedOrder.getOrderNumber(), savedOrder.getGrandTotal());

        RestaurantTable table = tableRepository.findById(order.getTableId()).orElse(null);
        String tableNumber = (table != null) ? table.getTableNumber() : null;
        Map<UUID, String> menuItemNames = fetchMenuItemNames(List.of(savedOrder));

        return orderMapper.toResponse(savedOrder, tableNumber, menuItemNames);
    }

    // ── Staff Operations ──────────────────────────────────────────────────────

    @Override
    public List<OrderResponse> getRestaurantOrders(UUID restaurantId, OrderStatus statusFilter, UUID callerId, String callerRole) {
        verifyStaffAccess(restaurantId, callerId, callerRole);

        List<Order> orders = (statusFilter != null)
                ? orderRepository.findByRestaurantIdAndStatusOrderByPlacedAtAsc(restaurantId, statusFilter)
                : orderRepository.findByRestaurantIdOrderByPlacedAtDesc(restaurantId);

        Map<UUID, String> menuItemNames = fetchMenuItemNames(orders);

        Set<UUID> tableIds = orders.stream().map(Order::getTableId).collect(Collectors.toSet());
        Map<UUID, String> tableNumberMap = tableRepository.findAllById(tableIds).stream()
                .collect(Collectors.toMap(RestaurantTable::getId, RestaurantTable::getTableNumber));

        return orders.stream()
                .map(order -> orderMapper.toResponse(order, tableNumberMap.get(order.getTableId()), menuItemNames))
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus, UUID callerId, String callerRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        verifyStaffAccess(order.getRestaurantId(), callerId, callerRole);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ValidationException("Cannot update status of a cancelled order");
        }
        if (order.getStatus() == OrderStatus.SERVED && newStatus != OrderStatus.SERVED) {
            throw new ValidationException("Cannot modify an order that has already been served");
        }

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.SERVED) {
            order.setCompletedAt(LocalDateTime.now());
            for (OrderItem item : order.getItems()) {
                if (item.getStatus() != OrderItemStatus.CANCELLED) {
                    item.setStatus(OrderItemStatus.SERVED);
                }
            }
        } else if (newStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                item.setStatus(OrderItemStatus.CANCELLED);
            }
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Staff updated order {} status to {}", savedOrder.getOrderNumber(), newStatus);

        RestaurantTable table = tableRepository.findById(order.getTableId()).orElse(null);
        String tableNumber = (table != null) ? table.getTableNumber() : null;
        Map<UUID, String> menuItemNames = fetchMenuItemNames(List.of(savedOrder));

        return orderMapper.toResponse(savedOrder, tableNumber, menuItemNames);
    }
}
