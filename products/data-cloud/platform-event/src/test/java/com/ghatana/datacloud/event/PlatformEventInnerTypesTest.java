/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Platform-event inner types [GH-90000]")
class PlatformEventInnerTypesTest {

    // ─── ConsumerGroup.GroupState ─────────────────────────────────────────────

    @Nested
    @DisplayName("ConsumerGroup.GroupState [GH-90000]")
    class GroupState {

        @Test
        void allValuesPresent() { // GH-90000
            assertThat(ConsumerGroup.GroupState.values()).containsExactlyInAnyOrder( // GH-90000
                    ConsumerGroup.GroupState.EMPTY,
                    ConsumerGroup.GroupState.PREPARING_REBALANCE,
                    ConsumerGroup.GroupState.COMPLETING_REBALANCE,
                    ConsumerGroup.GroupState.STABLE,
                    ConsumerGroup.GroupState.DEAD
            );
        }

        @Test
        void valueOfByName() { // GH-90000
            assertThat(ConsumerGroup.GroupState.valueOf("STABLE [GH-90000]"))
                    .isSameAs(ConsumerGroup.GroupState.STABLE); // GH-90000
        }
    }

    // ─── EventType inner enums ────────────────────────────────────────────────

    @Nested
    @DisplayName("EventType.LifecycleStatus [GH-90000]")
    class LifecycleStatus {

        @Test
        void allValuesPresent() { // GH-90000
            assertThat(EventType.LifecycleStatus.values()).containsExactlyInAnyOrder( // GH-90000
                    EventType.LifecycleStatus.DRAFT,
                    EventType.LifecycleStatus.ACTIVE,
                    EventType.LifecycleStatus.DEPRECATED,
                    EventType.LifecycleStatus.RETIRED
            );
        }

        @Test
        void valueOfByName() { // GH-90000
            assertThat(EventType.LifecycleStatus.valueOf("ACTIVE [GH-90000]"))
                    .isSameAs(EventType.LifecycleStatus.ACTIVE); // GH-90000
        }
    }

    @Nested
    @DisplayName("EventType.CompatibilityPolicy [GH-90000]")
    class CompatibilityPolicy {

        @Test
        void allValuesPresent() { // GH-90000
            assertThat(EventType.CompatibilityPolicy.values()).containsExactlyInAnyOrder( // GH-90000
                    EventType.CompatibilityPolicy.NONE,
                    EventType.CompatibilityPolicy.BACKWARD,
                    EventType.CompatibilityPolicy.FORWARD,
                    EventType.CompatibilityPolicy.FULL
            );
        }

        @Test
        void valueOfByName() { // GH-90000
            assertThat(EventType.CompatibilityPolicy.valueOf("BACKWARD [GH-90000]"))
                    .isSameAs(EventType.CompatibilityPolicy.BACKWARD); // GH-90000
        }
    }

    // ─── StoragePlugin.DuplicateEventException ────────────────────────────────

    @Nested
    @DisplayName("StoragePlugin.DuplicateEventException [GH-90000]")
    class DuplicateEventException {

        @Test
        void constructorSetsFieldsAndMessage() { // GH-90000
            StoragePlugin.DuplicateEventException ex =
                    new StoragePlugin.DuplicateEventException("idem-key-1", "event-id-99"); // GH-90000
            assertThat(ex.getIdempotencyKey()).isEqualTo("idem-key-1 [GH-90000]");
            assertThat(ex.getExistingEventId()).isEqualTo("event-id-99 [GH-90000]");
            assertThat(ex.getMessage()).contains("idem-key-1 [GH-90000]");
        }
    }

    // ─── EventDurabilityService inner records ─────────────────────────────────

    @Nested
    @DisplayName("EventDurabilityService.DurabilityResult [GH-90000]")
    class DurabilityResultTest {

        @Test
        void isSuccessful() { // GH-90000
            EventDurabilityService.DurabilityResult r = new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-1", 3L, EventDurabilityService.DurabilityLevel.MAJORITY_ACK, 5L, 10L, true);
            assertThat(r.isSuccessful()).isTrue(); // GH-90000
        }

        @Test
        void meetsLevelReturnsTrueWhenAchievedHigherOrEqual() { // GH-90000
            EventDurabilityService.DurabilityResult r = new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-2", 1L, EventDurabilityService.DurabilityLevel.ALL_ACK, 0L, 0L, true);
            assertThat(r.meetsLevel(EventDurabilityService.DurabilityLevel.MAJORITY_ACK)).isTrue(); // GH-90000
            assertThat(r.meetsLevel(EventDurabilityService.DurabilityLevel.ALL_ACK)).isTrue(); // GH-90000
        }

        @Test
        void meetsLevelReturnsFalseWhenAchievedLower() { // GH-90000
            EventDurabilityService.DurabilityResult r = new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-3", 1L, EventDurabilityService.DurabilityLevel.LEADER_ACK, 0L, 0L, false);
            assertThat(r.meetsLevel(EventDurabilityService.DurabilityLevel.MAJORITY_ACK)).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("EventDurabilityService.DurabilityStatus [GH-90000]")
    class DurabilityStatusTest {

        @Test
        void isFullyDurableWhenFsyncedAndReplicaCountMet() { // GH-90000
            EventDurabilityService.DurabilityStatus s = new EventDurabilityService.DurabilityStatus( // GH-90000
                    "evt-1", EventDurabilityService.DurabilityLevel.MAJORITY_ACK, 3, 2, true, List.of()); // GH-90000
            assertThat(s.isFullyDurable()).isTrue(); // GH-90000
        }

        @Test
        void notFullyDurableWhenReplicaCountNotMet() { // GH-90000
            EventDurabilityService.DurabilityStatus s = new EventDurabilityService.DurabilityStatus( // GH-90000
                    "evt-2", EventDurabilityService.DurabilityLevel.MAJORITY_ACK, 1, 3, true, List.of()); // GH-90000
            assertThat(s.isFullyDurable()).isFalse(); // GH-90000
        }
    }

    // ─── EventReplayService inner records ─────────────────────────────────────

    @Nested
    @DisplayName("EventReplayService.ReplayedEvent [GH-90000]")
    class ReplayedEventTest {

        @Test
        void isFirstReplay() { // GH-90000
            EventReplayService.ReplayedEvent ev = new EventReplayService.ReplayedEvent( // GH-90000
                    "id", "type", "tenant", 1L, 0L, new byte[0], 1);
            assertThat(ev.isFirstReplay()).isTrue(); // GH-90000
        }

        @Test
        void notFirstReplay() { // GH-90000
            EventReplayService.ReplayedEvent ev = new EventReplayService.ReplayedEvent( // GH-90000
                    "id", "type", "tenant", 1L, 0L, new byte[0], 2);
            assertThat(ev.isFirstReplay()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("EventReplayService.ReplayResult [GH-90000]")
    class ReplayResultTest {

        @Test
        void isSuccessful() { // GH-90000
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( // GH-90000
                    "c1", 0L, 10L, 10L, 10L, 0L, Duration.ofSeconds(1), true, List.of()); // GH-90000
            assertThat(result.isSuccessful()).isTrue(); // GH-90000
        }

        @Test
        void notSuccessfulWhenEventsFailed() { // GH-90000
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( // GH-90000
                    "c2", 0L, 10L, 10L, 9L, 1L, Duration.ofSeconds(2), true, List.of()); // GH-90000
            assertThat(result.isSuccessful()).isFalse(); // GH-90000
        }

        @Test
        void successRateIsOneWhenNoEvents() { // GH-90000
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( // GH-90000
                    "c3", 0L, 0L, 0L, 0L, 0L, Duration.ZERO, true, List.of()); // GH-90000
            assertThat(result.successRate()).isEqualTo(1.0); // GH-90000
        }

        @Test
        void successRateCalculated() { // GH-90000
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( // GH-90000
                    "c4", 0L, 10L, 10L, 8L, 0L, Duration.ofSeconds(1), true, List.of()); // GH-90000
            assertThat(result.successRate()).isEqualTo(0.8); // GH-90000
        }
    }

    @Nested
    @DisplayName("EventReplayService.ReplayError [GH-90000]")
    class ReplayErrorTest {

        @Test
        void recordConstruction() { // GH-90000
            EventReplayService.ReplayError err = new EventReplayService.ReplayError( // GH-90000
                    5L, "event-id", "NullPointerException", "NPE", true);
            assertThat(err.offset()).isEqualTo(5L); // GH-90000
            assertThat(err.retryable()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("EventReplayService.ReplayStatus [GH-90000]")
    class ReplayStatusTest {

        @Test
        void calculateProgressReturnZeroForZeroTarget() { // GH-90000
            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus( // GH-90000
                    "c1", EventReplayService.ReplayState.IDLE, 0L, 0L, 0L, 0.0, Duration.ZERO, Duration.ZERO);
            assertThat(status.calculateProgress()).isEqualTo(0.0); // GH-90000
        }

        @Test
        void calculateProgressReturnsPercentage() { // GH-90000
            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus( // GH-90000
                    "c2", EventReplayService.ReplayState.RUNNING, 50L, 100L, 50L, 50.0, Duration.ofSeconds(5), Duration.ofSeconds(5)); // GH-90000
            assertThat(status.calculateProgress()).isEqualTo(50.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("EventReplayService.ReplayState [GH-90000]")
    class ReplayStateTest {

        @Test
        void allValuesPresent() { // GH-90000
            assertThat(EventReplayService.ReplayState.values()).containsExactlyInAnyOrder( // GH-90000
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
    @DisplayName("FileSecretProvider [GH-90000]")
    class FileSecretProviderTest {

        private final FileSecretProvider provider = new FileSecretProvider(); // GH-90000

        @Test
        void nameIsFile() { // GH-90000
            assertThat(provider.name()).isEqualTo("file [GH-90000]");
        }

        @Test
        void blankLocatorThrows() { // GH-90000
            assertThatThrownBy(() -> provider.resolve("   [GH-90000]"))
                    .isInstanceOf(SecretResolutionException.class) // GH-90000
                    .hasMessageContaining("blank [GH-90000]");
        }

        @Test
        void nullLocatorThrows() { // GH-90000
            assertThatThrownBy(() -> provider.resolve(null)) // GH-90000
                    .isInstanceOf(SecretResolutionException.class); // GH-90000
        }

        @Test
        void missingFileThrows() { // GH-90000
            assertThatThrownBy(() -> provider.resolve("/tmp/does-not-exist-secret-xyzabcdef.txt [GH-90000]"))
                    .isInstanceOf(SecretResolutionException.class) // GH-90000
                    .hasMessageContaining("does not exist [GH-90000]");
        }

        @Test
        void readsSecretFromFile(@TempDir Path tmpDir) throws IOException { // GH-90000
            Path secretFile = tmpDir.resolve("secret.txt [GH-90000]");
            Files.writeString(secretFile, "my-secret-value\n"); // GH-90000
            SecretValue val = provider.resolve(secretFile.toString()); // GH-90000
            assertThat(new String(val.asCharArrayCopy())).isEqualTo("my-secret-value [GH-90000]");
        }

        @Test
        void blankFileContentThrows(@TempDir Path tmpDir) throws IOException { // GH-90000
            Path secretFile = tmpDir.resolve("blank.txt [GH-90000]");
            Files.writeString(secretFile, "   \n"); // GH-90000
            assertThatThrownBy(() -> provider.resolve(secretFile.toString())) // GH-90000
                    .isInstanceOf(SecretResolutionException.class) // GH-90000
                    .hasMessageContaining("blank [GH-90000]");
        }

        @Test
        void directoryInsteadOfFileThrows(@TempDir Path tmpDir) { // GH-90000
            assertThatThrownBy(() -> provider.resolve(tmpDir.toString())) // GH-90000
                    .isInstanceOf(SecretResolutionException.class) // GH-90000
                    .hasMessageContaining("not a regular file [GH-90000]");
        }
    }
}
