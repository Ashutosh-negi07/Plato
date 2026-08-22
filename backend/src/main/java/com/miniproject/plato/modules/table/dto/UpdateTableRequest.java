package com.miniproject.plato.modules.table.dto;

public record UpdateTableRequest(

        String tableNumber,
        Integer capacity,
        String label

) {}
