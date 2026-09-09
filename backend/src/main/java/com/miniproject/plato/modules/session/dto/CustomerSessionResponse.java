package com.miniproject.plato.modules.session.dto;

import com.miniproject.plato.modules.session.SessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

    @Getter
    @Builder
    public class CustomerSessionResponse {
        private UUID id;
        private UUID restaurantId;
        private String restaurantName;
        private UUID tableId;
        private String tableNumber;
        private String sessionToken;
        private SessionStatus status;
        private Integer guestCount;
        private LocalDateTime startedAt;
        private LocalDateTime expiresAt;
    }


