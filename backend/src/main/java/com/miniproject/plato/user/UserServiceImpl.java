package com.miniproject.plato.user;

import com.miniproject.plato.exception.ConflictException;
import com.miniproject.plato.exception.ResourceNotFoundException;
import com.miniproject.plato.user.dto.CreateUserRequest;
import com.miniproject.plato.user.dto.UpdateUserRequest;
import com.miniproject.plato.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)              // all methods are read-only by default
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    // ------------------------------------------------------------------
    // INTERNAL METHODS — used by AuthServiceImpl and DataInitializer
    // Returns raw User entity. Never call from Controller.
    // ------------------------------------------------------------------

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

    @Override
    @Transactional                           // overrides readOnly = true
    public User save(User user) {
        // Only check email conflict when creating (id is null = new user)
        if (user.getId() == null && userRepository.existsByEmail(user.getEmail())) {
            throw new ConflictException("A user with email '" + user.getEmail() + "' already exists");
        }
        User saved = userRepository.save(user);
        log.info("User saved: id={}, email={}, role={}", saved.getId(), saved.getEmail(), saved.getRole());
        return saved;
    }

    // ------------------------------------------------------------------
    // API METHODS — used only by UserController
    // Returns DTOs. passwordHash never leaves this layer.
    // ------------------------------------------------------------------

    // ── GET ALL ──────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of all users.
     *
     * Page.map() converts each User entity to UserResponse without loading
     * the entire result set into memory. Spring Data handles the SQL
     * LIMIT + OFFSET based on the Pageable passed in from the controller.
     */
    @Override
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toResponse);
    }

    // ── GET ONE ──────────────────────────────────────────────────────────

    /**
     * Returns a single user by ID.
     * Throws ResourceNotFoundException (→ 404) if not found.
     */
    @Override
    public UserResponse getUserById(UUID id) {
        User user = findById(id);              // reuses internal method
        return userMapper.toResponse(user);
    }

    // ── CREATE ───────────────────────────────────────────────────────────

    /**
     * Creates a new user from the request DTO.
     *
     * Why hash here and not in the controller?
     *   Business rule. The service owns "how a user is created".
     *   The controller only knows "someone wants to create a user".
     *
     * Why not call save() here?
     *   The internal save() checks id == null for conflict detection,
     *   which is correct here. But we build the entity manually so we
     *   also call userRepository.save() directly after conflict check
     *   to avoid double-checking. Either approach is valid — being
     *   explicit here is clearer for learning.
     */
    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // 1. Conflict check — email must be unique across all users
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("A user with email '" + request.getEmail() + "' already exists");
        }

        // 2. Build the entity — note: password is hashed here, never stored raw
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .build();

        // 3. Persist
        User saved = userRepository.save(user);
        log.info("User created: id={}, email={}, role={}", saved.getId(), saved.getEmail(), saved.getRole());

        // 4. Return DTO — entity never leaves this method
        return userMapper.toResponse(saved);
    }

    // ── UPDATE ───────────────────────────────────────────────────────────

    /**
     * Partially updates a user — only fields that are non-null in the request
     * are applied. This is the PATCH pattern.
     *
     * Why no explicit userRepository.save() call?
     *   JPA dirty checking. When a method is @Transactional, Hibernate
     *   tracks changes to managed entities. At the end of the transaction,
     *   it automatically issues an UPDATE for any fields that changed.
     *   Calling save() again would work too, but it's redundant.
     *
     * Why check email conflict only when email actually changed?
     *   If the user sends the same email they already have, checking
     *   existsByEmail would throw a false conflict (it finds itself).
     *   So we only check if the new email is different from the current one.
     */
    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findById(id);              // throws 404 if not found

        // Partial update — only apply non-null fields
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            // New email — check it isn't already taken by someone else
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("A user with email '" + request.getEmail() + "' already exists");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        // Hibernate dirty checking auto-saves — no explicit save() needed
        log.info("User updated: id={}", id);
        return userMapper.toResponse(user);
    }

    // ── UPDATE STATUS ────────────────────────────────────────────────────

    /**
     * Changes a user's status (ACTIVE / INACTIVE).
     * Only SUPER_ADMIN can call this — enforced at the controller layer
     * via @PreAuthorize, not here.
     */
    @Override
    @Transactional
    public UserResponse updateStatus(UUID id, UserStatus newStatus) {
        User user = findById(id);
        user.setStatus(newStatus);
        log.info("User status updated: id={}, newStatus={}", id, newStatus);
        return userMapper.toResponse(user);
    }

    // ── DELETE (SOFT) ─────────────────────────────────────────────────────

    /**
     * Soft deletes a user — sets status to DELETED, never removes the row.
     *
     * Why not actually DELETE from the database?
     *   1. Audit trail — you can see who existed and when they were removed.
     *   2. Foreign key safety — if this user is referenced in orders or
     *      employees tables, a real DELETE would fail or cascade incorrectly.
     *   3. Recovery — mistakes can be undone by setting status back to ACTIVE.
     */
    @Override
    @Transactional
    public void deleteUser(UUID id) {
        User user = findById(id);
        user.setStatus(UserStatus.DELETED);
        log.info("User soft-deleted: id={}, email={}", id, user.getEmail());
    }
}
