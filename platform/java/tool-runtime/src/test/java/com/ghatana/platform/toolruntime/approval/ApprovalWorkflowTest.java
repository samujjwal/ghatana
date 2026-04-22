/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("InMemoryApprovalWorkflow [GH-90000]")
class ApprovalWorkflowTest extends EventloopTestBase {

    private InMemoryApprovalWorkflow workflow;

    @BeforeEach
    void setUp() { // GH-90000
        workflow = new InMemoryApprovalWorkflow(); // GH-90000
    }

    @Nested
    @DisplayName("submit [GH-90000]")
    class SubmitTests {

        @Test
        @DisplayName("creates a PENDING request with a non-null ID [GH-90000]")
        void createsPendingRequest() { // GH-90000
            ApprovalRequest req = runPromise(() -> // GH-90000
                workflow.submit("t1", "agent1", "DELETE_RECORD", "ctx")); // GH-90000
            assertThat(req.requestId()).isNotNull(); // GH-90000
            assertThat(req.status()).isEqualTo(ApprovalStatus.PENDING); // GH-90000
            assertThat(req.tenantId()).isEqualTo("t1 [GH-90000]");
            assertThat(req.agentId()).isEqualTo("agent1 [GH-90000]");
            assertThat(req.actionType()).isEqualTo("DELETE_RECORD [GH-90000]");
        }

        @Test
        @DisplayName("each submit generates a unique request ID [GH-90000]")
        void uniqueRequestIds() { // GH-90000
            ApprovalRequest r1 = runPromise(() -> workflow.submit("t1", "a", "ACT", null)); // GH-90000
            ApprovalRequest r2 = runPromise(() -> workflow.submit("t1", "a", "ACT", null)); // GH-90000
            assertThat(r1.requestId()).isNotEqualTo(r2.requestId()); // GH-90000
        }
    }

    @Nested
    @DisplayName("approve [GH-90000]")
    class ApproveTests {

        @Test
        @DisplayName("transitions PENDING to APPROVED [GH-90000]")
        void approvesRequest() { // GH-90000
            ApprovalRequest req = runPromise(() -> // GH-90000
                workflow.submit("t1", "agent1", "SEND_EMAIL", null)); // GH-90000
            ApprovalRequest approved = runPromise(() -> // GH-90000
                workflow.approve(req.requestId(), "looks good")); // GH-90000

            assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED); // GH-90000
            assertThat(approved.reviewerNote()).isEqualTo("looks good [GH-90000]");
            assertThat(approved.decidedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("cannot approve an already-approved request [GH-90000]")
        void cannotApproveApproved() { // GH-90000
            ApprovalRequest req = runPromise(() -> workflow.submit("t1", "a", "ACT", null)); // GH-90000
            runPromise(() -> workflow.approve(req.requestId(), null)); // GH-90000

            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> workflow.approve(req.requestId(), null)) // GH-90000
            ).isInstanceOf(IllegalStateException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("reject [GH-90000]")
    class RejectTests {

        @Test
        @DisplayName("transitions PENDING to REJECTED [GH-90000]")
        void rejectsRequest() { // GH-90000
            ApprovalRequest req = runPromise(() -> // GH-90000
                workflow.submit("t1", "agent1", "DELETE_RECORD", null)); // GH-90000
            ApprovalRequest rejected = runPromise(() -> // GH-90000
                workflow.reject(req.requestId(), "too risky")); // GH-90000

            assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED); // GH-90000
            assertThat(rejected.reviewerNote()).isEqualTo("too risky [GH-90000]");
        }

        @Test
        @DisplayName("get on unknown ID throws IllegalArgumentException [GH-90000]")
        void getUnknownThrows() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> workflow.get("no-such-id [GH-90000]"))
            ).isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("requiresApproval [GH-90000]")
    class RequiresApprovalTests {

        @Test
        @DisplayName("returns true for registered action types [GH-90000]")
        void returnsTrueForRegistered() { // GH-90000
            workflow.requireApproval("DELETE_RECORD [GH-90000]");
            boolean result = runPromise(() -> // GH-90000
                workflow.requiresApproval("t1", "agent1", "DELETE_RECORD")); // GH-90000
            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns false for unregistered action types [GH-90000]")
        void returnsFalseForUnregistered() { // GH-90000
            boolean result = runPromise(() -> // GH-90000
                workflow.requiresApproval("t1", "agent1", "READ_RECORD")); // GH-90000
            assertThat(result).isFalse(); // GH-90000
        }
    }
}
