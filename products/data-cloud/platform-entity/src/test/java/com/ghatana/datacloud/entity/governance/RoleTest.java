package com.ghatana.datacloud.entity.governance;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    @Test
    void shouldBuildRoleWithExplicitPermissionsAndInheritance() {
        Instant createdAt = Instant.parse("2026-04-21T10:15:30Z");

        Role role = Role.builder("operator")
                .description("Operational diagnostics access")
                .permission("collection:read")
                .permission("audit:read")
                .inheritsFrom("viewer")
                .asSystemRole()
                .createdAt(createdAt)
                .build();

        assertThat(role.getRoleId()).isEqualTo("operator");
        assertThat(role.getName()).isEqualTo("operator");
        assertThat(role.getDescription()).isEqualTo("Operational diagnostics access");
        assertThat(role.getPermissions()).containsExactlyInAnyOrder("collection:read", "audit:read");
        assertThat(role.getInheritedRoles()).containsExactly("viewer");
        assertThat(role.isSystemRole()).isTrue();
        assertThat(role.getCreatedAt()).isEqualTo(createdAt);
        assertThat(role.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldSupportExactAndWildcardPermissionChecks() {
        Role exactRole = Role.builder("viewer")
                .permission("collection:read")
                .build();

        Role wildcardRole = Role.builder("admin")
                .permission("*")
                .build();

        assertThat(exactRole.hasPermission("collection:read")).isTrue();
        assertThat(exactRole.hasPermission("collection:write")).isFalse();
        assertThat(wildcardRole.hasPermission("rbac:manage")).isTrue();
    }

    @Test
    void shouldExposeImmutablePermissionAndInheritanceSets() {
        Role role = Role.builder("viewer")
                .permission("collection:read")
                .inheritsFrom("base-viewer")
                .build();

        assertThatThrownBy(() -> role.getPermissions().add("collection:write"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> role.getInheritedRoles().add("admin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldCompareRolesWithoutTimestampSensitivity() {
        Role first = new Role(
                "viewer",
                "viewer",
                "Read-only",
                Set.of("collection:read"),
                Set.of(),
                false,
                Instant.parse("2026-04-21T00:00:00Z"),
                Instant.parse("2026-04-21T00:00:05Z")
        );

        Role second = new Role(
                "viewer",
                "viewer",
                "Read-only",
                Set.of("collection:read"),
                Set.of(),
                false,
                Instant.parse("2026-04-20T00:00:00Z"),
                Instant.parse("2026-04-22T00:00:00Z")
        );

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.toString()).contains("viewer", "permissions=1");
    }
}