/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.approval;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link InMemoryApprovalWorkflow}.
 */
@DisplayName("InMemoryApprovalWorkflow")
class ApprovalWorkflowTest extends EventloopTestBase {

    private InMemoryApprovalWorkflow workflow;

    @BeforeEach
    void setUp() { 
        workflow = new InMemoryApprovalWorkflow(); 
    }

    @Nested
    @DisplayName("submit")
    class SubmitTests {

        @Test
        @DisplayName("creates a PENDING request with a non-null ID")
        void createsPendingRequest() { 
            ApprovalRequest req = runPromise(() -> 
                workflow.submit("t1", "agent1", "DELETE_RECORD", "ctx")); 
            assertThat(req.requestId()).isNotNull(); 
            assertThat(req.status()).isEqualTo(ApprovalStatus.PENDING); 
            assertThat(req.tenantId()).isEqualTo("t1");
            assertThat(req.agentId()).isEqualTo("agent1");
            assertThat(req.actionType()).isEqualTo("DELETE_RECORD");
        }

        @Test
        @DisplayName("each submit generates a unique request ID")
        void uniqueRequestIds() { 
            ApprovalRequest r1 = runPromise(() -> workflow.submit("t1", "a", "ACT", null)); 
            ApprovalRequest r2 = runPromise(() -> workflow.submit("t1", "a", "ACT", null)); 
            assertThat(r1.requestId()).isNotEqualTo(r2.requestId()); 
        }
    }

    @Nested
    @DisplayName("approve")
    class ApproveTests {

        @Test
        @DisplayName("transitions PENDING to APPROVED")
        void approvesRequest() { 
            ApprovalRequest req = runPromise(() -> 
                workflow.submit("t1", "agent1", "SEND_EMAIL", null)); 
            ApprovalRequest approved = runPromise(() -> 
                workflow.approve(req.requestId(), "looks good")); 

            assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED); 
            assertThat(approved.reviewerNote()).isEqualTo("looks good");
            assertThat(approved.decidedAt()).isNotNull(); 
        }

        @Test
        @DisplayName("cannot approve an already-approved request")
        void cannotApproveApproved() { 
            ApprovalRequest req = runPromise(() -> workflow.submit("t1", "a", "ACT", null)); 
            runPromise(() -> workflow.approve(req.requestId(), null)); 

            assertThatThrownBy(() -> 
                runPromise(() -> workflow.approve(req.requestId(), null)) 
            ).isInstanceOf(IllegalStateException.class); 
        }
    }

    @Nested
    @DisplayName("reject")
    class RejectTests {

        @Test
        @DisplayName("transitions PENDING to REJECTED")
        void rejectsRequest() { 
            ApprovalRequest req = runPromise(() -> 
                workflow.submit("t1", "agent1", "DELETE_RECORD", null)); 
            ApprovalRequest rejected = runPromise(() -> 
                workflow.reject(req.requestId(), "too risky")); 

            assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED); 
            assertThat(rejected.reviewerNote()).isEqualTo("too risky");
        }

        @Test
        @DisplayName("get on unknown ID throws IllegalArgumentException")
        void getUnknownThrows() { 
            assertThatThrownBy(() -> 
                runPromise(() -> workflow.get("no-such-id"))
            ).isInstanceOf(IllegalArgumentException.class); 
        }
    }

    @Nested
    @DisplayName("requiresApproval")
    class RequiresApprovalTests {

        @Test
        @DisplayName("returns true for registered action types")
        void returnsTrueForRegistered() { 
            workflow.requireApproval("DELETE_RECORD");
            boolean result = runPromise(() -> 
                workflow.requiresApproval("t1", "agent1", "DELETE_RECORD")); 
            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("returns false for unregistered action types")
        void returnsFalseForUnregistered() { 
            boolean result = runPromise(() -> 
                workflow.requiresApproval("t1", "agent1", "READ_RECORD")); 
            assertThat(result).isFalse(); 
        }
    }
}
