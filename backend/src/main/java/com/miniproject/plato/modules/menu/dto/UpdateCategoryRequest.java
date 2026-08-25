package com.miniproject.plato.modules.menu.dto;

public record UpdateCategoryRequest(

        String name,               // nullable (only update if provided)
        String description,           // nullable
        Integer displayOrder,       // nullable (boxed int so null = don't change)
        Boolean isActive            // nullable (toggle active/inactive)
) {
}
