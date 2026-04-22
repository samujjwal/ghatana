/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
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
 * Unit tests for {@link AepEventBridge} (YAPPC-Ph5). // GH-90000
 *
 * <p>All async tests use {@link EventloopTestBase#runPromise} to execute
 * ActiveJ Promises on the managed event loop without blocking.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the AepEventBridge resilient event-publishing facade
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepEventBridge (YAPPC-Ph5) [GH-90000]")
class AepEventBridgeTest extends EventloopTestBase {

    @Mock
    private AepEventPublisher publisher;

    private AepEventBridge bridge;

    @BeforeEach
    void setUp() { // GH-90000
        bridge = new AepEventBridge(publisher); // GH-90000
    }

    // =========================================================================
    // publishRawEvent tests
    // =========================================================================

    @Nested
    @DisplayName("publishRawEvent [GH-90000]")
    class PublishRawEventTests {

        @Test
        @DisplayName("should publish event and return null on success [GH-90000]")
        void shouldPublishEventOnSuccess() { // GH-90000
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any())) // GH-90000
                    .thenReturn(Promise.complete()); // GH-90000
            Map<String, Object> payload = Map.of("key", "value"); // GH-90000

            // WHEN
            Void result = runPromise(() -> bridge.publishRawEvent("test.event", "tenant-1", payload)); // GH-90000

            // THEN
            assertThat(result).isNull(); // GH-90000
            verify(publisher).publish("test.event", "tenant-1", payload); // GH-90000
        }

        @Test
        @DisplayName("should swallow publisher failure and still return null [GH-90000]")
        void shouldSwallowPublisherFailure() { // GH-90000
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Downstream AEP error [GH-90000]")));
            Map<String, Object> payload = Map.of("key", "val"); // GH-90000

            // WHEN — must not throw
            Void result = runPromise(() -> bridge.publishRawEvent("test.event", "tenant-x", payload)); // GH-90000

            // THEN — failure swallowed, null returned
            assertThat(result).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should pass all payload fields through to publisher unchanged [GH-90000]")
        void shouldPassPayloadThroughToPublisher() { // GH-90000
            // GIVEN
            @SuppressWarnings("unchecked [GH-90000]")
            ArgumentCaptor<Map<String, Object>> payloadCaptor =
                    ArgumentCaptor.forClass(Map.class); // GH-90000
            when(publisher.publish(anyString(), anyString(), any())) // GH-90000
                    .thenReturn(Promise.complete()); // GH-90000
            Map<String, Object> payload = Map.of("projectId", "proj-1", "stage", "context"); // GH-90000

            // WHEN
            runPromise(() -> bridge.publishRawEvent("lifecycle.phase.advanced", "t1", payload)); // GH-90000

            // THEN
            verify(publisher).publish(eq("lifecycle.phase.advanced [GH-90000]"), eq("t1 [GH-90000]"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue()).containsEntry("projectId", "proj-1"); // GH-90000
        }
    }

    // =========================================================================
    // publishTransitionEvent — success path
    // =========================================================================

    @Nested
    @DisplayName("publishTransitionEvent — success [GH-90000]")
    class PublishTransitionEventSuccessTests {

        @Test
        @DisplayName("should publish lifecycle.phase.advanced on successful transition [GH-90000]")
        void shouldPublishPhaseAdvancedOnSuccess() { // GH-90000
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any())) // GH-90000
                    .thenReturn(Promise.complete()); // GH-90000
            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-1", "intent", "context", "tenant-1", "user@example.com");
            TransitionResult result = TransitionResult.success("context [GH-90000]");

            // WHEN
            Void out = runPromise(() -> bridge.publishTransitionEvent(request, result)); // GH-90000

            // THEN
            assertThat(out).isNull(); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000
            verify(publisher).publish( // GH-90000
                    eq(AepEventBridge.EVENT_PHASE_ADVANCED), // GH-90000
                    eq("tenant-1 [GH-90000]"),
                    payloadCaptor.capture()); // GH-90000
            Map<String, Object> payload = payloadCaptor.getValue(); // GH-90000
            assertThat(payload).containsEntry("projectId", "proj-1"); // GH-90000
            assertThat(payload).containsEntry("fromPhase", "intent"); // GH-90000
            assertThat(payload).containsEntry("toPhase", "context"); // GH-90000
            assertThat(payload).containsEntry("requestedBy", "user@example.com"); // GH-90000
            assertThat(payload).containsKey("advancedAt [GH-90000]");
        }
    }

    // =========================================================================
    // publishTransitionEvent — blocked path
    // =========================================================================

    @Nested
    @DisplayName("publishTransitionEvent — blocked [GH-90000]")
    class PublishTransitionEventBlockedTests {

        @Test
        @DisplayName("should publish lifecycle.phase.blocked on blocked transition [GH-90000]")
        void shouldPublishPhaseBlockedOnBlockedResult() { // GH-90000
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any())) // GH-90000
                    .thenReturn(Promise.complete()); // GH-90000
            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-2", "context", "shape", "tenant-2", "agent-X");
            TransitionResult result = TransitionResult.blocked( // GH-90000
                    "MISSING_ARTIFACT", "Required PRD document not found");

            // WHEN
            Void out = runPromise(() -> bridge.publishTransitionEvent(request, result)); // GH-90000

            // THEN
            assertThat(out).isNull(); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000
            verify(publisher).publish( // GH-90000
                    eq(AepEventBridge.EVENT_PHASE_BLOCKED), // GH-90000
                    eq("tenant-2 [GH-90000]"),
                    payloadCaptor.capture()); // GH-90000
            Map<String, Object> payload = payloadCaptor.getValue(); // GH-90000
            assertThat(payload).containsEntry("projectId", "proj-2"); // GH-90000
            assertThat(payload).containsEntry("intendedToPhase", "shape"); // GH-90000
            assertThat(payload).containsEntry("blockCode", "MISSING_ARTIFACT"); // GH-90000
            assertThat(payload).containsEntry("blockReason", "Required PRD document not found"); // GH-90000
            assertThat(payload).containsKey("blockedAt [GH-90000]");
        }

        @Test
        @DisplayName("should publish lifecycle.phase.blocked on missing-artifact transition [GH-90000]")
        void shouldPublishPhaseBlockedOnMissingArtifacts() { // GH-90000
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any())) // GH-90000
                    .thenReturn(Promise.complete()); // GH-90000
            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-3", "shape", "generate", "tenant-1", "user");
            TransitionResult result = TransitionResult.missingArtifacts( // GH-90000
                    List.of("architecture-doc", "api-spec")); // GH-90000

            // WHEN
            runPromise(() -> bridge.publishTransitionEvent(request, result)); // GH-90000

            // THEN
            @SuppressWarnings("unchecked [GH-90000]")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000
            verify(publisher).publish( // GH-90000
                    eq(AepEventBridge.EVENT_PHASE_BLOCKED), // GH-90000
                    eq("tenant-1 [GH-90000]"),
                    payloadCaptor.capture()); // GH-90000
            Map<String, Object> payload = payloadCaptor.getValue(); // GH-90000
            assertThat(payload).containsEntry("blockCode", "MISSING_ARTIFACT"); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            List<String> artifacts = (List<String>) payload.get("missingArtifacts [GH-90000]");
            assertThat(artifacts).containsExactly("architecture-doc", "api-spec"); // GH-90000
        }

        @Test
        @DisplayName("should swallow publisher failure even on blocked events [GH-90000]")
        void shouldSwallowPublisherFailureOnBlockedEvent() { // GH-90000
            // GIVEN
            when(publisher.publish(anyString(), anyString(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("AEP down [GH-90000]")));
            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-4", "intent", "context", "tenant-3", "user");
            TransitionResult result = TransitionResult.blocked("POLICY_DENIED", "Policy rejected"); // GH-90000

            // WHEN — must not throw
            Void out = runPromise(() -> bridge.publishTransitionEvent(request, result)); // GH-90000

            // THEN — failure swallowed
            assertThat(out).isNull(); // GH-90000
        }
    }
}
