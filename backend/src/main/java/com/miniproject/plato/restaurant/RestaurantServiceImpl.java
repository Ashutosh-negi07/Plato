package com.miniproject.plato.restaurant;

import com.miniproject.plato.exception.ResourceNotFoundException;
import com.miniproject.plato.exception.UnauthorizedAccessException;
import com.miniproject.plato.restaurant.dto.CreateRestaurantRequest;
import com.miniproject.plato.restaurant.dto.RestaurantResponse;
import com.miniproject.plato.restaurant.dto.RestaurantSettingsRequest;
import com.miniproject.plato.restaurant.dto.UpdateRestaurantRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RestaurantServiceImpl implements RestaurantService{

    private final RestaurantMapper restaurantMapper;
    private final RestaurantRepository restaurantRepository;




    @Override
    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request, UUID ownerId) {
        Restaurant restaurant = restaurantMapper.toEntity(request, ownerId);
        Restaurant saved = restaurantRepository.save(restaurant);
        return restaurantMapper.toResponse(saved);
    }

    @Override
    public Page<RestaurantResponse> getAllRestaurants(UUID callerId, String role, Pageable pageable) {
        // SUPER_ADMIN sees all restaurants
        // OWNER sees only their own
        if ("SUPER_ADMIN".equals(role)) {
            return restaurantRepository.findAll(pageable)
                    .map(restaurantMapper::toResponse);
        } else {
            return restaurantRepository.findByOwnerId(callerId, pageable)
                    .map(restaurantMapper::toResponse);
        }
    }

    @Override
    public RestaurantResponse getRestaurantById(UUID id, UUID callerId, String role) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("Restaurant", id));

        // OWNER can only see their own
        if (!"SUPER_ADMIN".equals(role) && !restaurant.getOwnerId().equals(callerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }

        return restaurantMapper.toResponse(restaurant);
    }

    @Override
    @Transactional
    public RestaurantResponse updateRestaurant(UUID id, UpdateRestaurantRequest request, UUID ownerId) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));

        if (!restaurant.getOwnerId().equals(ownerId))
            throw new UnauthorizedAccessException("You do not own this restaurant");

        restaurantMapper.applyUpdate(restaurant, request);  // ← mapper handles all null-checks
        return restaurantMapper.toResponse(restaurant);     // dirty checking → auto UPDATE
    }

    @Override
    @Transactional
    public RestaurantResponse updateSettings(UUID id, RestaurantSettingsRequest request, UUID ownerId) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));

        if (!restaurant.getOwnerId().equals(ownerId))
            throw new UnauthorizedAccessException("You do not own this restaurant");

        restaurantMapper.applySettings(restaurant, request); // ← mapper handles it
        return restaurantMapper.toResponse(restaurant);
    }

    @Override
    @Transactional
    public RestaurantResponse updateStatus(UUID id, RestaurantStatus status) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));

        restaurant.setStatus(status);
        return restaurantMapper.toResponse(restaurant);
    }

    @Override
    @Transactional
    public void deleteRestaurant(UUID id, UUID ownerId) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));

        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }

        restaurant.setStatus(RestaurantStatus.INACTIVE);
        // dirty checking → UPDATE restaurants SET status = 'INACTIVE' WHERE id = ?
    }




}
