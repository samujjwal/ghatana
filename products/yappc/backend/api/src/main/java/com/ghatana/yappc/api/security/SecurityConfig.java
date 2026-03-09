/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - Security Configuration
 * 
 * Configuration for security settings including role permissions,
 * authentication requirements, and security policies.
 */

package com.ghatana.yappc.api.security;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security configuration for YAPPC API.
 * 
 * Provides:
 * - Role-based permission mappings
 * - Security policy settings
 * - Authentication configuration
 * - Tenant isolation rules
  *
 * @doc.type class
 * @doc.purpose security config
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class SecurityConfig {
    
    private final Map<String, List<Permission>> rolePermissions;
    private final boolean requireAuthentication;
    private final boolean enforceTenantIsolation;
    private final int maxFailedAttempts;
    private final long lockoutDurationMinutes;
    
    public SecurityConfig() {
        this.rolePermissions = new ConcurrentHashMap<>();
        this.requireAuthentication = true;
        this.enforceTenantIsolation = true;
        this.maxFailedAttempts = 5;
        this.lockoutDurationMinutes = 15;
        
        // Initialize default role permissions
        initializeDefaultPermissions();
    }
    
    public SecurityConfig(@NotNull Map<String, List<Permission>> rolePermissions,
                         boolean requireAuthentication,
                         boolean enforceTenantIsolation,
                         int maxFailedAttempts,
                         long lockoutDurationMinutes) {
        this.rolePermissions = new ConcurrentHashMap<>(rolePermissions);
        this.requireAuthentication = requireAuthentication;
        this.enforceTenantIsolation = enforceTenantIsolation;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
    }
    
    /**
     * Gets the permissions for a specific role.
     */
    @NotNull
    public List<Permission> getRolePermissions(@NotNull String role) {
        return rolePermissions.getOrDefault(role, List.of());
    }
    
    /**
     * Adds permissions for a role.
     */
    public void addRolePermissions(@NotNull String role, @NotNull List<Permission> permissions) {
        rolePermissions.put(role, permissions);
    }
    
    /**
     * Adds a permission for a role.
     */
    public void addRolePermission(@NotNull String role, @NotNull Permission permission) {
        rolePermissions.compute(role, (k, existing) -> {
            if (existing == null) {
                return List.of(permission);
            } else {
                return List.copyOf(java.util.stream.Stream.concat(
                    existing.stream(),
                    java.util.stream.Stream.of(permission)
                ).toList());
            }
        });
    }
    
    /**
     * Checks if authentication is required.
     */
    public boolean isRequireAuthentication() {
        return requireAuthentication;
    }
    
    /**
     * Checks if tenant isolation is enforced.
     */
    public boolean isEnforceTenantIsolation() {
        return enforceTenantIsolation;
    }
    
    /**
     * Gets the maximum number of failed login attempts.
     */
    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }
    
    /**
     * Gets the lockout duration in minutes.
     */
    public long getLockoutDurationMinutes() {
        return lockoutDurationMinutes;
    }
    
    /**
     * Initializes default role permissions.
     */
    private void initializeDefaultPermissions() {
        // Admin permissions - full access
        rolePermissions.put("admin", List.of(
            Permission.crud("/api/v1/**"),
            Permission.crud("/admin/**"),
            Permission.crud("/system/**")
        ));
        
        // Tenant admin permissions - tenant-scoped full access
        rolePermissions.put("tenant_admin", List.of(
            Permission.crud("/api/v1/tenants/{tenantId}/**"),
            Permission.crud("/api/v1/projects/**"),
            Permission.crud("/api/v1/agents/**"),
            Permission.readOnly("/api/v1/users/**"),
            Permission.readOnly("/api/v1/tenants/**")
        ));
        
        // Developer permissions - project and agent access
        rolePermissions.put("developer", List.of(
            Permission.crud("/api/v1/projects/**"),
            Permission.readOnly("/api/v1/agents/**"),
            Permission.readOnly("/api/v1/tenants/{tenantId}"),
            Permission.methods("/api/v1/agents/**", "GET", "POST"),
            Permission.crud("/api/v1/lifecycle/**")
        ));
        
        // Analyst permissions - read-only access
        rolePermissions.put("analyst", List.of(
            Permission.readOnly("/api/v1/projects/**"),
            Permission.readOnly("/api/v1/agents/**"),
            Permission.readOnly("/api/v1/lifecycle/**"),
            Permission.readOnly("/api/v1/tenants/{tenantId}")
        ));
        
        // Viewer permissions - limited read access
        rolePermissions.put("viewer", List.of(
            Permission.readOnly("/api/v1/projects/{projectId}"),
            Permission.readOnly("/api/v1/lifecycle/{projectId}/**"),
            Permission.readOnly("/api/v1/tenants/{tenantId}")
        ));
        
        // System service permissions - internal service access
        rolePermissions.put("system_service", List.of(
            Permission.crud("/internal/**"),
            Permission.crud("/system/health/**"),
            Permission.crud("/system/metrics/**")
        ));
    }
    
    /**
     * Creates a default security configuration.
     */
    public static SecurityConfig createDefault() {
        return new SecurityConfig();
    }
    
    /**
     * Creates a development security configuration with relaxed settings.
     */
    public static SecurityConfig createDevelopment() {
        SecurityConfig config = new SecurityConfig();
        // Add developer permissions to all roles in development
        config.addRolePermissions("admin", List.of(
            Permission.crud("/api/v1/**"),
            Permission.crud("/dev/**")
        ));
        return config;
    }
    
    /**
     * Creates a production security configuration with strict settings.
     */
    public static SecurityConfig createProduction() {
        return new SecurityConfig(
            Map.of(
                "admin", List.of(Permission.crud("/api/v1/**")),
                "tenant_admin", List.of(Permission.crud("/api/v1/tenants/{tenantId}/**")),
                "developer", List.of(
                    Permission.crud("/api/v1/projects/**"),
                    Permission.readOnly("/api/v1/agents/**")
                ),
                "analyst", List.of(Permission.readOnly("/api/v1/**")),
                "viewer", List.of(Permission.readOnly("/api/v1/projects/{projectId}/**"))
            ),
            true,  // require authentication
            true,  // enforce tenant isolation
            3,     // max failed attempts
            30     // lockout duration minutes
        );
    }
}
