/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — Simple Security Tests
 */
package com.ghatana.yappc.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles simple security test operations

 * @doc.layer product

 * @doc.pattern Test

 */

class SimpleSecurityTest {

    private UserContext userContext;
    private Permission permission;

    @BeforeEach
    void setUp() {
        userContext = new UserContext.Builder()
            .userId("user123")
            .email("test@ghatana.com")
            .userName("Test User")
            .tenantId("tenant123")
            .roles(List.of("user", "admin"))
            .permissions(List.of(
                new Permission("/api/**", List.of("GET", "POST"), "Full access")
            ))
            .build();

        permission = new Permission("/api/**", List.of("GET", "POST"), "API access");
    }

    @Test
    void userContextBuilder() {
        // Then
        assertThat(userContext.getUserId()).isEqualTo("user123");
        assertThat(userContext.getEmail()).isEqualTo("test@ghatana.com");
        assertThat(userContext.getUserName()).isEqualTo("Test User");
        assertThat(userContext.getTenantId()).isEqualTo("tenant123");
        assertThat(userContext.getRoles()).containsExactly("user", "admin");
        assertThat(userContext.getPermissions()).hasSize(1);
    }

    @Test
    void userContextHasRole() {
        // When/Then
        assertThat(userContext.hasRole("user")).isTrue();
        assertThat(userContext.hasRole("admin")).isTrue();
        assertThat(userContext.hasRole("superuser")).isFalse();
        // Test with null/empty strings - these should return false or throw NPE
        assertThat(userContext.hasRole("")).isFalse();
    }

    @Test
    void userContextHasPermission() {
        // When/Then
        assertThat(userContext.hasPermission("/api/users", "GET")).isTrue();
        assertThat(userContext.hasPermission("/api/users", "POST")).isTrue();
        assertThat(userContext.hasPermission("/api/users", "PUT")).isFalse();
        assertThat(userContext.hasPermission("/admin/**", "GET")).isFalse();
    }

    @Test
    void permissionCreation() {
        // Then
        assertThat(permission.getPathPattern()).isEqualTo("/api/**");
        assertThat(permission.getMethods()).containsExactly("GET", "POST");
        assertThat(permission.getDescription()).isEqualTo("API access");
    }

    @Test
    void permissionAllowsMethod() {
        // When/Then
        assertThat(permission.allowsMethod("GET")).isTrue();
        assertThat(permission.allowsMethod("POST")).isTrue();
        assertThat(permission.allowsMethod("PUT")).isFalse();
    }

    @Test
    void permissionMatchesPath() {
        // When/Then
        assertThat(permission.matchesPath("/api/users")).isTrue();
        assertThat(permission.matchesPath("/api/users/123")).isTrue();
        assertThat(permission.matchesPath("/admin/users")).isFalse();
    }

    @Test
    void permissionAllowsAccess() {
        // When/Then
        assertThat(permission.allows("/api/users", "GET")).isTrue();
        assertThat(permission.allows("/api/users", "POST")).isTrue();
        assertThat(permission.allows("/api/users", "PUT")).isFalse();
        assertThat(permission.allows("/admin/users", "GET")).isFalse();
    }

    @Test
    void permissionStaticMethods() {
        // When
        Permission allMethods = Permission.allMethods("/api/**");
        Permission readOnly = Permission.readOnly("/api/**");
        Permission writeOnly = Permission.writeOnly("/api/**");
        Permission crud = Permission.crud("/api/**");
        Permission methods = Permission.methods("/api/**", "GET", "POST");

        // Then
        assertThat(allMethods.getMethods()).isEmpty();
        assertThat(readOnly.getMethods()).containsExactly("GET", "HEAD", "OPTIONS");
        assertThat(writeOnly.getMethods()).containsExactly("POST", "PUT", "PATCH", "DELETE");
        assertThat(crud.getMethods()).containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
        assertThat(methods.getMethods()).containsExactly("GET", "POST");
    }

    @Test
    void permissionEqualsAndHashCode() {
        // Given
        Permission permission1 = new Permission("/api/**", List.of("GET", "POST"), "description");
        Permission permission2 = new Permission("/api/**", List.of("GET", "POST"), "description");
        Permission permission3 = new Permission("/api/**", List.of("GET"), "description");

        // Then
        assertThat(permission1).isEqualTo(permission2);
        assertThat(permission1.hashCode()).isEqualTo(permission2.hashCode());
        assertThat(permission1).isNotEqualTo(permission3);
    }

    @Test
    void userContextEqualsAndHashCode() {
        // Given
        UserContext user1 = new UserContext.Builder()
            .userId("user123")
            .email("test@ghatana.com")
            .userName("Test User")
            .tenantId("tenant123")
            .build();

        UserContext user2 = new UserContext.Builder()
            .userId("user123")
            .email("different@ghatana.com") // Different email but same userId and tenantId
            .userName("Different User") // Different userName but same userId and tenantId
            .tenantId("tenant123")
            .build();

        UserContext user3 = new UserContext.Builder()
            .userId("different")
            .email("test@ghatana.com")
            .userName("Test User")
            .tenantId("tenant123")
            .build();

        UserContext user4 = new UserContext.Builder()
            .userId("user123")
            .email("test@ghatana.com")
            .userName("Test User")
            .tenantId("different") // Different tenantId
            .build();

        // Then - equals only checks userId and tenantId
        assertThat(user1).isEqualTo(user2); // Same userId and tenantId
        assertThat(user1.hashCode()).isEqualTo(user2.hashCode());
        assertThat(user1).isNotEqualTo(user3); // Different userId
        assertThat(user1).isNotEqualTo(user4); // Different tenantId
    }

    @Test
    void userContextToString() {
        // When
        String stringRepresentation = userContext.toString();

        // Then
        assertThat(stringRepresentation).contains("user123");
        assertThat(stringRepresentation).contains("test@ghatana.com");
        assertThat(stringRepresentation).contains("Test User");
    }

    @Test
    void permissionToString() {
        // When
        String stringRepresentation = permission.toString();

        // Then
        assertThat(stringRepresentation).contains("/api/**");
        assertThat(stringRepresentation).contains("GET");
        assertThat(stringRepresentation).contains("POST");
        assertThat(stringRepresentation).contains("API access");
    }

    @Test
    void permissionConstructorValidation() {
        // When/Then
        assertThatThrownBy(() -> new Permission(null, List.of("GET"), "description"))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Permission("/api/**", null, "description"))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Permission("/api/**", List.of("GET"), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void userContextBuilderValidation() {
        // When/Then
        assertThatThrownBy(() -> new UserContext.Builder().userId(null).build())
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new UserContext.Builder().email(null).build())
            .isInstanceOf(NullPointerException.class);
    }
}
