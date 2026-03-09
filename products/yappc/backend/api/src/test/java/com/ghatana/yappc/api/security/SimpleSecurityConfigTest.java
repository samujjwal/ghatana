/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — Simple SecurityConfig Tests
 */
package com.ghatana.yappc.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles simple security config test operations

 * @doc.layer product

 * @doc.pattern Test

 */

class SimpleSecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig();
    }

    @Test
    void defaultConfigurationValues() {
        // Then
        assertThat(securityConfig.isRequireAuthentication()).isTrue();
        assertThat(securityConfig.isEnforceTenantIsolation()).isTrue();
        assertThat(securityConfig.getMaxFailedAttempts()).isEqualTo(5);
        assertThat(securityConfig.getLockoutDurationMinutes()).isEqualTo(15);
    }

    @Test
    void defaultRolePermissionsAreInitialized() {
        // Then
        assertThat(securityConfig.getRolePermissions("admin")).isNotEmpty();
        assertThat(securityConfig.getRolePermissions("tenant_admin")).isNotEmpty();
        assertThat(securityConfig.getRolePermissions("developer")).isNotEmpty();
        assertThat(securityConfig.getRolePermissions("analyst")).isNotEmpty();
        assertThat(securityConfig.getRolePermissions("viewer")).isNotEmpty();
        assertThat(securityConfig.getRolePermissions("system_service")).isNotEmpty();
    }

    @Test
    void adminHasFullAccess() {
        // When
        List<Permission> adminPermissions = securityConfig.getRolePermissions("admin");

        // Then
        assertThat(adminPermissions).anyMatch(p -> p.getPathPattern().equals("/api/v1/**"));
        assertThat(adminPermissions).anyMatch(p -> p.getPathPattern().equals("/admin/**"));
        assertThat(adminPermissions).anyMatch(p -> p.getPathPattern().equals("/system/**"));
    }

    @Test
    void developerHasProjectAccess() {
        // When
        List<Permission> developerPermissions = securityConfig.getRolePermissions("developer");

        // Then
        assertThat(developerPermissions).anyMatch(p -> p.getPathPattern().equals("/api/v1/projects/**"));
        assertThat(developerPermissions).anyMatch(p -> p.getPathPattern().equals("/api/v1/agents/**"));
    }

    @Test
    void viewerHasLimitedAccess() {
        // When
        List<Permission> viewerPermissions = securityConfig.getRolePermissions("viewer");

        // Then
        assertThat(viewerPermissions).anyMatch(p -> p.getPathPattern().equals("/api/v1/projects/{projectId}"));
        assertThat(viewerPermissions).allMatch(p -> p.getMethods().contains("GET"));
    }

    @Test
    void getRolePermissionsForNonExistingRole() {
        // When
        List<Permission> permissions = securityConfig.getRolePermissions("non_existing_role");

        // Then
        assertThat(permissions).isEmpty();
    }

    @Test
    void addRolePermissions() {
        // Given
        List<Permission> newPermissions = List.of(
            Permission.readOnly("/api/v1/new/**"),
            Permission.crud("/api/v1/other/**")
        );

        // When
        securityConfig.addRolePermissions("new_role", newPermissions);

        // Then
        List<Permission> permissions = securityConfig.getRolePermissions("new_role");
        assertThat(permissions).isEqualTo(newPermissions);
    }

    @Test
    void addRolePermissionToExistingRole() {
        // Given
        securityConfig.addRolePermissions("existing_role", List.of(Permission.readOnly("/api/v1/existing/**")));
        Permission newPermission = Permission.crud("/api/v1/new/**");

        // When
        securityConfig.addRolePermission("existing_role", newPermission);

        // Then
        List<Permission> permissions = securityConfig.getRolePermissions("existing_role");
        assertThat(permissions).hasSize(2);
        assertThat(permissions).contains(Permission.readOnly("/api/v1/existing/**"));
        assertThat(permissions).contains(newPermission);
    }

    @Test
    void addRolePermissionToNewRole() {
        // Given
        Permission newPermission = Permission.crud("/api/v1/new/**");

        // When
        securityConfig.addRolePermission("new_role", newPermission);

        // Then
        List<Permission> permissions = securityConfig.getRolePermissions("new_role");
        assertThat(permissions).hasSize(1);
        assertThat(permissions.get(0)).isEqualTo(newPermission);
    }

    @Test
    void addRolePermissionsWithNullRole_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> securityConfig.addRolePermissions(null, List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addRolePermissionsWithNullPermissions_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> securityConfig.addRolePermissions("role", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addRolePermissionWithNullRole_ThrowsException() {
        // Given
        Permission permission = Permission.crud("/api/v1/test/**");

        // When/Then
        assertThatThrownBy(() -> securityConfig.addRolePermission(null, permission))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addRolePermissionWithNullPermission_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> securityConfig.addRolePermission("role", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getRolePermissionsWithNullRole_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> securityConfig.getRolePermissions(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createDefault() {
        // When
        SecurityConfig defaultConfig = SecurityConfig.createDefault();

        // Then
        assertThat(defaultConfig.isRequireAuthentication()).isTrue();
        assertThat(defaultConfig.isEnforceTenantIsolation()).isTrue();
        assertThat(defaultConfig.getMaxFailedAttempts()).isEqualTo(5);
        assertThat(defaultConfig.getLockoutDurationMinutes()).isEqualTo(15);
        assertThat(defaultConfig.getRolePermissions("admin")).isNotEmpty();
    }

    @Test
    void createDevelopment() {
        // When
        SecurityConfig devConfig = SecurityConfig.createDevelopment();

        // Then
        assertThat(devConfig.isRequireAuthentication()).isTrue();
        assertThat(devConfig.isEnforceTenantIsolation()).isTrue();
        assertThat(devConfig.getMaxFailedAttempts()).isEqualTo(5);
        assertThat(devConfig.getLockoutDurationMinutes()).isEqualTo(15);
        
        // Should have additional development permissions
        List<Permission> adminPermissions = devConfig.getRolePermissions("admin");
        assertThat(adminPermissions).anyMatch(p -> p.getPathPattern().equals("/dev/**"));
    }

    @Test
    void createProduction() {
        // When
        SecurityConfig prodConfig = SecurityConfig.createProduction();

        // Then
        assertThat(prodConfig.isRequireAuthentication()).isTrue();
        assertThat(prodConfig.isEnforceTenantIsolation()).isTrue();
        assertThat(prodConfig.getMaxFailedAttempts()).isEqualTo(3); // Stricter than default
        assertThat(prodConfig.getLockoutDurationMinutes()).isEqualTo(30); // Longer lockout
    }

    @Test
    void customConstructor() {
        // Given
        Map<String, List<Permission>> customPermissions = Map.of(
            "custom_role", List.of(Permission.readOnly("/api/v1/custom/**"))
        );

        // When
        SecurityConfig customConfig = new SecurityConfig(
            customPermissions,
            false,  // requireAuthentication
            false,  // enforceTenantIsolation
            10,     // maxFailedAttempts
            60      // lockoutDurationMinutes
        );

        // Then
        assertThat(customConfig.isRequireAuthentication()).isFalse();
        assertThat(customConfig.isEnforceTenantIsolation()).isFalse();
        assertThat(customConfig.getMaxFailedAttempts()).isEqualTo(10);
        assertThat(customConfig.getLockoutDurationMinutes()).isEqualTo(60);
        assertThat(customConfig.getRolePermissions("custom_role"))
            .isEqualTo(List.of(Permission.readOnly("/api/v1/custom/**")));
    }
}
