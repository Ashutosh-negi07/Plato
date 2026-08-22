package com.miniproject.plato.modules.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {
    String fullName;   // null = don't touch it
    String email;      // null = don't touch it
    String phone;      // null = don't touch it
}
