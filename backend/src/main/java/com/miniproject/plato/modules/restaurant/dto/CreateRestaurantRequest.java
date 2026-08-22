package com.miniproject.plato.modules.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalTime;

public record CreateRestaurantRequest(

        // Required
        @NotBlank String name,

        // Optional identity
        String description,
        String phone,
        @Email String email,

        // Optional location
        String address,
        String city,
        String state,
        String country,
        String zipcode,

        // Optional operations
        String timezone,
        LocalTime openingTime,
        LocalTime closingTime,

        // Optional settings — entity @Builder.Default handles nulls
        @DecimalMin("0.00") BigDecimal taxPercentage,
        @DecimalMin("0.00") BigDecimal serviceCharge,
        Boolean allowCashPayment,
        Boolean allowCardPayment,
        Boolean allowUpi,
        Boolean allowOnlinePayment,
        Boolean acceptingOrders,
        Boolean autoAcceptOrders

) {}
