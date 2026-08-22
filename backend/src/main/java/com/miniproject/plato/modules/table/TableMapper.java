package com.miniproject.plato.table;

import com.miniproject.plato.table.dto.CreateTableRequest;
import com.miniproject.plato.table.dto.TableResponse;
import com.miniproject.plato.table.dto.UpdateTableRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
public class TableMapper {

    public RestaurantTable toEntity(UUID restaurantId, CreateTableRequest request, String qrToken) {
        return RestaurantTable.builder()
                .restaurantId(restaurantId)
                .tableNumber(request.tableNumber())
                .capacity(request.capacity())
                .label(request.label())
                .qrToken(qrToken)
                .build();
    }

    public TableResponse toResponse(RestaurantTable table) {
        return new TableResponse(
                table.getId(),
                table.getRestaurantId(),
                table.getTableNumber(),
                table.getCapacity(),
                table.getLabel(),
                table.getQrToken(),
                table.getStatus(),
                table.getCreatedAt(),
                table.getUpdatedAt()
        );
    }

    public void applyUpdate(UpdateTableRequest request, RestaurantTable table) {
        if (request.tableNumber() != null) table.setTableNumber(request.tableNumber());
        if (request.capacity() != null) table.setCapacity(request.capacity());
        if (request.label() != null) table.setLabel(request.label());
    }
}


