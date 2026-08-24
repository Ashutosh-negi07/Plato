package com.miniproject.plato.modules.employee;


import com.miniproject.plato.modules.employee.dto.AssignEmployeeRequest;
import com.miniproject.plato.modules.employee.dto.EmployeeResponse;
import com.miniproject.plato.modules.employee.dto.UpdateEmployeeRoleRequest;

import java.util.List;
import java.util.UUID;

public interface EmployeeService {

    EmployeeResponse assignEmployee(UUID restaurantId, AssignEmployeeRequest request, UUID ownerId);
    List<EmployeeResponse> getEmployees(UUID restaurantId, UUID callerId, String callerRole);
    EmployeeResponse updateEmployeeRole(UUID restaurantId, UUID employeeId, UpdateEmployeeRoleRequest request, UUID ownerId);
    void deactivateEmployee(UUID restaurantId, UUID employeeId, UUID ownerId);
}
