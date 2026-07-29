package com.miniproject.plato.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Application-wide bean definitions that don't belong to any single feature package.
 *
 * <p>PasswordEncoder lives here (not in SecurityConfig) so that it can be
 * injected by components that need it — like DataInitializer — before
 * the full Security filter chain is configured in Day 3.
 */
@Configuration
public class AppConfig {

    /**
     * BCrypt with default strength (10 rounds).
     * Used everywhere a password needs to be hashed or verified.
     * Never instantiate BCryptPasswordEncoder directly — always inject this bean.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
