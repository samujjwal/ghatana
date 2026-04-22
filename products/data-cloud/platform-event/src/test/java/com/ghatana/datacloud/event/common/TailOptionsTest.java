/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.event.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TailOptions} and its builder.
 */
@DisplayName("TailOptions [GH-90000]")
class TailOptionsTest {

    @Nested
    @DisplayName("StartMode enum [GH-90000]")
    class StartModeEnum {

        @Test
        void allValuesPresent() { // GH-90000
            TailOptions.StartMode[] modes = TailOptions.StartMode.values(); // GH-90000
            assertThat(modes).containsExactlyInAnyOrder( // GH-90000
                    TailOptions.StartMode.FROM_OFFSET,
                    TailOptions.StartMode.FROM_TIME,
                    TailOptions.StartMode.FROM_LATEST,
                    TailOptions.StartMode.FROM_EARLIEST,
                    TailOptions.StartMode.FROM_COMMITTED
            );
        }

        @Test
        void valueOfByName() { // GH-90000
            assertThat(TailOptions.StartMode.valueOf("FROM_OFFSET [GH-90000]"))
                    .isSameAs(TailOptions.StartMode.FROM_OFFSET); // GH-90000
        }
    }

    @Nested
    @DisplayName("factory methods [GH-90000]")
    class FactoryMethods {

        @Test
        void fromLatestFactory() { // GH-90000
            TailOptions opts = TailOptions.fromLatest(); // GH-90000
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_LATEST); // GH-90000
            assertThat(opts.startOffset()).isEmpty(); // GH-90000
            assertThat(opts.startTime()).isEmpty(); // GH-90000
        }

        @Test
        void fromEarliestFactory() { // GH-90000
            TailOptions opts = TailOptions.fromEarliest(); // GH-90000
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_EARLIEST); // GH-90000
        }

        @Test
        void fromOffsetFactory() { // GH-90000
            Offset off = Offset.of(42); // GH-90000
            TailOptions opts = TailOptions.fromOffset(off); // GH-90000
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_OFFSET); // GH-90000
            assertThat(opts.startOffset()).contains(off); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder [GH-90000]")
    class BuilderTest {

        @Test
        void defaults() { // GH-90000
            TailOptions opts = TailOptions.builder().build(); // GH-90000
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_LATEST); // GH-90000
            assertThat(opts.batchSize()).isEqualTo(100); // GH-90000
            assertThat(opts.pollTimeout()).isEqualTo(Duration.ofSeconds(1)); // GH-90000
            assertThat(opts.consumerGroup()).isEmpty(); // GH-90000
            assertThat(opts.autoCommit()).isFalse(); // GH-90000
            assertThat(opts.autoCommitInterval()).isEqualTo(Duration.ofSeconds(5)); // GH-90000
        }

        @Test
        void fromOffsetBuilder() { // GH-90000
            Offset off = Offset.of(7); // GH-90000
            TailOptions opts = TailOptions.builder().fromOffset(off).build(); // GH-90000
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_OFFSET); // GH-90000
            assertThat(opts.startOffset()).contains(off); // GH-90000
        }

        @Test
        void fromOffsetBuilderNullThrows() { // GH-90000
            assertThatThrownBy(() -> TailOptions.builder().fromOffset(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        void fromTimeBuilder() { // GH-90000
            Instant t = Instant.now(); // GH-90000
            TailOptions opts = TailOptions.builder().fromTime(t).build(); // GH-90000
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_TIME); // GH-90000
            assertThat(opts.startTime()).contains(t); // GH-90000
        }

        @Test
        void fromTimeBuilderNullThrows() { // GH-90000
            assertThatThrownBy(() -> TailOptions.builder().fromTime(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        void fromLatestBuilder() { // GH-90000
            TailOptions opts = TailOptions.builder().fromLatest().build(); // GH-90000
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_LATEST); // GH-90000
        }

        @Test
        void fromEarliestBuilder() { // GH-90000
            TailOptions opts = TailOptions.builder().fromEarliest().build(); // GH-90000
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_EARLIEST); // GH-90000
        }

        @Test
        void fromCommittedWithGroup() { // GH-90000
            TailOptions opts = TailOptions.builder() // GH-90000
                    .fromCommitted() // GH-90000
                    .consumerGroup("my-group [GH-90000]")
                    .build(); // GH-90000
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_COMMITTED); // GH-90000
            assertThat(opts.consumerGroup()).contains("my-group [GH-90000]");
        }

        @Test
        void fromCommittedWithoutGroupThrows() { // GH-90000
            assertThatThrownBy(() -> TailOptions.builder().fromCommitted().build()) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("consumerGroup [GH-90000]");
        }

        @Test
        void consumerGroupAutoSwitchesToFromCommitted() { // GH-90000
            // Setting consumerGroup when in FROM_LATEST switches mode to FROM_COMMITTED
            TailOptions opts = TailOptions.builder() // GH-90000
                    .fromLatest() // GH-90000
                    .consumerGroup("grp [GH-90000]")
                    .build(); // GH-90000
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_COMMITTED); // GH-90000
        }

        @Test
        void batchSizeSet() { // GH-90000
            TailOptions opts = TailOptions.builder().batchSize(500).build(); // GH-90000
            assertThat(opts.batchSize()).isEqualTo(500); // GH-90000
        }

        @Test
        void batchSizeZeroThrows() { // GH-90000
            assertThatThrownBy(() -> TailOptions.builder().batchSize(0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        void batchSizeNegativeThrows() { // GH-90000
            assertThatThrownBy(() -> TailOptions.builder().batchSize(-1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        void pollTimeoutSet() { // GH-90000
            Duration d = Duration.ofMillis(500); // GH-90000
            TailOptions opts = TailOptions.builder().pollTimeout(d).build(); // GH-90000
            assertThat(opts.pollTimeout()).isEqualTo(d); // GH-90000
        }

        @Test
        void pollTimeoutNullThrows() { // GH-90000
            assertThatThrownBy(() -> TailOptions.builder().pollTimeout(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        void autoCommitEnabled() { // GH-90000
            TailOptions opts = TailOptions.builder().autoCommit(true).build(); // GH-90000
            assertThat(opts.autoCommit()).isTrue(); // GH-90000
        }

        @Test
        void autoCommitIntervalSet() { // GH-90000
            Duration d = Duration.ofSeconds(10); // GH-90000
            TailOptions opts = TailOptions.builder().autoCommitInterval(d).build(); // GH-90000
            assertThat(opts.autoCommitInterval()).isEqualTo(d); // GH-90000
        }

        @Test
        void autoCommitIntervalNullThrows() { // GH-90000
            assertThatThrownBy(() -> TailOptions.builder().autoCommitInterval(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Test
    @DisplayName("toString() includes key fields [GH-90000]")
    void toStringContainsFields() { // GH-90000
        TailOptions opts = TailOptions.builder().batchSize(50).build(); // GH-90000
        String s = opts.toString(); // GH-90000
        assertThat(s).contains("FROM_LATEST [GH-90000]").contains("50 [GH-90000]");
    }
}
