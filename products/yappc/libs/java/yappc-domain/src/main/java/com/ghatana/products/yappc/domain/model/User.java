package com.ghatana.products.yappc.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * User entity representing a platform user account.
 *
 * <p><b>Purpose</b><br>
 * User represents an authenticated user in the DevSecOps platform. Users can be members
 * of multiple workspaces with different roles and permissions.
 *
 * <p><b>Authentication</b><br>
 * Email is the primary login identifier (unique constraint). Password/auth details
 * are managed externally by identity provider (not stored in this entity).
 *
 * <p><b>Multi-Workspace</b><br>
 * Users can belong to multiple workspaces via WorkspaceMember relationships.
 *
 * <p><b>Architecture Role</b><br>
 * This is a domain model in the JPA persistence layer, mapped to the "user" table
 * in PostgreSQL. Referenced by many entities (workspace owner, incident owner, etc.).
 *
 * <p><b>Thread Safety</b><br>
 * Entity class is NOT thread-safe. Instances should not be shared across threads.
 *
 * @see WorkspaceMember
 * @see Workspace
 * @doc.type class
 * @doc.purpose User account entity
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "\"user\"", indexes = {
    @Index(name = "idx_user_email", columnList = "email"),
    @Index(name = "idx_user_display_name", columnList = "display_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * Unique identifier for the user.
     * UUID generated automatically using PostgreSQL gen_random_uuid().
     */
    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * User's email address (login identifier).
     * Must be unique across all users. Used for authentication.
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * User's display name (shown in UI).
     * May differ from email username.
     */
    @Column(name = "display_name", length = 255)
    private String displayName;

    /**
     * URL to user's avatar/profile image.
     * Nullable if user hasn't uploaded an avatar.
     */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * Timestamp when user account was created.
     * Set automatically by database default.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when user profile was last updated.
     * Automatically updated by database trigger.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
