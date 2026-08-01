package com.miniproject.plato.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        String role,
        String fullName
) {}
