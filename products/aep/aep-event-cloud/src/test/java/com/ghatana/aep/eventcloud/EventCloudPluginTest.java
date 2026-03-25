/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.eventcloud.channel.EventChannel;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EventCloudPlugin} lifecycle and capabilities.
 */
@DisplayName("EventCloudPlugin")
@ExtendWith(MockitoExtension.class)
class EventCloudPluginTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Mock
    private EntityStore entityStore;

    @Mock
    private PluginContext pluginContext;

    private EventCloudPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = EventCloudPluginFactory.embedded(eventLogStore, entityStore);
    }

    @Test
    void shouldHaveCorrectMetadata() {
        assertThat(plugin.metadata().id()).isEqualTo("aep-event-cloud");
        assertThat(plugin.metadata().name()).isEqualTo("AEP Event-Cloud Plugin");
        assertThat(plugin.metadata().version()).isEqualTo("1.0.0");
        assertThat(plugin.metadata().capabilities())
            .contains("event-storage", "event-streaming", "entity-registry");
    }

    @Test
    void shouldStartInUnloadedState() {
        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED);
    }

    @Test
    void shouldTransitionThroughLifecycleStates() {
        // GIVEN
        // WHEN: initialize
        runPromise(() -> plugin.initialize(pluginContext));
        // THEN
        assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED);

        // WHEN: start
        runPromise(() -> plugin.start());
        // THEN
        assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING);

        // WHEN: stop
        runPromise(() -> plugin.stop());
        // THEN
        assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED);
    }

    @Test
    void shouldExposeCapabilitiesAfterInitialization() {
        // GIVEN
        runPromise(() -> plugin.initialize(pluginContext));

        // WHEN
        var capabilities = plugin.getCapabilities();

        // THEN
        assertThat(capabilities).hasSize(3);
    }

    @Test
    void shouldExposeNoCapabilitiesBeforeInitialization() {
        assertThat(plugin.getCapabilities()).isEmpty();
    }

    @Test
    void shouldRegisterDefaultChannelsOnStart() {
        // GIVEN
        runPromise(() -> plugin.initialize(pluginContext));

        // WHEN
        runPromise(() -> plugin.start());

        // THEN
        assertThat(plugin.channelRegistry().channelCount()).isEqualTo(5);
        assertThat(plugin.channelRegistry().getChannel(EventChannel.EVENTS_INTAKE.name()))
            .isPresent();
        assertThat(plugin.channelRegistry().getChannel(EventChannel.PIPELINE_RUNS.name()))
            .isPresent();
        assertThat(plugin.channelRegistry().getChannel(EventChannel.AGENT_DECISIONS.name()))
            .isPresent();
        assertThat(plugin.channelRegistry().getChannel(EventChannel.LEARNING_EPISODES.name()))
            .isPresent();
        assertThat(plugin.channelRegistry().getChannel(EventChannel.POLICY_PROMOTIONS.name()))
            .isPresent();
    }

    @Test
    void shouldProvideEventCloudFacadeAfterInitialization() {
        // GIVEN
        runPromise(() -> plugin.initialize(pluginContext));

        // THEN
        assertThat(plugin.eventCloud()).isNotNull();
        assertThat(plugin.eventCloud()).isInstanceOf(DataCloudBackedEventCloud.class);
    }

    @Test
    void shouldProvideConnectorAfterInitialization() {
        // GIVEN
        runPromise(() -> plugin.initialize(pluginContext));

        // THEN
        assertThat(plugin.connector()).isNotNull();
        assertThat(plugin.connector()).isInstanceOf(DataCloudEventCloudConnector.class);
    }

    @Test
    void shouldReportHealthyWhenRunning() {
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class)))
            .thenReturn(Promise.of(Offset.zero()));

        runPromise(() -> plugin.initialize(pluginContext));
        runPromise(() -> plugin.start());

        // WHEN
        HealthStatus health = runPromise(() -> plugin.healthCheck());

        // THEN
        assertThat(health.healthy()).isTrue();
    }

    @Test
    void shouldReportUnhealthyWhenNotRunning() {
        // WHEN
        HealthStatus health = runPromise(() -> plugin.healthCheck());

        // THEN
        assertThat(health.healthy()).isFalse();
    }

    @Test
    void shouldReportUnhealthyWhenDataCloudUnreachable() {
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Connection refused")));

        runPromise(() -> plugin.initialize(pluginContext));
        runPromise(() -> plugin.start());

        // WHEN
        HealthStatus health = runPromise(() -> plugin.healthCheck());

        // THEN
        assertThat(health.healthy()).isFalse();
        assertThat(health.message()).contains("Data-Cloud unreachable");
    }

    @Test
    void shouldClearChannelsOnStop() {
        // GIVEN
        runPromise(() -> plugin.initialize(pluginContext));
        runPromise(() -> plugin.start());
        assertThat(plugin.channelRegistry().channelCount()).isPositive();

        // WHEN
        runPromise(() -> plugin.stop());

        // THEN
        assertThat(plugin.channelRegistry().channelCount()).isZero();
    }
}
