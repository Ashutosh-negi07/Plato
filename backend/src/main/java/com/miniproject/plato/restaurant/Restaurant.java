package com.miniproject.plato.restaurant;

import com.miniproject.plato.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant extends BaseEntity {

    // ── Ownership ─────────────────────────────────────────────────────────────
    // Plain UUID — not @ManyToOne because we never need to load the full
    // User object just to check ownership. A UUID is enough.
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    // ── Identity ──────────────────────────────────────────────────────────────
    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String email;

    // ── Location ──────────────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 100)
    private String country;

    @Column(length = 20)
    private String zipcode;

    // ── Operations ────────────────────────────────────────────────────────────
    @Column(length = 50)
    private String timezone;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    // restaurant_status is a PostgreSQL named enum type.
    // @JdbcTypeCode(SqlTypes.NAMED_ENUM) tells Hibernate 6 to bind
    // this as the named enum — without it, Hibernate sends VARCHAR
    // and PostgreSQL rejects it with a type mismatch error.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "restaurant_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private RestaurantStatus status = RestaurantStatus.ACTIVE;

    // ── Settings ──────────────────────────────────────────────────────────────
    // BigDecimal — NEVER use double/float for money.
    // double has floating point precision errors (99.99 * 0.10 = 9.999000000000001).
    // BigDecimal is exact.
    // precision=5, scale=2 → max value: 999.99 (fits tax% and service charge%)
    @Column(name = "tax_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxPercentage = BigDecimal.ZERO;

    @Column(name = "service_charge", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal serviceCharge = BigDecimal.ZERO;

    // Payment method toggles — owner decides which methods to accept
    @Column(name = "allow_cash_payment", nullable = false)
    @Builder.Default
    private Boolean allowCashPayment = true;

    @Column(name = "allow_card_payment", nullable = false)
    @Builder.Default
    private Boolean allowCardPayment = true;

    @Column(name = "allow_upi", nullable = false)
    @Builder.Default
    private Boolean allowUpi = true;

    @Column(name = "allow_online_payment", nullable = false)
    @Builder.Default
    private Boolean allowOnlinePayment = false;

    // Order management toggles
    @Column(name = "accepting_orders", nullable = false)
    @Builder.Default
    private Boolean acceptingOrders = true;

    // If true — orders go directly to ACCEPTED without staff confirmation
    @Column(name = "auto_accept_orders", nullable = false)
    @Builder.Default
    private Boolean autoAcceptOrders = false;
}
