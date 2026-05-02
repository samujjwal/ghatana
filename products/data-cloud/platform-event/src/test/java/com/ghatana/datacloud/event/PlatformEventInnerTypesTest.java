/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.event;

import com.ghatana.datacloud.event.model.ConsumerGroup;
import com.ghatana.datacloud.event.model.EventType;
import com.ghatana.datacloud.event.spi.StoragePlugin;
import com.ghatana.datacloud.event.spi.secrets.FileSecretProvider;
import com.ghatana.datacloud.event.spi.secrets.SecretResolutionException;
import com.ghatana.datacloud.event.spi.secrets.SecretValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for inner enums, exceptions, and small value types across the
 * platform-event module.
 */
@DisplayName("Platform-event inner types")
class PlatformEventInnerTypesTest {

    // ─── ConsumerGroup.GroupState ─────────────────────────────────────────────

    @Nested
    @DisplayName("ConsumerGroup.GroupState")
    class GroupState {

        @Test
        void allValuesPresent() { 
            assertThat(ConsumerGroup.GroupState.values()).containsExactlyInAnyOrder( 
                    ConsumerGroup.GroupState.EMPTY,
                    ConsumerGroup.GroupState.PREPARING_REBALANCE,
                    ConsumerGroup.GroupState.COMPLETING_REBALANCE,
                    ConsumerGroup.GroupState.STABLE,
                    ConsumerGroup.GroupState.DEAD
            );
        }

        @Test
        void valueOfByName() { 
            assertThat(ConsumerGroup.GroupState.valueOf("STABLE"))
                    .isSameAs(ConsumerGroup.GroupState.STABLE); 
        }
    }

    // ─── EventType inner enums ────────────────────────────────────────────────

    @Nested
    @DisplayName("EventType.LifecycleStatus")
    class LifecycleStatus {

        @Test
        void allValuesPresent() { 
            assertThat(EventType.LifecycleStatus.values()).containsExactlyInAnyOrder( 
                    EventType.LifecycleStatus.DRAFT,
                    EventType.LifecycleStatus.ACTIVE,
                    EventType.LifecycleStatus.DEPRECATED,
                    EventType.LifecycleStatus.RETIRED
            );
        }

        @Test
        void valueOfByName() { 
            assertThat(EventType.LifecycleStatus.valueOf("ACTIVE"))
                    .isSameAs(EventType.LifecycleStatus.ACTIVE); 
        }
    }

    @Nested
    @DisplayName("EventType.CompatibilityPolicy")
    class CompatibilityPolicy {

        @Test
        void allValuesPresent() { 
            assertThat(EventType.CompatibilityPolicy.values()).containsExactlyInAnyOrder( 
                    EventType.CompatibilityPolicy.NONE,
                    EventType.CompatibilityPolicy.BACKWARD,
                    EventType.CompatibilityPolicy.FORWARD,
                    EventType.CompatibilityPolicy.FULL
            );
        }

        @Test
        void valueOfByName() { 
            assertThat(EventType.CompatibilityPolicy.valueOf("BACKWARD"))
                    .isSameAs(EventType.CompatibilityPolicy.BACKWARD); 
        }
    }

    // ─── StoragePlugin.DuplicateEventException ────────────────────────────────

    @Nested
    @DisplayName("StoragePlugin.DuplicateEventException")
    class DuplicateEventException {

        @Test
        void constructorSetsFieldsAndMessage() { 
            StoragePlugin.DuplicateEventException ex =
                    new StoragePlugin.DuplicateEventException("idem-key-1", "event-id-99"); 
            assertThat(ex.getIdempotencyKey()).isEqualTo("idem-key-1");
            assertThat(ex.getExistingEventId()).isEqualTo("event-id-99");
            assertThat(ex.getMessage()).contains("idem-key-1");
        }
    }

    // ─── EventDurabilityService inner records ─────────────────────────────────

    @Nested
    @DisplayName("EventDurabilityService.DurabilityResult")
    class DurabilityResultTest {

        @Test
        void isSuccessful() { 
            EventDurabilityService.DurabilityResult r = new EventDurabilityService.DurabilityResult( 
                    "evt-1", 3L, EventDurabilityService.DurabilityLevel.MAJORITY_ACK, 5L, 10L, true);
            assertThat(r.isSuccessful()).isTrue(); 
        }

        @Test
        void meetsLevelReturnsTrueWhenAchievedHigherOrEqual() { 
            EventDurabilityService.DurabilityResult r = new EventDurabilityService.DurabilityResult( 
                    "evt-2", 1L, EventDurabilityService.DurabilityLevel.ALL_ACK, 0L, 0L, true);
            assertThat(r.meetsLevel(EventDurabilityService.DurabilityLevel.MAJORITY_ACK)).isTrue(); 
            assertThat(r.meetsLevel(EventDurabilityService.DurabilityLevel.ALL_ACK)).isTrue(); 
        }

        @Test
        void meetsLevelReturnsFalseWhenAchievedLower() { 
            EventDurabilityService.DurabilityResult r = new EventDurabilityService.DurabilityResult( 
                    "evt-3", 1L, EventDurabilityService.DurabilityLevel.LEADER_ACK, 0L, 0L, false);
            assertThat(r.meetsLevel(EventDurabilityService.DurabilityLevel.MAJORITY_ACK)).isFalse(); 
        }
    }

    @Nested
    @DisplayName("EventDurabilityService.DurabilityStatus")
    class DurabilityStatusTest {

        @Test
        void isFullyDurableWhenFsyncedAndReplicaCountMet() { 
            EventDurabilityService.DurabilityStatus s = new EventDurabilityService.DurabilityStatus( 
                    "evt-1", EventDurabilityService.DurabilityLevel.MAJORITY_ACK, 3, 2, true, List.of()); 
            assertThat(s.isFullyDurable()).isTrue(); 
        }

        @Test
        void notFullyDurableWhenReplicaCountNotMet() { 
            EventDurabilityService.DurabilityStatus s = new EventDurabilityService.DurabilityStatus( 
                    "evt-2", EventDurabilityService.DurabilityLevel.MAJORITY_ACK, 1, 3, true, List.of()); 
            assertThat(s.isFullyDurable()).isFalse(); 
        }
    }

    // ─── EventReplayService inner records ─────────────────────────────────────

    @Nested
    @DisplayName("EventReplayService.ReplayedEvent")
    class ReplayedEventTest {

        @Test
        void isFirstReplay() { 
            EventReplayService.ReplayedEvent ev = new EventReplayService.ReplayedEvent( 
                    "id", "type", "tenant", 1L, 0L, new byte[0], 1);
            assertThat(ev.isFirstReplay()).isTrue(); 
        }

        @Test
        void notFirstReplay() { 
            EventReplayService.ReplayedEvent ev = new EventReplayService.ReplayedEvent( 
                    "id", "type", "tenant", 1L, 0L, new byte[0], 2);
            assertThat(ev.isFirstReplay()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("EventReplayService.ReplayResult")
    class ReplayResultTest {

        @Test
        void isSuccessful() { 
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( 
                    "c1", 0L, 10L, 10L, 10L, 0L, Duration.ofSeconds(1), true, List.of()); 
            assertThat(result.isSuccessful()).isTrue(); 
        }

        @Test
        void notSuccessfulWhenEventsFailed() { 
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( 
                    "c2", 0L, 10L, 10L, 9L, 1L, Duration.ofSeconds(2), true, List.of()); 
            assertThat(result.isSuccessful()).isFalse(); 
        }

        @Test
        void successRateIsOneWhenNoEvents() { 
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( 
                    "c3", 0L, 0L, 0L, 0L, 0L, Duration.ZERO, true, List.of()); 
            assertThat(result.successRate()).isEqualTo(1.0); 
        }

        @Test
        void successRateCalculated() { 
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( 
                    "c4", 0L, 10L, 10L, 8L, 0L, Duration.ofSeconds(1), true, List.of()); 
            assertThat(result.successRate()).isEqualTo(0.8); 
        }
    }

    @Nested
    @DisplayName("EventReplayService.ReplayError")
    class ReplayErrorTest {

        @Test
        void recordConstruction() { 
            EventReplayService.ReplayError err = new EventReplayService.ReplayError( 
                    5L, "event-id", "NullPointerException", "NPE", true);
            assertThat(err.offset()).isEqualTo(5L); 
            assertThat(err.retryable()).isTrue(); 
        }
    }

    @Nested
    @DisplayName("EventReplayService.ReplayStatus")
    class ReplayStatusTest {

        @Test
        void calculateProgressReturnZeroForZeroTarget() { 
            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus( 
                    "c1", EventReplayService.ReplayState.IDLE, 0L, 0L, 0L, 0.0, Duration.ZERO, Duration.ZERO);
            assertThat(status.calculateProgress()).isEqualTo(0.0); 
        }

        @Test
        void calculateProgressReturnsPercentage() { 
            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus( 
                    "c2", EventReplayService.ReplayState.RUNNING, 50L, 100L, 50L, 50.0, Duration.ofSeconds(5), Duration.ofSeconds(5)); 
            assertThat(status.calculateProgress()).isEqualTo(50.0); 
        }
    }

    @Nested
    @DisplayName("EventReplayService.ReplayState")
    class ReplayStateTest {

        @Test
        void allValuesPresent() { 
            assertThat(EventReplayService.ReplayState.values()).containsExactlyInAnyOrder( 
                    EventReplayService.ReplayState.IDLE,
                    EventReplayService.ReplayState.RUNNING,
                    EventReplayService.ReplayState.PAUSED,
                    EventReplayService.ReplayState.COMPLETED,
                    EventReplayService.ReplayState.FAILED,
                    EventReplayService.ReplayState.CANCELLED
            );
        }
    }

    // ─── FileSecretProvider ───────────────────────────────────────────────────

    @Nested
    @DisplayName("FileSecretProvider")
    class FileSecretProviderTest {

        private final FileSecretProvider provider = new FileSecretProvider(); 

        @Test
        void nameIsFile() { 
            assertThat(provider.name()).isEqualTo("file");
        }

        @Test
        void blankLocatorThrows() { 
            assertThatThrownBy(() -> provider.resolve("  "))
                    .isInstanceOf(SecretResolutionException.class) 
                    .hasMessageContaining("blank");
        }

        @Test
        void nullLocatorThrows() { 
            assertThatThrownBy(() -> provider.resolve(null)) 
                    .isInstanceOf(SecretResolutionException.class); 
        }

        @Test
        void missingFileThrows() { 
            assertThatThrownBy(() -> provider.resolve("/tmp/does-not-exist-secret-xyzabcdef.txt"))
                    .isInstanceOf(SecretResolutionException.class) 
                    .hasMessageContaining("does not exist");
        }

        @Test
        void readsSecretFromFile(@TempDir Path tmpDir) throws IOException { 
            Path secretFile = tmpDir.resolve("secret.txt");
            Files.writeString(secretFile, "my-secret-value\n"); 
            SecretValue val = provider.resolve(secretFile.toString()); 
            assertThat(new String(val.asCharArrayCopy())).isEqualTo("my-secret-value");
        }

        @Test
        void blankFileContentThrows(@TempDir Path tmpDir) throws IOException { 
            Path secretFile = tmpDir.resolve("blank.txt");
            Files.writeString(secretFile, "   \n"); 
            assertThatThrownBy(() -> provider.resolve(secretFile.toString())) 
                    .isInstanceOf(SecretResolutionException.class) 
                    .hasMessageContaining("blank");
        }

        @Test
        void directoryInsteadOfFileThrows(@TempDir Path tmpDir) { 
            assertThatThrownBy(() -> provider.resolve(tmpDir.toString())) 
                    .isInstanceOf(SecretResolutionException.class) 
                    .hasMessageContaining("not a regular file");
        }
    }
}
