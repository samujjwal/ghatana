/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.channel;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EventChannelRegistry}.
 */
@DisplayName("EventChannelRegistry [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class EventChannelRegistryTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Captor
    private ArgumentCaptor<EventEntry> entryCaptor;

    private EventChannelRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new EventChannelRegistry(eventLogStore); // GH-90000
    }

    @Test
    void shouldRegisterChannel() { // GH-90000
        // WHEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE); // GH-90000

        // THEN
        assertThat(registry.channelCount()).isEqualTo(1); // GH-90000
        assertThat(registry.getChannel(EventChannel.EVENTS_INTAKE.name())).isPresent(); // GH-90000
    }

    @Test
    void shouldRegisterMultipleChannels() { // GH-90000
        // WHEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE); // GH-90000
        registry.registerChannel(EventChannel.PIPELINE_RUNS); // GH-90000
        registry.registerChannel(EventChannel.AGENT_DECISIONS); // GH-90000

        // THEN
        assertThat(registry.channelCount()).isEqualTo(3); // GH-90000
    }

    @Test
    void shouldBeIdempotentOnDuplicateRegistration() { // GH-90000
        // WHEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE); // GH-90000
        registry.registerChannel(EventChannel.EVENTS_INTAKE); // GH-90000

        // THEN
        assertThat(registry.channelCount()).isEqualTo(1); // GH-90000
    }

    @Test
    void shouldListAllChannels() { // GH-90000
        // GIVEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE); // GH-90000
        registry.registerChannel(EventChannel.PIPELINE_RUNS); // GH-90000

        // WHEN
        var channels = registry.listChannels(); // GH-90000

        // THEN
        assertThat(channels).hasSize(2); // GH-90000
    }

    @Test
    void shouldPublishToRegisteredChannel() { // GH-90000
        // GIVEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE); // GH-90000
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("1 [GH-90000]")));

        byte[] payload = "{\"data\":\"test\"}".getBytes(StandardCharsets.UTF_8); // GH-90000

        // WHEN
        String eventId = runPromise(() -> // GH-90000
            registry.publish(EventChannel.EVENTS_INTAKE.name(), // GH-90000
                "tenant-1", "order.created", payload));

        // THEN
        assertThat(eventId).isNotBlank(); // GH-90000
        verify(eventLogStore).append(any(), entryCaptor.capture()); // GH-90000

        EventEntry entry = entryCaptor.getValue(); // GH-90000
        assertThat(entry.eventType()) // GH-90000
            .isEqualTo("aep.events.intake.order.created [GH-90000]");
        assertThat(entry.headers().get("channel [GH-90000]"))
            .isEqualTo(EventChannel.EVENTS_INTAKE.name()); // GH-90000
    }

    @Test
    void shouldRejectPublishToUnregisteredChannel() { // GH-90000
        // WHEN/THEN
        try {
            runPromise(() -> // GH-90000
                registry.publish("nonexistent", "t1", "event", new byte[0])); // GH-90000
        } catch (Exception e) { // GH-90000
            assertThat(e).hasMessageContaining("Channel not registered: nonexistent [GH-90000]");
        }
        clearFatalError(); // GH-90000
    }

    @Test
    void shouldRemoveChannel() { // GH-90000
        // GIVEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE); // GH-90000
        assertThat(registry.channelCount()).isEqualTo(1); // GH-90000

        // WHEN
        boolean removed = registry.removeChannel(EventChannel.EVENTS_INTAKE.name()); // GH-90000

        // THEN
        assertThat(removed).isTrue(); // GH-90000
        assertThat(registry.channelCount()).isZero(); // GH-90000
    }

    @Test
    void shouldReturnFalseWhenRemovingNonexistentChannel() { // GH-90000
        assertThat(registry.removeChannel("nonexistent [GH-90000]")).isFalse();
    }

    @Test
    void shouldClearAllChannelsOnClose() { // GH-90000
        // GIVEN
        registry.registerChannel(EventChannel.EVENTS_INTAKE); // GH-90000
        registry.registerChannel(EventChannel.PIPELINE_RUNS); // GH-90000

        // WHEN
        registry.close(); // GH-90000

        // THEN
        assertThat(registry.channelCount()).isZero(); // GH-90000
    }

    @Test
    void shouldCreateCustomChannel() { // GH-90000
        // GIVEN
        EventChannel custom = EventChannel.of("custom.channel", "Custom events"); // GH-90000

        // WHEN
        registry.registerChannel(custom); // GH-90000

        // THEN
        assertThat(registry.getChannel("custom.channel [GH-90000]")).isPresent();
        assertThat(registry.getChannel("custom.channel [GH-90000]").get().description())
            .isEqualTo("Custom events [GH-90000]");
    }
}
