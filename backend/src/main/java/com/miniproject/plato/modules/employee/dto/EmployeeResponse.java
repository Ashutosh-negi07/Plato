package com.miniproject.plato.modules.employee.dto;

import com.miniproject.plato.modules.employee.EmployeeRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
UUID userId,
        UUID restaurantId,
        EmployeeRole role,
        boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
