package com.miniproject.plato.modules.employee.dto;

import com.miniproject.plato.modules.employee.EmployeeRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignEmployeeRequest(
        @NotNull
        UUID userId,

        @NotNull
        EmployeeRole role
) {
}
