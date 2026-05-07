/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Tests for DestructiveActionApproval
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DestructiveActionApproval")
class DestructiveActionApprovalTest {

    @Test
    @DisplayName("create creates pending approval")
    void create_createsPendingApproval() {
        DestructiveActionApproval approval = DestructiveActionApproval.create(
            "tenant-1",
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            "Test justification"
        );

        assertThat(approval.approvalId()).isNotNull();
        assertThat(approval.tenantId()).isEqualTo("tenant-1");
        assertThat(approval.requesterId()).isEqualTo("user-1");
        assertThat(approval.actionType()).isEqualTo(DestructiveActionApproval.ActionType.PURGE);
        assertThat(approval.targetCollection()).isEqualTo("collection-1");
        assertThat(approval.targetEntityId()).isEqualTo("entity-1");
        assertThat(approval.justification()).isEqualTo("Test justification");
        assertThat(approval.requestedAt()).isNotNull();
        assertThat(approval.approvedAt()).isNull();
        assertThat(approval.approvedBy()).isNull();
        assertThat(approval.status()).isEqualTo(DestructiveActionApproval.Status.PENDING);
    }

    @Test
    @DisplayName("approve returns approved copy")
    void approve_returnsApprovedCopy() {
        DestructiveActionApproval pending = DestructiveActionApproval.create(
            "tenant-1",
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            "Test justification"
        );

        DestructiveActionApproval approved = pending.approve("admin-1");

        assertThat(approved.approvalId()).isEqualTo(pending.approvalId());
        assertThat(approved.tenantId()).isEqualTo(pending.tenantId());
        assertThat(approved.approvedBy()).isEqualTo("admin-1");
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(approved.status()).isEqualTo(DestructiveActionApproval.Status.APPROVED);
    }

    @Test
    @DisplayName("reject returns rejected copy")
    void reject_returnsRejectedCopy() {
        DestructiveActionApproval pending = DestructiveActionApproval.create(
            "tenant-1",
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            "Test justification"
        );

        DestructiveActionApproval rejected = pending.reject("admin-1");

        assertThat(rejected.approvalId()).isEqualTo(pending.approvalId());
        assertThat(rejected.tenantId()).isEqualTo(pending.tenantId());
        assertThat(rejected.approvedBy()).isEqualTo("admin-1");
        assertThat(rejected.approvedAt()).isNotNull();
        assertThat(rejected.status()).isEqualTo(DestructiveActionApproval.Status.REJECTED);
    }

    @Test
    @DisplayName("isApproved returns true for approved status")
    void isApproved_returnsTrueForApproved() {
        DestructiveActionApproval approval = DestructiveActionApproval.create(
            "tenant-1",
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            "Test justification"
        ).approve("admin-1");

        assertThat(approval.isApproved()).isTrue();
    }

    @Test
    @DisplayName("isApproved returns false for pending status")
    void isApproved_returnsFalseForPending() {
        DestructiveActionApproval approval = DestructiveActionApproval.create(
            "tenant-1",
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            "Test justification"
        );

        assertThat(approval.isApproved()).isFalse();
    }

    @Test
    @DisplayName("isPending returns true for pending status")
    void isPending_returnsTrueForPending() {
        DestructiveActionApproval approval = DestructiveActionApproval.create(
            "tenant-1",
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            "Test justification"
        );

        assertThat(approval.isPending()).isTrue();
    }

    @Test
    @DisplayName("isPending returns false for approved status")
    void isPending_returnsFalseForApproved() {
        DestructiveActionApproval approval = DestructiveActionApproval.create(
            "tenant-1",
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            "Test justification"
        ).approve("admin-1");

        assertThat(approval.isPending()).isFalse();
    }

    @Test
    @DisplayName("constructor throws for null approvalId")
    void constructor_throwsForNullApprovalId() {
        assertThatThrownBy(() -> new DestructiveActionApproval(
            null,
            "tenant-1",
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            "justification",
            Instant.now(),
            null,
            null,
            DestructiveActionApproval.Status.PENDING
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("approvalId");
    }

    @Test
    @DisplayName("constructor throws for null tenantId")
    void constructor_throwsForNullTenantId() {
        assertThatThrownBy(() -> new DestructiveActionApproval(
            "approval-1",
            null,
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            "justification",
            Instant.now(),
            null,
            null,
            DestructiveActionApproval.Status.PENDING
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("constructor throws for null actionType")
    void constructor_throwsForNullActionType() {
        assertThatThrownBy(() -> new DestructiveActionApproval(
            "approval-1",
            "tenant-1",
            "user-1",
            null,
            "collection-1",
            "entity-1",
            "justification",
            Instant.now(),
            null,
            null,
            DestructiveActionApproval.Status.PENDING
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("actionType");
    }

    @Test
    @DisplayName("constructor throws for null justification")
    void constructor_throwsForNullJustification() {
        assertThatThrownBy(() -> new DestructiveActionApproval(
            "approval-1",
            "tenant-1",
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            null,
            Instant.now(),
            null,
            null,
            DestructiveActionApproval.Status.PENDING
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("justification");
    }

    @Test
    @DisplayName("constructor throws for null requestedAt")
    void constructor_throwsForNullRequestedAt() {
        assertThatThrownBy(() -> new DestructiveActionApproval(
            "approval-1",
            "tenant-1",
            "user-1",
            DestructiveActionApproval.ActionType.PURGE,
            "collection-1",
            "entity-1",
            "justification",
            null,
            null,
            null,
            DestructiveActionApproval.Status.PENDING
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("requestedAt");
    }
}
