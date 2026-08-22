package com.miniproject.plato.modules.restaurant.dto;

import com.miniproject.plato.modules.restaurant.RestaurantStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
public class RestaurantResponse {
    private UUID id;
    private UUID ownerId;
    private String name;
    private String description;
    private String logoUrl;
    private String phone;
    private String email;
    private String address;
    private String city;
    private String state;
    private String country;
    private String zipcode;
    private String timezone;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private RestaurantStatus status;
    // Settings
    private BigDecimal taxPercentage;
    private BigDecimal serviceCharge;
    private Boolean allowCashPayment;
    private Boolean allowCardPayment;
    private Boolean allowUpi;
    private Boolean allowOnlinePayment;
    private Boolean acceptingOrders;
    private Boolean autoAcceptOrders;
    // Audit (from BaseEntity)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
