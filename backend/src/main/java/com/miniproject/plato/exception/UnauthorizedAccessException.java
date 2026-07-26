package com.miniproject.plato.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a user attempts an action they are not permitted to perform.
 * Maps to HTTP 403 Forbidden.
 *
 * <p>Distinct from Spring Security's {@code AccessDeniedException}:
 * use this when you are enforcing business-level ownership rules in
 * service code (e.g. an Owner trying to modify another Owner's restaurant).
 *
 * <p>Usage:
 * <pre>
 *   if (!restaurant.getOwnerId().equals(currentUser.getId())) {
 *       throw new UnauthorizedAccessException("You do not own this restaurant");
 *   }
 * </pre>
 */
public class UnauthorizedAccessException extends PlatoException {

    public UnauthorizedAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }

    /** Default message for simple cases. */
    public UnauthorizedAccessException() {
        super("You do not have permission to perform this action", HttpStatus.FORBIDDEN);
    }
}
