package com.miniproject.plato.modules.table.dto;

import com.miniproject.plato.modules.table.TableStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TableResponse(

        UUID id,
        UUID restaurantId,
        String tableNumber,
        Integer capacity,
        String label,
        String qrToken,
        TableStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {}

