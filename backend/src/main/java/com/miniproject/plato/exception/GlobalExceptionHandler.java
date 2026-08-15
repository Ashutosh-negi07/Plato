package com.miniproject.plato.exception;

import com.miniproject.plato.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
//import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Central exception handler for the entire application.
 *
 * <p>{@code @RestControllerAdvice} makes Spring intercept every exception
 * thrown from any {@code @Controller} or {@code @RestController} and route
 * it through the matching {@code @ExceptionHandler} method here.
 *
 * <p>This means controllers never need try-catch blocks.
 * Services throw typed exceptions; this class converts them to the correct
 * HTTP status + {@link ApiResponse} shape automatically.
 *
 * <p>Handler priority (most specific wins):
 * <ol>
 *   <li>{@link PlatoException} subclasses — all custom app errors</li>
 *   <li>{@link MethodArgumentNotValidException} — {@code @Valid} DTO failures</li>
 *   <li>{@link AccessDeniedException} — Spring Security role check failed</li>
 *   <li>{@link AuthenticationException} — Spring Security unauthenticated</li>
 *   <li>{@link Exception} — catch-all for anything unexpected</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 1. All custom Plato exceptions ───────────────────────────────────────
    //    PlatoException carries its own HttpStatus — use it directly.

    @ExceptionHandler(PlatoException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatoException(
            PlatoException ex, HttpServletRequest request) {
        log.warn("PlatoException [{}] at {}: {}", ex.getStatus(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── 2. Database constraint violation (409) ───────────────────────────────
//    Thrown when Hibernate/PostgreSQL detects a UNIQUE or FK constraint
//    violation at the DB layer — e.g. duplicate email that slipped past
//    the service-layer existsByEmail() check (race condition).
//    ConflictException handles the normal path; this catches the DB fallback.


    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("DataIntegrityViolation at {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("The request conflicts with existing data."));
    }



    // ── 3. @Valid DTO validation failures ────────────────────────────────────
    //    Returns a list of field-level error messages so the frontend
    //    can highlight exactly which fields failed.

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<String>>> handleValidationException(
            MethodArgumentNotValidException ex) {
        List<String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(false, "Validation failed", fieldErrors));
    }

    // ── 4. Spring Security: role-based access check failed (403) ─────────────
    //    Thrown when @PreAuthorize fails or manually thrown by Spring Security.

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to access this resource"));
    }

    // ── 5. Spring Security: token missing or invalid (401) ───────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required"));
    }

    // ── 6. Catch-all (500) ───────────────────────────────────────────────────
    //    Logs the full stack trace internally; returns a safe, vague message
    //    to the client so no internals are leaked.

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Invalid parameter value: " + ex.getMessage()));
    }


}
