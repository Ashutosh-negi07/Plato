package com.miniproject.plato.auth;

import com.miniproject.plato.auth.dto.LoginRequest;
import com.miniproject.plato.auth.dto.LoginResponse;
import com.miniproject.plato.security.JwtTokenProvider;
import com.miniproject.plato.modules.user.User;
import com.miniproject.plato.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. Delegate credential check to Spring Security.
        //    Internally calls UserDetailsServiceImpl.loadUserByUsername(email)
        //    then BCrypt.matches(rawPassword, storedHash).
        //    Throws AuthenticationException if credentials are wrong.
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );
        } catch (AuthenticationException e) {
            log.warn("Login failed for email: {}", request.email());
            throw e; // GlobalExceptionHandler converts this to 401
        }

        // 2. Credentials valid. Load our entity (we need userId and role for the token).
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(); // already confirmed exists above

        // 3. Update last_login timestamp
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // 4. Generate signed JWT
        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getRole().name()   // "OWNER", "SUPER_ADMIN", "EMPLOYEE"
        );

        log.info("Login successful for userId={}, role={}", user.getId(), user.getRole());

        return new LoginResponse(token, "Bearer", user.getRole().name(), user.getFullName());
    }
}
