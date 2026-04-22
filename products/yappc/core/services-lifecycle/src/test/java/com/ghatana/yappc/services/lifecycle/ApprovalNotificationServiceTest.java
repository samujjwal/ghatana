/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service — ApprovalNotificationService Tests
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.AepEventPublisher;
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

import static com.ghatana.yappc.services.lifecycle.ApprovalNotificationService.TOPIC_APPROVED;
import static com.ghatana.yappc.services.lifecycle.ApprovalNotificationService.TOPIC_REJECTED;
import static com.ghatana.yappc.services.lifecycle.ApprovalNotificationService.TOPIC_REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies ApprovalNotificationService publishes correct AEP events on each approval state change
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ApprovalNotificationService [GH-90000]")
class ApprovalNotificationServiceTest extends EventloopTestBase {

    @Mock
    private AepEventPublisher publisher;

    private ApprovalNotificationService notificationService;

    private ApprovalRequest pendingRequest;

    @BeforeEach
    void setUp() { // GH-90000
        when(publisher.publish(anyString(), anyString(), any())).thenReturn(Promise.complete()); // GH-90000
        notificationService = new ApprovalNotificationService(publisher); // GH-90000

        pendingRequest = new ApprovalRequest( // GH-90000
                "req-001",
                "project-alpha",
                "agent-007",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext( // GH-90000
                        "INTENT",
                        "SHAPE",
                        "Unmet entry criteria",
                        List.of("criterion-A [GH-90000]"),
                        List.of("artifact-B [GH-90000]")),
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-123",
                Instant.parse("2026-01-01T10:00:00Z [GH-90000]"),
                null,
                null,
                null);
    }

    @Test
    @DisplayName("notifyRequested publishes to the requested topic with correct payload [GH-90000]")
    void notifyRequestedPublishesToCorrectTopic() { // GH-90000
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000

        runPromise(() -> notificationService.notifyRequested(pendingRequest)); // GH-90000

        verify(publisher).publish(eq(TOPIC_REQUESTED), eq("tenant-123 [GH-90000]"), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue(); // GH-90000
        assertThat(payload.get("requestId [GH-90000]")).isEqualTo("req-001 [GH-90000]");
        assertThat(payload.get("tenantId [GH-90000]")).isEqualTo("tenant-123 [GH-90000]");
        assertThat(payload.get("projectId [GH-90000]")).isEqualTo("project-alpha [GH-90000]");
        assertThat(payload.get("approvalType [GH-90000]")).isEqualTo("PHASE_ADVANCE [GH-90000]");
        assertThat(payload.get("status [GH-90000]")).isEqualTo("PENDING [GH-90000]");
        assertThat(payload.get("fromPhase [GH-90000]")).isEqualTo("INTENT [GH-90000]");
        assertThat(payload.get("toPhase [GH-90000]")).isEqualTo("SHAPE [GH-90000]");
        assertThat(payload).containsKey("notificationTimestamp [GH-90000]");
    }

    @Test
    @DisplayName("notifyApproved publishes to the approved topic with decidedBy [GH-90000]")
    void notifyApprovedPublishesToCorrectTopic() { // GH-90000
        ApprovalRequest approvedRequest = pendingRequest.asApproved("reviewer-A [GH-90000]");
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000

        runPromise(() -> notificationService.notifyApproved(approvedRequest, "reviewer-A")); // GH-90000

        verify(publisher).publish(eq(TOPIC_APPROVED), eq("tenant-123 [GH-90000]"), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue(); // GH-90000
        assertThat(payload.get("requestId [GH-90000]")).isEqualTo("req-001 [GH-90000]");
        assertThat(payload.get("status [GH-90000]")).isEqualTo("APPROVED [GH-90000]");
        assertThat(payload.get("decidedBy [GH-90000]")).isEqualTo("reviewer-A [GH-90000]");
        assertThat(payload).containsKey("decidedAt [GH-90000]");
    }

    @Test
    @DisplayName("notifyRejected publishes to the rejected topic with decidedBy [GH-90000]")
    void notifyRejectedPublishesToCorrectTopic() { // GH-90000
        ApprovalRequest rejectedRequest = pendingRequest.asRejected("reviewer-B [GH-90000]");
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000

        runPromise(() -> notificationService.notifyRejected(rejectedRequest, "reviewer-B")); // GH-90000

        verify(publisher).publish(eq(TOPIC_REJECTED), eq("tenant-123 [GH-90000]"), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue(); // GH-90000
        assertThat(payload.get("requestId [GH-90000]")).isEqualTo("req-001 [GH-90000]");
        assertThat(payload.get("status [GH-90000]")).isEqualTo("REJECTED [GH-90000]");
        assertThat(payload.get("decidedBy [GH-90000]")).isEqualTo("reviewer-B [GH-90000]");
    }

    @Test
    @DisplayName("publish failure does not propagate — promise completes normally [GH-90000]")
    void publishFailureIsSwallowed() { // GH-90000
        when(publisher.publish(anyString(), anyString(), any())) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("AEP unavailable [GH-90000]")));

        // Should complete without throwing
        runPromise(() -> notificationService.notifyRequested(pendingRequest)); // GH-90000
    }

    @Test
    @DisplayName("notifyRequested omits expiresAt when null [GH-90000]")
    void notifyRequestedOmitsNullExpiresAt() { // GH-90000
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000
        runPromise(() -> notificationService.notifyRequested(pendingRequest)); // GH-90000

        verify(publisher).publish(eq(TOPIC_REQUESTED), anyString(), payloadCaptor.capture()); // GH-90000
        assertThat(payloadCaptor.getValue()).doesNotContainKey("expiresAt [GH-90000]");
    }

    @Test
    @DisplayName("notifyRequested includes expiresAt when present [GH-90000]")
    void notifyRequestedIncludesExpiresAtWhenSet() { // GH-90000
        Instant expiresAt = Instant.parse("2026-01-02T10:00:00Z [GH-90000]");
        ApprovalRequest requestWithExpiry = new ApprovalRequest( // GH-90000
                "req-expire",
                "project-alpha",
                "agent-007",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                pendingRequest.context(), // GH-90000
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-123",
                pendingRequest.createdAt(), // GH-90000
                null,
                null,
                expiresAt);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000
        runPromise(() -> notificationService.notifyRequested(requestWithExpiry)); // GH-90000

        verify(publisher).publish(eq(TOPIC_REQUESTED), anyString(), payloadCaptor.capture()); // GH-90000
        assertThat(payloadCaptor.getValue()).containsKey("expiresAt [GH-90000]");
        assertThat(payloadCaptor.getValue().get("expiresAt [GH-90000]")).isEqualTo(expiresAt.toString());
    }
}
