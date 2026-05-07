/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Unit tests for {@link CachingCheckpointStorage} (AEP-004.3). 
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("CachingCheckpointStorage — AEP-004.3")
class CachingCheckpointStorageTest {

    @Mock
    private CheckpointStorage delegate;

    private MutableClock clock;
    private CachingCheckpointStorage storage;

    private CheckpointId id;
    private CheckpointMetadata metadata;

    @BeforeEach
    void setUp() { 
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        storage = CachingCheckpointStorage.builder(delegate) 
                .ttl(Duration.ofMinutes(5)) 
                .maxEntries(100) 
                .clock(clock) 
                .build(); 

        id = CheckpointId.of("ckpt-001", CheckpointType.CHECKPOINT); 
        metadata = CheckpointMetadata.builder(id) 
                .status(CheckpointStatus.COMPLETED) 
                .startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .build(); 

        lenient().when(delegate.loadCheckpoint(any())).thenReturn(Promise.of(metadata)); 
        lenient().when(delegate.saveCheckpoint(any())).thenReturn(Promise.of(metadata)); 
        lenient().when(delegate.saveSavepoint(any())).thenReturn(Promise.of(metadata)); 
        lenient().when(delegate.deleteCheckpoint(any())).thenReturn(Promise.of(null)); 
    }

    @Test
    @DisplayName("First load is a cache miss and delegates")
    void firstLoadIsCacheMiss() { 
        storage.loadCheckpoint(id).getResult(); 

        verify(delegate).loadCheckpoint(id); 
        assertThat(storage.stats().misses()).isEqualTo(1); 
        assertThat(storage.stats().hits()).isEqualTo(0); 
    }

    @Test
    @DisplayName("Subsequent load within TTL is a cache hit and does not delegate")
    void secondLoadIsHit() { 
        storage.loadCheckpoint(id).getResult(); 
        storage.loadCheckpoint(id).getResult(); 

        verify(delegate, times(1)).loadCheckpoint(id); 
        assertThat(storage.stats().hits()).isEqualTo(1); 
        assertThat(storage.stats().misses()).isEqualTo(1); 
    }

    @Test
    @DisplayName("Load after TTL expires re-delegates")
    void loadAfterTtlExpiry() { 
        storage.loadCheckpoint(id).getResult(); 
        clock.advance(Duration.ofMinutes(6)); 
        storage.loadCheckpoint(id).getResult(); 

        verify(delegate, times(2)).loadCheckpoint(id); 
        assertThat(storage.stats().misses()).isEqualTo(2); 
    }

    @Test
    @DisplayName("saveCheckpoint writes through to delegate and warms cache")
    void saveCheckpointWarmsCacheAndDelegates() { 
        storage.saveCheckpoint(metadata).getResult(); 

        verify(delegate).saveCheckpoint(metadata); 

        // Next load should be a cache hit
        storage.loadCheckpoint(id).getResult(); 
        verify(delegate, never()).loadCheckpoint(any()); 
        assertThat(storage.stats().hits()).isEqualTo(1); 
    }

    @Test
    @DisplayName("deleteCheckpoint invalidates cache and delegates")
    void deleteInvalidatesCache() { 
        storage.saveCheckpoint(metadata).getResult(); 
        storage.deleteCheckpoint(id).getResult(); 

        verify(delegate).deleteCheckpoint(id); 

        // After delete, next load should miss cache
        storage.loadCheckpoint(id).getResult(); 
        verify(delegate).loadCheckpoint(id); 
        assertThat(storage.stats().misses()).isEqualTo(1); 
    }

    @Test
    @DisplayName("clear empties the cache")
    void clearEmptiesCache() { 
        storage.saveCheckpoint(metadata).getResult(); 
        storage.clear(); 

        assertThat(storage.stats().size()).isEqualTo(0); 
    }

    @Test
    @DisplayName("Builder rejects null delegate")
    void builderRejectsNullDelegate() { 
        assertThatThrownBy(() -> CachingCheckpointStorage.builder(null)) 
                .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("Builder rejects non-positive TTL")
    void builderRejectsNonPositiveTtl() { 
        assertThatThrownBy(() -> CachingCheckpointStorage.builder(delegate) 
                .ttl(Duration.ZERO)) 
                .isInstanceOf(IllegalArgumentException.class); 
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) { this.instant = start; } 

        void advance(Duration d) { instant = instant.plus(d); } 

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; } 
        @Override public Clock withZone(java.time.ZoneId zone) { return this; } 
        @Override public Instant instant() { return instant; } 
    }
}
