/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link UserRole} enum.
 */
class UserRoleTest {

    @Test
    void allRolesShouldBeAccessible() { // GH-90000
        UserRole[] roles = UserRole.values(); // GH-90000
        assertThat(roles).hasSize(5); // GH-90000
    }

    @ParameterizedTest
    @EnumSource(UserRole.class) // GH-90000
    void eachRoleShouldHaveNonNullRoleId(UserRole role) { // GH-90000
        assertThat(role.getRoleId()).isNotNull().isNotEmpty(); // GH-90000
    }

    @ParameterizedTest
    @EnumSource(UserRole.class) // GH-90000
    void eachRoleShouldHaveNonNullDescription(UserRole role) { // GH-90000
        assertThat(role.getDescription()).isNotNull().isNotEmpty(); // GH-90000
    }

    @ParameterizedTest
    @EnumSource(UserRole.class) // GH-90000
    void eachRoleShouldHaveDefaultPermissions(UserRole role) { // GH-90000
        assertThat(role.getDefaultPermissions()).isNotNull(); // GH-90000
    }

    @Test
    void adminShouldHaveFullPermissions() { // GH-90000
        Set<String> adminPerms = UserRole.ADMIN.getDefaultPermissions(); // GH-90000
        assertThat(adminPerms).contains("collection:read", "collection:write", "collection:delete"); // GH-90000
        assertThat(adminPerms).contains("entity:read", "entity:write", "entity:delete"); // GH-90000
        assertThat(adminPerms).contains("user:read", "user:write", "user:delete"); // GH-90000
    }

    @Test
    void viewerShouldHaveReadOnlyPermissions() { // GH-90000
        Set<String> viewerPerms = UserRole.VIEWER.getDefaultPermissions(); // GH-90000
        assertThat(viewerPerms).containsOnly("collection:read", "entity:read", "schema:read"); // GH-90000
    }

    @Test
    void editorShouldHaveLimitedWritePermissions() { // GH-90000
        Set<String> editorPerms = UserRole.EDITOR.getDefaultPermissions(); // GH-90000
        assertThat(editorPerms).contains("entity:write [GH-90000]");
        assertThat(editorPerms).doesNotContain("entity:delete [GH-90000]");
        assertThat(editorPerms).doesNotContain("collection:write [GH-90000]");
    }

    @Test
    void fromRoleIdShouldReturnCorrectEnum() { // GH-90000
        assertThat(UserRole.fromRoleId("admin [GH-90000]")).isEqualTo(UserRole.ADMIN);
        assertThat(UserRole.fromRoleId("curator [GH-90000]")).isEqualTo(UserRole.CURATOR);
        assertThat(UserRole.fromRoleId("editor [GH-90000]")).isEqualTo(UserRole.EDITOR);
        assertThat(UserRole.fromRoleId("reviewer [GH-90000]")).isEqualTo(UserRole.REVIEWER);
        assertThat(UserRole.fromRoleId("viewer [GH-90000]")).isEqualTo(UserRole.VIEWER);
    }

    @Test
    void fromRoleIdShouldBeCaseInsensitive() { // GH-90000
        assertThat(UserRole.fromRoleId("ADMIN [GH-90000]")).isEqualTo(UserRole.ADMIN);
        assertThat(UserRole.fromRoleId("Admin [GH-90000]")).isEqualTo(UserRole.ADMIN);
        assertThat(UserRole.fromRoleId("EDITOR [GH-90000]")).isEqualTo(UserRole.EDITOR);
    }

    @Test
    void fromRoleIdShouldThrowForNull() { // GH-90000
        assertThatThrownBy(() -> UserRole.fromRoleId(null)) // GH-90000
            .isInstanceOf(NullPointerException.class) // GH-90000
            .hasMessageContaining("roleId cannot be null [GH-90000]");
    }

    @Test
    void fromRoleIdShouldThrowForUnknownRole() { // GH-90000
        assertThatThrownBy(() -> UserRole.fromRoleId("unknown_role [GH-90000]"))
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Unknown role ID [GH-90000]");
    }

    @Test
    void adminShouldIncludeAllOtherRoles() { // GH-90000
        assertThat(UserRole.ADMIN.includes(UserRole.CURATOR)).isTrue(); // GH-90000
        assertThat(UserRole.ADMIN.includes(UserRole.EDITOR)).isTrue(); // GH-90000
        assertThat(UserRole.ADMIN.includes(UserRole.REVIEWER)).isTrue(); // GH-90000
        assertThat(UserRole.ADMIN.includes(UserRole.VIEWER)).isTrue(); // GH-90000
        assertThat(UserRole.ADMIN.includes(UserRole.ADMIN)).isTrue(); // GH-90000
    }

    @Test
    void curatorShouldIncludeEditorReviewerAndViewer() { // GH-90000
        assertThat(UserRole.CURATOR.includes(UserRole.EDITOR)).isTrue(); // GH-90000
        assertThat(UserRole.CURATOR.includes(UserRole.REVIEWER)).isTrue(); // GH-90000
        assertThat(UserRole.CURATOR.includes(UserRole.VIEWER)).isTrue(); // GH-90000
    }

    @Test
    void curatorShouldNotIncludeAdmin() { // GH-90000
        assertThat(UserRole.CURATOR.includes(UserRole.ADMIN)).isFalse(); // GH-90000
    }

    @Test
    void editorShouldNotIncludeOtherRoles() { // GH-90000
        assertThat(UserRole.EDITOR.includes(UserRole.REVIEWER)).isFalse(); // GH-90000
        assertThat(UserRole.EDITOR.includes(UserRole.CURATOR)).isFalse(); // GH-90000
        assertThat(UserRole.EDITOR.includes(UserRole.ADMIN)).isFalse(); // GH-90000
    }

    @Test
    void editorShouldIncludeOnlyItself() { // GH-90000
        assertThat(UserRole.EDITOR.includes(UserRole.EDITOR)).isTrue(); // GH-90000
    }

    @Test
    void defaultPermissionsShouldBeImmutable() { // GH-90000
        Set<String> perms = UserRole.ADMIN.getDefaultPermissions(); // GH-90000
        assertThatThrownBy(() -> perms.add("new:permission [GH-90000]"))
            .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    @Test
    void valueOfShouldReturnCorrectEnum() { // GH-90000
        assertThat(UserRole.valueOf("ADMIN [GH-90000]")).isEqualTo(UserRole.ADMIN);
        assertThat(UserRole.valueOf("VIEWER [GH-90000]")).isEqualTo(UserRole.VIEWER);
    }
}
