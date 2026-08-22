package com.miniproject.plato.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the database with a default Super Admin on first run.
 *
 * <p>{@code CommandLineRunner.run()} is called once by Spring Boot
 * immediately after the application context is fully loaded — after
 * Flyway migrations have run and all beans are ready.
 *
 * <p>The check {@code existsByRole(SUPER_ADMIN)} ensures this is truly
 * idempotent — restarting the server never creates a duplicate admin.
 *
 * <p>Credentials are sourced from {@code plato.seed.admin.*} properties.
 * In development these come from {@code application-local.yml} (git-ignored).
 * In production they must be set via environment variables:
 * {@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD}, {@code ADMIN_NAME}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${plato.seed.admin.email}")
    private String adminEmail;

    @Value("${plato.seed.admin.password}")
    private String adminPassword;

    @Value("${plato.seed.admin.name:Super Admin}")
    private String adminName;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByRole(UserRole.SUPER_ADMIN)) {
            log.info("DataInitializer: Super Admin already exists — skipping seed.");
            return;
        }

        User superAdmin = User.builder()
                .fullName(adminName)
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(superAdmin);

        log.info("DataInitializer: Super Admin seeded successfully.");
        log.info("DataInitializer: Login email → {}", adminEmail);
        log.warn("DataInitializer: Using default seed password. Change it immediately via the admin panel in production.");
    }
}
