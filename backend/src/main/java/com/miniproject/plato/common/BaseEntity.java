package com.miniproject.plato.common;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Abstract base class for every JPA entity in the system.
 *
 * <p>Every table has an {@code id}, {@code created_at}, and {@code updated_at}.
 * Rather than declaring these 3 fields in every entity class, they live here
 * and are inherited automatically.
 *
 * <p>{@code @MappedSuperclass} tells Hibernate: do not create a table for
 * this class itself — just include its columns in every subclass table.
 *
 * <p>{@code @EntityListeners(AuditingEntityListener.class)} hooks into Spring
 * Data's JPA auditing. {@code @EnableJpaAuditing} on {@code PlatoApplication}
 * activates this — so {@code createdAt} and {@code updatedAt} are set
 * automatically on every INSERT and UPDATE without writing a single line
 * of timestamp logic in any service.
 *
 * <p>Usage:
 * <pre>
 *   {@literal @}Entity
 *   {@literal @}Table(name = "restaurants")
 *   public class Restaurant extends BaseEntity {
 *       private String name;   // id, createdAt, updatedAt are inherited
 *   }
 * </pre>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /**
     * Timestamp of when this record was first created.
     * Set automatically by {@link AuditingEntityListener} on INSERT.
     * {@code updatable = false} ensures this column never changes after creation.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last modification.
     * Updated automatically by {@link AuditingEntityListener} on every UPDATE.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
