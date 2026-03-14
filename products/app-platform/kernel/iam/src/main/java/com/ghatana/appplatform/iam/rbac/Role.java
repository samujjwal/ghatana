package com.ghatana.appplatform.iam.rbac;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * A named set of permissions scoped to a tenant.
 *
 * @doc.type record
 * @doc.purpose RBAC role value object (STORY-K01-RBAC)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record Role(
    String roleId,
    String roleName,
    String tenantId,   // null for platform-wide roles
    String description,
    Set<Permission> permissions
) {
    public Role {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(roleName, "roleName");
        permissions = permissions != null
            ? Collections.unmodifiableSet(permissions)
            : Collections.emptySet();
    }

    public boolean hasPermission(String resource, String action) {
        return permissions.stream()
            .anyMatch(p -> p.resource().equals(resource) && p.action().equals(action));
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
