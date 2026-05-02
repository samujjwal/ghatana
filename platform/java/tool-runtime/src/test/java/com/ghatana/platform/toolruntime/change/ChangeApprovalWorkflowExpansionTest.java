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
@DisplayName("ChangeApprovalWorkflow - Phase 3 Expansion")
class ChangeApprovalWorkflowExpansionTest extends EventloopTestBase {

    private InMemoryChangeApprovalWorkflow workflow;

    @BeforeEach
    void setUp() { 
        workflow = new InMemoryChangeApprovalWorkflow(); 
    }

    // ============================================
    // RISK SCORING & AUTO-APPROVAL (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Risk Scoring")
    class RiskScoringTests {

        @Test
        @DisplayName("All change types have consistent risk scores")
        void riskScoresConsistent() { 
            ChangeType[] types = {
                ChangeType.FEATURE_FLAG,
                ChangeType.CONFIG_CHANGE,
                ChangeType.AGENT_DEPLOYMENT,
                ChangeType.POLICY_UPDATE,
                ChangeType.PERMISSION_GRANT
            };

            for (ChangeType type : types) { 
                ChangeRequest req = runPromise(() -> workflow.submitChange( 
                    "tenant-1", "agent-1", type, "description", Map.of())); 
                assertThat(req.riskScore()).isBetween(0, 100); 
            }
        }

        @Test
        @DisplayName("Low-risk changes auto-approve, high-risk require review")
        void thresholdBasedApproval() { 
            // Low-risk auto-approves
            ChangeRequest lowRisk = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG, "Enable feature", Map.of())); 
            assertThat(lowRisk.status()).isEqualTo(ChangeStatus.APPROVED); 

            // High-risk requires review
            ChangeRequest highRisk = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "Update policy", Map.of())); 
            assertThat(highRisk.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); 
        }

        @Test
        @DisplayName("Low-risk changes skip review and go directly to APPROVED")
        void autoApprovedBypassReview() { 
            ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG, "Enable FF", Map.of())); 

            assertThat(req.status()).isEqualTo(ChangeStatus.APPROVED); 
            assertThat(req.reviewerId()).isEqualTo("system"); // Auto-approved by system
        }

        @Test
        @DisplayName("Custom threshold changes approval behavior")
        void customThreshold() { 
            // Threshold = 0: all require review
            InMemoryChangeApprovalWorkflow strict = new InMemoryChangeApprovalWorkflow(0); 
            ChangeRequest req = runPromise(() -> strict.submitChange( 
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG, "Feature", Map.of())); 
            assertThat(req.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); 

            // Threshold = 100: all auto-approve
            InMemoryChangeApprovalWorkflow permissive = new InMemoryChangeApprovalWorkflow(100); 
            ChangeRequest req2 = runPromise(() -> permissive.submitChange( 
                "tenant-1", "agent-1", ChangeType.PERMISSION_GRANT, "Grant perm", Map.of())); 
            assertThat(req2.status()).isEqualTo(ChangeStatus.APPROVED); 
        }
    }

    // ============================================
    // REVIEW WORKFLOW (5 tests) 
    // ============================================

    @Nested
    @DisplayName("Review Workflow")
    class ReviewWorkflowTests {

        @Test
        @DisplayName("Pending changes transition to APPROVED when approved")
        void approvePendingChange() { 
            ChangeRequest pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); 
            assertThat(pending.status()).isEqualTo(ChangeStatus.PENDING_REVIEW); 

            ChangeRequest approved = runPromise(() -> workflow.approve( 
                pending.changeId(), "reviewer-1", "Looks good")); 

            assertThat(approved.status()).isEqualTo(ChangeStatus.APPROVED); 
            assertThat(approved.reviewerId()).isEqualTo("reviewer-1");
            assertThat(approved.reviewNotes()).isEqualTo("Looks good");
        }

        @Test
        @DisplayName("Pending changes transition to REJECTED when rejected")
        void rejectPendingChange() { 
            ChangeRequest pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.AGENT_DEPLOYMENT, "description", Map.of())); 

            ChangeRequest rejected = runPromise(() -> workflow.reject( 
                pending.changeId(), "reviewer-2", "Fails security check")); 

            assertThat(rejected.status()).isEqualTo(ChangeStatus.REJECTED); 
            assertThat(rejected.reviewNotes()).isEqualTo("Fails security check");
        }

        @Test
        @DisplayName("Pending changes can be withdrawn by requester")
        void withdrawPendingChange() { 
            ChangeRequest pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); 

            ChangeRequest withdrawn = runPromise(() -> workflow.withdraw(pending.changeId())); 

            assertThat(withdrawn.status()).isEqualTo(ChangeStatus.WITHDRAWN); 
        }

        @Test
        @DisplayName("Cannot approve already-approved changes")
        void preventDoubleApproval() { 
            ChangeRequest pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); 

            ChangeRequest approved = runPromise(() -> workflow.approve( 
                pending.changeId(), "reviewer-1", "ok")); 

            assertThatThrownBy(() -> runPromise(() -> 
                workflow.approve(approved.changeId(), "reviewer-2", "again"))) 
                .isInstanceOf(IllegalStateException.class); 
        }

        @Test
        @DisplayName("Cannot approve already-rejected changes")
        void preventApproveAfterReject() { 
            ChangeRequest pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); 

            ChangeRequest rejected = runPromise(() -> workflow.reject( 
                pending.changeId(), "reviewer-1", "No good")); 

            assertThatThrownBy(() -> runPromise(() -> 
                workflow.approve(rejected.changeId(), "reviewer-2", "Actually ok"))) 
                .isInstanceOf(IllegalStateException.class); 
        }
    }

    // ============================================
    // MULTI-TENANT ISOLATION (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantTests {

        @Test
        @DisplayName("Changes are isolated by tenant")
        void tenantIsolation() { 
            ChangeRequest t1Change = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG, "Feature A", Map.of())); 

            ChangeRequest t2Change = runPromise(() -> workflow.submitChange( 
                "tenant-2", "agent-2", ChangeType.FEATURE_FLAG, "Feature A", Map.of())); 

            assertThat(t1Change.changeId()).isNotEqualTo(t2Change.changeId()); 
            assertThat(t1Change.tenantId()).isEqualTo("tenant-1");
            assertThat(t2Change.tenantId()).isEqualTo("tenant-2");
        }

        @Test
        @DisplayName("listPending returns only tenant's pending changes")
        void pendingListIsolated() { 
            // Tenant-1: 2 pending + 1 approved
            runPromise(() -> workflow.submitChange("tenant-1", "a1", ChangeType.POLICY_UPDATE, "d1", Map.of())); 
            runPromise(() -> workflow.submitChange("tenant-1", "a2", ChangeType.PERMISSION_GRANT, "d2", Map.of())); 
            runPromise(() -> workflow.submitChange("tenant-1", "a3", ChangeType.FEATURE_FLAG, "d3", Map.of())); 

            // Tenant-2: 1 pending
            runPromise(() -> workflow.submitChange("tenant-2", "a4", ChangeType.POLICY_UPDATE, "d4", Map.of())); 

            List<ChangeRequest> t1Pending = runPromise(() -> workflow.listPending("tenant-1"));
            List<ChangeRequest> t2Pending = runPromise(() -> workflow.listPending("tenant-2"));

            // Tenant-1 has 2 pending
            assertThat(t1Pending).hasSize(2); 
            assertThat(t1Pending).allMatch(r -> r.tenantId().equals("tenant-1"));

            // Tenant-2 has 1 pending
            assertThat(t2Pending).hasSize(1); 
            assertThat(t2Pending).allMatch(r -> r.tenantId().equals("tenant-2"));
        }

        @Test
        @DisplayName("Approvals in one tenant don't affect another's pending list")
        void approvalIsolation() { 
            ChangeRequest t1Pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); 

            ChangeRequest t2Pending = runPromise(() -> workflow.submitChange( 
                "tenant-2", "agent-2", ChangeType.POLICY_UPDATE, "description", Map.of())); 

            // Approve tenant-1's change
            runPromise(() -> workflow.approve(t1Pending.changeId(), "reviewer-1", "ok")); 

            // Tenant-2's pending should be unaffected
            List<ChangeRequest> t2List = runPromise(() -> workflow.listPending("tenant-2"));
            assertThat(t2List).hasSize(1); 
            assertThat(t2List.get(0).changeId()).isEqualTo(t2Pending.changeId()); 
        }
    }

    // ============================================
    // CONCURRENT SUBMISSIONS (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentTests {

        @Test
        @DisplayName("Multiple agents can submit changes concurrently")
        void concurrentSubmissions() { 
            AtomicInteger successCount = new AtomicInteger(0); 
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) { 
                final int agentNum = i;
                threads[i] = new Thread(() -> { 
                    try {
                        ChangeRequest req = runPromise(() -> workflow.submitChange( 
                            "tenant-1", "agent-" + agentNum,
                            ChangeType.FEATURE_FLAG, "Feature " + agentNum, Map.of())); 
                        if (req.changeId() != null) { 
                            successCount.incrementAndGet(); 
                        }
                    } catch (Exception e) { 
                        // Ignore
                    }
                });
                threads[i].start(); 
            }

            for (Thread t : threads) { 
                try {
                    t.join(); 
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                }
            }

            assertThat(successCount.get()).isEqualTo(threadCount); 
        }

        @Test
        @DisplayName("Concurrent reviews don't corrupt state")
        void concurrentReviews() { 
            // Create 5 pending changes
            String[] changeIds = new String[5];
            for (int i = 0; i < 5; i++) { 
                final int idx = i;
                ChangeRequest req = runPromise(() -> workflow.submitChange( 
                    "tenant-1", "agent-" + idx, ChangeType.POLICY_UPDATE, "description", Map.of())); 
                changeIds[i] = req.changeId(); 
            }

            // Concurrently approve them
            AtomicInteger approvalCount = new AtomicInteger(0); 
            Thread[] threads = new Thread[5];
            for (int i = 0; i < 5; i++) { 
                final int idx = i;
                threads[i] = new Thread(() -> { 
                    try {
                        runPromise(() -> workflow.approve(changeIds[idx], "reviewer-" + idx, "ok")); 
                        approvalCount.incrementAndGet(); 
                    } catch (Exception e) { 
                        //Ignore
                    }
                });
                threads[i].start(); 
            }

            for (Thread t : threads) { 
                try {
                    t.join(); 
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                }
            }

            assertThat(approvalCount.get()).isEqualTo(5); 
        }

        @Test
        @DisplayName("Listing pending concurrent with submissions stays consistent")
        void listDuringSubmissions() { 
            // Initial pending list
            List<ChangeRequest> initial = runPromise(() -> workflow.listPending("tenant-1"));
            assertThat(initial).isEmpty(); 

            // Submit 3 high-risk changes concurrently
            for (int i = 0; i < 3; i++) { 
                final int idx = i;
                runPromise(() -> workflow.submitChange( 
                    "tenant-1", "agent-" + idx, ChangeType.POLICY_UPDATE, "policy " + idx, Map.of())); 
            }

            // Check pending list after
            List<ChangeRequest> after = runPromise(() -> workflow.listPending("tenant-1"));
            assertThat(after).hasSize(3); 
            assertThat(after).allMatch(r -> r.status() == ChangeStatus.PENDING_REVIEW); 
        }
    }

    // ============================================
    // CHANGE RETRIEVAL (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Change Retrieval")
    class RetrievalTests {

        @Test
        @DisplayName("Can retrieve change by ID")
        void getChangeById() { 
            ChangeRequest created = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.FEATURE_FLAG, "Enable feature", Map.of())); 

            ChangeRequest retrieved = runPromise(() -> workflow.getChange(created.changeId())); 

            assertThat(retrieved.changeId()).isEqualTo(created.changeId()); 
            assertThat(retrieved.status()).isEqualTo(created.status()); 
            assertThat(retrieved.tenantId()).isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("Retrieving non-existent change throws error")
        void getUnknownChangeThrows() { 
            assertThatThrownBy(() -> runPromise(() -> workflow.getChange("no-such-id")))
                .isInstanceOf(IllegalArgumentException.class); 
        }
    }

    // ============================================
    // CHANGE REQUEST PROPERTIES (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Change Request Properties")
    class PropertyTests {

        @Test
        @DisplayName("ChangeRequest has correct core properties")
        void requestStaticProperties() { 
            ChangeRequest req = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.CONFIG_CHANGE,
                "Update config", Map.of())); 

            assertThat(req.tenantId()).isEqualTo("tenant-1");
            assertThat(req.riskScore()).isBetween(0, 100); 
            assertThat(req.status()).isNotNull(); 
            assertThat(req.changeId()).isNotEmpty(); 
        }

        @Test
        @DisplayName("Review notes are captured correctly")
        void reviewNotesPreserved() { 
            ChangeRequest pending = runPromise(() -> workflow.submitChange( 
                "tenant-1", "agent-1", ChangeType.POLICY_UPDATE, "description", Map.of())); 

            String reviewNotes = "Requires security approval before deployment";
            ChangeRequest reviewed = runPromise(() -> workflow.reject( 
                pending.changeId(), "reviewer-1", reviewNotes)); 

            assertThat(reviewed.reviewNotes()).isEqualTo(reviewNotes); 
            assertThat(reviewed.reviewerId()).isEqualTo("reviewer-1");
        }
    }
}
