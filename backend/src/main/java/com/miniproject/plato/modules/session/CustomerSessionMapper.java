package com.miniproject.plato.modules.session;

import com.miniproject.plato.modules.restaurant.Restaurant;
import com.miniproject.plato.modules.session.dto.CustomerSessionResponse;
import com.miniproject.plato.modules.table.RestaurantTable;
import org.springframework.stereotype.Component;

@Component
public class CustomerSessionMapper {

    public CustomerSessionResponse toResponse(CustomerSession session, Restaurant restaurant, RestaurantTable table) {
        return CustomerSessionResponse.builder()
                .id(session.getId())
                .restaurantId(session.getRestaurantId())
                .restaurantName(restaurant != null ? restaurant.getName() : null)
                .tableId(session.getTableId())
                .tableNumber(table != null ? table.getTableNumber() : null)
                .sessionToken(session.getSessionToken())
                .status(session.getStatus())
                .guestCount(session.getGuestCount())
                .startedAt(session.getStartedAt())
                .expiresAt(session.getExpiresAt())
                .build();
    }
}
