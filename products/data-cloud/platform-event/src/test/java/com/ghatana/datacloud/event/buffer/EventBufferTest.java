/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event.buffer;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EventBuffer}.
 */
@DisplayName("EventBuffer")
@ExtendWith(MockitoExtension.class)
class EventBufferTest extends EventloopTestBase {

    @Mock
    private EventLogStore spillStore;

    private EventBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new EventBuffer(spillStore, "test-buffer", 10, 8, 2);
    }

    private EventEntry testEntry(String eventType) {
        return EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .payload(ByteBuffer.wrap(("payload-" + eventType).getBytes()))
            .headers(Map.of())
            .build();
    }

    @Test
    void shouldAcceptEventsWithinCapacity() {
        for (int i = 0; i < 10; i++) {
            assertThat(buffer.offer(testEntry("evt." + i))).isTrue();
        }
        assertThat(buffer.size()).isEqualTo(10);
    }

    @Test
    void shouldRejectWhenFull() {
        for (int i = 0; i < 10; i++) {
            buffer.offer(testEntry("evt." + i));
        }
        assertThat(buffer.offer(testEntry("evt.overflow"))).isFalse();
        assertThat(buffer.size()).isEqualTo(10);
    }

    @Test
    void shouldDrainInOrder() {
        buffer.offer(testEntry("first"));
        buffer.offer(testEntry("second"));
        buffer.offer(testEntry("third"));

        List<EventEntry> drained = buffer.drain(2);

        assertThat(drained).hasSize(2);
        assertThat(drained.get(0).eventType()).isEqualTo("first");
        assertThat(drained.get(1).eventType()).isEqualTo("second");
        assertThat(buffer.size()).isEqualTo(1);
    }

    @Test
    void shouldDrainLessThanRequested() {
        buffer.offer(testEntry("only-one"));

        List<EventEntry> drained = buffer.drain(5);

        assertThat(drained).hasSize(1);
        assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    void shouldReturnEmptyOnDrainWhenEmpty() {
        List<EventEntry> drained = buffer.drain(10);
        assertThat(drained).isEmpty();
    }

    @Test
    void shouldReportHighWaterMark() {
        assertThat(buffer.isOverHighWaterMark()).isFalse();

        for (int i = 0; i < 9; i++) {
            buffer.offer(testEntry("evt." + i));
        }
        assertThat(buffer.isOverHighWaterMark()).isTrue();
    }

    @Test
    void shouldReportLowWaterMark() {
        assertThat(buffer.isBelowLowWaterMark()).isTrue();
        buffer.offer(testEntry("one"));
        assertThat(buffer.isBelowLowWaterMark()).isTrue();
        buffer.offer(testEntry("two"));
        assertThat(buffer.isBelowLowWaterMark()).isFalse();
    }

    @Test
    void shouldSpillExcessToStore() {
        for (int i = 0; i < 10; i++) {
            buffer.offer(testEntry("evt." + i));
        }

        when(spillStore.appendBatch(any(TenantContext.class), any()))
            .thenReturn(Promise.of(List.of(Offset.of(1L), Offset.of(2L))));

        Integer spilled = runPromise(() -> buffer.spillExcess("tenant-1"));

        assertThat(spilled).isEqualTo(2);
        assertThat(buffer.size()).isEqualTo(8);
    }

    @Test
    void shouldNotSpillWhenBelowHighWaterMark() {
        buffer.offer(testEntry("evt.1"));

        Integer spilled = runPromise(() -> buffer.spillExcess("tenant-1"));

        assertThat(spilled).isZero();
    }

    @Test
    void shouldTrackStats() {
        buffer.offer(testEntry("evt.1"));
        buffer.offer(testEntry("evt.2"));
        buffer.drain(1);

        Map<String, Object> stats = buffer.stats();

        assertThat(stats.get("bufferName")).isEqualTo("test-buffer");
        assertThat(stats.get("size")).isEqualTo(1);
        assertThat(stats.get("capacity")).isEqualTo(10);
        assertThat(stats.get("totalEnqueued")).isEqualTo(2L);
        assertThat(stats.get("totalDrained")).isEqualTo(1L);
    }

    @Test
    void shouldRejectNullEntry() {
        assertThatThrownBy(() -> buffer.offer(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectInvalidConstructorParams() {
        assertThatThrownBy(() -> new EventBuffer(spillStore, "bad", 0, 0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("capacity");

        assertThatThrownBy(() -> new EventBuffer(spillStore, "bad", 10, 15, 2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("highWaterMark");

        assertThatThrownBy(() -> new EventBuffer(spillStore, "bad", 10, 8, 9))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lowWaterMark");
    }
}
