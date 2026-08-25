package com.miniproject.plato.modules.menu;


import com.miniproject.plato.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;


@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder

@Table(name = "menu_categories")
public class MenuCategory extends BaseEntity {
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

}
