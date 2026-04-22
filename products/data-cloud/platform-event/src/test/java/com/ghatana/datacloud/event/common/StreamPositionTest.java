/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.event.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link StreamPosition}.
 */
@DisplayName("StreamPosition [GH-90000]")
class StreamPositionTest {

    @Nested
    @DisplayName("factory methods [GH-90000]")
    class FactoryMethods {

        @Test
        void ofPartitionAndOffset() { // GH-90000
            StreamPosition pos = StreamPosition.of(PartitionId.of(2), Offset.of(10)); // GH-90000
            assertThat(pos.partitionId()).isEqualTo(PartitionId.of(2)); // GH-90000
            assertThat(pos.offset()).isEqualTo(Offset.of(10)); // GH-90000
        }

        @Test
        void ofRawInts() { // GH-90000
            StreamPosition pos = StreamPosition.of(3, 42L); // GH-90000
            assertThat(pos.partitionId().value()).isEqualTo(3); // GH-90000
            assertThat(pos.offset().value()).isEqualTo(42L); // GH-90000
        }

        @Test
        void startOf() { // GH-90000
            StreamPosition start = StreamPosition.startOf(PartitionId.of(1)); // GH-90000
            assertThat(start.partitionId()).isEqualTo(PartitionId.of(1)); // GH-90000
            assertThat(start.offset()).isSameAs(Offset.FIRST); // GH-90000
        }

        @Test
        void latestOf() { // GH-90000
            StreamPosition latest = StreamPosition.latestOf(PartitionId.of(0)); // GH-90000
            assertThat(latest.partitionId()).isEqualTo(PartitionId.FIRST); // GH-90000
            assertThat(latest.offset()).isSameAs(Offset.LATEST); // GH-90000
        }

        @Test
        void constructorRejectsNullPartition() { // GH-90000
            assertThatThrownBy(() -> StreamPosition.of(null, Offset.FIRST)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        void constructorRejectsNullOffset() { // GH-90000
            assertThatThrownBy(() -> StreamPosition.of(PartitionId.FIRST, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("nextOffset() [GH-90000]")
    class NextOffset {

        @Test
        void incrementsOffsetInSamePartition() { // GH-90000
            StreamPosition pos = StreamPosition.of(2, 5L); // GH-90000
            StreamPosition next = pos.nextOffset(); // GH-90000
            assertThat(next.partitionId()).isEqualTo(PartitionId.of(2)); // GH-90000
            assertThat(next.offset().value()).isEqualTo(6L); // GH-90000
        }
    }

    @Nested
    @DisplayName("isBefore() / isAfter() [GH-90000]")
    class InPartitionComparison {

        @Test
        void isBeforeInSamePartition() { // GH-90000
            StreamPosition a = StreamPosition.of(0, 3L); // GH-90000
            StreamPosition b = StreamPosition.of(0, 7L); // GH-90000
            assertThat(a.isBefore(b)).isTrue(); // GH-90000
            assertThat(b.isBefore(a)).isFalse(); // GH-90000
        }

        @Test
        void isAfterInSamePartition() { // GH-90000
            StreamPosition a = StreamPosition.of(0, 7L); // GH-90000
            StreamPosition b = StreamPosition.of(0, 3L); // GH-90000
            assertThat(a.isAfter(b)).isTrue(); // GH-90000
            assertThat(b.isAfter(a)).isFalse(); // GH-90000
        }

        @Test
        void isBeforeThrowsOnDifferentPartitions() { // GH-90000
            StreamPosition a = StreamPosition.of(0, 1L); // GH-90000
            StreamPosition b = StreamPosition.of(1, 1L); // GH-90000
            assertThatThrownBy(() -> a.isBefore(b)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("different partitions [GH-90000]");
        }

        @Test
        void isAfterThrowsOnDifferentPartitions() { // GH-90000
            StreamPosition a = StreamPosition.of(0, 1L); // GH-90000
            StreamPosition b = StreamPosition.of(1, 1L); // GH-90000
            assertThatThrownBy(() -> a.isAfter(b)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("different partitions [GH-90000]");
        }

        @Test
        void isBeforeThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> StreamPosition.of(0, 0L).isBefore(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        void isAfterThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> StreamPosition.of(0, 0L).isAfter(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Test
    @DisplayName("compareTo() orders by partition then offset [GH-90000]")
    void compareToOrdering() { // GH-90000
        StreamPosition p0o1 = StreamPosition.of(0, 1L); // GH-90000
        StreamPosition p0o9 = StreamPosition.of(0, 9L); // GH-90000
        StreamPosition p1o0 = StreamPosition.of(1, 0L); // GH-90000

        assertThat(p0o1.compareTo(p0o9)).isNegative(); // GH-90000
        assertThat(p0o9.compareTo(p0o1)).isPositive(); // GH-90000
        assertThat(p0o1.compareTo(p1o0)).isNegative(); // partition 0 < partition 1 // GH-90000
        assertThat(p0o1.compareTo(p0o1)).isZero(); // GH-90000
    }

    @Test
    @DisplayName("record equality [GH-90000]")
    void recordEquality() { // GH-90000
        StreamPosition a = StreamPosition.of(2, 5L); // GH-90000
        StreamPosition b = StreamPosition.of(2, 5L); // GH-90000
        assertThat(a).isEqualTo(b); // GH-90000
        assertThat(a).isNotEqualTo(StreamPosition.of(2, 6L)); // GH-90000
    }
}
