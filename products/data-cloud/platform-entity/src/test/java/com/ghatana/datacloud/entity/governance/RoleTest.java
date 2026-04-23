package com.ghatana.datacloud.entity.governance;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    @Test
    void shouldBuildRoleWithExplicitPermissionsAndInheritance() { // GH-90000
        Instant createdAt = Instant.parse("2026-04-21T10:15:30Z");

        Role role = Role.builder("operator")
                .description("Operational diagnostics access")
                .permission("collection:read")
                .permission("audit:read")
                .inheritsFrom("viewer")
                .asSystemRole() // GH-90000
                .createdAt(createdAt) // GH-90000
                .build(); // GH-90000

        assertThat(role.getRoleId()).isEqualTo("operator");
        assertThat(role.getName()).isEqualTo("operator");
        assertThat(role.getDescription()).isEqualTo("Operational diagnostics access");
        assertThat(role.getPermissions()).containsExactlyInAnyOrder("collection:read", "audit:read"); // GH-90000
        assertThat(role.getInheritedRoles()).containsExactly("viewer");
        assertThat(role.isSystemRole()).isTrue(); // GH-90000
        assertThat(role.getCreatedAt()).isEqualTo(createdAt); // GH-90000
        assertThat(role.getUpdatedAt()).isNotNull(); // GH-90000
    }

    @Test
    void shouldSupportExactAndWildcardPermissionChecks() { // GH-90000
        Role exactRole = Role.builder("viewer")
                .permission("collection:read")
                .build(); // GH-90000

        Role wildcardRole = Role.builder("admin")
                .permission("*")
                .build(); // GH-90000

        assertThat(exactRole.hasPermission("collection:read")).isTrue();
        assertThat(exactRole.hasPermission("collection:write")).isFalse();
        assertThat(wildcardRole.hasPermission("rbac:manage")).isTrue();
    }

    @Test
    void shouldExposeImmutablePermissionAndInheritanceSets() { // GH-90000
        Role role = Role.builder("viewer")
                .permission("collection:read")
                .inheritsFrom("base-viewer")
                .build(); // GH-90000

        assertThatThrownBy(() -> role.getPermissions().add("collection:write"))
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        assertThatThrownBy(() -> role.getInheritedRoles().add("admin"))
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    @Test
    void shouldCompareRolesWithoutTimestampSensitivity() { // GH-90000
        Role first = new Role( // GH-90000
                "viewer",
                "viewer",
                "Read-only",
                Set.of("collection:read"),
                Set.of(), // GH-90000
                false,
                Instant.parse("2026-04-21T00:00:00Z"),
                Instant.parse("2026-04-21T00:00:05Z")
        );

        Role second = new Role( // GH-90000
                "viewer",
                "viewer",
                "Read-only",
                Set.of("collection:read"),
                Set.of(), // GH-90000
                false,
                Instant.parse("2026-04-20T00:00:00Z"),
                Instant.parse("2026-04-22T00:00:00Z")
        );

        assertThat(first).isEqualTo(second); // GH-90000
        assertThat(first.hashCode()).isEqualTo(second.hashCode()); // GH-90000
        assertThat(first.toString()).contains("viewer", "permissions=1"); // GH-90000
    }
}