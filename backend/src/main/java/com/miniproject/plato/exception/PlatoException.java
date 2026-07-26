package com.miniproject.plato.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all Plato-specific runtime errors.
 *
 * <p>Every custom exception in the system extends this class.
 * Each subclass sets the appropriate HTTP status in its constructor,
 * so {@link GlobalExceptionHandler} can map it to the correct response
 * code without any if-else chains.
 *
 * <p>Hierarchy:
 * <pre>
 *   PlatoException (400–500 range)
 *     ├── ResourceNotFoundException   (404)
 *     ├── UnauthorizedAccessException (403)
 *     ├── ConflictException           (409)
 *     ├── ValidationException         (400)
 *     └── SessionExpiredException     (401)
 * </pre>
 */
@Getter
public class PlatoException extends RuntimeException {

    private final HttpStatus status;

    public PlatoException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
