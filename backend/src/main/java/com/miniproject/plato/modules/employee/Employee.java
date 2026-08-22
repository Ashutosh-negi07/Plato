package com.miniproject.plato.modules.employee;

import com.miniproject.plato.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "employee_role")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EmployeeRole role;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}

