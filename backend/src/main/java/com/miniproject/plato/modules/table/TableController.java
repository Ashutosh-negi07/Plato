package com.miniproject.plato.modules.table;

import com.miniproject.plato.common.ApiResponse;
import com.miniproject.plato.security.JwtTokenProvider;
import com.miniproject.plato.modules.table.dto.CreateTableRequest;
import com.miniproject.plato.modules.table.dto.TableResponse;
import com.miniproject.plato.modules.table.dto.UpdateTableRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/tables")
@RequiredArgsConstructor
@Slf4j
public class TableController {

    private final TableService tableService;
    private final JwtTokenProvider jwtTokenProvider;

    // ── Helper methods ────────────────────────────────────────────────────────
    private UUID getCurrentUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtTokenProvider.getUserIdFromToken(token);
    }

    private String getCurrentRole(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtTokenProvider.getRoleFromToken(token).replace("ROLE_", "");
    }

    // ── 1. Create table ───────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TableResponse> createTable(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateTableRequest request,
            HttpServletRequest httpRequest) {
        UUID ownerId = getCurrentUserId(httpRequest);
        return ApiResponse.ok("Table created successfully",
                tableService.createTable(restaurantId, request, ownerId));
    }

    // ── 2. List tables ────────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public ApiResponse<List<TableResponse>> getTablesByRestaurant(
            @PathVariable UUID restaurantId,
            HttpServletRequest httpRequest) {
        UUID callerId = getCurrentUserId(httpRequest);
        String role = getCurrentRole(httpRequest);
        return ApiResponse.ok("Tables fetched successfully",
                tableService.getTablesByRestaurant(restaurantId, callerId, role));
    }

    // ── 3. Get single table ───────────────────────────────────────────────────
    @GetMapping("/{tableId}")
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public ApiResponse<TableResponse> getTableById(
            @PathVariable UUID restaurantId,
            @PathVariable UUID tableId,
            HttpServletRequest httpRequest) {
        UUID callerId = getCurrentUserId(httpRequest);
        String role = getCurrentRole(httpRequest);
        return ApiResponse.ok("Table fetched successfully",
                tableService.getTableById(restaurantId, tableId, callerId, role));
    }

    // ── 4. Update table ───────────────────────────────────────────────────────
    @PutMapping("/{tableId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<TableResponse> updateTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID tableId,
            @Valid @RequestBody UpdateTableRequest request,
            HttpServletRequest httpRequest) {
        UUID ownerId = getCurrentUserId(httpRequest);
        return ApiResponse.ok("Table updated successfully",
                tableService.updateTable(restaurantId, tableId, request, ownerId));
    }

    // ── 5. Delete table ───────────────────────────────────────────────────────
    @DeleteMapping("/{tableId}")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID tableId,
            HttpServletRequest httpRequest) {
        UUID ownerId = getCurrentUserId(httpRequest);
        tableService.deleteTable(restaurantId, tableId, ownerId);
    }

    // ── 6. Regenerate QR token ────────────────────────────────────────────────
    @PostMapping("/{tableId}/qr/regenerate")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<TableResponse> regenerateQrToken(
            @PathVariable UUID restaurantId,
            @PathVariable UUID tableId,
            HttpServletRequest httpRequest) {
        UUID ownerId = getCurrentUserId(httpRequest);
        return ApiResponse.ok("QR token regenerated successfully",
                tableService.regenerateQrToken(restaurantId, tableId, ownerId));
    }
}
