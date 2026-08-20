package com.miniproject.plato.table.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTableRequest(

        @NotBlank String tableNumber,
        Integer capacity,
        String label

) {}

