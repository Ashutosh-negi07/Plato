package com.miniproject.plato.modules.restaurant;

import com.miniproject.plato.modules.restaurant.dto.CreateRestaurantRequest;
import com.miniproject.plato.modules.restaurant.dto.RestaurantSettingsRequest;
import com.miniproject.plato.modules.restaurant.dto.RestaurantResponse;
import com.miniproject.plato.modules.restaurant.dto.UpdateRestaurantRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RestaurantService {

    RestaurantResponse createRestaurant(CreateRestaurantRequest request, UUID ownerId);

    Page<RestaurantResponse> getAllRestaurants(UUID callerId, String role, Pageable pageable);

    RestaurantResponse getRestaurantById(UUID id, UUID callerId, String role);

    RestaurantResponse updateRestaurant(UUID id, UpdateRestaurantRequest request, UUID ownerId);

    RestaurantResponse updateSettings(UUID id, RestaurantSettingsRequest request, UUID ownerId);

    RestaurantResponse updateStatus(UUID id, RestaurantStatus status);

    void deleteRestaurant(UUID id, UUID ownerId);
}

