/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.channel;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EventChannelRegistry}.
 */
@DisplayName("EventChannelRegistry")
@ExtendWith(MockitoExtension.class)
class EventChannelRegistryTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Captor
    private ArgumentCaptor<EventEntry> entryCaptor;

    private EventChannelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new EventChannelRegistry(eventLogStore);
    }

    @Test
    void shouldRegisterChannel() {
        // WHEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE);

        // THEN
        assertThat(registry.channelCount()).isEqualTo(1);
        assertThat(registry.getChannel(EventChannel.EVENTS_INTAKE.name())).isPresent();
    }

    @Test
    void shouldRegisterMultipleChannels() {
        // WHEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE);
        registry.registerChannel(EventChannel.PIPELINE_RUNS);
        registry.registerChannel(EventChannel.AGENT_DECISIONS);

        // THEN
        assertThat(registry.channelCount()).isEqualTo(3);
    }

    @Test
    void shouldBeIdempotentOnDuplicateRegistration() {
        // WHEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE);
        registry.registerChannel(EventChannel.EVENTS_INTAKE);

        // THEN
        assertThat(registry.channelCount()).isEqualTo(1);
    }

    @Test
    void shouldListAllChannels() {
        // GIVEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE);
        registry.registerChannel(EventChannel.PIPELINE_RUNS);

        // WHEN
        var channels = registry.listChannels();

        // THEN
        assertThat(channels).hasSize(2);
    }

    @Test
    void shouldPublishToRegisteredChannel() {
        // GIVEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE);
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("1")));

        byte[] payload = "{\"data\":\"test\"}".getBytes(StandardCharsets.UTF_8);

        // WHEN
        String eventId = runPromise(() ->
            registry.publish(EventChannel.EVENTS_INTAKE.name(),
                "tenant-1", "order.created", payload));

        // THEN
        assertThat(eventId).isNotBlank();
        verify(eventLogStore).append(any(), entryCaptor.capture());

        EventEntry entry = entryCaptor.getValue();
        assertThat(entry.eventType())
            .isEqualTo("aep.events.intake.order.created");
        assertThat(entry.headers().get("channel"))
            .isEqualTo(EventChannel.EVENTS_INTAKE.name());
    }

    @Test
    void shouldRejectPublishToUnregisteredChannel() {
        // WHEN/THEN
        try {
            runPromise(() ->
                registry.publish("nonexistent", "t1", "event", new byte[0]));
        } catch (Exception e) {
            assertThat(e).hasMessageContaining("Channel not registered: nonexistent");
        }
        clearFatalError();
    }

    @Test
    void shouldRemoveChannel() {
        // GIVEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE);
        assertThat(registry.channelCount()).isEqualTo(1);

        // WHEN
        boolean removed = registry.removeChannel(EventChannel.EVENTS_INTAKE.name());

        // THEN
        assertThat(removed).isTrue();
        assertThat(registry.channelCount()).isZero();
    }

    @Test
    void shouldReturnFalseWhenRemovingNonexistentChannel() {
        assertThat(registry.removeChannel("nonexistent")).isFalse();
    }

    @Test
    void shouldClearAllChannelsOnClose() {
        // GIVEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE);
        registry.registerChannel(EventChannel.PIPELINE_RUNS);

        // WHEN
        registry.close();

        // THEN
        assertThat(registry.channelCount()).isZero();
    }

    @Test
    void shouldCreateCustomChannel() {
        // GIVEN
        EventChannel custom = EventChannel.of("custom.channel", "Custom events");

        // WHEN
        registry.registerChannel(custom);

        // THEN
        assertThat(registry.getChannel("custom.channel")).isPresent();
        assertThat(registry.getChannel("custom.channel").get().description())
            .isEqualTo("Custom events");
    }
}
