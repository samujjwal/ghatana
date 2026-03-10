package com.ghatana.yappc.ai.requirements.domain.workspace;

import java.time.Instant;
import java.util.Objects;

/**
 * Workspace member record.
 *
 * <p><b>Purpose</b><br>
 * Represents a user's membership in a workspace with their assigned role
 * and metadata about when they joined.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkspaceMember member = new WorkspaceMember(
 *     userId,
 *     WorkspaceRole.MEMBER,
 *     Instant.now()
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable. Safe for concurrent access.
 *
 * @doc.type record
 * @doc.purpose Workspace membership
 * @doc.layer product
 * @doc.pattern Value Object
 * @see Workspace
 * @see WorkspaceRole
 */
public record WorkspaceMember(
    String userId,
    WorkspaceRole role,
    Instant joinedAt
) {
    /**
     * Creates workspace member with validation.
     *
     * @param userId User ID (cannot be null/blank)
     * @param role Role in workspace (cannot be null)
     * @param joinedAt Join timestamp (cannot be null)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if userId is blank
     */
    public WorkspaceMember {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(joinedAt, "joinedAt cannot be null");

        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be blank");
        }
    }
}