package com.miniproject.plato.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Universal response envelope for every API endpoint.
 *
 * <p>Every response — success or error — is wrapped in this class so the
 * frontend always gets the same shape regardless of which endpoint it calls.
 *
 * <p>Usage in a controller:
 * <pre>
 *   return ResponseEntity.ok(ApiResponse.ok("Restaurant found", response));
 *   return ResponseEntity.status(404).body(ApiResponse.error("Restaurant not found"));
 * </pre>
 *
 * <p>{@code @JsonInclude(NON_NULL)} means if {@code data} is null it is
 * omitted from the JSON output — keeping error responses lean.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    // ── Static factory methods ────────────────────────────────────────────────

    /** Successful response with data payload. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /** Successful response with no data (e.g. logout, delete). */
    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, message, null);
    }

    /** Error response. Data will be null and omitted from JSON. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
