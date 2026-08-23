package com.miniproject.plato.modules.employee;


import com.miniproject.plato.modules.employee.dto.AssignEmployeeRequest;
import com.miniproject.plato.modules.employee.dto.EmployeeResponse;
import com.miniproject.plato.modules.employee.dto.UpdateEmployeeRoleRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EmployeeMapper {

    public Employee toEntity(UUID restaurantId, AssignEmployeeRequest request){
        return Employee.builder()
                .restaurantId(restaurantId)
                .role(request.role())
                .userId(request.userId())
                .build();

    }

    public EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),          // ← also missing id!
                employee.getUserId(),
                employee.getRestaurantId(),
                employee.getRole(),
                employee.isActive(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }

    public void applyRoleUpdate(UpdateEmployeeRoleRequest request, Employee employee) {
        employee.setRole(request.role());
    }
}
