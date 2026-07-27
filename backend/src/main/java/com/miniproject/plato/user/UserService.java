package com.miniproject.plato.user;

import java.util.UUID;

/**
 * Service contract for all user-related operations.
 *
 * <p>Controllers and other services depend on this interface, not on
 * {@link UserServiceImpl} directly. This keeps the layers decoupled and
 * makes unit testing straightforward — just mock this interface.
 *
 * <p>Business rules enforced here (not in the controller):
 * <ul>
 *   <li>Duplicate email → {@code ConflictException}</li>
 *   <li>User not found → {@code ResourceNotFoundException}</li>
 *   <li>Suspended/deleted account access → checked by auth layer via {@link #findByEmail}</li>
 * </ul>
 */
public interface UserService {

    /**
     * Look up a user by primary key.
     *
     * @throws com.miniproject.plato.exception.ResourceNotFoundException if no user exists with that id
     */
    User findById(UUID id);

    /**
     * Look up a user by email address.
     * Used by the auth layer during login.
     *
     * @throws com.miniproject.plato.exception.ResourceNotFoundException if no user exists with that email
     */
    User findByEmail(String email);

    /**
     * Return true if a user with the given email already exists.
     * Used before creating a new user to give a clean 409 error.
     */
    boolean existsByEmail(String email);

    /**
     * Persist a user entity (create or update).
     * The caller is responsible for hashing the password before calling this.
     *
     * @throws com.miniproject.plato.exception.ConflictException if the email is already taken (on create)
     */
    User save(User user);
}
