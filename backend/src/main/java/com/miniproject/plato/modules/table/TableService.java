package com.miniproject.plato.table;

import com.miniproject.plato.table.dto.CreateTableRequest;
import com.miniproject.plato.table.dto.TableResponse;
import com.miniproject.plato.table.dto.UpdateTableRequest;
import java.util.List;
import java.util.UUID;


public interface TableService {
    TableResponse createTable(UUID restaurantId, CreateTableRequest request, UUID ownerId);
    List<TableResponse> getTablesByRestaurant(UUID restaurantId, UUID callerId, String callerRole);
    TableResponse getTableById(UUID restaurantId, UUID tableId, UUID callerId, String callerRole);
    TableResponse updateTable(UUID restaurantId, UUID tableId, UpdateTableRequest request, UUID ownerId);
    void deleteTable(UUID restaurantId, UUID tableId, UUID ownerId);
    TableResponse regenerateQrToken(UUID restaurantId, UUID tableId, UUID ownerId);

}
