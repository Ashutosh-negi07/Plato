package com.miniproject.plato.modules.menu.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank String name,
        String description,   // nullable
        int displayOrder            // default 0 if not sent

) {
}
