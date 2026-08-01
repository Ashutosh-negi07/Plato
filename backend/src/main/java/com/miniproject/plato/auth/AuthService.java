package com.miniproject.plato.auth;

import com.miniproject.plato.auth.dto.LoginRequest;
import com.miniproject.plato.auth.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}

