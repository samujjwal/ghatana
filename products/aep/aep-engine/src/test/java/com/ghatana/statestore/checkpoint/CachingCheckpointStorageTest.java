/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.statestore.checkpoint;

import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CachingCheckpointStorage} (AEP-004.3). // GH-90000
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("CachingCheckpointStorage — AEP-004.3")
class CachingCheckpointStorageTest {

    @Mock
    private CheckpointStorage delegate;

    private MutableClock clock;
    private CachingCheckpointStorage storage;

    private CheckpointId id;
    private CheckpointMetadata metadata;

    @BeforeEach
    void setUp() { // GH-90000
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        storage = CachingCheckpointStorage.builder(delegate) // GH-90000
                .ttl(Duration.ofMinutes(5)) // GH-90000
                .maxEntries(100) // GH-90000
                .clock(clock) // GH-90000
                .build(); // GH-90000

        id = CheckpointId.of("ckpt-001", CheckpointType.CHECKPOINT); // GH-90000
        metadata = CheckpointMetadata.builder(id) // GH-90000
                .status(CheckpointStatus.COMPLETED) // GH-90000
                .startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .build(); // GH-90000

        lenient().when(delegate.loadCheckpoint(any())).thenReturn(Promise.of(metadata)); // GH-90000
        lenient().when(delegate.saveCheckpoint(any())).thenReturn(Promise.of(metadata)); // GH-90000
        lenient().when(delegate.saveSavepoint(any())).thenReturn(Promise.of(metadata)); // GH-90000
        lenient().when(delegate.deleteCheckpoint(any())).thenReturn(Promise.of(null)); // GH-90000
    }

    @Test
    @DisplayName("First load is a cache miss and delegates")
    void firstLoadIsCacheMiss() { // GH-90000
        storage.loadCheckpoint(id).getResult(); // GH-90000

        verify(delegate).loadCheckpoint(id); // GH-90000
        assertThat(storage.stats().misses()).isEqualTo(1); // GH-90000
        assertThat(storage.stats().hits()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Subsequent load within TTL is a cache hit and does not delegate")
    void secondLoadIsHit() { // GH-90000
        storage.loadCheckpoint(id).getResult(); // GH-90000
        storage.loadCheckpoint(id).getResult(); // GH-90000

        verify(delegate, times(1)).loadCheckpoint(id); // GH-90000
        assertThat(storage.stats().hits()).isEqualTo(1); // GH-90000
        assertThat(storage.stats().misses()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Load after TTL expires re-delegates")
    void loadAfterTtlExpiry() { // GH-90000
        storage.loadCheckpoint(id).getResult(); // GH-90000
        clock.advance(Duration.ofMinutes(6)); // GH-90000
        storage.loadCheckpoint(id).getResult(); // GH-90000

        verify(delegate, times(2)).loadCheckpoint(id); // GH-90000
        assertThat(storage.stats().misses()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("saveCheckpoint writes through to delegate and warms cache")
    void saveCheckpointWarmsCacheAndDelegates() { // GH-90000
        storage.saveCheckpoint(metadata).getResult(); // GH-90000

        verify(delegate).saveCheckpoint(metadata); // GH-90000

        // Next load should be a cache hit
        storage.loadCheckpoint(id).getResult(); // GH-90000
        verify(delegate, never()).loadCheckpoint(any()); // GH-90000
        assertThat(storage.stats().hits()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("deleteCheckpoint invalidates cache and delegates")
    void deleteInvalidatesCache() { // GH-90000
        storage.saveCheckpoint(metadata).getResult(); // GH-90000
        storage.deleteCheckpoint(id).getResult(); // GH-90000

        verify(delegate).deleteCheckpoint(id); // GH-90000

        // After delete, next load should miss cache
        storage.loadCheckpoint(id).getResult(); // GH-90000
        verify(delegate).loadCheckpoint(id); // GH-90000
        assertThat(storage.stats().misses()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("clear empties the cache")
    void clearEmptiesCache() { // GH-90000
        storage.saveCheckpoint(metadata).getResult(); // GH-90000
        storage.clear(); // GH-90000

        assertThat(storage.stats().size()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects null delegate")
    void builderRejectsNullDelegate() { // GH-90000
        assertThatThrownBy(() -> CachingCheckpointStorage.builder(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects non-positive TTL")
    void builderRejectsNonPositiveTtl() { // GH-90000
        assertThatThrownBy(() -> CachingCheckpointStorage.builder(delegate) // GH-90000
                .ttl(Duration.ZERO)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) { this.instant = start; } // GH-90000

        void advance(Duration d) { instant = instant.plus(d); } // GH-90000

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; } // GH-90000
        @Override public Clock withZone(java.time.ZoneId zone) { return this; } // GH-90000
        @Override public Instant instant() { return instant; } // GH-90000
    }
}
