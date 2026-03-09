package com.ghatana.platform.security.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an access control policy.
 * A policy defines the permissions granted to a role for a specific resource.
 */
/**
 * Policy.
 *
 * @doc.type class
 * @doc.purpose Policy
 * @doc.layer core
 * @doc.pattern Component
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Policy {

    /**
     * The unique identifier of the policy.
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * The name of the policy.
     */
    private String name;

    /**
     * The description of the policy.
     */
    private String description;

    /**
     * The role to which this policy applies.
     */
    private String role;

    /**
     * The resource to which this policy applies.
     * This can be a specific resource ID or a resource pattern.
     */
    private String resource;

    /**
     * The permissions granted by this policy.
     */
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    /**
     * Whether this policy is enabled.
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Adds a permission to the policy.
     *
     * @param permission The permission to add
     * @return This policy
     */
    public Policy addPermission(String permission) {
        permissions.add(permission);
        return this;
    }

    /**
     * Removes a permission from the policy.
     *
     * @param permission The permission to remove
     * @return This policy
     */
    public Policy removePermission(String permission) {
        permissions.remove(permission);
        return this;
    }

    /**
     * Checks if the policy grants the specified permission.
     *
     * @param permission The permission to check
     * @return true if the policy grants the permission, false otherwise
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Checks if the policy applies to the specified resource.
     *
     * @param resourceId The resource ID
     * @return true if the policy applies to the resource, false otherwise
     */
    public boolean appliesTo(String resourceId) {
        if (resource == null || resourceId == null) {
            return false;
        }
        
        // Exact match
        if (resource.equals(resourceId)) {
            return true;
        }
        
        // Wildcard match
        if (resource.endsWith("*")) {
            String prefix = resource.substring(0, resource.length() - 1);
            return resourceId.startsWith(prefix);
        }
        
        return false;
    }

    /**
     * Checks if the policy is valid.
     *
     * @return true if the policy is valid, false otherwise
     */
    public boolean isValid() {
        return enabled && role != null && resource != null && !permissions.isEmpty();
    }

    /**
     * Creates a copy of this policy with the specified permissions.
     *
     * @param newPermissions the permissions for the new policy
     * @return a new Policy with the specified permissions
     */
    public Policy withPermissions(Set<String> newPermissions) {
        return Policy.builder()
                .id(this.id)
                .name(this.name)
                .description(this.description)
                .role(this.role)
                .resource(this.resource)
                .permissions(newPermissions != null ? new HashSet<>(newPermissions) : new HashSet<>())
                .enabled(this.enabled)
                .build();
    }
}
