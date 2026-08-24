package com.miniproject.plato.modules.employee.dto;

import com.miniproject.plato.modules.employee.EmployeeRole;
import jakarta.validation.constraints.NotNull;

public record UpdateEmployeeRoleRequest(
        @NotNull
        EmployeeRole role
) {
}
