package com.miniproject.plato.user;

/**
 * Account lifecycle state.
 *
 * <p>Maps to the PostgreSQL {@code user_status} enum defined in V1__create_enums.sql.
 *
 * <ul>
 *   <li>{@link #ACTIVE}    — can log in and use the platform normally</li>
 *   <li>{@link #SUSPENDED} — temporarily blocked by Super Admin; cannot log in</li>
 *   <li>{@link #DELETED}   — soft-deleted; row is kept for audit purposes but the account is inaccessible</li>
 * </ul>
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
}
