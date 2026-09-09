package com.miniproject.plato.modules.session;

import com.miniproject.plato.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSession extends BaseEntity {
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;
    @Column(name = "table_id", nullable = false)
    private UUID tableId;
    @Column(name = "session_token", nullable = false, unique = true, length = 128)
    private String sessionToken;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "session_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;
    @Column(name = "guest_count", nullable = false)
    @Builder.Default
    private Integer guestCount = 1;
    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();
    @Column(name = "last_activity", nullable = false)
    @Builder.Default
    private LocalDateTime lastActivity = LocalDateTime.now();
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "ended_at")
    private LocalDateTime endedAt;
    /**
     * Checks if the session has exceeded its activity expiration time.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt) || this.status == SessionStatus.EXPIRED;
    }
    /**
     * Slides the expiration window forward by 30 minutes from now.
     */
    public void refreshActivity() {
        this.lastActivity = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusMinutes(30);
    }
}
