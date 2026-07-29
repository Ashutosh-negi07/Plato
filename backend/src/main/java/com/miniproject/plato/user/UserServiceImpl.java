package com.miniproject.plato.user;

import com.miniproject.plato.exception.ConflictException;
import com.miniproject.plato.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    // ── Queries ──────────────────────────────────────────────────────────────

    @Override
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /**
     * Saves a user. If the user is new (id is null), checks for duplicate email first.
     * If the user already exists (update scenario), skips the email conflict check.
     */
    @Override
    @Transactional
    public User save(User user) {
        if (user.getId() == null && userRepository.existsByEmail(user.getEmail())) {
            throw new ConflictException("A user with email '" + user.getEmail() + "' already exists");
        }
        User saved = userRepository.save(user);
        log.info("User saved: id={}, email={}, role={}", saved.getId(), saved.getEmail(), saved.getRole());
        return saved;
    }
}
