package com.miniproject.plato.modules.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;



public record UpdateRestaurantRequest(
        @NotBlank String name,
        String description,
        String phone,
        @Email String email,
        String address,
        String city,
        String state,
        String country,
        String zipcode,
        String timezone,
        LocalTime openingTime,
        LocalTime closingTime
) {}

