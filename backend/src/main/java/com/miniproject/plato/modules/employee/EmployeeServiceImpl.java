package com.miniproject.plato.modules.employee;

import com.miniproject.plato.exception.ConflictException;
import com.miniproject.plato.exception.ResourceNotFoundException;
import com.miniproject.plato.exception.UnauthorizedAccessException;
import com.miniproject.plato.exception.ValidationException;
import com.miniproject.plato.modules.employee.dto.AssignEmployeeRequest;
import com.miniproject.plato.modules.employee.dto.EmployeeResponse;
import com.miniproject.plato.modules.employee.dto.UpdateEmployeeRoleRequest;
import com.miniproject.plato.modules.restaurant.Restaurant;
import com.miniproject.plato.modules.restaurant.RestaurantRepository;
import com.miniproject.plato.modules.user.User;
import com.miniproject.plato.modules.user.UserRepository;
import com.miniproject.plato.modules.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final EmployeeMapper employeeMapper;

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Verifies the restaurant exists AND the caller is its owner. Returns the restaurant. */
    private Restaurant verifyOwnership(UUID restaurantId, UUID ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }
        return restaurant;
    }

    /** Verifies restaurant exists and caller is either OWNER of it or SUPER_ADMIN. */
    private Restaurant verifyReadAccess(UUID restaurantId, UUID callerId, String callerRole) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        if (!"SUPER_ADMIN".equals(callerRole) && !restaurant.getOwnerId().equals(callerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }
        return restaurant;
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public EmployeeResponse assignEmployee(UUID restaurantId, AssignEmployeeRequest request, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));

        if (user.getRole() != UserRole.EMPLOYEE) {
            throw new ValidationException("Only users with platform role EMPLOYEE can be assigned to a restaurant");
        }

        if (employeeRepository.existsByUserIdAndRestaurantId(request.userId(), restaurantId)) {
            throw new ConflictException("User is already assigned to this restaurant");
        }

        Employee employee = employeeMapper.toEntity(restaurantId, request);
        return employeeMapper.toResponse(employeeRepository.save(employee));
    }

    @Override
    public List<EmployeeResponse> getEmployees(UUID restaurantId, UUID callerId, String callerRole) {
        verifyReadAccess(restaurantId, callerId, callerRole);

        return employeeRepository.findByRestaurantIdAndIsActiveTrue(restaurantId)
                .stream()
                .map(employeeMapper::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public EmployeeResponse updateEmployeeRole(UUID restaurantId, UUID employeeId, UpdateEmployeeRoleRequest request, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        Employee employee = employeeRepository.findByIdAndRestaurantId(employeeId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        employeeMapper.applyRoleUpdate(request, employee);
        return employeeMapper.toResponse(employee);
    }

    @Transactional
    @Override
    public void deactivateEmployee(UUID restaurantId, UUID employeeId, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        Employee employee = employeeRepository.findByIdAndRestaurantId(employeeId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        employee.setActive(false);
        // Hibernate dirty checking automatically updates is_active = false
    }
}
