/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.eventcloud.channel.EventChannel;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
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


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EventCloudPlugin} lifecycle and capabilities.
 */
@DisplayName("EventCloudPlugin [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class EventCloudPluginTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Mock
    private EntityStore entityStore;

    @Mock
    private PluginContext pluginContext;

    private EventCloudPlugin plugin;

    @BeforeEach
    void setUp() { // GH-90000
        // Construct directly with platform types (factory wraps legacy data-cloud SPI; tests use platform mocks) // GH-90000
        plugin = new EventCloudPlugin(eventLogStore, entityStore, EventCloudPluginConfig.embeddedMode()); // GH-90000
    }

    @Test
    void shouldHaveCorrectMetadata() { // GH-90000
        assertThat(plugin.metadata().id()).isEqualTo("aep-event-cloud [GH-90000]");
        assertThat(plugin.metadata().name()).isEqualTo("AEP Event-Cloud Plugin [GH-90000]");
        assertThat(plugin.metadata().version()).isEqualTo("1.0.0 [GH-90000]");
        assertThat(plugin.metadata().capabilities()) // GH-90000
            .contains("event-storage", "event-streaming", "entity-registry"); // GH-90000
    }

    @Test
    void shouldStartInUnloadedState() { // GH-90000
        assertThat(plugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
    }

    @Test
    void shouldTransitionThroughLifecycleStates() { // GH-90000
        // GIVEN
        // WHEN: initialize
        runPromise(() -> plugin.initialize(pluginContext)); // GH-90000
        // THEN
        assertThat(plugin.getState()).isEqualTo(PluginState.INITIALIZED); // GH-90000

        // WHEN: start
        runPromise(() -> plugin.start()); // GH-90000
        // THEN
        assertThat(plugin.getState()).isEqualTo(PluginState.RUNNING); // GH-90000

        // WHEN: stop
        runPromise(() -> plugin.stop()); // GH-90000
        // THEN
        assertThat(plugin.getState()).isEqualTo(PluginState.STOPPED); // GH-90000
    }

    @Test
    void shouldExposeCapabilitiesAfterInitialization() { // GH-90000
        // GIVEN
        runPromise(() -> plugin.initialize(pluginContext)); // GH-90000

        // WHEN
        var capabilities = plugin.getCapabilities(); // GH-90000

        // THEN
        assertThat(capabilities).hasSize(3); // GH-90000
    }

    @Test
    void shouldExposeNoCapabilitiesBeforeInitialization() { // GH-90000
        assertThat(plugin.getCapabilities()).isEmpty(); // GH-90000
    }

    @Test
    void shouldRegisterDefaultChannelsOnStart() { // GH-90000
        // GIVEN
        runPromise(() -> plugin.initialize(pluginContext)); // GH-90000

        // WHEN
        runPromise(() -> plugin.start()); // GH-90000

        // THEN
        assertThat(plugin.channelRegistry().channelCount()).isEqualTo(5); // GH-90000
        assertThat(plugin.channelRegistry().getChannel(EventChannel.EVENTS_INTAKE.name())) // GH-90000
            .isPresent(); // GH-90000
        assertThat(plugin.channelRegistry().getChannel(EventChannel.PIPELINE_RUNS.name())) // GH-90000
            .isPresent(); // GH-90000
        assertThat(plugin.channelRegistry().getChannel(EventChannel.AGENT_DECISIONS.name())) // GH-90000
            .isPresent(); // GH-90000
        assertThat(plugin.channelRegistry().getChannel(EventChannel.LEARNING_EPISODES.name())) // GH-90000
            .isPresent(); // GH-90000
        assertThat(plugin.channelRegistry().getChannel(EventChannel.POLICY_PROMOTIONS.name())) // GH-90000
            .isPresent(); // GH-90000
    }

    @Test
    void shouldProvideEventCloudFacadeAfterInitialization() { // GH-90000
        // GIVEN
        runPromise(() -> plugin.initialize(pluginContext)); // GH-90000

        // THEN
        assertThat(plugin.eventCloud()).isNotNull(); // GH-90000
        assertThat(plugin.eventCloud()).isInstanceOf(DataCloudBackedEventCloud.class); // GH-90000
    }

    @Test
    void shouldProvideConnectorAfterInitialization() { // GH-90000
        // GIVEN
        runPromise(() -> plugin.initialize(pluginContext)); // GH-90000

        // THEN
        assertThat(plugin.connector()).isNotNull(); // GH-90000
        assertThat(plugin.connector()).isInstanceOf(DataCloudEventCloudConnector.class); // GH-90000
    }

    @Test
    void shouldReportHealthyWhenRunning() { // GH-90000
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class))) // GH-90000
            .thenReturn(Promise.of(Offset.zero())); // GH-90000

        runPromise(() -> plugin.initialize(pluginContext)); // GH-90000
        runPromise(() -> plugin.start()); // GH-90000

        // WHEN
        HealthStatus health = runPromise(() -> plugin.healthCheck()); // GH-90000

        // THEN
        assertThat(health.isHealthy()).isTrue(); // GH-90000
    }

    @Test
    void shouldReportUnhealthyWhenNotRunning() { // GH-90000
        // WHEN
        HealthStatus health = runPromise(() -> plugin.healthCheck()); // GH-90000

        // THEN
        assertThat(health.isHealthy()).isFalse(); // GH-90000
    }

    @Test
    void shouldReportUnhealthyWhenDataCloudUnreachable() { // GH-90000
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class))) // GH-90000
            .thenReturn(Promise.ofException(new RuntimeException("Connection refused [GH-90000]")));

        runPromise(() -> plugin.initialize(pluginContext)); // GH-90000
        runPromise(() -> plugin.start()); // GH-90000

        // WHEN
        HealthStatus health = runPromise(() -> plugin.healthCheck()); // GH-90000

        // THEN
        assertThat(health.isHealthy()).isFalse(); // GH-90000
        assertThat(health.getMessage()).contains("Data-Cloud unreachable [GH-90000]");
    }

    @Test
    void shouldClearChannelsOnStop() { // GH-90000
        // GIVEN
        runPromise(() -> plugin.initialize(pluginContext)); // GH-90000
        runPromise(() -> plugin.start()); // GH-90000
        assertThat(plugin.channelRegistry().channelCount()).isPositive(); // GH-90000

        // WHEN
        runPromise(() -> plugin.stop()); // GH-90000

        // THEN
        assertThat(plugin.channelRegistry().channelCount()).isZero(); // GH-90000
    }
}
