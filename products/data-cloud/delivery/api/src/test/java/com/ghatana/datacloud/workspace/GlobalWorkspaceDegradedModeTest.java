/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.workspace;

import com.ghatana.datacloud.attention.SalienceScore;
import com.ghatana.datacloud.client.ContextGateway;
import com.ghatana.datacloud.client.LearningSignalStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-A15: Optional backend degraded-mode coverage for GlobalWorkspace.
 *
 * <p>Verifies spotlight and broadcast flows remain available when optional AI
 * backends are absent or temporarily unavailable.
 *
 * @doc.type class
 * @doc.purpose Degraded-mode tests for optional context and learning backends
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalWorkspace degraded mode")
class GlobalWorkspaceDegradedModeTest extends EventloopTestBase {

    @Mock
    private ContextGateway contextGateway;

    @Mock
    private LearningSignalStore signalStore;

    @Test
    @DisplayName("spotlight succeeds when optional AI backends are not configured")
    void spotlightSucceedsWhenOptionalBackendsAbsent() {
        GlobalWorkspace workspace = GlobalWorkspace.builder()
                .maxSpotlightSize(10)
                .build();

        SpotlightItem item = spotlightItem("item-null-deps", "tenant-a");

        runPromise(() -> workspace.spotlight(item));

        assertThat(workspace.size()).isEqualTo(1);
        assertThat(workspace.get("item-null-deps")).isPresent();
        assertThat(workspace.getStats().totalSpotlighted()).isEqualTo(1L);
    }

    @Test
    @DisplayName("spotlight degrades gracefully when context and learning backends fail")
    void spotlightDegradesGracefullyWhenBackendsFail() {
        GlobalWorkspace workspace = GlobalWorkspace.builder()
                .contextGateway(contextGateway)
                .signalStore(signalStore)
                .maxSpotlightSize(10)
                .build();

        when(contextGateway.store(any()))
                .thenReturn(Promise.ofException(new RuntimeException("context backend unavailable")));
        when(signalStore.store(any()))
                .thenReturn(Promise.ofException(new RuntimeException("signal backend unavailable")));

        SpotlightItem item = spotlightItem("item-degraded", "tenant-b");

        runPromise(() -> workspace.spotlight(item));

        verify(contextGateway).store(any());
        verify(signalStore).store(any());
        assertThat(workspace.size()).isEqualTo(1);
        assertThat(workspace.get("item-degraded")).isPresent();
        assertThat(workspace.getStats().totalSpotlighted()).isEqualTo(1L);
    }

    @Test
    @DisplayName("broadcast degrades gracefully when optional backends fail")
    void broadcastDegradesGracefullyWhenBackendsFail() {
        GlobalWorkspace workspace = GlobalWorkspace.builder()
                .contextGateway(contextGateway)
                .signalStore(signalStore)
                .maxSpotlightSize(10)
                .build();

        when(contextGateway.store(any()))
                .thenReturn(Promise.ofException(new RuntimeException("context backend unavailable")));
        when(signalStore.store(any()))
                .thenReturn(Promise.ofException(new RuntimeException("signal backend unavailable")));

        SpotlightItem emergency = spotlightItem("item-emergency", "tenant-c").toBuilder()
                .emergency(true)
                .priority(1)
                .build();

        runPromise(() -> workspace.broadcast(emergency));

        verify(contextGateway).store(any());
        verify(signalStore).store(any());
        assertThat(workspace.get("item-emergency")).isPresent();
        assertThat(workspace.getStats().totalBroadcasts()).isEqualTo(1L);
    }

    private static SpotlightItem spotlightItem(String id, String tenantId) {
        return SpotlightItem.builder()
                .id(id)
                .tenantId(tenantId)
                .summary("workspace item " + id)
                .salienceScore(SalienceScore.of(0.85))
                .context(Map.of("source", "test"))
                .build();
    }
}
