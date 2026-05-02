/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("InMemoryChangeApprovalWorkflow")
class ChangeApprovalWorkflowTest extends EventloopTestBase {

    private InMemoryChangeApprovalWorkflow workflow;

    @BeforeEach
    void setUp() { 
        workflow = new InMemoryChangeApprovalWorkflow(); 
    }

    @Nested
    @DisplayName("submitChange – auto-approve low-risk changes")
    class LowRiskChanges {

        @Test
        @DisplayName("FEATURE_FLAG (risk=20) is auto-approved under default threshold 60")
        void featureFlagAutoApproved() { 
            ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG,
                "Enable dark mode", Map.of("flag", "dark-mode"))); 

            assertThat(req.status()).isEqualTo(ChangeStatus.APPROVED); 
            assertThat(req.riskScore()).isEqualTo(20); 
            assertThat(req.reviewerId()).isEqualTo("system");
            assertThat(req.reviewedAt()).isNotNull(); 
        }

        @Test
        @DisplayName("CONFIG_CHANGE (risk=40) is auto-approved under default threshold 60")
        void configChangeAutoApproved() { 
            ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.CONFIG_CHANGE,
                "Update timeout to 30s", Map.of())); 

            assertThat(req.status()).isEqualTo(ChangeStatus.APPROVED); 
            assertThat(req.riskScore()).isEqualTo(40); 
        }
    }

    @Nested
    @DisplayName("submitChange – pending-review for high-risk changes")
    class HighRiskChanges {

        @Test
        @DisplayName("POLICY_UPDATE (risk=70) requires review under default threshold 60")
        void policyUpdatePendingReview() { 
            ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE,
                "Add egress policy v3", Map.of("policy", "egress-v3"))); 

            assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); 
            assertThat(req.riskScore()).isEqualTo(70); 
            assertThat(req.reviewerId()).isNull(); 
            assertThat(req.reviewedAt()).isNull(); 
        }

        @Test
        @DisplayName("PERMISSION_GRANT (risk=80) requires review")
        void permissionGrantPendingReview() { 
            ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-2", ChangeType.PERMISSION_GRANT,
                "Grant read-events to agent-99", Map.of())); 

            assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); 
            assertThat(req.riskScore()).isEqualTo(80); 
        }

        @Test
        @DisplayName("TOOL_REGISTRATION (risk=60) requires review at default threshold 60")
        void toolRegistrationPendingReview() { 
            // risk == threshold → requires review (not strictly less than) 
            ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.TOOL_REGISTRATION,
                "Register new web-search tool", Map.of())); 

            assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); 
        }
    }

    @Nested
    @DisplayName("approve / reject / withdraw")
    class ReviewActions {

        @Test
        @DisplayName("approve transitions PENDING_REVIEW → APPROVED")
        void approvePendingChange() { 
            ChangeRequest pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "desc", Map.of())); 

            ChangeRequest approved = runPromise(() -> workflow.approve( 
                pending.changeId(), "reviewer-1", "Looks good")); 

            assertThat(approved.status()).isEqualTo(ChangeStatus.APPROVED); 
            assertThat(approved.reviewerId()).isEqualTo("reviewer-1");
            assertThat(approved.reviewNotes()).isEqualTo("Looks good");
            assertThat(approved.reviewedAt()).isNotNull(); 
        }

        @Test
        @DisplayName("reject transitions PENDING_REVIEW → REJECTED")
        void rejectPendingChange() { 
            ChangeRequest pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.AGENT_DEPLOYMENT, "desc", Map.of())); 

            ChangeRequest rejected = runPromise(() -> workflow.reject( 
                pending.changeId(), "reviewer-2", "Fails security review")); 

            assertThat(rejected.status()).isEqualTo(ChangeStatus.REJECTED); 
            assertThat(rejected.reviewNotes()).isEqualTo("Fails security review");
        }

        @Test
        @DisplayName("withdraw transitions PENDING_REVIEW → WITHDRAWN")
        void withdrawPendingChange() { 
            ChangeRequest pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.AGENT_DEPLOYMENT, "desc", Map.of())); 

            ChangeRequest withdrawn = runPromise(() -> workflow.withdraw(pending.changeId())); 
            assertThat(withdrawn.status()).isEqualTo(ChangeStatus.WITHDRAWN); 
        }

        @Test
        @DisplayName("cannot approve an already-approved change")
        void cannotApproveAlreadyApproved() { 
            ChangeRequest pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "desc", Map.of())); 
            runPromise(() -> workflow.approve(pending.changeId(), "r1", "ok")); 

            assertThatThrownBy(() -> runPromise(() -> 
                workflow.approve(pending.changeId(), "r2", "again"))) 
                .isInstanceOf(IllegalStateException.class); 
        }

        @Test
        @DisplayName("getChange returns NotFound for unknown ID")
        void getUnknownChangeReturnsError() { 
            assertThatThrownBy(() -> runPromise(() -> workflow.getChange("no-such-id")))
                .isInstanceOf(IllegalArgumentException.class); 
        }
    }

    @Nested
    @DisplayName("listPending")
    class ListPending {

        @Test
        @DisplayName("returns only PENDING_REVIEW changes for the given tenant")
        void listsPendingForTenant() { 
            // Two high-risk (pending) + one low-risk (auto-approved) for tenant-1 
            runPromise(() -> workflow.submitChange("tenant-1", "a1", ChangeType.POLICY_UPDATE, "d1", Map.of())); 
            runPromise(() -> workflow.submitChange("tenant-1", "a2", ChangeType.PERMISSION_GRANT, "d2", Map.of())); 
            runPromise(() -> workflow.submitChange("tenant-1", "a3", ChangeType.FEATURE_FLAG, "d3", Map.of())); 
            // Different tenant — should not appear
            runPromise(() -> workflow.submitChange("tenant-2", "a4", ChangeType.POLICY_UPDATE, "d4", Map.of())); 

            List<ChangeRequest> pending = runPromise(() -> workflow.listPending("tenant-1"));
            assertThat(pending).hasSize(2); 
            assertThat(pending).allMatch(r -> r.status() == ChangeStatus.PENDING_REVIEW); 
            assertThat(pending).allMatch(r -> r.tenantId().equals("tenant-1"));
        }
    }

    @Nested
    @DisplayName("custom auto-approve threshold")
    class CustomThreshold {

        @Test
        @DisplayName("threshold=0 means all changes require review")
        void thresholdZeroRequiresAllReview() { 
            workflow = new InMemoryChangeApprovalWorkflow(0); 
            ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-1", "a1", ChangeType.FEATURE_FLAG, "d", Map.of())); 
            assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); 
        }

        @Test
        @DisplayName("threshold=100 means all changes are auto-approved")
        void thresholdMaxAutoApprovesAll() { 
            workflow = new InMemoryChangeApprovalWorkflow(100); 
            ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-1", "a1", ChangeType.PERMISSION_GRANT, "d", Map.of())); 
            assertThat(req.status()).isEqualTo(ChangeStatus.APPROVED); 
        }
    }
}
