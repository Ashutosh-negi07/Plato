package com.miniproject.plato.restaurant;

import com.miniproject.plato.common.ApiResponse;
import com.miniproject.plato.restaurant.dto.CreateRestaurantRequest;
import com.miniproject.plato.restaurant.dto.RestaurantResponse;
import com.miniproject.plato.restaurant.dto.RestaurantSettingsRequest;
import com.miniproject.plato.restaurant.dto.UpdateRestaurantRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
@Slf4j
public class RestaurantController {

    private final RestaurantService restaurantService;

    // ── 1. Create restaurant ─────────────────────────────────────────────────
    // Only OWNERs can create a restaurant.
    // ownerId is extracted from the JWT — not from the request body.
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RestaurantResponse> createRestaurant(
            @Valid @RequestBody CreateRestaurantRequest request) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Restaurant created successfully",
                restaurantService.createRestaurant(request, ownerId));
    }

    // ── 2. List restaurants ──────────────────────────────────────────────────
    // SUPER_ADMIN → all restaurants (paginated)
    // OWNER → only their own restaurants (paginated)
    // The service decides which query to run based on role.
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public ApiResponse<Page<RestaurantResponse>> getAllRestaurants(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID callerId = getCurrentUserId();
        String role = getCurrentRole(); // "SUPER_ADMIN" or "OWNER" (ROLE_ stripped)
        return ApiResponse.ok("Restaurants fetched successfully",
                restaurantService.getAllRestaurants(callerId, role, pageable));
    }

    // ── 3. Get one restaurant ────────────────────────────────────────────────
    // SUPER_ADMIN → any restaurant
    // OWNER → only their own (service throws 403 if not theirs)
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public ApiResponse<RestaurantResponse> getRestaurantById(@PathVariable UUID id) {
        UUID callerId = getCurrentUserId();
        String role = getCurrentRole();
        return ApiResponse.ok("Restaurant fetched successfully",
                restaurantService.getRestaurantById(id, callerId, role));
    }

    // ── 4. Update identity / location / operations ───────────────────────────
    // Only OWNER can update their own restaurant.
    // Service verifies ownership and throws 403 if someone else tries.
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<RestaurantResponse> updateRestaurant(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRestaurantRequest request) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Restaurant updated successfully",
                restaurantService.updateRestaurant(id, request, ownerId));
    }

    // ── 5. Change status ─────────────────────────────────────────────────────
    // SUPER_ADMIN only — used to SUSPEND or reactivate a restaurant.
    // ?value=SUSPENDED or ?value=INACTIVE or ?value=ACTIVE
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<RestaurantResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam RestaurantStatus value) {
        return ApiResponse.ok("Restaurant status updated successfully",
                restaurantService.updateStatus(id, value));
    }

    // ── 6. Get settings ──────────────────────────────────────────────────────
    // Reuses getRestaurantById — all restaurant data including settings is
    // returned in the same RestaurantResponse (no separate SettingsResponse
    // needed).
    @GetMapping("/{id}/settings")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<RestaurantResponse> getSettings(@PathVariable UUID id) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Settings fetched successfully",
                restaurantService.getRestaurantById(id, ownerId, "OWNER"));
    }

    // ── 7. Update settings ───────────────────────────────────────────────────
    // OWNER updates payment toggles, tax %, service charge, order settings.
    // Only the 8 settings fields — not name/location (those use endpoint 4).
    @PutMapping("/{id}/settings")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<RestaurantResponse> updateSettings(
            @PathVariable UUID id,
            @Valid @RequestBody RestaurantSettingsRequest request) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Settings updated successfully",
                restaurantService.updateSettings(id, request, ownerId));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    // Extracts the authenticated user's UUID from the JWT principal.
    // JwtAuthenticationFilter sets the principal as userId.toString()
    // (see JwtAuthenticationFilter line 94)
    private UUID getCurrentUserId() {
        String principal = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return UUID.fromString(principal);
    }

    // Extracts the role string without the "ROLE_" prefix.
    // JwtAuthenticationFilter builds: "ROLE_" + role (e.g. "ROLE_OWNER")
    // We strip "ROLE_" so the service receives "OWNER" or "SUPER_ADMIN"
    // which is what the service's .equals() checks compare against.
    private String getCurrentRole() {
        return SecurityContextHolder.getContext()
                .getAuthentication()
                .getAuthorities()
                .iterator().next()
                .getAuthority()
                .replace("ROLE_", ""); // "ROLE_OWNER" → "OWNER"
    }
}
