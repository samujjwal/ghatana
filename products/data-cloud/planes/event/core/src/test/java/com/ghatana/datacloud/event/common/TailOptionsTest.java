/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("TailOptions")
class TailOptionsTest {

    @Nested
    @DisplayName("StartMode enum")
    class StartModeEnum {

        @Test
        void allValuesPresent() { 
            TailOptions.StartMode[] modes = TailOptions.StartMode.values(); 
            assertThat(modes).containsExactlyInAnyOrder( 
                    TailOptions.StartMode.FROM_OFFSET,
                    TailOptions.StartMode.FROM_TIME,
                    TailOptions.StartMode.FROM_LATEST,
                    TailOptions.StartMode.FROM_EARLIEST,
                    TailOptions.StartMode.FROM_COMMITTED
            );
        }

        @Test
        void valueOfByName() { 
            assertThat(TailOptions.StartMode.valueOf("FROM_OFFSET"))
                    .isSameAs(TailOptions.StartMode.FROM_OFFSET); 
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        void fromLatestFactory() { 
            TailOptions opts = TailOptions.fromLatest(); 
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_LATEST); 
            assertThat(opts.startOffset()).isEmpty(); 
            assertThat(opts.startTime()).isEmpty(); 
        }

        @Test
        void fromEarliestFactory() { 
            TailOptions opts = TailOptions.fromEarliest(); 
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_EARLIEST); 
        }

        @Test
        void fromOffsetFactory() { 
            Offset off = Offset.of(42); 
            TailOptions opts = TailOptions.fromOffset(off); 
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_OFFSET); 
            assertThat(opts.startOffset()).contains(off); 
        }
    }

    @Nested
    @DisplayName("builder")
    class BuilderTest {

        @Test
        void defaults() { 
            TailOptions opts = TailOptions.builder().build(); 
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_LATEST); 
            assertThat(opts.batchSize()).isEqualTo(100); 
            assertThat(opts.pollTimeout()).isEqualTo(Duration.ofSeconds(1)); 
            assertThat(opts.consumerGroup()).isEmpty(); 
            assertThat(opts.autoCommit()).isFalse(); 
            assertThat(opts.autoCommitInterval()).isEqualTo(Duration.ofSeconds(5)); 
        }

        @Test
        void fromOffsetBuilder() { 
            Offset off = Offset.of(7); 
            TailOptions opts = TailOptions.builder().fromOffset(off).build(); 
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_OFFSET); 
            assertThat(opts.startOffset()).contains(off); 
        }

        @Test
        void fromOffsetBuilderNullThrows() { 
            assertThatThrownBy(() -> TailOptions.builder().fromOffset(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        void fromTimeBuilder() { 
            Instant t = Instant.now(); 
            TailOptions opts = TailOptions.builder().fromTime(t).build(); 
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_TIME); 
            assertThat(opts.startTime()).contains(t); 
        }

        @Test
        void fromTimeBuilderNullThrows() { 
            assertThatThrownBy(() -> TailOptions.builder().fromTime(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        void fromLatestBuilder() { 
            TailOptions opts = TailOptions.builder().fromLatest().build(); 
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_LATEST); 
        }

        @Test
        void fromEarliestBuilder() { 
            TailOptions opts = TailOptions.builder().fromEarliest().build(); 
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_EARLIEST); 
        }

        @Test
        void fromCommittedWithGroup() { 
            TailOptions opts = TailOptions.builder() 
                    .fromCommitted() 
                    .consumerGroup("my-group")
                    .build(); 
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_COMMITTED); 
            assertThat(opts.consumerGroup()).contains("my-group");
        }

        @Test
        void fromCommittedWithoutGroupThrows() { 
            assertThatThrownBy(() -> TailOptions.builder().fromCommitted().build()) 
                    .isInstanceOf(IllegalStateException.class) 
                    .hasMessageContaining("consumerGroup");
        }

        @Test
        void consumerGroupAutoSwitchesToFromCommitted() { 
            // Setting consumerGroup when in FROM_LATEST switches mode to FROM_COMMITTED
            TailOptions opts = TailOptions.builder() 
                    .fromLatest() 
                    .consumerGroup("grp")
                    .build(); 
            assertThat(opts.startMode()).isEqualTo(TailOptions.StartMode.FROM_COMMITTED); 
        }

        @Test
        void batchSizeSet() { 
            TailOptions opts = TailOptions.builder().batchSize(500).build(); 
            assertThat(opts.batchSize()).isEqualTo(500); 
        }

        @Test
        void batchSizeZeroThrows() { 
            assertThatThrownBy(() -> TailOptions.builder().batchSize(0)) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        void batchSizeNegativeThrows() { 
            assertThatThrownBy(() -> TailOptions.builder().batchSize(-1)) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        void pollTimeoutSet() { 
            Duration d = Duration.ofMillis(500); 
            TailOptions opts = TailOptions.builder().pollTimeout(d).build(); 
            assertThat(opts.pollTimeout()).isEqualTo(d); 
        }

        @Test
        void pollTimeoutNullThrows() { 
            assertThatThrownBy(() -> TailOptions.builder().pollTimeout(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        void autoCommitEnabled() { 
            TailOptions opts = TailOptions.builder().autoCommit(true).build(); 
            assertThat(opts.autoCommit()).isTrue(); 
        }

        @Test
        void autoCommitIntervalSet() { 
            Duration d = Duration.ofSeconds(10); 
            TailOptions opts = TailOptions.builder().autoCommitInterval(d).build(); 
            assertThat(opts.autoCommitInterval()).isEqualTo(d); 
        }

        @Test
        void autoCommitIntervalNullThrows() { 
            assertThatThrownBy(() -> TailOptions.builder().autoCommitInterval(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }

    @Test
    @DisplayName("toString() includes key fields")
    void toStringContainsFields() { 
        TailOptions opts = TailOptions.builder().batchSize(50).build(); 
        String s = opts.toString(); 
        assertThat(s).contains("FROM_LATEST").contains("50");
    }
}
