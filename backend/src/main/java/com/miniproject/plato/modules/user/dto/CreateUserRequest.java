package com.miniproject.plato.modules.user.dto;

import com.miniproject.plato.modules.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {
    @NotBlank
    String fullName;
    @NotBlank @Email
    String email;
    @NotBlank @Size(min = 8) String password;   // plaintext — hashed in service
    String phone;                               // optional
    @NotNull
    UserRole role;
}
