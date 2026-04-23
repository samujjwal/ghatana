/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class) // GH-90000
@DisplayName("DeadLetterQueue – Failed Event Storage & Retrieval")
class DeadLetterQueueTest {

    @Test
    @Order(1) // GH-90000
    @DisplayName("1. Store and retrieve a failed event")
    void storeAndRetrieve() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder().build(); // GH-90000

        String id = dlq.store("event-payload", new RuntimeException("boom"), "processing-error");

        assertThat(id).isNotBlank(); // GH-90000
        assertThat(dlq.size()).isEqualTo(1); // GH-90000
        assertThat(dlq.get(id)).isPresent(); // GH-90000

        DeadLetterQueue.FailedEvent fe = dlq.get(id).get(); // GH-90000
        assertThat(fe.getOriginalEvent()).isEqualTo("event-payload");
        assertThat(fe.getErrorMessage()).isEqualTo("boom");
        assertThat(fe.getErrorType()).isEqualTo("RuntimeException");
        assertThat(fe.getReason()).isEqualTo("processing-error");
        assertThat(fe.getFailedAt()).isBeforeOrEqualTo(Instant.now()); // GH-90000
        assertThat(fe.getStackTrace()).contains("RuntimeException");
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("2. Enforces max size by evicting oldest")
    void enforcesMaxSize() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder() // GH-90000
                .maxSize(3) // GH-90000
                .build(); // GH-90000

        String id1 = dlq.store("e1", new Exception("err1"), "r1");
        String id2 = dlq.store("e2", new Exception("err2"), "r2");
        String id3 = dlq.store("e3", new Exception("err3"), "r3");
        String id4 = dlq.store("e4", new Exception("err4"), "r4");

        assertThat(dlq.size()).isEqualTo(3); // GH-90000
        assertThat(dlq.get(id1)).isEmpty(); // evicted // GH-90000
        assertThat(dlq.get(id4)).isPresent(); // GH-90000
        assertThat(dlq.getTotalEvicted()).isEqualTo(1); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("3. getAll returns snapshot of all events")
    void getAllSnapshot() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder().build(); // GH-90000
        dlq.store("e1", new Exception("err1"), "r1");
        dlq.store("e2", new Exception("err2"), "r2");

        List<DeadLetterQueue.FailedEvent> all = dlq.getAll(); // GH-90000
        assertThat(all).hasSize(2); // GH-90000
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("4. Filter by error type")
    void filterByErrorType() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder().build(); // GH-90000
        dlq.store("e1", new IllegalArgumentException("bad arg"), "r1");
        dlq.store("e2", new NullPointerException("null"), "r2");
        dlq.store("e3", new IllegalArgumentException("another bad"), "r3");

        List<DeadLetterQueue.FailedEvent> filtered = dlq.getByErrorType("IllegalArgumentException");
        assertThat(filtered).hasSize(2); // GH-90000
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("5. Filter by time range")
    void filterByTimeRange() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder().build(); // GH-90000
        Instant before = Instant.now().minusSeconds(1); // GH-90000
        dlq.store("e1", new Exception("err"), "r1");
        Instant after = Instant.now().plusSeconds(1); // GH-90000

        List<DeadLetterQueue.FailedEvent> inRange = dlq.getByTimeRange(before, after); // GH-90000
        assertThat(inRange).hasSize(1); // GH-90000

        List<DeadLetterQueue.FailedEvent> outOfRange = dlq.getByTimeRange( // GH-90000
                after, after.plusSeconds(10)); // GH-90000
        assertThat(outOfRange).isEmpty(); // GH-90000
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("6. Remove individual event")
    void removeEvent() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder().build(); // GH-90000
        String id = dlq.store("e1", new Exception("err"), "r1");

        assertThat(dlq.remove(id)).isTrue(); // GH-90000
        assertThat(dlq.size()).isZero(); // GH-90000
        assertThat(dlq.remove(id)).isFalse(); // already removed // GH-90000
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("7. Clear removes all events")
    void clearAll() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder().build(); // GH-90000
        dlq.store("e1", new Exception("e"), "r");
        dlq.store("e2", new Exception("e"), "r");

        dlq.clear(); // GH-90000
        assertThat(dlq.size()).isZero(); // GH-90000
        assertThat(dlq.getAll()).isEmpty(); // GH-90000
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("8. Builder defaults are sensible")
    void builderDefaults() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder().build(); // GH-90000
        assertThat(dlq.size()).isZero(); // GH-90000
        assertThat(dlq.isReplayEnabled()).isTrue(); // GH-90000
        assertThat(dlq.getTotalStored()).isZero(); // GH-90000
        assertThat(dlq.getTotalEvicted()).isZero(); // GH-90000
    }

    @Test
    @Order(9) // GH-90000
    @DisplayName("9. Handles null error gracefully")
    void handlesNullError() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder().build(); // GH-90000
        String id = dlq.store("event", null, "unknown-failure"); // GH-90000

        var fe = dlq.get(id).orElseThrow(); // GH-90000
        assertThat(fe.getErrorMessage()).isEqualTo("unknown");
        assertThat(fe.getErrorType()).isEqualTo("Unknown");
        assertThat(fe.getStackTrace()).isEmpty(); // GH-90000
    }

    @Test
    @Order(10) // GH-90000
    @DisplayName("10. FailedEvent equality based on ID")
    void failedEventEquality() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder().build(); // GH-90000
        String id1 = dlq.store("e1", new Exception("err"), "r");
        String id2 = dlq.store("e2", new Exception("err"), "r");

        var fe1 = dlq.get(id1).orElseThrow(); // GH-90000
        var fe2 = dlq.get(id2).orElseThrow(); // GH-90000
        assertThat(fe1).isNotEqualTo(fe2); // GH-90000
        assertThat(fe1).isEqualTo(fe1); // self-equality // GH-90000
        assertThat(fe1.hashCode()).isEqualTo(fe1.hashCode()); // GH-90000
    }

    @Test
    @Order(11) // GH-90000
    @DisplayName("11. Metrics track stored and evicted counts")
    void metricsTracking() { // GH-90000
        DeadLetterQueue dlq = DeadLetterQueue.builder().maxSize(2).build(); // GH-90000

        dlq.store("e1", new Exception("e"), "r");
        dlq.store("e2", new Exception("e"), "r");
        dlq.store("e3", new Exception("e"), "r"); // evicts e1

        assertThat(dlq.getTotalStored()).isEqualTo(3); // GH-90000
        assertThat(dlq.getTotalEvicted()).isEqualTo(1); // GH-90000
        assertThat(dlq.size()).isEqualTo(2); // GH-90000
    }
}
