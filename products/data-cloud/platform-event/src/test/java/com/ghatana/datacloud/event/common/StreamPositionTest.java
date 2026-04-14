/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("StreamPosition")
class StreamPositionTest {

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        void ofPartitionAndOffset() {
            StreamPosition pos = StreamPosition.of(PartitionId.of(2), Offset.of(10));
            assertThat(pos.partitionId()).isEqualTo(PartitionId.of(2));
            assertThat(pos.offset()).isEqualTo(Offset.of(10));
        }

        @Test
        void ofRawInts() {
            StreamPosition pos = StreamPosition.of(3, 42L);
            assertThat(pos.partitionId().value()).isEqualTo(3);
            assertThat(pos.offset().value()).isEqualTo(42L);
        }

        @Test
        void startOf() {
            StreamPosition start = StreamPosition.startOf(PartitionId.of(1));
            assertThat(start.partitionId()).isEqualTo(PartitionId.of(1));
            assertThat(start.offset()).isSameAs(Offset.FIRST);
        }

        @Test
        void latestOf() {
            StreamPosition latest = StreamPosition.latestOf(PartitionId.of(0));
            assertThat(latest.partitionId()).isEqualTo(PartitionId.FIRST);
            assertThat(latest.offset()).isSameAs(Offset.LATEST);
        }

        @Test
        void constructorRejectsNullPartition() {
            assertThatThrownBy(() -> StreamPosition.of(null, Offset.FIRST))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructorRejectsNullOffset() {
            assertThatThrownBy(() -> StreamPosition.of(PartitionId.FIRST, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("nextOffset()")
    class NextOffset {

        @Test
        void incrementsOffsetInSamePartition() {
            StreamPosition pos = StreamPosition.of(2, 5L);
            StreamPosition next = pos.nextOffset();
            assertThat(next.partitionId()).isEqualTo(PartitionId.of(2));
            assertThat(next.offset().value()).isEqualTo(6L);
        }
    }

    @Nested
    @DisplayName("isBefore() / isAfter()")
    class InPartitionComparison {

        @Test
        void isBeforeInSamePartition() {
            StreamPosition a = StreamPosition.of(0, 3L);
            StreamPosition b = StreamPosition.of(0, 7L);
            assertThat(a.isBefore(b)).isTrue();
            assertThat(b.isBefore(a)).isFalse();
        }

        @Test
        void isAfterInSamePartition() {
            StreamPosition a = StreamPosition.of(0, 7L);
            StreamPosition b = StreamPosition.of(0, 3L);
            assertThat(a.isAfter(b)).isTrue();
            assertThat(b.isAfter(a)).isFalse();
        }

        @Test
        void isBeforeThrowsOnDifferentPartitions() {
            StreamPosition a = StreamPosition.of(0, 1L);
            StreamPosition b = StreamPosition.of(1, 1L);
            assertThatThrownBy(() -> a.isBefore(b))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different partitions");
        }

        @Test
        void isAfterThrowsOnDifferentPartitions() {
            StreamPosition a = StreamPosition.of(0, 1L);
            StreamPosition b = StreamPosition.of(1, 1L);
            assertThatThrownBy(() -> a.isAfter(b))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different partitions");
        }

        @Test
        void isBeforeThrowsOnNull() {
            assertThatThrownBy(() -> StreamPosition.of(0, 0L).isBefore(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void isAfterThrowsOnNull() {
            assertThatThrownBy(() -> StreamPosition.of(0, 0L).isAfter(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("compareTo() orders by partition then offset")
    void compareToOrdering() {
        StreamPosition p0o1 = StreamPosition.of(0, 1L);
        StreamPosition p0o9 = StreamPosition.of(0, 9L);
        StreamPosition p1o0 = StreamPosition.of(1, 0L);

        assertThat(p0o1.compareTo(p0o9)).isNegative();
        assertThat(p0o9.compareTo(p0o1)).isPositive();
        assertThat(p0o1.compareTo(p1o0)).isNegative(); // partition 0 < partition 1
        assertThat(p0o1.compareTo(p0o1)).isZero();
    }

    @Test
    @DisplayName("record equality")
    void recordEquality() {
        StreamPosition a = StreamPosition.of(2, 5L);
        StreamPosition b = StreamPosition.of(2, 5L);
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(StreamPosition.of(2, 6L));
    }
}
