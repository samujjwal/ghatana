/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-P0-02: Golden test for Action Plane bridge canonical envelope reception.
 *
 * <p>Verifies that when events are shared to the Action Plane bridge via the AepContextBridge,
 * all canonical envelope fields are preserved. This ensures the Action Plane receives the complete
 * event envelope with actor, trace context, correlation ID, causation ID, policy context, and other
 * critical fields.
 *
 * <p>This test is critical for ensuring:
 * <ul>
 *   <li>Action Plane receives full event context for proper execution</li>
 *   <li>Distributed tracing is maintained across the bridge</li>
 *   <li>Policy evaluation context is preserved</li>
 *   <li>Audit trail integrity is maintained</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Golden test for Action Plane bridge canonical envelope reception (DC-P0-02)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Action Plane Bridge Canonical Envelope Test (DC-P0-02)")
@Tag("golden")
@Tag("durability")
@Tag("production")
@ExtendWith(MockitoExtension.class)
class AepContextBridgeCanonicalEnvelopeTest extends EventloopTestBase {

    @Mock private MemoryStore memoryStore;
    @Mock private AepEngine engine;
    @Mock private EventCloud eventCloud;

    private AepContextBridge bridge;

    @BeforeEach
    void setUp() {
        // Only some tests exercise engine-backed sharing; keep shared setup lenient.
        lenient().when(engine.eventCloud()).thenReturn(eventCloud);
        bridge = new AepContextBridge(memoryStore, engine);
    }

    @Test
    @DisplayName("DC-P0-02: Bridge activation enables event sharing to Action Plane")
    void bridgeActivationEnablesEventSharing() {
        runPromise(() -> bridge.activate());
        assertThat(bridge.isActive()).isTrue();
    }

    @Test
    @DisplayName("DC-P0-02: Shared context is appended to Action Plane event cloud")
    void sharedContextIsAppendedToActionPlaneEventCloud() {
        runPromise(() -> bridge.activate());

        String context = """
            {
              "eventId": "evt-12345678-1234-1234-1234-123456789abc",
              "type": "entity.created",
              "actor": "user-alice",
              "traceContext": "trace-abc123",
              "correlationId": "corr-def456",
              "causationId": "cause-ghi789",
              "policyContext": "{\"policyId\":\"policy-001\",\"decision\":\"allow\"}",
              "tenantId": "tenant-123",
              "workspaceId": "workspace-456",
              "payload": {"name": "Test Product"}
            }
            """;

        when(eventCloud.append(any(), any(), any())).thenReturn("event-1");

        runPromise(() -> bridge.shareToAep("tenant-123", context));

        verify(eventCloud).append(eq("tenant-123"), eq(AepContextBridge.CONTEXT_EVENT_TYPE), any());
    }

    @Test
    @DisplayName("DC-P0-02: Canonical envelope fields are preserved in shared context")
    void canonicalEnvelopeFieldsArePreservedInSharedContext() {
        runPromise(() -> bridge.activate());

        String canonicalContext = """
            {
              "eventId": "evt-12345678-1234-1234-1234-123456789abc",
              "type": "entity.created",
              "actor": "user-alice",
              "traceContext": "trace-abc123",
              "correlationId": "corr-def456",
              "causationId": "cause-ghi789",
              "policyContext": "{\"policyId\":\"policy-001\",\"decision\":\"allow\"}",
              "tenantId": "tenant-123",
              "workspaceId": "workspace-456",
              "classification": "sensitive",
              "provenance": "datacloud.launcher.event-handler",
              "subjectType": "Product",
              "subjectId": "entity-789",
              "timestamp": "2026-05-20T12:00:00Z",
              "payload": {"name": "Test Product"}
            }
            """;

        when(eventCloud.append(any(), any(), any())).thenReturn("event-1");

        runPromise(() -> bridge.shareToAep("tenant-123", canonicalContext));

        verify(eventCloud).append(eq("tenant-123"), eq(AepContextBridge.CONTEXT_EVENT_TYPE), any(byte[].class));

        // Verify the context can be retrieved
        String retrievedContext = runPromise(() -> bridge.getFromAep("tenant-123"));
        assertThat(retrievedContext).isEqualTo(canonicalContext);
    }

    @Test
    @DisplayName("DC-P0-02: Bridge preserves context across multiple tenants")
    void bridgePreservesContextAcrossMultipleTenants() {
        runPromise(() -> bridge.activate());

        String context1 = "{\"eventId\":\"evt-1\",\"type\":\"event1\"}";
        String context2 = "{\"eventId\":\"evt-2\",\"type\":\"event2\"}";

        when(eventCloud.append(any(), any(), any())).thenReturn("event-1");

        runPromise(() -> bridge.shareToAep("tenant-1", context1));
        runPromise(() -> bridge.shareToAep("tenant-2", context2));

        String retrieved1 = runPromise(() -> bridge.getFromAep("tenant-1"));
        String retrieved2 = runPromise(() -> bridge.getFromAep("tenant-2"));

        assertThat(retrieved1).isEqualTo(context1);
        assertThat(retrieved2).isEqualTo(context2);
    }

    @Test
    @DisplayName("DC-P0-02: Bridge rejects sharing when not activated")
    void bridgeRejectsSharingWhenNotActivated() {
        String context = "{\"eventId\":\"evt-1\"}";

        assertThatThrownBy(() -> runPromise(() -> bridge.shareToAep("tenant-123", context)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Bridge not active");
    }

    @Test
    @DisplayName("DC-P0-02: Bridge rejects null tenant ID")
    void bridgeRejectsNullTenantId() {
        runPromise(() -> bridge.activate());

        String context = "{\"eventId\":\"evt-1\"}";

        assertThatThrownBy(() -> {
            runPromise(() -> bridge.shareToAep(null, context));
        }).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId must not be null");
    }

    @Test
    @DisplayName("DC-P0-02: Bridge rejects null context")
    void bridgeRejectsNullContext() {
        runPromise(() -> bridge.activate());

        assertThatThrownBy(() -> {
            runPromise(() -> bridge.shareToAep("tenant-123", null));
        }).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("context must not be null");
    }

    @Test
    @DisplayName("DC-P0-02: Bridge works without engine (context-only mode)")
    void bridgeWorksWithoutEngine() {
        AepContextBridge bridgeWithoutEngine = new AepContextBridge(memoryStore, null);
        runPromise(() -> bridgeWithoutEngine.activate());

        String context = "{\"eventId\":\"evt-1\"}";

        runPromise(() -> bridgeWithoutEngine.shareToAep("tenant-123", context));

        String retrieved = runPromise(() -> bridgeWithoutEngine.getFromAep("tenant-123"));
        assertThat(retrieved).isEqualTo(context);

        // Verify no eventCloud.append was called (engine is null)
        verify(eventCloud, never()).append(any(), any(), any());
    }

    @Test
    @DisplayName("DC-P0-02: Bridge deactivation prevents further sharing")
    void bridgeDeactivationPreventsFurtherSharing() {
        runPromise(() -> bridge.activate());
        runPromise(() -> bridge.deactivate());

        String context = "{\"eventId\":\"evt-1\"}";

        assertThatThrownBy(() -> runPromise(() -> bridge.shareToAep("tenant-123", context)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Bridge not active");
    }
}
