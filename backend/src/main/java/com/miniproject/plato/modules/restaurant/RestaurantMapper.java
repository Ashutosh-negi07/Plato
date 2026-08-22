package com.miniproject.plato.restaurant;

import com.miniproject.plato.restaurant.dto.CreateRestaurantRequest;
import com.miniproject.plato.restaurant.dto.RestaurantResponse;
import com.miniproject.plato.restaurant.dto.RestaurantSettingsRequest;
import com.miniproject.plato.restaurant.dto.UpdateRestaurantRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class RestaurantMapper {

    public RestaurantResponse toResponse(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .ownerId(restaurant.getOwnerId())
                .name(restaurant.getName())
                .description(restaurant.getDescription())
                .logoUrl(restaurant.getLogoUrl())
                .phone(restaurant.getPhone())
                .email(restaurant.getEmail())
                .address(restaurant.getAddress())
                .city(restaurant.getCity())
                .state(restaurant.getState())
                .country(restaurant.getCountry())
                .zipcode(restaurant.getZipcode())
                .timezone(restaurant.getTimezone())
                .openingTime(restaurant.getOpeningTime())
                .closingTime(restaurant.getClosingTime())
                .status(restaurant.getStatus())
                .taxPercentage(restaurant.getTaxPercentage())
                .serviceCharge(restaurant.getServiceCharge())
                .allowCashPayment(restaurant.getAllowCashPayment())
                .allowCardPayment(restaurant.getAllowCardPayment())
                .allowUpi(restaurant.getAllowUpi())
                .allowOnlinePayment(restaurant.getAllowOnlinePayment())
                .acceptingOrders(restaurant.getAcceptingOrders())
                .autoAcceptOrders(restaurant.getAutoAcceptOrders())
                .createdAt(restaurant.getCreatedAt())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }

    public Restaurant toEntity(CreateRestaurantRequest request, UUID ownerId) {
        return Restaurant.builder()
                .ownerId(ownerId)
                .name(request.name())
                .description(request.description())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .city(request.city())
                .state(request.state())
                .country(request.country())
                .zipcode(request.zipcode())
                .timezone(request.timezone())
                .openingTime(request.openingTime())
                .closingTime(request.closingTime())
                .taxPercentage(request.taxPercentage() != null ? request.taxPercentage() : BigDecimal.ZERO)
                .serviceCharge(request.serviceCharge() != null ? request.serviceCharge() : BigDecimal.ZERO)
                .allowCashPayment(request.allowCashPayment() != null ? request.allowCashPayment() : true)
                .allowCardPayment(request.allowCardPayment() != null ? request.allowCardPayment() : true)
                .allowUpi(request.allowUpi() != null ? request.allowUpi() : true)
                .allowOnlinePayment(request.allowOnlinePayment() != null ? request.allowOnlinePayment() : false)
                .acceptingOrders(request.acceptingOrders() != null ? request.acceptingOrders() : true)
                .autoAcceptOrders(request.autoAcceptOrders() != null ? request.autoAcceptOrders() : false)
                .build();
    }

    // For updateRestaurant — applies non-null fields onto existing entity
    public void applyUpdate(Restaurant restaurant, UpdateRestaurantRequest request) {
        if (request.name() != null)        restaurant.setName(request.name());
        if (request.description() != null) restaurant.setDescription(request.description());
        if (request.phone() != null)       restaurant.setPhone(request.phone());
        if (request.email() != null)       restaurant.setEmail(request.email());
        if (request.address() != null)     restaurant.setAddress(request.address());
        if (request.city() != null)        restaurant.setCity(request.city());
        if (request.state() != null)       restaurant.setState(request.state());
        if (request.country() != null)     restaurant.setCountry(request.country());
        if (request.zipcode() != null)     restaurant.setZipcode(request.zipcode());
        if (request.timezone() != null)    restaurant.setTimezone(request.timezone());
        if (request.openingTime() != null) restaurant.setOpeningTime(request.openingTime());
        if (request.closingTime() != null) restaurant.setClosingTime(request.closingTime());
    }

    // For updateSettings — applies non-null settings only
    public void applySettings(Restaurant restaurant, RestaurantSettingsRequest request) {
        if (request.taxPercentage() != null)    restaurant.setTaxPercentage(request.taxPercentage());
        if (request.serviceCharge() != null)    restaurant.setServiceCharge(request.serviceCharge());
        if (request.allowCashPayment() != null) restaurant.setAllowCashPayment(request.allowCashPayment());
        if (request.allowCardPayment() != null) restaurant.setAllowCardPayment(request.allowCardPayment());
        if (request.allowUpi() != null)         restaurant.setAllowUpi(request.allowUpi());
        if (request.allowOnlinePayment() != null) restaurant.setAllowOnlinePayment(request.allowOnlinePayment());
        if (request.acceptingOrders() != null)  restaurant.setAcceptingOrders(request.acceptingOrders());
        if (request.autoAcceptOrders() != null) restaurant.setAutoAcceptOrders(request.autoAcceptOrders());
    }


}
