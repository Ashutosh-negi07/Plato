package com.miniproject.plato.user;

import com.miniproject.plato.common.ApiResponse;
import com.miniproject.plato.exception.UnauthorizedAccessException;
import com.miniproject.plato.user.dto.CreateUserRequest;
import com.miniproject.plato.user.dto.UpdateUserRequest;
import com.miniproject.plato.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// ------------------------------------------------------------------
// UserController — HTTP layer for user management
//
// Responsibilities:
//   1. Parse HTTP request (path vars, body, pageable)
//   2. Check authorization (SUPER_ADMIN or self)
//   3. Call UserService
//   4. Wrap result in ApiResponse and return HTTP status
//
// What this class does NOT do:
//   - No business logic
//   - No DB calls
//   - No entity handling — only DTOs cross this boundary
// ------------------------------------------------------------------
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── GET ALL ───────────────────────────────────────────────────────────
    // SUPER_ADMIN only — returns paginated list of all users.
    // Query params handled automatically by Spring:
    //   ?page=0&size=20&sort=createdAt,desc

    @GetMapping
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<UserResponse> users = userService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.ok("Users fetched successfully", users));
    }

    // ── GET ONE ───────────────────────────────────────────────────────────
    // SUPER_ADMIN can fetch any user.
    // A regular user can only fetch themselves.

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable UUID id,
            Authentication authentication) {

        requireSelfOrAdmin(authentication, id);
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok("User fetched successfully", user));
    }

    // ── CREATE ────────────────────────────────────────────────────────────
    // SUPER_ADMIN only — creates a new Owner or Employee account.
    // @Valid triggers bean validation on CreateUserRequest before this method runs.
    // Returns 201 CREATED with the new user in the body.

    @PostMapping
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        UserResponse created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created successfully", created));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────
    // SUPER_ADMIN can update anyone.
    // A user can update only themselves.
    // Only fullName, email, phone — role and status are separate endpoints.

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {

        requireSelfOrAdmin(authentication, id);
        UserResponse updated = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.ok("User updated successfully", updated));
    }

    // ── UPDATE STATUS ─────────────────────────────────────────────────────
    // SUPER_ADMIN only — activates or deactivates a user account.
    // Called as: PATCH /api/v1/users/{id}/status?value=INACTIVE

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestParam UserStatus value) {

        UserResponse updated = userService.updateStatus(id, value);
        return ResponseEntity.ok(ApiResponse.ok("User status updated successfully", updated));
    }

    // ── DELETE (SOFT) ─────────────────────────────────────────────────────
    // SUPER_ADMIN only — sets status to DELETED, never removes the DB row.
    // Returns 204 No Content — no body needed for a delete response.

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // requireSelfOrAdmin — shared authorization check
    //
    // Used by GET /{id} and PATCH /{id} which allow either the
    // SUPER_ADMIN or the user themselves to perform the action.
    //
    // Why not @PreAuthorize SpEL?
    //   Spring's default UserDetails has no getId() method, so SpEL
    //   expressions can't compare principal.id to {id} directly.
    //   Doing it manually here is explicit and easy to follow.
    //
    // Flow:
    //   1. Check if caller has ROLE_SUPER_ADMIN → if yes, allow
    //   2. If not → load caller's User from DB by their email
    //      (email = username in our JWT setup)
    //   3. Compare their DB id to the requested {id}
    //   4. Mismatch → throw UnauthorizedAccessException → 403
    // ------------------------------------------------------------------
    private void requireSelfOrAdmin(Authentication authentication, UUID targetId) {
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        if (!isSuperAdmin) {
            UserDetails principal = (UserDetails) authentication.getPrincipal();
            User currentUser = userService.findByEmail(principal.getUsername());

            if (!currentUser.getId().equals(targetId)) {
                throw new UnauthorizedAccessException(
                        "You are not allowed to access another user's data");
            }
        }
    }
}
