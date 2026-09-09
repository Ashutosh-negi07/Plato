package com.miniproject.plato.modules.session.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StartSessionRequest(
        @NotBlank(message = "QR token is required")
        String qrToken,

        @Min(value = 1, message = "Guest count must be at least 1")
        Integer guestCount
) {}
