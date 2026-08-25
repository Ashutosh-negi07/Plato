package com.miniproject.plato.modules.table;

import com.miniproject.plato.exception.ConflictException;
import com.miniproject.plato.exception.ResourceNotFoundException;
import com.miniproject.plato.exception.UnauthorizedAccessException;
import com.miniproject.plato.modules.restaurant.Restaurant;
import com.miniproject.plato.modules.restaurant.RestaurantRepository;
import com.miniproject.plato.modules.table.dto.CreateTableRequest;
import com.miniproject.plato.modules.table.dto.TableResponse;
import com.miniproject.plato.modules.table.dto.UpdateTableRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TableServiceImpl implements TableService {

    private final TableRepository tableRepository;
    private final TableMapper tableMapper;
    private final QrTokenService qrTokenService;
    private final RestaurantRepository restaurantRepository;

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

    @Override
    @Transactional
    public TableResponse createTable(UUID restaurantId, CreateTableRequest request, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        // Prevent duplicate table numbers within the same restaurant
        if (tableRepository.existsByRestaurantIdAndTableNumber(restaurantId, request.tableNumber())) {
            throw new ConflictException(
                    "Table number '" + request.tableNumber() + "' already exists in this restaurant");
        }
        String token = qrTokenService.generateToken();
        RestaurantTable table = tableMapper.toEntity(restaurantId, request, token);
        return tableMapper.toResponse(tableRepository.save(table));
    }

    @Override
    @Transactional
    public TableResponse updateTable(UUID restaurantId, UUID tableId, UpdateTableRequest request, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        RestaurantTable table = tableRepository.findByIdAndRestaurantId(tableId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));

        tableMapper.applyUpdate(request, table);
        return tableMapper.toResponse(table); // dirty checking saves automatically
    }

    @Override
    @Transactional
    public void deleteTable(UUID restaurantId, UUID tableId, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        RestaurantTable table = tableRepository.findByIdAndRestaurantId(tableId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));

        tableRepository.delete(table);
    }

    @Override
    @Transactional
    public TableResponse regenerateQrToken(UUID restaurantId, UUID tableId, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        RestaurantTable table = tableRepository.findByIdAndRestaurantId(tableId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));

        table.setQrToken(qrTokenService.generateToken());
        return tableMapper.toResponse(table); // dirty checking saves automatically
    }

    @Override
    public List<TableResponse> getTablesByRestaurant(UUID restaurantId, UUID callerId, String callerRole) {
        verifyReadAccess(restaurantId, callerId, callerRole);

        return tableRepository.findByRestaurantId(restaurantId)
                .stream()
                .map(tableMapper::toResponse)
                .toList();
    }

    @Override
    public TableResponse getTableById(UUID restaurantId, UUID tableId, UUID callerId, String callerRole) {
        verifyReadAccess(restaurantId, callerId, callerRole);

        return tableRepository.findByIdAndRestaurantId(tableId, restaurantId)
                .map(tableMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));
    }
}
