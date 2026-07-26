package com.miniproject.plato.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when business-rule validation fails inside a service method —
 * not field-level DTO validation (which is handled by {@code @Valid}).
 * Maps to HTTP 400 Bad Request.
 *
 * <p>Use this when you need to enforce rules that cannot be expressed
 * with Bean Validation annotations alone.
 *
 * <p>Usage:
 * <pre>
 *   if (!restaurant.isAcceptingOrders()) {
 *       throw new ValidationException("This restaurant is not currently accepting orders");
 *   }
 *
 *   if (cartItems.isEmpty()) {
 *       throw new ValidationException("Cannot place an order with an empty cart");
 *   }
 * </pre>
 */
public class ValidationException extends PlatoException {

    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
