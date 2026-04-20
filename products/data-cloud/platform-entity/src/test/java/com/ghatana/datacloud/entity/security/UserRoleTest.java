/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void allRolesShouldBeAccessible() {
        UserRole[] roles = UserRole.values();
        assertThat(roles).hasSize(5);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void eachRoleShouldHaveNonNullRoleId(UserRole role) {
        assertThat(role.getRoleId()).isNotNull().isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void eachRoleShouldHaveNonNullDescription(UserRole role) {
        assertThat(role.getDescription()).isNotNull().isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void eachRoleShouldHaveDefaultPermissions(UserRole role) {
        assertThat(role.getDefaultPermissions()).isNotNull();
    }

    @Test
    void adminShouldHaveFullPermissions() {
        Set<String> adminPerms = UserRole.ADMIN.getDefaultPermissions();
        assertThat(adminPerms).contains("collection:read", "collection:write", "collection:delete");
        assertThat(adminPerms).contains("entity:read", "entity:write", "entity:delete");
        assertThat(adminPerms).contains("user:read", "user:write", "user:delete");
    }

    @Test
    void viewerShouldHaveReadOnlyPermissions() {
        Set<String> viewerPerms = UserRole.VIEWER.getDefaultPermissions();
        assertThat(viewerPerms).containsOnly("collection:read", "entity:read", "schema:read");
    }

    @Test
    void editorShouldHaveLimitedWritePermissions() {
        Set<String> editorPerms = UserRole.EDITOR.getDefaultPermissions();
        assertThat(editorPerms).contains("entity:write");
        assertThat(editorPerms).doesNotContain("entity:delete");
        assertThat(editorPerms).doesNotContain("collection:write");
    }

    @Test
    void fromRoleIdShouldReturnCorrectEnum() {
        assertThat(UserRole.fromRoleId("admin")).isEqualTo(UserRole.ADMIN);
        assertThat(UserRole.fromRoleId("curator")).isEqualTo(UserRole.CURATOR);
        assertThat(UserRole.fromRoleId("editor")).isEqualTo(UserRole.EDITOR);
        assertThat(UserRole.fromRoleId("reviewer")).isEqualTo(UserRole.REVIEWER);
        assertThat(UserRole.fromRoleId("viewer")).isEqualTo(UserRole.VIEWER);
    }

    @Test
    void fromRoleIdShouldBeCaseInsensitive() {
        assertThat(UserRole.fromRoleId("ADMIN")).isEqualTo(UserRole.ADMIN);
        assertThat(UserRole.fromRoleId("Admin")).isEqualTo(UserRole.ADMIN);
        assertThat(UserRole.fromRoleId("EDITOR")).isEqualTo(UserRole.EDITOR);
    }

    @Test
    void fromRoleIdShouldThrowForNull() {
        assertThatThrownBy(() -> UserRole.fromRoleId(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("roleId cannot be null");
    }

    @Test
    void fromRoleIdShouldThrowForUnknownRole() {
        assertThatThrownBy(() -> UserRole.fromRoleId("unknown_role"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown role ID");
    }

    @Test
    void adminShouldIncludeAllOtherRoles() {
        assertThat(UserRole.ADMIN.includes(UserRole.CURATOR)).isTrue();
        assertThat(UserRole.ADMIN.includes(UserRole.EDITOR)).isTrue();
        assertThat(UserRole.ADMIN.includes(UserRole.REVIEWER)).isTrue();
        assertThat(UserRole.ADMIN.includes(UserRole.VIEWER)).isTrue();
        assertThat(UserRole.ADMIN.includes(UserRole.ADMIN)).isTrue();
    }

    @Test
    void curatorShouldIncludeEditorReviewerAndViewer() {
        assertThat(UserRole.CURATOR.includes(UserRole.EDITOR)).isTrue();
        assertThat(UserRole.CURATOR.includes(UserRole.REVIEWER)).isTrue();
        assertThat(UserRole.CURATOR.includes(UserRole.VIEWER)).isTrue();
    }

    @Test
    void curatorShouldNotIncludeAdmin() {
        assertThat(UserRole.CURATOR.includes(UserRole.ADMIN)).isFalse();
    }

    @Test
    void editorShouldNotIncludeOtherRoles() {
        assertThat(UserRole.EDITOR.includes(UserRole.REVIEWER)).isFalse();
        assertThat(UserRole.EDITOR.includes(UserRole.CURATOR)).isFalse();
        assertThat(UserRole.EDITOR.includes(UserRole.ADMIN)).isFalse();
    }

    @Test
    void editorShouldIncludeOnlyItself() {
        assertThat(UserRole.EDITOR.includes(UserRole.EDITOR)).isTrue();
    }

    @Test
    void defaultPermissionsShouldBeImmutable() {
        Set<String> perms = UserRole.ADMIN.getDefaultPermissions();
        assertThatThrownBy(() -> perms.add("new:permission"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void valueOfShouldReturnCorrectEnum() {
        assertThat(UserRole.valueOf("ADMIN")).isEqualTo(UserRole.ADMIN);
        assertThat(UserRole.valueOf("VIEWER")).isEqualTo(UserRole.VIEWER);
    }
}
