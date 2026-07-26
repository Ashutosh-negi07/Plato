package com.miniproject.plato.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an action would create a duplicate or violate a uniqueness constraint.
 * Maps to HTTP 409 Conflict.
 *
 * <p>Usage:
 * <pre>
 *   if (userRepository.existsByEmail(email)) {
 *       throw new ConflictException("A user with this email already exists");
 *   }
 *
 *   if (restaurantRepository.existsByNameAndOwnerId(name, ownerId)) {
 *       throw new ConflictException("You already have a restaurant with this name");
 *   }
 * </pre>
 */
public class ConflictException extends PlatoException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
