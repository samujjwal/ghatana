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
 * Tests for {@link PartitionId}.
 */
@DisplayName("PartitionId [GH-90000]")
class PartitionIdTest {

    @Nested
    @DisplayName("factory method of() [GH-90000]")
    class FactoryOf {

        @Test
        void returnsFirstForZero() { // GH-90000
            assertThat(PartitionId.of(0)).isSameAs(PartitionId.FIRST); // GH-90000
        }

        @Test
        void returnsAllForMinusOne() { // GH-90000
            assertThat(PartitionId.of(-1)).isSameAs(PartitionId.ALL); // GH-90000
        }

        @Test
        void createsNewPartitionForPositive() { // GH-90000
            PartitionId pid = PartitionId.of(3); // GH-90000
            assertThat(pid.value()).isEqualTo(3); // GH-90000
        }

        @Test
        void rejectsValueBelowMinusOneViaConstructor() { // GH-90000
            assertThatThrownBy(() -> new PartitionId(-2)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("-2 [GH-90000]");
        }
    }

    @Nested
    @DisplayName("constants [GH-90000]")
    class Constants {

        @Test
        void firstHasValueZero() { // GH-90000
            assertThat(PartitionId.FIRST.value()).isEqualTo(0); // GH-90000
        }

        @Test
        void allHasValueMinusOne() { // GH-90000
            assertThat(PartitionId.ALL.value()).isEqualTo(-1); // GH-90000
        }
    }

    @Nested
    @DisplayName("isBroadcast() [GH-90000]")
    class IsBroadcast {

        @Test
        void trueForAll() { // GH-90000
            assertThat(PartitionId.ALL.isBroadcast()).isTrue(); // GH-90000
        }

        @Test
        void falseForFirst() { // GH-90000
            assertThat(PartitionId.FIRST.isBroadcast()).isFalse(); // GH-90000
        }

        @Test
        void falseForPositive() { // GH-90000
            assertThat(PartitionId.of(2).isBroadcast()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("isValidFor(partitionCount) [GH-90000]")
    class IsValidFor {

        @Test
        void broadcastAlwaysValid() { // GH-90000
            assertThat(PartitionId.ALL.isValidFor(1)).isTrue(); // GH-90000
            assertThat(PartitionId.ALL.isValidFor(100)).isTrue(); // GH-90000
        }

        @Test
        void firstIsValidForCountAboveOne() { // GH-90000
            assertThat(PartitionId.FIRST.isValidFor(1)).isTrue(); // GH-90000
            assertThat(PartitionId.FIRST.isValidFor(8)).isTrue(); // GH-90000
        }

        @Test
        void partitionThreeInvalidForThreePartitions() { // GH-90000
            // valid indices 0,1,2 for count=3
            assertThat(PartitionId.of(3).isValidFor(3)).isFalse(); // GH-90000
        }

        @Test
        void partitionTwoValidForThreePartitions() { // GH-90000
            assertThat(PartitionId.of(2).isValidFor(3)).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("compareTo() [GH-90000]")
    class CompareToTest {

        @Test
        void ordersNumerically() { // GH-90000
            assertThat(PartitionId.of(1).compareTo(PartitionId.of(2))).isNegative(); // GH-90000
            assertThat(PartitionId.of(2).compareTo(PartitionId.of(1))).isPositive(); // GH-90000
            assertThat(PartitionId.of(3).compareTo(PartitionId.of(3))).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("toString() [GH-90000]")
    class ToStringTest {

        @Test
        void allRepresentation() { // GH-90000
            assertThat(PartitionId.ALL.toString()).isEqualTo("PartitionId[ALL] [GH-90000]");
        }

        @Test
        void regularRepresentation() { // GH-90000
            assertThat(PartitionId.of(5).toString()).isEqualTo("PartitionId[5] [GH-90000]");
        }

        @Test
        void firstRepresentation() { // GH-90000
            assertThat(PartitionId.FIRST.toString()).isEqualTo("PartitionId[0] [GH-90000]");
        }
    }

    @Test
    @DisplayName("record equality [GH-90000]")
    void recordEquality() { // GH-90000
        assertThat(PartitionId.of(4)).isEqualTo(new PartitionId(4)); // GH-90000
        assertThat(PartitionId.of(4)).isNotEqualTo(PartitionId.of(5)); // GH-90000
    }
}
