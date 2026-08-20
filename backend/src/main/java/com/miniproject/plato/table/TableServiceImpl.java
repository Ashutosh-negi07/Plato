package com.miniproject.plato.table;


import com.miniproject.plato.exception.ConflictException;
import com.miniproject.plato.exception.ResourceNotFoundException;
import com.miniproject.plato.exception.UnauthorizedAccessException;
import com.miniproject.plato.restaurant.Restaurant;
import com.miniproject.plato.restaurant.RestaurantRepository;
import com.miniproject.plato.table.dto.CreateTableRequest;
import com.miniproject.plato.table.dto.TableResponse;
import com.miniproject.plato.table.dto.UpdateTableRequest;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.miniproject.plato.table.RestaurantTable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TableServiceImpl implements TableService{


    private final TableRepository tableRepository;
    private final TableMapper tableMapper;
    private final QrTokenService qrTokenService;
    private final RestaurantRepository restaurantRepository;


    @Override
    @Transactional
    public TableResponse createTable(UUID restaurantId, CreateTableRequest request, UUID ownerId) {
        // Verify restaurant exists and caller owns it
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }
        // Prevent duplicate table numbers within the same restaurant
        if (tableRepository.existsByRestaurantIdAndTableNumber(restaurantId, request.tableNumber())) {
            throw new ConflictException("Table number '" + request.tableNumber() + "' already exists in this restaurant");
        }
        String token = qrTokenService.generateToken();
        RestaurantTable table = tableMapper.toEntity(restaurantId, request, token);
        return tableMapper.toResponse(tableRepository.save(table));
    }

    @Override
    @Transactional
    public TableResponse updateTable(UUID restaurantId, UUID tableId, UpdateTableRequest request, UUID ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));

// ADD THIS — prevent cross-restaurant table access
        if (!table.getRestaurantId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table", tableId); // treat as not found, not 403
        }

        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));
        if (!table.getRestaurantId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table", tableId);
        }
        tableMapper.applyUpdate(request, table);
        return tableMapper.toResponse(table); // dirty checking saves automatically
    }

    @Override
    @Transactional
    public void deleteTable(UUID restaurantId, UUID tableId, UUID ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));

// ADD THIS — prevent cross-restaurant table access
        if (!table.getRestaurantId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table", tableId); // treat as not found, not 403
        }


        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));
        if (!table.getRestaurantId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table", tableId);
        }
        tableRepository.delete(table);
    }

    @Override
    @Transactional
    public TableResponse regenerateQrToken(UUID restaurantId, UUID tableId, UUID ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));
        if (!table.getRestaurantId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table", tableId);
        }
        table.setQrToken(qrTokenService.generateToken());
        return tableMapper.toResponse(table); // dirty checking saves automatically
    }


    @Override
    public List<TableResponse> getTablesByRestaurant(UUID restaurantId, UUID callerId, String callerRole) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));

        if (!"SUPER_ADMIN".equals(callerRole) && !restaurant.getOwnerId().equals(callerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }

        return tableRepository.findByRestaurantId(restaurantId)
                .stream()
                .map(tableMapper::toResponse)
                .toList();
    }



    @Override
    public TableResponse getTableById(UUID restaurantId, UUID tableId, UUID callerId, String callerRole) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));

        if (!"SUPER_ADMIN".equals(callerRole) && !restaurant.getOwnerId().equals(callerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }

        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));

        return tableMapper.toResponse(table);
    }

}
