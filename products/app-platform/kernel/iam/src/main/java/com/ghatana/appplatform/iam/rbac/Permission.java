package com.ghatana.appplatform.iam.rbac;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a permission that can be granted to a role.
 * Format: {@code resource:action} (e.g. {@code ledger:read}, {@code payment:write}).
 *
 * @doc.type record
 * @doc.purpose RBAC permission value object (STORY-K01-RBAC)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record Permission(String resource, String action) {

    public Permission {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(action, "action");
        if (resource.isBlank()) throw new IllegalArgumentException("resource must not be blank");
        if (action.isBlank())   throw new IllegalArgumentException("action must not be blank");
    }

    /**
     * Parse a permission from {@code resource:action} notation.
     */
    public static Permission of(String resourceAction) {
        String[] parts = resourceAction.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Permission must be in resource:action format: " + resourceAction);
        }
        return new Permission(parts[0], parts[1]);
    }

    public String toKey() {
        return resource + ":" + action;
    }

    @Override
    public String toString() {
        return toKey();
    }
}
