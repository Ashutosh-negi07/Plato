package com.miniproject.plato.table.dto;

public record UpdateTableRequest(

        String tableNumber,
        Integer capacity,
        String label

) {}
