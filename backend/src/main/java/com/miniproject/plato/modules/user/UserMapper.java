package com.miniproject.plato.modules.user;

import com.miniproject.plato.modules.user.dto.UserResponse;
import org.springframework.stereotype.Component;

// ------------------------------------------------------------------
// UserMapper — single responsibility: convert User entity to UserResponse
//
// Why @Component and not @Service?
//   @Component = generic Spring bean. Mapper is not business logic,
//   it's a utility. Using @Service would be semantically wrong.
//
// Why not use MapStruct or ModelMapper?
//   Explicit mapping is better for learning. You see exactly which
//   field maps to which. Zero hidden magic.
//
// Why not put this logic directly in UserServiceImpl?
//   If two services ever need to map a User (e.g. AuthService returning
//   user profile), you'd duplicate code. One mapper = one place to change.
// ------------------------------------------------------------------
@Component
public class UserMapper {

    // ------------------------------------------------------------------
    // toResponse — maps a User entity to a safe outbound UserResponse
    //
    // createdAt and updatedAt come from BaseEntity, not User directly.
    // Lombok @Getter on BaseEntity exposes them via getCreatedAt() etc.
    // ------------------------------------------------------------------
    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

