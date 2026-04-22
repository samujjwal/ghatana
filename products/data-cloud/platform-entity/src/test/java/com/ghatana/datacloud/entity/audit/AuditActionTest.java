/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void allEnumValuesShouldBeAccessible() { // GH-90000
        AuditAction[] actions = AuditAction.values(); // GH-90000
        assertThat(actions).hasSizeGreaterThanOrEqualTo(20); // GH-90000
    }

    @ParameterizedTest
    @EnumSource(AuditAction.class) // GH-90000
    void eachActionShouldHaveNonNullActionId(AuditAction action) { // GH-90000
        assertThat(action.getActionId()).isNotNull().isNotEmpty(); // GH-90000
    }

    @ParameterizedTest
    @EnumSource(AuditAction.class) // GH-90000
    void eachActionShouldHaveNonNullDescription(AuditAction action) { // GH-90000
        assertThat(action.getDescription()).isNotNull().isNotEmpty(); // GH-90000
    }

    @Test
    void collectionOperationsShouldExist() { // GH-90000
        assertThat(AuditAction.CREATE_COLLECTION.getActionId()).isEqualTo("create_collection [GH-90000]");
        assertThat(AuditAction.UPDATE_COLLECTION.getActionId()).isEqualTo("update_collection [GH-90000]");
        assertThat(AuditAction.DELETE_COLLECTION.getActionId()).isEqualTo("delete_collection [GH-90000]");
    }

    @Test
    void entityOperationsShouldExist() { // GH-90000
        assertThat(AuditAction.CREATE_ENTITY.getActionId()).isEqualTo("create_entity [GH-90000]");
        assertThat(AuditAction.UPDATE_ENTITY.getActionId()).isEqualTo("update_entity [GH-90000]");
        assertThat(AuditAction.DELETE_ENTITY.getActionId()).isEqualTo("delete_entity [GH-90000]");
    }

    @Test
    void userOperationsShouldExist() { // GH-90000
        assertThat(AuditAction.CREATE_USER.getActionId()).isEqualTo("create_user [GH-90000]");
        assertThat(AuditAction.ROLE_ASSIGNED.getActionId()).isEqualTo("role_assigned [GH-90000]");
        assertThat(AuditAction.ROLE_REVOKED.getActionId()).isEqualTo("role_revoked [GH-90000]");
    }

    @Test
    void fromActionIdShouldReturnCorrectEnum() { // GH-90000
        assertThat(AuditAction.fromActionId("create_entity [GH-90000]")).isEqualTo(AuditAction.CREATE_ENTITY);
        assertThat(AuditAction.fromActionId("update_entity [GH-90000]")).isEqualTo(AuditAction.UPDATE_ENTITY);
        assertThat(AuditAction.fromActionId("delete_entity [GH-90000]")).isEqualTo(AuditAction.DELETE_ENTITY);
    }

    @Test
    void fromActionIdShouldBeCaseInsensitive() { // GH-90000
        assertThat(AuditAction.fromActionId("CREATE_ENTITY [GH-90000]")).isEqualTo(AuditAction.CREATE_ENTITY);
        assertThat(AuditAction.fromActionId("Create_Entity [GH-90000]")).isEqualTo(AuditAction.CREATE_ENTITY);
        assertThat(AuditAction.fromActionId("create_ENTITY [GH-90000]")).isEqualTo(AuditAction.CREATE_ENTITY);
    }

    @Test
    void fromActionIdShouldThrowForNull() { // GH-90000
        assertThatThrownBy(() -> AuditAction.fromActionId(null)) // GH-90000
            .isInstanceOf(NullPointerException.class) // GH-90000
            .hasMessageContaining("actionId cannot be null [GH-90000]");
    }

    @Test
    void fromActionIdShouldThrowForUnknownAction() { // GH-90000
        assertThatThrownBy(() -> AuditAction.fromActionId("unknown_action [GH-90000]"))
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Unknown action ID [GH-90000]");
    }

    @Test
    void descriptionsShouldBeMeaningful() { // GH-90000
        assertThat(AuditAction.CREATE_ENTITY.getDescription()).contains("created [GH-90000]");
        assertThat(AuditAction.UPDATE_ENTITY.getDescription()).contains("updated [GH-90000]");
        assertThat(AuditAction.DELETE_ENTITY.getDescription()).contains("deleted [GH-90000]");
    }

    @Test
    void valueOfShouldReturnCorrectEnum() { // GH-90000
        assertThat(AuditAction.valueOf("CREATE_ENTITY [GH-90000]")).isEqualTo(AuditAction.CREATE_ENTITY);
        assertThat(AuditAction.valueOf("ACCESS_DENIED [GH-90000]")).isEqualTo(AuditAction.ACCESS_DENIED);
    }
}
