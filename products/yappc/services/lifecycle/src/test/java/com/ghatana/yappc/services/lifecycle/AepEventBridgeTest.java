/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AepEventBridge} (YAPPC-Ph5).
 *
 * <p>All async tests use {@link EventloopTestBase#runPromise} to execute
 * ActiveJ Promises on the managed event loop without blocking.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the AepEventBridge resilient event-publishing facade
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepEventBridge (YAPPC-Ph5)")
class AepEventBridgeTest extends EventloopTestBase {

    @Mock
    private AepEventPublisher publisher;

    private AepEventBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new AepEventBridge(publisher);
    }

    // =========================================================================
    // publishRawEvent tests
    // =========================================================================

    @Nested
    @DisplayName("publishRawEvent")
    class PublishRawEventTests {

        @Test
        @DisplayName("should publish event and return null on success")
        void shouldPublishEventOnSuccess() {
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any()))
                    .thenReturn(Promise.complete());
            Map<String, Object> payload = Map.of("key", "value");

            // WHEN
            Void result = runPromise(() -> bridge.publishRawEvent("test.event", "tenant-1", payload));

            // THEN
            assertThat(result).isNull();
            verify(publisher).publish("test.event", "tenant-1", payload);
        }

        @Test
        @DisplayName("should swallow publisher failure and still return null")
        void shouldSwallowPublisherFailure() {
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Downstream AEP error")));
            Map<String, Object> payload = Map.of("key", "val");

            // WHEN — must not throw
            Void result = runPromise(() -> bridge.publishRawEvent("test.event", "tenant-x", payload));

            // THEN — failure swallowed, null returned
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should pass all payload fields through to publisher unchanged")
        void shouldPassPayloadThroughToPublisher() {
            // GIVEN
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> payloadCaptor =
                    ArgumentCaptor.forClass(Map.class);
            when(publisher.publish(anyString(), anyString(), any()))
                    .thenReturn(Promise.complete());
            Map<String, Object> payload = Map.of("projectId", "proj-1", "stage", "context");

            // WHEN
            runPromise(() -> bridge.publishRawEvent("lifecycle.phase.advanced", "t1", payload));

            // THEN
            verify(publisher).publish(eq("lifecycle.phase.advanced"), eq("t1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue()).containsEntry("projectId", "proj-1");
        }
    }

    // =========================================================================
    // publishTransitionEvent — success path
    // =========================================================================

    @Nested
    @DisplayName("publishTransitionEvent — success")
    class PublishTransitionEventSuccessTests {

        @Test
        @DisplayName("should publish lifecycle.phase.advanced on successful transition")
        void shouldPublishPhaseAdvancedOnSuccess() {
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any()))
                    .thenReturn(Promise.complete());
            TransitionRequest request = new TransitionRequest(
                    "proj-1", "intent", "context", "tenant-1", "user@example.com");
            TransitionResult result = TransitionResult.success("context");

            // WHEN
            Void out = runPromise(() -> bridge.publishTransitionEvent(request, result));

            // THEN
            assertThat(out).isNull();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            verify(publisher).publish(
                    eq(AepEventBridge.EVENT_PHASE_ADVANCED),
                    eq("tenant-1"),
                    payloadCaptor.capture());
            Map<String, Object> payload = payloadCaptor.getValue();
            assertThat(payload).containsEntry("projectId", "proj-1");
            assertThat(payload).containsEntry("fromPhase", "intent");
            assertThat(payload).containsEntry("toPhase", "context");
            assertThat(payload).containsEntry("requestedBy", "user@example.com");
            assertThat(payload).containsKey("advancedAt");
        }
    }

    // =========================================================================
    // publishTransitionEvent — blocked path
    // =========================================================================

    @Nested
    @DisplayName("publishTransitionEvent — blocked")
    class PublishTransitionEventBlockedTests {

        @Test
        @DisplayName("should publish lifecycle.phase.blocked on blocked transition")
        void shouldPublishPhaseBlockedOnBlockedResult() {
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any()))
                    .thenReturn(Promise.complete());
            TransitionRequest request = new TransitionRequest(
                    "proj-2", "context", "shape", "tenant-2", "agent-X");
            TransitionResult result = TransitionResult.blocked(
                    "MISSING_ARTIFACT", "Required PRD document not found");

            // WHEN
            Void out = runPromise(() -> bridge.publishTransitionEvent(request, result));

            // THEN
            assertThat(out).isNull();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            verify(publisher).publish(
                    eq(AepEventBridge.EVENT_PHASE_BLOCKED),
                    eq("tenant-2"),
                    payloadCaptor.capture());
            Map<String, Object> payload = payloadCaptor.getValue();
            assertThat(payload).containsEntry("projectId", "proj-2");
            assertThat(payload).containsEntry("intendedToPhase", "shape");
            assertThat(payload).containsEntry("blockCode", "MISSING_ARTIFACT");
            assertThat(payload).containsEntry("blockReason", "Required PRD document not found");
            assertThat(payload).containsKey("blockedAt");
        }

        @Test
        @DisplayName("should publish lifecycle.phase.blocked on missing-artifact transition")
        void shouldPublishPhaseBlockedOnMissingArtifacts() {
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any()))
                    .thenReturn(Promise.complete());
            TransitionRequest request = new TransitionRequest(
                    "proj-3", "shape", "generate", "tenant-1", "user");
            TransitionResult result = TransitionResult.missingArtifacts(
                    List.of("architecture-doc", "api-spec"));

            // WHEN
            runPromise(() -> bridge.publishTransitionEvent(request, result));

            // THEN
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            verify(publisher).publish(
                    eq(AepEventBridge.EVENT_PHASE_BLOCKED),
                    eq("tenant-1"),
                    payloadCaptor.capture());
            Map<String, Object> payload = payloadCaptor.getValue();
            assertThat(payload).containsEntry("blockCode", "MISSING_ARTIFACT");
            @SuppressWarnings("unchecked")
            List<String> artifacts = (List<String>) payload.get("missingArtifacts");
            assertThat(artifacts).containsExactly("architecture-doc", "api-spec");
        }

        @Test
        @DisplayName("should swallow publisher failure even on blocked events")
        void shouldSwallowPublisherFailureOnBlockedEvent() {
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("AEP down")));
            TransitionRequest request = new TransitionRequest(
                    "proj-4", "intent", "context", "tenant-3", "user");
            TransitionResult result = TransitionResult.blocked("POLICY_DENIED", "Policy rejected");

            // WHEN — must not throw
            Void out = runPromise(() -> bridge.publishTransitionEvent(request, result));

            // THEN — failure swallowed
            assertThat(out).isNull();
        }
    }
}
