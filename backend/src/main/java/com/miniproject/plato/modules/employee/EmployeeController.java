package com.miniproject.plato.modules.employee;

import com.miniproject.plato.common.ApiResponse;
import com.miniproject.plato.modules.employee.dto.AssignEmployeeRequest;
import com.miniproject.plato.modules.employee.dto.EmployeeResponse;
import com.miniproject.plato.modules.employee.dto.UpdateEmployeeRoleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/employees")
@RequiredArgsConstructor
@Slf4j
public class EmployeeController {

    private final EmployeeService employeeService;

    // ── Private helpers ───────────────────────────────────────────────────────

    private UUID getCurrentUserId() {
        String principal = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return UUID.fromString(principal);
    }

    private String getCurrentRole() {
        return SecurityContextHolder.getContext()
                .getAuthentication()
                .getAuthorities()
                .iterator().next()
                .getAuthority()
                .replace("ROLE_", "");
    }

    // ── 1. Assign employee ────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmployeeResponse> assignEmployee(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody AssignEmployeeRequest request) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Employee assigned successfully",
                employeeService.assignEmployee(restaurantId, request, ownerId));
    }

    // ── 2. List active employees ──────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public ApiResponse<List<EmployeeResponse>> getEmployees(
            @PathVariable UUID restaurantId) {
        UUID callerId = getCurrentUserId();
        String role = getCurrentRole();
        return ApiResponse.ok("Employees fetched successfully",
                employeeService.getEmployees(restaurantId, callerId, role));
    }

    // ── 3. Update employee role ───────────────────────────────────────────────
    @PatchMapping("/{employeeId}/role")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<EmployeeResponse> updateEmployeeRole(
            @PathVariable UUID restaurantId,
            @PathVariable UUID employeeId,
            @Valid @RequestBody UpdateEmployeeRoleRequest request) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Employee role updated successfully",
                employeeService.updateEmployeeRole(restaurantId, employeeId, request, ownerId));
    }

    // ── 4. Deactivate employee ────────────────────────────────────────────────
    @DeleteMapping("/{employeeId}")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateEmployee(
            @PathVariable UUID restaurantId,
            @PathVariable UUID employeeId) {
        UUID ownerId = getCurrentUserId();
        employeeService.deactivateEmployee(restaurantId, employeeId, ownerId);
    }
}
