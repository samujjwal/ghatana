package com.ghatana.datacloud.config.model;

import java.util.List;
import java.util.Objects;

/**
 * Compiled permissions configuration for access control.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled permissions configuration for access control
 * @doc.layer core
 * @doc.pattern Immutable Value Object
 */
public record CompiledPermissionsConfig(
        List<CompiledPermissionRule> readRules,
        List<CompiledPermissionRule> writeRules,
        List<CompiledPermissionRule> deleteRules
        ) {

    /**
     * Creates a CompiledPermissionsConfig with defensive copies.
     */
    public CompiledPermissionsConfig   {
        readRules = readRules != null ? List.copyOf(readRules) : List.of();
        writeRules = writeRules != null ? List.copyOf(writeRules) : List.of();
        deleteRules = deleteRules != null ? List.copyOf(deleteRules) : List.of();
    }

    /**
     * Create a default open permissions config.
     *
     * @return permissions config allowing all operations
     */
    public static CompiledPermissionsConfig openAccess() {
        var anyRole = new CompiledPermissionRule("*", null);
        return new CompiledPermissionsConfig(
                List.of(anyRole),
                List.of(anyRole),
                List.of(anyRole)
        );
    }

    /**
     * Create a read-only permissions config.
     *
     * @param roles roles allowed to read
     * @return permissions config allowing only read
     */
    public static CompiledPermissionsConfig readOnly(List<String> roles) {
        var rules = roles.stream()
                .map(role -> new CompiledPermissionRule(role, null))
                .toList();
        return new CompiledPermissionsConfig(rules, List.of(), List.of());
    }

    /**
     * Check if any role can read.
     *
     * @return true if any role has read access
     */
    public boolean hasAnyReadAccess() {
        return !readRules.isEmpty();
    }

    /**
     * Permission rule for a specific role.
     */
    public record CompiledPermissionRule(
            String role,
            String condition
    ) {
        

    public CompiledPermissionRule   {
        Objects.requireNonNull(role, "Role cannot be null");
    }

    /**
     * Check if this rule has a condition.
     *
     * @return true if conditional
     */
    public boolean isConditional() {
        return condition != null && !condition.isBlank();
    }

    /**
     * Check if this is a wildcard rule (matches all roles).
     *
     * @return true if wildcard
     */
    public boolean isWildcard() {
        return "*".equals(role);
    }
}
}
