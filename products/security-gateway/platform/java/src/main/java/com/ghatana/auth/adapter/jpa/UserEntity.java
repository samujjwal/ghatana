package com.ghatana.auth.adapter.jpa;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.ghatana.platform.domain.auth.User;
import com.ghatana.platform.domain.auth.UserStatus;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA entity mapping for User domain model.
 *
 * <p>
 * <b>Purpose</b><br>
 * Persists User domain model to PostgreSQL via JPA/Hibernate. Handles
 * relational storage while preserving domain model semantics.
 *
 * <p>
 * <b>Mapping</b><br>
 * - id: UUID primary key - tenantId: VARCHAR for multi-tenant isolation -
 * email: VARCHAR with unique constraint per tenant - passwordHash: VARCHAR
 * (BCrypt hash) - status: VARCHAR enum (ACTIVE, SUSPENDED, LOCKED) - createdAt:
 * TIMESTAMP - updatedAt: TIMESTAMP
 *
 * <p>
 * <b>Indexes</b><br>
 * - UNIQUE(tenant_id, email) - email uniqueness per tenant - INDEX(tenant_id) -
 * fast tenant filtering
 *
 * <p>
 * <b>Tenant Isolation</b><br>
 * Queries must always include tenant_id filter. Direct id lookups without
 * tenant check are forbidden.
 *
 * @doc.type class
 * @doc.purpose JPA entity for User persistence
 * @doc.layer product
 * @doc.pattern Adapter
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"tenant_id", "email"})
        },
        indexes = {
            @Index(name = "idx_tenant_id", columnList = "tenant_id")
        }
)
public class UserEntity {

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant updatedAt;

    // Constructors
    /**
     * Default constructor for JPA/Hibernate.
     */
    protected UserEntity() {
    }

    /**
     * Construct from domain model.
     */
    public UserEntity(String tenantId, User user) {
        this.id = UUID.fromString(user.getUserId().value());
        this.tenantId = tenantId;
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.passwordHash = user.getPasswordHash().orElse(null);
        this.status = user.getStatus();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Conversion to Domain Model
    /**
     * Convert JPA entity to domain model.
     */
    public User toDomain() {
        return User.builder()
                .tenantId(TenantId.of(tenantId))
                .userId(UserId.of(id.toString()))
                .email(email)
                .username(email) // Using email as username
                .displayName(displayName)
                .passwordHash(passwordHash)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserEntity)) {
            return false;
        }
        UserEntity that = (UserEntity) o;
        return Objects.equals(id, that.id)
                && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }

    @Override
    public String toString() {
        return "UserEntity{"
                + "id=" + id
                + ", tenantId='" + tenantId + '\''
                + ", email='" + email + '\''
                + ", displayName='" + displayName + '\''
                + ", status=" + status
                + '}';
    }
}
