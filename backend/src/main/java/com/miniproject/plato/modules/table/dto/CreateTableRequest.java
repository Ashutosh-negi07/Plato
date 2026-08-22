package com.miniproject.plato.modules.table.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTableRequest(

        @NotBlank String tableNumber,
        Integer capacity,
        String label

) {}

