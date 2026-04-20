/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AuditAction} enum.
 */
class AuditActionTest {

    @Test
    void allEnumValuesShouldBeAccessible() {
        AuditAction[] actions = AuditAction.values();
        assertThat(actions).hasSizeGreaterThanOrEqualTo(20);
    }

    @ParameterizedTest
    @EnumSource(AuditAction.class)
    void eachActionShouldHaveNonNullActionId(AuditAction action) {
        assertThat(action.getActionId()).isNotNull().isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(AuditAction.class)
    void eachActionShouldHaveNonNullDescription(AuditAction action) {
        assertThat(action.getDescription()).isNotNull().isNotEmpty();
    }

    @Test
    void collectionOperationsShouldExist() {
        assertThat(AuditAction.CREATE_COLLECTION.getActionId()).isEqualTo("create_collection");
        assertThat(AuditAction.UPDATE_COLLECTION.getActionId()).isEqualTo("update_collection");
        assertThat(AuditAction.DELETE_COLLECTION.getActionId()).isEqualTo("delete_collection");
    }

    @Test
    void entityOperationsShouldExist() {
        assertThat(AuditAction.CREATE_ENTITY.getActionId()).isEqualTo("create_entity");
        assertThat(AuditAction.UPDATE_ENTITY.getActionId()).isEqualTo("update_entity");
        assertThat(AuditAction.DELETE_ENTITY.getActionId()).isEqualTo("delete_entity");
    }

    @Test
    void userOperationsShouldExist() {
        assertThat(AuditAction.CREATE_USER.getActionId()).isEqualTo("create_user");
        assertThat(AuditAction.ROLE_ASSIGNED.getActionId()).isEqualTo("role_assigned");
        assertThat(AuditAction.ROLE_REVOKED.getActionId()).isEqualTo("role_revoked");
    }

    @Test
    void fromActionIdShouldReturnCorrectEnum() {
        assertThat(AuditAction.fromActionId("create_entity")).isEqualTo(AuditAction.CREATE_ENTITY);
        assertThat(AuditAction.fromActionId("update_entity")).isEqualTo(AuditAction.UPDATE_ENTITY);
        assertThat(AuditAction.fromActionId("delete_entity")).isEqualTo(AuditAction.DELETE_ENTITY);
    }

    @Test
    void fromActionIdShouldBeCaseInsensitive() {
        assertThat(AuditAction.fromActionId("CREATE_ENTITY")).isEqualTo(AuditAction.CREATE_ENTITY);
        assertThat(AuditAction.fromActionId("Create_Entity")).isEqualTo(AuditAction.CREATE_ENTITY);
        assertThat(AuditAction.fromActionId("create_ENTITY")).isEqualTo(AuditAction.CREATE_ENTITY);
    }

    @Test
    void fromActionIdShouldThrowForNull() {
        assertThatThrownBy(() -> AuditAction.fromActionId(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("actionId cannot be null");
    }

    @Test
    void fromActionIdShouldThrowForUnknownAction() {
        assertThatThrownBy(() -> AuditAction.fromActionId("unknown_action"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown action ID");
    }

    @Test
    void descriptionsShouldBeMeaningful() {
        assertThat(AuditAction.CREATE_ENTITY.getDescription()).contains("created");
        assertThat(AuditAction.UPDATE_ENTITY.getDescription()).contains("updated");
        assertThat(AuditAction.DELETE_ENTITY.getDescription()).contains("deleted");
    }

    @Test
    void valueOfShouldReturnCorrectEnum() {
        assertThat(AuditAction.valueOf("CREATE_ENTITY")).isEqualTo(AuditAction.CREATE_ENTITY);
        assertThat(AuditAction.valueOf("ACCESS_DENIED")).isEqualTo(AuditAction.ACCESS_DENIED);
    }
}
