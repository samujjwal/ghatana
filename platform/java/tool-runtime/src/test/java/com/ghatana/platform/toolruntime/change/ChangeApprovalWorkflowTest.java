/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.change;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link InMemoryChangeApprovalWorkflow}.
 *
 * @doc.type class
 * @doc.purpose Tests for risk-gated change approval workflow
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("InMemoryChangeApprovalWorkflow [GH-90000]")
class ChangeApprovalWorkflowTest extends EventloopTestBase {

    private InMemoryChangeApprovalWorkflow workflow;

    @BeforeEach
    void setUp() { // GH-90000
        workflow = new InMemoryChangeApprovalWorkflow(); // GH-90000
    }

    @Nested
    @DisplayName("submitChange – auto-approve low-risk changes [GH-90000]")
    class LowRiskChanges {

        @Test
        @DisplayName("FEATURE_FLAG (risk=20) is auto-approved under default threshold 60 [GH-90000]")
        void featureFlagAutoApproved() { // GH-90000
            ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG,
                "Enable dark mode", Map.of("flag", "dark-mode"))); // GH-90000

            assertThat(req.status()).isEqualTo(ChangeStatus.APPROVED); // GH-90000
            assertThat(req.riskScore()).isEqualTo(20); // GH-90000
            assertThat(req.reviewerId()).isEqualTo("system [GH-90000]");
            assertThat(req.reviewedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("CONFIG_CHANGE (risk=40) is auto-approved under default threshold 60 [GH-90000]")
        void configChangeAutoApproved() { // GH-90000
            ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.CONFIG_CHANGE,
                "Update timeout to 30s", Map.of())); // GH-90000

            assertThat(req.status()).isEqualTo(ChangeStatus.APPROVED); // GH-90000
            assertThat(req.riskScore()).isEqualTo(40); // GH-90000
        }
    }

    @Nested
    @DisplayName("submitChange – pending-review for high-risk changes [GH-90000]")
    class HighRiskChanges {

        @Test
        @DisplayName("POLICY_UPDATE (risk=70) requires review under default threshold 60 [GH-90000]")
        void policyUpdatePendingReview() { // GH-90000
            ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE,
                "Add egress policy v3", Map.of("policy", "egress-v3"))); // GH-90000

            assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); // GH-90000
            assertThat(req.riskScore()).isEqualTo(70); // GH-90000
            assertThat(req.reviewerId()).isNull(); // GH-90000
            assertThat(req.reviewedAt()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("PERMISSION_GRANT (risk=80) requires review [GH-90000]")
        void permissionGrantPendingReview() { // GH-90000
            ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-2", ChangeType.PERMISSION_GRANT,
                "Grant read-events to agent-99", Map.of())); // GH-90000

            assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); // GH-90000
            assertThat(req.riskScore()).isEqualTo(80); // GH-90000
        }

        @Test
        @DisplayName("TOOL_REGISTRATION (risk=60) requires review at default threshold 60 [GH-90000]")
        void toolRegistrationPendingReview() { // GH-90000
            // risk == threshold → requires review (not strictly less than) // GH-90000
            ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.TOOL_REGISTRATION,
                "Register new web-search tool", Map.of())); // GH-90000

            assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); // GH-90000
        }
    }

    @Nested
    @DisplayName("approve / reject / withdraw [GH-90000]")
    class ReviewActions {

        @Test
        @DisplayName("approve transitions PENDING_REVIEW → APPROVED [GH-90000]")
        void approvePendingChange() { // GH-90000
            ChangeRequest pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "desc", Map.of())); // GH-90000

            ChangeRequest approved = runPromise(() -> workflow.approve( // GH-90000
                pending.changeId(), "reviewer-1", "Looks good")); // GH-90000

            assertThat(approved.status()).isEqualTo(ChangeStatus.APPROVED); // GH-90000
            assertThat(approved.reviewerId()).isEqualTo("reviewer-1 [GH-90000]");
            assertThat(approved.reviewNotes()).isEqualTo("Looks good [GH-90000]");
            assertThat(approved.reviewedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("reject transitions PENDING_REVIEW → REJECTED [GH-90000]")
        void rejectPendingChange() { // GH-90000
            ChangeRequest pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.AGENT_DEPLOYMENT, "desc", Map.of())); // GH-90000

            ChangeRequest rejected = runPromise(() -> workflow.reject( // GH-90000
                pending.changeId(), "reviewer-2", "Fails security review")); // GH-90000

            assertThat(rejected.status()).isEqualTo(ChangeStatus.REJECTED); // GH-90000
            assertThat(rejected.reviewNotes()).isEqualTo("Fails security review [GH-90000]");
        }

        @Test
        @DisplayName("withdraw transitions PENDING_REVIEW → WITHDRAWN [GH-90000]")
        void withdrawPendingChange() { // GH-90000
            ChangeRequest pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.AGENT_DEPLOYMENT, "desc", Map.of())); // GH-90000

            ChangeRequest withdrawn = runPromise(() -> workflow.withdraw(pending.changeId())); // GH-90000
            assertThat(withdrawn.status()).isEqualTo(ChangeStatus.WITHDRAWN); // GH-90000
        }

        @Test
        @DisplayName("cannot approve an already-approved change [GH-90000]")
        void cannotApproveAlreadyApproved() { // GH-90000
            ChangeRequest pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "desc", Map.of())); // GH-90000
            runPromise(() -> workflow.approve(pending.changeId(), "r1", "ok")); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                workflow.approve(pending.changeId(), "r2", "again"))) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
        }

        @Test
        @DisplayName("getChange returns NotFound for unknown ID [GH-90000]")
        void getUnknownChangeReturnsError() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> workflow.getChange("no-such-id [GH-90000]")))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("listPending [GH-90000]")
    class ListPending {

        @Test
        @DisplayName("returns only PENDING_REVIEW changes for the given tenant [GH-90000]")
        void listsPendingForTenant() { // GH-90000
            // Two high-risk (pending) + one low-risk (auto-approved) for tenant-1 // GH-90000
            runPromise(() -> workflow.submitChange("tenant-1", "a1", ChangeType.POLICY_UPDATE, "d1", Map.of())); // GH-90000
            runPromise(() -> workflow.submitChange("tenant-1", "a2", ChangeType.PERMISSION_GRANT, "d2", Map.of())); // GH-90000
            runPromise(() -> workflow.submitChange("tenant-1", "a3", ChangeType.FEATURE_FLAG, "d3", Map.of())); // GH-90000
            // Different tenant — should not appear
            runPromise(() -> workflow.submitChange("tenant-2", "a4", ChangeType.POLICY_UPDATE, "d4", Map.of())); // GH-90000

            List<ChangeRequest> pending = runPromise(() -> workflow.listPending("tenant-1 [GH-90000]"));
            assertThat(pending).hasSize(2); // GH-90000
            assertThat(pending).allMatch(r -> r.status() == ChangeStatus.PENDING_REVIEW); // GH-90000
            assertThat(pending).allMatch(r -> r.tenantId().equals("tenant-1 [GH-90000]"));
        }
    }

    @Nested
    @DisplayName("custom auto-approve threshold [GH-90000]")
    class CustomThreshold {

        @Test
        @DisplayName("threshold=0 means all changes require review [GH-90000]")
        void thresholdZeroRequiresAllReview() { // GH-90000
            workflow = new InMemoryChangeApprovalWorkflow(0); // GH-90000
            ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "a1", ChangeType.FEATURE_FLAG, "d", Map.of())); // GH-90000
            assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); // GH-90000
        }

        @Test
        @DisplayName("threshold=100 means all changes are auto-approved [GH-90000]")
        void thresholdMaxAutoApprovesAll() { // GH-90000
            workflow = new InMemoryChangeApprovalWorkflow(100); // GH-90000
            ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "a1", ChangeType.PERMISSION_GRANT, "d", Map.of())); // GH-90000
            assertThat(req.status()).isEqualTo(ChangeStatus.APPROVED); // GH-90000
        }
    }
}
