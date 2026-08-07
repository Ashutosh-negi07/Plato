package com.miniproject.plato.restaurant;

import com.miniproject.plato.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

// =========================================================================
// Restaurant — JPA entity mapping to the 'restaurants' table.
// -------------------------------------------------------------------------
// WHY extends BaseEntity:
//   BaseEntity provides id (UUID), createdAt, updatedAt via JPA Auditing.
//   Every entity in this project follows this pattern — no duplication.
//
// WHY settings are fields here (not a nested object):
//   All settings are columns on the same DB table. Hibernate's @Embedded
//   would add complexity with no benefit — they are just fields.
//
// WHY @JdbcTypeCode(SqlTypes.NAMED_ENUM) on status:
//   restaurant_status is a PostgreSQL custom enum type (not VARCHAR).
//   Without this, Hibernate 6 sends the value as character varying,
//   which PostgreSQL rejects. Same fix as UserRole/UserStatus.
// =========================================================================
@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant extends BaseEntity {

    // ── Ownership ─────────────────────────────────────────────────────────────
    // Stored as a plain UUID column, not a @ManyToOne join.
    // WHY: We don't need to load the full User object every time we load a
    // restaurant. A UUID column is enough for ownership checks and queries.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "restaurant_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private RestaurantStatus status = RestaurantStatus.ACTIVE;

    // ── Settings ──────────────────────────────────────────────────────────────
    // NUMERIC(5,2) → BigDecimal in Java. Never use double/float for money.
    @Column(name = "tax_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxPercentage = BigDecimal.ZERO;

    @Column(name = "service_charge", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal serviceCharge = BigDecimal.ZERO;

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

    @Column(name = "accepting_orders", nullable = false)
    @Builder.Default
    private Boolean acceptingOrders = true;

    @Column(name = "auto_accept_orders", nullable = false)
    @Builder.Default
    private Boolean autoAcceptOrders = false;
}
