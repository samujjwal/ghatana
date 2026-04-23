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
@DisplayName("ApprovalNotificationService")
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
                        List.of("criterion-A"),
                        List.of("artifact-B")),
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant-123",
                Instant.parse("2026-01-01T10:00:00Z"),
                null,
                null,
                null);
    }

    @Test
    @DisplayName("notifyRequested publishes to the requested topic with correct payload")
    void notifyRequestedPublishesToCorrectTopic() { // GH-90000
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000

        runPromise(() -> notificationService.notifyRequested(pendingRequest)); // GH-90000

        verify(publisher).publish(eq(TOPIC_REQUESTED), eq("tenant-123"), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue(); // GH-90000
        assertThat(payload.get("requestId")).isEqualTo("req-001");
        assertThat(payload.get("tenantId")).isEqualTo("tenant-123");
        assertThat(payload.get("projectId")).isEqualTo("project-alpha");
        assertThat(payload.get("approvalType")).isEqualTo("PHASE_ADVANCE");
        assertThat(payload.get("status")).isEqualTo("PENDING");
        assertThat(payload.get("fromPhase")).isEqualTo("INTENT");
        assertThat(payload.get("toPhase")).isEqualTo("SHAPE");
        assertThat(payload).containsKey("notificationTimestamp");
    }

    @Test
    @DisplayName("notifyApproved publishes to the approved topic with decidedBy")
    void notifyApprovedPublishesToCorrectTopic() { // GH-90000
        ApprovalRequest approvedRequest = pendingRequest.asApproved("reviewer-A");
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000

        runPromise(() -> notificationService.notifyApproved(approvedRequest, "reviewer-A")); // GH-90000

        verify(publisher).publish(eq(TOPIC_APPROVED), eq("tenant-123"), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue(); // GH-90000
        assertThat(payload.get("requestId")).isEqualTo("req-001");
        assertThat(payload.get("status")).isEqualTo("APPROVED");
        assertThat(payload.get("decidedBy")).isEqualTo("reviewer-A");
        assertThat(payload).containsKey("decidedAt");
    }

    @Test
    @DisplayName("notifyRejected publishes to the rejected topic with decidedBy")
    void notifyRejectedPublishesToCorrectTopic() { // GH-90000
        ApprovalRequest rejectedRequest = pendingRequest.asRejected("reviewer-B");
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000

        runPromise(() -> notificationService.notifyRejected(rejectedRequest, "reviewer-B")); // GH-90000

        verify(publisher).publish(eq(TOPIC_REJECTED), eq("tenant-123"), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue(); // GH-90000
        assertThat(payload.get("requestId")).isEqualTo("req-001");
        assertThat(payload.get("status")).isEqualTo("REJECTED");
        assertThat(payload.get("decidedBy")).isEqualTo("reviewer-B");
    }

    @Test
    @DisplayName("publish failure does not propagate — promise completes normally")
    void publishFailureIsSwallowed() { // GH-90000
        when(publisher.publish(anyString(), anyString(), any())) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("AEP unavailable")));

        // Should complete without throwing
        runPromise(() -> notificationService.notifyRequested(pendingRequest)); // GH-90000
    }

    @Test
    @DisplayName("notifyRequested omits expiresAt when null")
    void notifyRequestedOmitsNullExpiresAt() { // GH-90000
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000
        runPromise(() -> notificationService.notifyRequested(pendingRequest)); // GH-90000

        verify(publisher).publish(eq(TOPIC_REQUESTED), anyString(), payloadCaptor.capture()); // GH-90000
        assertThat(payloadCaptor.getValue()).doesNotContainKey("expiresAt");
    }

    @Test
    @DisplayName("notifyRequested includes expiresAt when present")
    void notifyRequestedIncludesExpiresAtWhenSet() { // GH-90000
        Instant expiresAt = Instant.parse("2026-01-02T10:00:00Z");
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
        assertThat(payloadCaptor.getValue()).containsKey("expiresAt");
        assertThat(payloadCaptor.getValue().get("expiresAt")).isEqualTo(expiresAt.toString());
    }
}
