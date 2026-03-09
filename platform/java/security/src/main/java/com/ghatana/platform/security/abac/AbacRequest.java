package com.ghatana.platform.security.abac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Attribute-Based Access Control (ABAC) authorization request.
 *
 * <p>Encapsulates the subject, resource, action, and environment attributes
 * needed for a fine-grained authorization decision. Works alongside RBAC:
 * RBAC gates coarse access, ABAC evaluates attribute-level conditions.
 *
 * @doc.type record
 * @doc.purpose ABAC authorization request
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record AbacRequest(
    /** Subject attributes (userId, roles, department, clearance, etc.) */
    @NotNull Map<String, Object> subject,
    /** Resource attributes (type, owner, sensitivity, tenant, etc.) */
    @NotNull Map<String, Object> resource,
    /** Action being performed (read, write, delete, execute, etc.) */
    @NotNull String action,
    /** Environment attributes (time, IP, device, location, etc.) */
    @NotNull Map<String, Object> environment
) {
    public AbacRequest {
        subject = Map.copyOf(subject);
        resource = Map.copyOf(resource);
        environment = Map.copyOf(environment);
    }

    /**
     * Convenience factory for simple requests.
     */
    public static AbacRequest of(
            @NotNull String userId,
            @NotNull String resourceType,
            @NotNull String action) {
        return new AbacRequest(
            Map.of("userId", userId),
            Map.of("type", resourceType),
            action,
            Map.of()
        );
    }
}
