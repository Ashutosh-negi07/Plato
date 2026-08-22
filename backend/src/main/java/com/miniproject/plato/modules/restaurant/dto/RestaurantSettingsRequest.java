package com.miniproject.plato.modules.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record RestaurantSettingsRequest(
        @DecimalMin("0.00") BigDecimal taxPercentage,
        @DecimalMin("0.00") BigDecimal serviceCharge,
        Boolean allowCashPayment,
        Boolean allowCardPayment,
        Boolean allowUpi,
        Boolean allowOnlinePayment,
        Boolean acceptingOrders,
        Boolean autoAcceptOrders) {
}
