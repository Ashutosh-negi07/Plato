package com.miniproject.plato.modules.user.dto;

import com.miniproject.plato.modules.user.UserRole;
import com.miniproject.plato.modules.user.UserStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class UserResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private UserRole role;
    private UserStatus status;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // passwordHash is intentionally NOT here — never expose it
}
