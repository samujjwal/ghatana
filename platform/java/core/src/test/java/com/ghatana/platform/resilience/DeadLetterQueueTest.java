/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
@DisplayName("DeadLetterQueue – Failed Event Storage & Retrieval")
class DeadLetterQueueTest {

    @Test
    @Order(1)
    @DisplayName("1. Store and retrieve a failed event")
    void storeAndRetrieve() {
        DeadLetterQueue dlq = DeadLetterQueue.builder().build();

        String id = dlq.store("event-payload", new RuntimeException("boom"), "processing-error");

        assertThat(id).isNotBlank();
        assertThat(dlq.size()).isEqualTo(1);
        assertThat(dlq.get(id)).isPresent();

        DeadLetterQueue.FailedEvent fe = dlq.get(id).get();
        assertThat(fe.getOriginalEvent()).isEqualTo("event-payload");
        assertThat(fe.getErrorMessage()).isEqualTo("boom");
        assertThat(fe.getErrorType()).isEqualTo("RuntimeException");
        assertThat(fe.getReason()).isEqualTo("processing-error");
        assertThat(fe.getFailedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(fe.getStackTrace()).contains("RuntimeException");
    }

    @Test
    @Order(2)
    @DisplayName("2. Enforces max size by evicting oldest")
    void enforcesMaxSize() {
        DeadLetterQueue dlq = DeadLetterQueue.builder()
                .maxSize(3)
                .build();

        String id1 = dlq.store("e1", new Exception("err1"), "r1");
        String id2 = dlq.store("e2", new Exception("err2"), "r2");
        String id3 = dlq.store("e3", new Exception("err3"), "r3");
        String id4 = dlq.store("e4", new Exception("err4"), "r4");

        assertThat(dlq.size()).isEqualTo(3);
        assertThat(dlq.get(id1)).isEmpty(); // evicted
        assertThat(dlq.get(id4)).isPresent();
        assertThat(dlq.getTotalEvicted()).isEqualTo(1);
    }

    @Test
    @Order(3)
    @DisplayName("3. getAll returns snapshot of all events")
    void getAllSnapshot() {
        DeadLetterQueue dlq = DeadLetterQueue.builder().build();
        dlq.store("e1", new Exception("err1"), "r1");
        dlq.store("e2", new Exception("err2"), "r2");

        List<DeadLetterQueue.FailedEvent> all = dlq.getAll();
        assertThat(all).hasSize(2);
    }

    @Test
    @Order(4)
    @DisplayName("4. Filter by error type")
    void filterByErrorType() {
        DeadLetterQueue dlq = DeadLetterQueue.builder().build();
        dlq.store("e1", new IllegalArgumentException("bad arg"), "r1");
        dlq.store("e2", new NullPointerException("null"), "r2");
        dlq.store("e3", new IllegalArgumentException("another bad"), "r3");

        List<DeadLetterQueue.FailedEvent> filtered = dlq.getByErrorType("IllegalArgumentException");
        assertThat(filtered).hasSize(2);
    }

    @Test
    @Order(5)
    @DisplayName("5. Filter by time range")
    void filterByTimeRange() {
        DeadLetterQueue dlq = DeadLetterQueue.builder().build();
        Instant before = Instant.now().minusSeconds(1);
        dlq.store("e1", new Exception("err"), "r1");
        Instant after = Instant.now().plusSeconds(1);

        List<DeadLetterQueue.FailedEvent> inRange = dlq.getByTimeRange(before, after);
        assertThat(inRange).hasSize(1);

        List<DeadLetterQueue.FailedEvent> outOfRange = dlq.getByTimeRange(
                after, after.plusSeconds(10));
        assertThat(outOfRange).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("6. Remove individual event")
    void removeEvent() {
        DeadLetterQueue dlq = DeadLetterQueue.builder().build();
        String id = dlq.store("e1", new Exception("err"), "r1");

        assertThat(dlq.remove(id)).isTrue();
        assertThat(dlq.size()).isZero();
        assertThat(dlq.remove(id)).isFalse(); // already removed
    }

    @Test
    @Order(7)
    @DisplayName("7. Clear removes all events")
    void clearAll() {
        DeadLetterQueue dlq = DeadLetterQueue.builder().build();
        dlq.store("e1", new Exception("e"), "r");
        dlq.store("e2", new Exception("e"), "r");

        dlq.clear();
        assertThat(dlq.size()).isZero();
        assertThat(dlq.getAll()).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("8. Builder defaults are sensible")
    void builderDefaults() {
        DeadLetterQueue dlq = DeadLetterQueue.builder().build();
        assertThat(dlq.size()).isZero();
        assertThat(dlq.isReplayEnabled()).isTrue();
        assertThat(dlq.getTotalStored()).isZero();
        assertThat(dlq.getTotalEvicted()).isZero();
    }

    @Test
    @Order(9)
    @DisplayName("9. Handles null error gracefully")
    void handlesNullError() {
        DeadLetterQueue dlq = DeadLetterQueue.builder().build();
        String id = dlq.store("event", null, "unknown-failure");

        var fe = dlq.get(id).orElseThrow();
        assertThat(fe.getErrorMessage()).isEqualTo("unknown");
        assertThat(fe.getErrorType()).isEqualTo("Unknown");
        assertThat(fe.getStackTrace()).isEmpty();
    }

    @Test
    @Order(10)
    @DisplayName("10. FailedEvent equality based on ID")
    void failedEventEquality() {
        DeadLetterQueue dlq = DeadLetterQueue.builder().build();
        String id1 = dlq.store("e1", new Exception("err"), "r");
        String id2 = dlq.store("e2", new Exception("err"), "r");

        var fe1 = dlq.get(id1).orElseThrow();
        var fe2 = dlq.get(id2).orElseThrow();
        assertThat(fe1).isNotEqualTo(fe2);
        assertThat(fe1).isEqualTo(fe1); // self-equality
        assertThat(fe1.hashCode()).isEqualTo(fe1.hashCode());
    }

    @Test
    @Order(11)
    @DisplayName("11. Metrics track stored and evicted counts")
    void metricsTracking() {
        DeadLetterQueue dlq = DeadLetterQueue.builder().maxSize(2).build();

        dlq.store("e1", new Exception("e"), "r");
        dlq.store("e2", new Exception("e"), "r");
        dlq.store("e3", new Exception("e"), "r"); // evicts e1

        assertThat(dlq.getTotalStored()).isEqualTo(3);
        assertThat(dlq.getTotalEvicted()).isEqualTo(1);
        assertThat(dlq.size()).isEqualTo(2);
    }
}
