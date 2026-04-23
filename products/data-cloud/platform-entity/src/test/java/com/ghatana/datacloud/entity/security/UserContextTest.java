package com.ghatana.datacloud.entity.security;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextTest {

    @Test
    void shouldGrantAdminBypassWithoutExplicitPermissions() { // GH-90000
        UserContext context = UserContext.builder() // GH-90000
                .userId("admin-user")
                .tenantId("tenant-a")
                .roles(Set.of(UserRole.ADMIN)) // GH-90000
                .build(); // GH-90000

        assertThat(context.hasRole(UserRole.ADMIN)).isTrue(); // GH-90000
        assertThat(context.hasPermission("collection", "delete")).isTrue(); // GH-90000
        assertThat(context.hasPermission("schema", "write")).isTrue(); // GH-90000
    }

    @Test
    void shouldUseExplicitPermissionsForNonAdminUsers() { // GH-90000
        UserContext context = UserContext.builder() // GH-90000
                .userId("editor-user")
                .tenantId("tenant-a")
                .roles(Set.of(UserRole.EDITOR)) // GH-90000
                .permissions(Map.of( // GH-90000
                        "collection", Set.of("read", "write"), // GH-90000
                        "entity", Set.of("read")
                ))
                .build(); // GH-90000

        assertThat(context.hasPermission("collection", "read")).isTrue(); // GH-90000
        assertThat(context.hasPermission("collection", "write")).isTrue(); // GH-90000
        assertThat(context.hasPermission("collection", "delete")).isFalse(); // GH-90000
        assertThat(context.hasPermission("entity", "read")).isTrue(); // GH-90000
        assertThat(context.hasPermission("entity", "write")).isFalse(); // GH-90000
    }

    @Test
    void shouldExposeImmutableRoleAndPermissionCollections() { // GH-90000
        UserContext context = UserContext.builder() // GH-90000
                .userId("viewer-user")
                .tenantId("tenant-a")
                .roles(Set.of(UserRole.VIEWER)) // GH-90000
                .permissions(Map.of("collection", Set.of("read")))
                .build(); // GH-90000

        assertThatThrownBy(() -> context.getRoles().add(UserRole.ADMIN)) // GH-90000
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        assertThatThrownBy(() -> context.getPermissions().put("schema", Set.of("write")))
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    @Test
    void shouldTreatIssuedAtZeroAsNonExpiringAndEnforceTtlOtherwise() { // GH-90000
        UserContext nonExpiring = UserContext.builder() // GH-90000
                .userId("service-user")
                .tenantId("tenant-a")
                .issuedAtMs(0) // GH-90000
                .build(); // GH-90000

        UserContext fresh = UserContext.builder() // GH-90000
                .userId("fresh-user")
                .tenantId("tenant-a")
                .issuedAtMs(System.currentTimeMillis() - 3_599_000) // GH-90000
                .build(); // GH-90000

        UserContext expired = UserContext.builder() // GH-90000
                .userId("expired-user")
                .tenantId("tenant-a")
                .issuedAtMs(System.currentTimeMillis() - 3_601_000) // GH-90000
                .build(); // GH-90000

        assertThat(nonExpiring.isValid()).isTrue(); // GH-90000
        assertThat(fresh.isValid()).isTrue(); // GH-90000
        assertThat(expired.isValid()).isFalse(); // GH-90000
    }

    @Test
    void shouldCompareEqualityByUserAndTenantIdentity() { // GH-90000
        UserContext first = UserContext.builder() // GH-90000
                .userId("user-1")
                .tenantId("tenant-a")
                .roles(Set.of(UserRole.VIEWER)) // GH-90000
                .build(); // GH-90000

        UserContext second = UserContext.builder() // GH-90000
                .userId("user-1")
                .tenantId("tenant-a")
                .roles(Set.of(UserRole.ADMIN)) // GH-90000
                .permissions(Map.of("collection", Set.of("delete")))
                .build(); // GH-90000

        UserContext differentTenant = UserContext.builder() // GH-90000
                .userId("user-1")
                .tenantId("tenant-b")
                .build(); // GH-90000

        assertThat(first).isEqualTo(second); // GH-90000
        assertThat(first.hashCode()).isEqualTo(second.hashCode()); // GH-90000
        assertThat(first).isNotEqualTo(differentTenant); // GH-90000
        assertThat(first.toString()).contains("user-1", "tenant-a"); // GH-90000
    }
}