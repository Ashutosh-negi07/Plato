package com.miniproject.plato.modules.table;

import com.miniproject.plato.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "restaurant_tables")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantTable extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId ;

    @Column(name = "table_number" , nullable = false, length = 20)
    private String tableNumber;

    @Column
    private Integer capacity;

    @Column(length = 100)
    private String label;

    @Column(name = "qr_token", nullable = false, unique = true, length = 64)
    private String qrToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "table_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private TableStatus status = TableStatus.AVAILABLE;
}
