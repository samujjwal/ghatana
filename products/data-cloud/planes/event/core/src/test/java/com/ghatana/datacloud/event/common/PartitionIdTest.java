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
 * Tests for {@link PartitionId}.
 */
@DisplayName("PartitionId")
class PartitionIdTest {

    @Nested
    @DisplayName("factory method of()")
    class FactoryOf {

        @Test
        void returnsFirstForZero() { 
            assertThat(PartitionId.of(0)).isSameAs(PartitionId.FIRST); 
        }

        @Test
        void returnsAllForMinusOne() { 
            assertThat(PartitionId.of(-1)).isSameAs(PartitionId.ALL); 
        }

        @Test
        void createsNewPartitionForPositive() { 
            PartitionId pid = PartitionId.of(3); 
            assertThat(pid.value()).isEqualTo(3); 
        }

        @Test
        void rejectsValueBelowMinusOneViaConstructor() { 
            assertThatThrownBy(() -> new PartitionId(-2)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("-2");
        }
    }

    @Nested
    @DisplayName("constants")
    class Constants {

        @Test
        void firstHasValueZero() { 
            assertThat(PartitionId.FIRST.value()).isEqualTo(0); 
        }

        @Test
        void allHasValueMinusOne() { 
            assertThat(PartitionId.ALL.value()).isEqualTo(-1); 
        }
    }

    @Nested
    @DisplayName("isBroadcast()")
    class IsBroadcast {

        @Test
        void trueForAll() { 
            assertThat(PartitionId.ALL.isBroadcast()).isTrue(); 
        }

        @Test
        void falseForFirst() { 
            assertThat(PartitionId.FIRST.isBroadcast()).isFalse(); 
        }

        @Test
        void falseForPositive() { 
            assertThat(PartitionId.of(2).isBroadcast()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("isValidFor(partitionCount)")
    class IsValidFor {

        @Test
        void broadcastAlwaysValid() { 
            assertThat(PartitionId.ALL.isValidFor(1)).isTrue(); 
            assertThat(PartitionId.ALL.isValidFor(100)).isTrue(); 
        }

        @Test
        void firstIsValidForCountAboveOne() { 
            assertThat(PartitionId.FIRST.isValidFor(1)).isTrue(); 
            assertThat(PartitionId.FIRST.isValidFor(8)).isTrue(); 
        }

        @Test
        void partitionThreeInvalidForThreePartitions() { 
            // valid indices 0,1,2 for count=3
            assertThat(PartitionId.of(3).isValidFor(3)).isFalse(); 
        }

        @Test
        void partitionTwoValidForThreePartitions() { 
            assertThat(PartitionId.of(2).isValidFor(3)).isTrue(); 
        }
    }

    @Nested
    @DisplayName("compareTo()")
    class CompareToTest {

        @Test
        void ordersNumerically() { 
            assertThat(PartitionId.of(1).compareTo(PartitionId.of(2))).isNegative(); 
            assertThat(PartitionId.of(2).compareTo(PartitionId.of(1))).isPositive(); 
            assertThat(PartitionId.of(3).compareTo(PartitionId.of(3))).isZero(); 
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        @Test
        void allRepresentation() { 
            assertThat(PartitionId.ALL.toString()).isEqualTo("PartitionId[ALL]");
        }

        @Test
        void regularRepresentation() { 
            assertThat(PartitionId.of(5).toString()).isEqualTo("PartitionId[5]");
        }

        @Test
        void firstRepresentation() { 
            assertThat(PartitionId.FIRST.toString()).isEqualTo("PartitionId[0]");
        }
    }

    @Test
    @DisplayName("record equality")
    void recordEquality() { 
        assertThat(PartitionId.of(4)).isEqualTo(new PartitionId(4)); 
        assertThat(PartitionId.of(4)).isNotEqualTo(PartitionId.of(5)); 
    }
}
