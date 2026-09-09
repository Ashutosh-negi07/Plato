package com.miniproject.plato.modules.order;

import com.miniproject.plato.modules.order.dto.OrderItemResponse;
import com.miniproject.plato.modules.order.dto.OrderResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order, String tableNumber, Map<UUID, String> menuItemNames) {
        List<OrderItemResponse> itemResponses = (order.getItems() != null)
                ? order.getItems().stream()
                .map(item -> toItemResponse(item, menuItemNames != null ? menuItemNames.get(item.getMenuItemId()) : null))
                .toList()
                : Collections.emptyList();

        return OrderResponse.builder()
                .id(order.getId())
                .restaurantId(order.getRestaurantId())
                .tableId(order.getTableId())
                .tableNumber(tableNumber)
                .sessionId(order.getSessionId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .tax(order.getTax())
                .discount(order.getDiscount())
                .grandTotal(order.getGrandTotal())
                .notes(order.getNotes())
                .placedAt(order.getPlacedAt())
                .completedAt(order.getCompletedAt())
                .items(itemResponses)
                .build();
    }

    public OrderItemResponse toItemResponse(OrderItem item, String menuItemName) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .menuItemId(item.getMenuItemId())
                .menuItemName(menuItemName)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .specialRequest(item.getSpecialRequest())
                .status(item.getStatus())
                .build();
    }
}
