package com.miniproject.plato.user;

/**
 * Platform-level role assigned to every user account.
 *
 * <p>Maps to the PostgreSQL {@code user_role} enum defined in V1__create_enums.sql.
 *
 * <ul>
 *   <li>{@link #SUPER_ADMIN} — manages the entire platform (create/suspend owners, view all data)</li>
 *   <li>{@link #OWNER}       — manages one or more restaurants they own</li>
 *   <li>{@link #EMPLOYEE}    — works inside a restaurant; fine-grained role in {@code employee_role}</li>
 * </ul>
 *
 * <p>Customers are <b>never</b> assigned a {@code UserRole} — they use {@code customer_sessions} instead.
 */
public enum UserRole {
    SUPER_ADMIN,
    OWNER,
    EMPLOYEE
}
