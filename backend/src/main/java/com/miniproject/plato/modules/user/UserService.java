package com.miniproject.plato.modules.user;

import com.miniproject.plato.modules.user.dto.CreateUserRequest;
import com.miniproject.plato.modules.user.dto.UpdateUserRequest;
import com.miniproject.plato.modules.user.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    // ------------------------------------------------------------------
    // Internal methods — used by AuthServiceImpl and DataInitializer
    // These work with the raw User entity, NOT DTOs
    // Never call these from a Controller
    // ------------------------------------------------------------------

    User findById(UUID id);

    User findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);

    // ------------------------------------------------------------------
    // API methods — used only by UserController
    // These work with DTOs (request in, UserResponse out)
    // passwordHash never crosses this boundary
    // ------------------------------------------------------------------

    Page<UserResponse> getAllUsers(Pageable pageable);

    UserResponse getUserById(UUID id);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(UUID id, UpdateUserRequest request);

    UserResponse updateStatus(UUID id, UserStatus newStatus);

    void deleteUser(UUID id);
}
