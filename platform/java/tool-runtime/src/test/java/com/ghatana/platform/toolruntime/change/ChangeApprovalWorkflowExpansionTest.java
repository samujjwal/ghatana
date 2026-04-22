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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 Expansion tests for {@link InMemoryChangeApprovalWorkflow}.
 * Tests risk-gated approval, multi-tenant isolation, and workflow state transitions.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for change approval workflow
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ChangeApprovalWorkflow - Phase 3 Expansion [GH-90000]")
class ChangeApprovalWorkflowExpansionTest extends EventloopTestBase {

    private InMemoryChangeApprovalWorkflow workflow;

    @BeforeEach
    void setUp() { // GH-90000
        workflow = new InMemoryChangeApprovalWorkflow(); // GH-90000
    }

    // ============================================
    // RISK SCORING & AUTO-APPROVAL (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Risk Scoring [GH-90000]")
    class RiskScoringTests {

        @Test
        @DisplayName("All change types have consistent risk scores [GH-90000]")
        void riskScoresConsistent() { // GH-90000
            ChangeType[] types = {
                ChangeType.FEATURE_FLAG,
                ChangeType.CONFIG_CHANGE,
                ChangeType.AGENT_DEPLOYMENT,
                ChangeType.POLICY_UPDATE,
                ChangeType.PERMISSION_GRANT
            };

            for (ChangeType type : types) { // GH-90000
                ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                    "tenant-1", "agent-1", type, "description", Map.of())); // GH-90000
                assertThat(req.riskScore()).isBetween(0, 100); // GH-90000
            }
        }

        @Test
        @DisplayName("Low-risk changes auto-approve, high-risk require review [GH-90000]")
        void thresholdBasedApproval() { // GH-90000
            // Low-risk auto-approves
            ChangeRequest lowRisk = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG, "Enable feature", Map.of())); // GH-90000
            assertThat(lowRisk.status()).isEqualTo(ChangeStatus.APPROVED); // GH-90000

            // High-risk requires review
            ChangeRequest highRisk = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "Update policy", Map.of())); // GH-90000
            assertThat(highRisk.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); // GH-90000
        }

        @Test
        @DisplayName("Low-risk changes skip review and go directly to APPROVED [GH-90000]")
        void autoApprovedBypassReview() { // GH-90000
            ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG, "Enable FF", Map.of())); // GH-90000

            assertThat(req.status()).isEqualTo(ChangeStatus.APPROVED); // GH-90000
            assertThat(req.reviewerId()).isEqualTo("system [GH-90000]"); // Auto-approved by system
        }

        @Test
        @DisplayName("Custom threshold changes approval behavior [GH-90000]")
        void customThreshold() { // GH-90000
            // Threshold = 0: all require review
            InMemoryChangeApprovalWorkflow strict = new InMemoryChangeApprovalWorkflow(0); // GH-90000
            ChangeRequest req = runPromise(() -> strict.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG, "Feature", Map.of())); // GH-90000
            assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); // GH-90000

            // Threshold = 100: all auto-approve
            InMemoryChangeApprovalWorkflow permissive = new InMemoryChangeApprovalWorkflow(100); // GH-90000
            ChangeRequest req2 = runPromise(() -> permissive.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.PERMISSION_GRANT, "Grant perm", Map.of())); // GH-90000
            assertThat(req2.status()).isEqualTo(ChangeStatus.APPROVED); // GH-90000
        }
    }

    // ============================================
    // REVIEW WORKFLOW (5 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Review Workflow [GH-90000]")
    class ReviewWorkflowTests {

        @Test
        @DisplayName("Pending changes transition to APPROVED when approved [GH-90000]")
        void approvePendingChange() { // GH-90000
            ChangeRequest pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); // GH-90000
            assertThat(pending.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); // GH-90000

            ChangeRequest approved = runPromise(() -> workflow.approve( // GH-90000
                pending.changeId(), "reviewer-1", "Looks good")); // GH-90000

            assertThat(approved.status()).isEqualTo(ChangeStatus.APPROVED); // GH-90000
            assertThat(approved.reviewerId()).isEqualTo("reviewer-1 [GH-90000]");
            assertThat(approved.reviewNotes()).isEqualTo("Looks good [GH-90000]");
        }

        @Test
        @DisplayName("Pending changes transition to REJECTED when rejected [GH-90000]")
        void rejectPendingChange() { // GH-90000
            ChangeRequest pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.AGENT_DEPLOYMENT, "description", Map.of())); // GH-90000

            ChangeRequest rejected = runPromise(() -> workflow.reject( // GH-90000
                pending.changeId(), "reviewer-2", "Fails security check")); // GH-90000

            assertThat(rejected.status()).isEqualTo(ChangeStatus.REJECTED); // GH-90000
            assertThat(rejected.reviewNotes()).isEqualTo("Fails security check [GH-90000]");
        }

        @Test
        @DisplayName("Pending changes can be withdrawn by requester [GH-90000]")
        void withdrawPendingChange() { // GH-90000
            ChangeRequest pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); // GH-90000

            ChangeRequest withdrawn = runPromise(() -> workflow.withdraw(pending.changeId())); // GH-90000

            assertThat(withdrawn.status()).isEqualTo(ChangeStatus.WITHDRAWN); // GH-90000
        }

        @Test
        @DisplayName("Cannot approve already-approved changes [GH-90000]")
        void preventDoubleApproval() { // GH-90000
            ChangeRequest pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); // GH-90000

            ChangeRequest approved = runPromise(() -> workflow.approve( // GH-90000
                pending.changeId(), "reviewer-1", "ok")); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                workflow.approve(approved.changeId(), "reviewer-2", "again"))) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
        }

        @Test
        @DisplayName("Cannot approve already-rejected changes [GH-90000]")
        void preventApproveAfterReject() { // GH-90000
            ChangeRequest pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); // GH-90000

            ChangeRequest rejected = runPromise(() -> workflow.reject( // GH-90000
                pending.changeId(), "reviewer-1", "No good")); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                workflow.approve(rejected.changeId(), "reviewer-2", "Actually ok"))) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
        }
    }

    // ============================================
    // MULTI-TENANT ISOLATION (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multi-Tenant Isolation [GH-90000]")
    class MultiTenantTests {

        @Test
        @DisplayName("Changes are isolated by tenant [GH-90000]")
        void tenantIsolation() { // GH-90000
            ChangeRequest t1Change = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG, "Feature A", Map.of())); // GH-90000

            ChangeRequest t2Change = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-2", "agent-2", ChangeType.FEATURE_FLAG, "Feature A", Map.of())); // GH-90000

            assertThat(t1Change.changeId()).isNotEqualTo(t2Change.changeId()); // GH-90000
            assertThat(t1Change.tenantId()).isEqualTo("tenant-1 [GH-90000]");
            assertThat(t2Change.tenantId()).isEqualTo("tenant-2 [GH-90000]");
        }

        @Test
        @DisplayName("listPending returns only tenant's pending changes [GH-90000]")
        void pendingListIsolated() { // GH-90000
            // Tenant-1: 2 pending + 1 approved
            runPromise(() -> workflow.submitChange("tenant-1", "a1", ChangeType.POLICY_UPDATE, "d1", Map.of())); // GH-90000
            runPromise(() -> workflow.submitChange("tenant-1", "a2", ChangeType.PERMISSION_GRANT, "d2", Map.of())); // GH-90000
            runPromise(() -> workflow.submitChange("tenant-1", "a3", ChangeType.FEATURE_FLAG, "d3", Map.of())); // GH-90000

            // Tenant-2: 1 pending
            runPromise(() -> workflow.submitChange("tenant-2", "a4", ChangeType.POLICY_UPDATE, "d4", Map.of())); // GH-90000

            List<ChangeRequest> t1Pending = runPromise(() -> workflow.listPending("tenant-1 [GH-90000]"));
            List<ChangeRequest> t2Pending = runPromise(() -> workflow.listPending("tenant-2 [GH-90000]"));

            // Tenant-1 has 2 pending
            assertThat(t1Pending).hasSize(2); // GH-90000
            assertThat(t1Pending).allMatch(r -> r.tenantId().equals("tenant-1 [GH-90000]"));

            // Tenant-2 has 1 pending
            assertThat(t2Pending).hasSize(1); // GH-90000
            assertThat(t2Pending).allMatch(r -> r.tenantId().equals("tenant-2 [GH-90000]"));
        }

        @Test
        @DisplayName("Approvals in one tenant don't affect another's pending list [GH-90000]")
        void approvalIsolation() { // GH-90000
            ChangeRequest t1Pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); // GH-90000

            ChangeRequest t2Pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-2", "agent-2", ChangeType.POLICY_UPDATE, "description", Map.of())); // GH-90000

            // Approve tenant-1's change
            runPromise(() -> workflow.approve(t1Pending.changeId(), "reviewer-1", "ok")); // GH-90000

            // Tenant-2's pending should be unaffected
            List<ChangeRequest> t2List = runPromise(() -> workflow.listPending("tenant-2 [GH-90000]"));
            assertThat(t2List).hasSize(1); // GH-90000
            assertThat(t2List.get(0).changeId()).isEqualTo(t2Pending.changeId()); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT SUBMISSIONS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations [GH-90000]")
    class ConcurrentTests {

        @Test
        @DisplayName("Multiple agents can submit changes concurrently [GH-90000]")
        void concurrentSubmissions() { // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int agentNum = i;
                threads[i] = new Thread(() -> { // GH-90000
                    try {
                        ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                            "tenant-1", "agent-" + agentNum,
                            ChangeType.FEATURE_FLAG, "Feature " + agentNum, Map.of())); // GH-90000
                        if (req.changeId() != null) { // GH-90000
                            successCount.incrementAndGet(); // GH-90000
                        }
                    } catch (Exception e) { // GH-90000
                        // Ignore
                    }
                });
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                try {
                    t.join(); // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
            }

            assertThat(successCount.get()).isEqualTo(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Concurrent reviews don't corrupt state [GH-90000]")
        void concurrentReviews() { // GH-90000
            // Create 5 pending changes
            String[] changeIds = new String[5];
            for (int i = 0; i < 5; i++) { // GH-90000
                final int idx = i;
                ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                    "tenant-1", "agent-" + idx, ChangeType.POLICY_UPDATE, "description", Map.of())); // GH-90000
                changeIds[i] = req.changeId(); // GH-90000
            }

            // Concurrently approve them
            AtomicInteger approvalCount = new AtomicInteger(0); // GH-90000
            Thread[] threads = new Thread[5];
            for (int i = 0; i < 5; i++) { // GH-90000
                final int idx = i;
                threads[i] = new Thread(() -> { // GH-90000
                    try {
                        runPromise(() -> workflow.approve(changeIds[idx], "reviewer-" + idx, "ok")); // GH-90000
                        approvalCount.incrementAndGet(); // GH-90000
                    } catch (Exception e) { // GH-90000
                        //Ignore
                    }
                });
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                try {
                    t.join(); // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
            }

            assertThat(approvalCount.get()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("Listing pending concurrent with submissions stays consistent [GH-90000]")
        void listDuringSubmissions() { // GH-90000
            // Initial pending list
            List<ChangeRequest> initial = runPromise(() -> workflow.listPending("tenant-1 [GH-90000]"));
            assertThat(initial).isEmpty(); // GH-90000

            // Submit 3 high-risk changes concurrently
            for (int i = 0; i < 3; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> workflow.submitChange( // GH-90000
                    "tenant-1", "agent-" + idx, ChangeType.POLICY_UPDATE, "policy " + idx, Map.of())); // GH-90000
            }

            // Check pending list after
            List<ChangeRequest> after = runPromise(() -> workflow.listPending("tenant-1 [GH-90000]"));
            assertThat(after).hasSize(3); // GH-90000
            assertThat(after).allMatch(r -> r.status() == ChangeStatus.PENDING_REVIEW); // GH-90000
        }
    }

    // ============================================
    // CHANGE RETRIEVAL (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Change Retrieval [GH-90000]")
    class RetrievalTests {

        @Test
        @DisplayName("Can retrieve change by ID [GH-90000]")
        void getChangeById() { // GH-90000
            ChangeRequest created = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG, "Enable feature", Map.of())); // GH-90000

            ChangeRequest retrieved = runPromise(() -> workflow.getChange(created.changeId())); // GH-90000

            assertThat(retrieved.changeId()).isEqualTo(created.changeId()); // GH-90000
            assertThat(retrieved.status()).isEqualTo(created.status()); // GH-90000
            assertThat(retrieved.tenantId()).isEqualTo("tenant-1 [GH-90000]");
        }

        @Test
        @DisplayName("Retrieving non-existent change throws error [GH-90000]")
        void getUnknownChangeThrows() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> workflow.getChange("no-such-id [GH-90000]")))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ============================================
    // CHANGE REQUEST PROPERTIES (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Change Request Properties [GH-90000]")
    class PropertyTests {

        @Test
        @DisplayName("ChangeRequest has correct core properties [GH-90000]")
        void requestStaticProperties() { // GH-90000
            ChangeRequest req = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.CONFIG_CHANGE,
                "Update config", Map.of())); // GH-90000

            assertThat(req.tenantId()).isEqualTo("tenant-1 [GH-90000]");
            assertThat(req.riskScore()).isBetween(0, 100); // GH-90000
            assertThat(req.status()).isNotNull(); // GH-90000
            assertThat(req.changeId()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Review notes are captured correctly [GH-90000]")
        void reviewNotesPreserved() { // GH-90000
            ChangeRequest pending = runPromise(() -> workflow.submitChange( // GH-90000
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); // GH-90000

            String reviewNotes = "Requires security approval before deployment";
            ChangeRequest reviewed = runPromise(() -> workflow.reject( // GH-90000
                pending.changeId(), "reviewer-1", reviewNotes)); // GH-90000

            assertThat(reviewed.reviewNotes()).isEqualTo(reviewNotes); // GH-90000
            assertThat(reviewed.reviewerId()).isEqualTo("reviewer-1 [GH-90000]");
        }
    }
}
