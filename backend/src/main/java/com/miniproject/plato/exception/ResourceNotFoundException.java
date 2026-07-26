package com.miniproject.plato.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a requested resource does not exist in the database.
 * Maps to HTTP 404 Not Found.
 *
 * <p>Usage:
 * <pre>
 *   Restaurant restaurant = restaurantRepository.findById(id)
 *       .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));
 *
 *   User user = userRepository.findByEmail(email)
 *       .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
 * </pre>
 */
public class ResourceNotFoundException extends PlatoException {

    /** Look up by UUID primary key. */
    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " not found with id: " + id, HttpStatus.NOT_FOUND);
    }

    /** Look up by any named field (e.g. email, slug, token). */
    public ResourceNotFoundException(String resource, String field, String value) {
        super(resource + " not found with " + field + ": " + value, HttpStatus.NOT_FOUND);
    }
}
