/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
class EventBufferTest extends EventloopTestBase {

    @Mock
    private EventLogStore spillStore;

    private EventBuffer buffer;

    @BeforeEach
    void setUp() { // GH-90000
        buffer = new EventBuffer(spillStore, "test-buffer", 10, 8, 2); // GH-90000
    }

    private EventEntry testEntry(String eventType) { // GH-90000
        return EventEntry.builder() // GH-90000
            .eventId(UUID.randomUUID()) // GH-90000
            .eventType(eventType) // GH-90000
            .payload(ByteBuffer.wrap(("payload-" + eventType).getBytes())) // GH-90000
            .headers(Map.of()) // GH-90000
            .build(); // GH-90000
    }

    @Test
    void shouldAcceptEventsWithinCapacity() { // GH-90000
        for (int i = 0; i < 10; i++) { // GH-90000
            assertThat(buffer.offer(testEntry("evt." + i))).isTrue(); // GH-90000
        }
        assertThat(buffer.size()).isEqualTo(10); // GH-90000
    }

    @Test
    void shouldRejectWhenFull() { // GH-90000
        for (int i = 0; i < 10; i++) { // GH-90000
            buffer.offer(testEntry("evt." + i)); // GH-90000
        }
        assertThat(buffer.offer(testEntry("evt.overflow"))).isFalse();
        assertThat(buffer.size()).isEqualTo(10); // GH-90000
    }

    @Test
    void shouldDrainInOrder() { // GH-90000
        buffer.offer(testEntry("first"));
        buffer.offer(testEntry("second"));
        buffer.offer(testEntry("third"));

        List<EventEntry> drained = buffer.drain(2); // GH-90000

        assertThat(drained).hasSize(2); // GH-90000
        assertThat(drained.get(0).eventType()).isEqualTo("first");
        assertThat(drained.get(1).eventType()).isEqualTo("second");
        assertThat(buffer.size()).isEqualTo(1); // GH-90000
    }

    @Test
    void shouldDrainLessThanRequested() { // GH-90000
        buffer.offer(testEntry("only-one"));

        List<EventEntry> drained = buffer.drain(5); // GH-90000

        assertThat(drained).hasSize(1); // GH-90000
        assertThat(buffer.isEmpty()).isTrue(); // GH-90000
    }

    @Test
    void shouldReturnEmptyOnDrainWhenEmpty() { // GH-90000
        List<EventEntry> drained = buffer.drain(10); // GH-90000
        assertThat(drained).isEmpty(); // GH-90000
    }

    @Test
    void shouldReportHighWaterMark() { // GH-90000
        assertThat(buffer.isOverHighWaterMark()).isFalse(); // GH-90000

        for (int i = 0; i < 9; i++) { // GH-90000
            buffer.offer(testEntry("evt." + i)); // GH-90000
        }
        assertThat(buffer.isOverHighWaterMark()).isTrue(); // GH-90000
    }

    @Test
    void shouldReportLowWaterMark() { // GH-90000
        assertThat(buffer.isBelowLowWaterMark()).isTrue(); // GH-90000
        buffer.offer(testEntry("one"));
        assertThat(buffer.isBelowLowWaterMark()).isTrue(); // GH-90000
        buffer.offer(testEntry("two"));
        assertThat(buffer.isBelowLowWaterMark()).isFalse(); // GH-90000
    }

    @Test
    void shouldSpillExcessToStore() { // GH-90000
        for (int i = 0; i < 10; i++) { // GH-90000
            buffer.offer(testEntry("evt." + i)); // GH-90000
        }

        when(spillStore.appendBatch(any(TenantContext.class), any())) // GH-90000
            .thenReturn(Promise.of(List.of(Offset.of(1L), Offset.of(2L)))); // GH-90000

        Integer spilled = runPromise(() -> buffer.spillExcess("tenant-1"));

        assertThat(spilled).isEqualTo(2); // GH-90000
        assertThat(buffer.size()).isEqualTo(8); // GH-90000
    }

    @Test
    void shouldNotSpillWhenBelowHighWaterMark() { // GH-90000
        buffer.offer(testEntry("evt.1"));

        Integer spilled = runPromise(() -> buffer.spillExcess("tenant-1"));

        assertThat(spilled).isZero(); // GH-90000
    }

    @Test
    void shouldTrackStats() { // GH-90000
        buffer.offer(testEntry("evt.1"));
        buffer.offer(testEntry("evt.2"));
        buffer.drain(1); // GH-90000

        Map<String, Object> stats = buffer.stats(); // GH-90000

        assertThat(stats.get("bufferName")).isEqualTo("test-buffer");
        assertThat(stats.get("size")).isEqualTo(1);
        assertThat(stats.get("capacity")).isEqualTo(10);
        assertThat(stats.get("totalEnqueued")).isEqualTo(2L);
        assertThat(stats.get("totalDrained")).isEqualTo(1L);
    }

    @Test
    void shouldRejectNullEntry() { // GH-90000
        assertThatThrownBy(() -> buffer.offer(null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    void shouldRejectInvalidConstructorParams() { // GH-90000
        assertThatThrownBy(() -> new EventBuffer(spillStore, "bad", 0, 0, 0)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("capacity");

        assertThatThrownBy(() -> new EventBuffer(spillStore, "bad", 10, 15, 2)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("highWaterMark");

        assertThatThrownBy(() -> new EventBuffer(spillStore, "bad", 10, 8, 9)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("lowWaterMark");
    }
}
