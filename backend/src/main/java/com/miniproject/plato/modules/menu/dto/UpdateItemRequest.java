package com.miniproject.plato.modules.menu.dto;

import java.math.BigDecimal;

public record UpdateItemRequest(
        String name                 ,// nullable
        String description          ,// nullable
        BigDecimal price        ,    // nullable
        String imageUrl         ,    // nullable
        Integer displayOrder        // nullable

) {
}
