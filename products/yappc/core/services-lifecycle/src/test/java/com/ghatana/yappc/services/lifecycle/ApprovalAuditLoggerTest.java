/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service — ApprovalAuditLogger Tests
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.ghatana.yappc.services.lifecycle.ApprovalAuditLogger.EVENT_APPROVED;
import static com.ghatana.yappc.services.lifecycle.ApprovalAuditLogger.EVENT_CREATED;
import static com.ghatana.yappc.services.lifecycle.ApprovalAuditLogger.EVENT_REJECTED;
import static com.ghatana.yappc.services.lifecycle.ApprovalAuditLogger.EVENT_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies ApprovalAuditLogger emits correct structured events for each approval action
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ApprovalAuditLogger [GH-90000]")
class ApprovalAuditLoggerTest extends EventloopTestBase {

    @Mock
    private AuditLogger delegate;

    private ApprovalAuditLogger auditLogger;
    private ApprovalRequest pendingRequest;

    @SuppressWarnings("unchecked [GH-90000]")
    @BeforeEach
    void setUp() { // GH-90000
        lenient().when(delegate.log(any(Map.class))).thenReturn(Promise.complete()); // GH-90000
        auditLogger = new ApprovalAuditLogger(delegate); // GH-90000

        pendingRequest = new ApprovalRequest( // GH-90000
                "req-audit-001",
                "proj-xyz",
                "agent-007",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext( // GH-90000
                        "INTENT", "SHAPE", "gate failed", List.of("criterion-1 [GH-90000]"), List.of()),
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-audit",
                Instant.parse("2026-01-10T09:00:00Z [GH-90000]"),
                null, null, null);
    }

    @Test
    @DisplayName("logCreated emits approval.created event with correct fields [GH-90000]")
    @SuppressWarnings("unchecked [GH-90000]")
    void logCreatedEmitsCorrectEvent() { // GH-90000
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class); // GH-90000
        runPromise(() -> auditLogger.logCreated(pendingRequest)); // GH-90000

        verify(delegate).log(captor.capture()); // GH-90000
        Map<String, Object> event = captor.getValue(); // GH-90000
        assertThat(event.get("type [GH-90000]")).isEqualTo(EVENT_CREATED);
        assertThat(event.get("tenantId [GH-90000]")).isEqualTo("tenant-audit [GH-90000]");
        assertThat(event.get("requestId [GH-90000]")).isEqualTo("req-audit-001 [GH-90000]");
        assertThat(event.get("projectId [GH-90000]")).isEqualTo("proj-xyz [GH-90000]");
        assertThat(event.get("approvalType [GH-90000]")).isEqualTo("PHASE_ADVANCE [GH-90000]");
        assertThat(event.get("status [GH-90000]")).isEqualTo("PENDING [GH-90000]");
        assertThat(event.get("fromPhase [GH-90000]")).isEqualTo("INTENT [GH-90000]");
        assertThat(event.get("toPhase [GH-90000]")).isEqualTo("SHAPE [GH-90000]");
        assertThat(event).containsKey("occurredAt [GH-90000]");
        assertThat(event).doesNotContainKey("decidedBy [GH-90000]");
    }

    @Test
    @DisplayName("logReviewStarted emits approval.review.started event [GH-90000]")
    @SuppressWarnings("unchecked [GH-90000]")
    void logReviewStartedEmitsCorrectEvent() { // GH-90000
        ApprovalRequest reviewing = pendingRequest.asReviewing(); // GH-90000
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class); // GH-90000

        runPromise(() -> auditLogger.logReviewStarted(reviewing)); // GH-90000

        verify(delegate).log(captor.capture()); // GH-90000
        assertThat(captor.getValue().get("type [GH-90000]")).isEqualTo(EVENT_REVIEW);
        assertThat(captor.getValue().get("status [GH-90000]")).isEqualTo("REVIEWING [GH-90000]");
    }

    @Test
    @DisplayName("logApproved emits approval.approved event with decidedBy [GH-90000]")
    @SuppressWarnings("unchecked [GH-90000]")
    void logApprovedEmitsCorrectEvent() { // GH-90000
        ApprovalRequest approved = pendingRequest.asApproved("reviewer-A [GH-90000]");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class); // GH-90000

        runPromise(() -> auditLogger.logApproved(approved, "reviewer-A")); // GH-90000

        verify(delegate).log(captor.capture()); // GH-90000
        Map<String, Object> event = captor.getValue(); // GH-90000
        assertThat(event.get("type [GH-90000]")).isEqualTo(EVENT_APPROVED);
        assertThat(event.get("status [GH-90000]")).isEqualTo("APPROVED [GH-90000]");
        assertThat(event.get("decidedBy [GH-90000]")).isEqualTo("reviewer-A [GH-90000]");
        assertThat(event).containsKey("decidedAt [GH-90000]");
    }

    @Test
    @DisplayName("logRejected emits approval.rejected event with decidedBy [GH-90000]")
    @SuppressWarnings("unchecked [GH-90000]")
    void logRejectedEmitsCorrectEvent() { // GH-90000
        ApprovalRequest rejected = pendingRequest.asRejected("reviewer-B [GH-90000]");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class); // GH-90000

        runPromise(() -> auditLogger.logRejected(rejected, "reviewer-B")); // GH-90000

        verify(delegate).log(captor.capture()); // GH-90000
        Map<String, Object> event = captor.getValue(); // GH-90000
        assertThat(event.get("type [GH-90000]")).isEqualTo(EVENT_REJECTED);
        assertThat(event.get("status [GH-90000]")).isEqualTo("REJECTED [GH-90000]");
        assertThat(event.get("decidedBy [GH-90000]")).isEqualTo("reviewer-B [GH-90000]");
    }

    @Test
    @DisplayName("audit failure is swallowed — promise completes normally [GH-90000]")
    @SuppressWarnings("unchecked [GH-90000]")
    void auditFailureIsSwallowed() { // GH-90000
        when(delegate.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("DB down [GH-90000]")));

        // Should complete without throwing
        runPromise(() -> auditLogger.logCreated(pendingRequest)); // GH-90000
    }

    @Test
    @DisplayName("decidedBy is omitted when null [GH-90000]")
    @SuppressWarnings("unchecked [GH-90000]")
    void decidedByOmittedWhenNull() { // GH-90000
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class); // GH-90000
        runPromise(() -> auditLogger.logCreated(pendingRequest)); // GH-90000

        verify(delegate).log(captor.capture()); // GH-90000
        assertThat(captor.getValue()).doesNotContainKey("decidedBy [GH-90000]");
    }
}
