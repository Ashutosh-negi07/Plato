package com.miniproject.plato.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a customer's dining session has expired or does not exist.
 * Maps to HTTP 401 Unauthorized.
 *
 * <p>Thrown by {@code CustomerSessionFilter} when:
 * <ul>
 *   <li>The {@code X-Session-Token} header is missing</li>
 *   <li>No session row matches the provided token</li>
 *   <li>The session's {@code expires_at} timestamp is in the past</li>
 *   <li>The session status is {@code CLOSED} or {@code EXPIRED}</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   CustomerSession session = sessionRepository.findByToken(token)
 *       .orElseThrow(SessionExpiredException::new);
 *
 *   if (session.isExpired()) {
 *       throw new SessionExpiredException();
 *   }
 * </pre>
 */
public class SessionExpiredException extends PlatoException {

    public SessionExpiredException() {
        super("Your session has expired. Please scan the QR code again.", HttpStatus.UNAUTHORIZED);
    }

    public SessionExpiredException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
