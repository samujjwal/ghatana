package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.security.rbac.InMemoryRolePermissionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests canonical role-to-permission mappings used by Data Cloud security filter.
 *
 * @doc.type class
 * @doc.purpose Validate canonical Data Cloud permission mappings for connector/media/action routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudPermissionRegistry")
class DataCloudPermissionRegistryTest {

    @Test
    @DisplayName("ADMIN includes full connector/media/action permissions")
    void adminIncludesCanonicalConnectorMediaAndActionPermissions() {
        DataCloudPermissionRegistry registry =
            new DataCloudPermissionRegistry(new InMemoryRolePermissionRegistry());
        registry.initialize();

        Set<String> admin = registry.getPermissions("ADMIN");

        assertThat(admin).contains(
            "connector:register",
            "connector:rotate-credentials",
            "connector:link-dataset",
            "media:artifact:update-consent",
            "media:artifact:retry",
            "action:pipeline:write",
            "action:pattern:activate",
            "action:review:approve"
        );
    }

    @Test
    @DisplayName("OPERATOR excludes destructive and admin connector permissions")
    void operatorExcludesDestructiveConnectorPermissions() {
        DataCloudPermissionRegistry registry =
            new DataCloudPermissionRegistry(new InMemoryRolePermissionRegistry());
        registry.initialize();

        Set<String> operator = registry.getPermissions("OPERATOR");

        assertThat(operator).contains(
            "connector:register",
            "connector:test",
            "connector:sync",
            "media:artifact:process",
            "action:pipeline:execute"
        );
        assertThat(operator).doesNotContain(
            "connector:delete",
            "connector:rotate-credentials",
            "action:review:approve"
        );
    }

    @Test
    @DisplayName("VIEWER remains read-only for connector/media/action")
    void viewerReadOnlyPolicy() {
        DataCloudPermissionRegistry registry =
            new DataCloudPermissionRegistry(new InMemoryRolePermissionRegistry());
        registry.initialize();

        Set<String> viewer = registry.getPermissions("VIEWER");

        assertThat(viewer).contains(
            "connector:read",
            "media:artifact:read",
            "media:artifact:read-result",
            "action:pipeline:read",
            "action:agent:read",
            "action:pattern:read"
        );
        assertThat(viewer).doesNotContain(
            "connector:register",
            "media:artifact:process",
            "action:pipeline:execute"
        );
    }
}
