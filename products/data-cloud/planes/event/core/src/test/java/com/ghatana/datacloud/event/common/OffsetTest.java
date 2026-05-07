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
 * Tests for {@link Offset}.
 */
@DisplayName("Offset")
class OffsetTest {

    @Nested
    @DisplayName("factory method of()")
    class FactoryOf {

        @Test
        void returnsFirstForZero() { 
            assertThat(Offset.of(0)).isSameAs(Offset.FIRST); 
        }

        @Test
        void returnsLatestForMinusOne() { 
            assertThat(Offset.of(-1)).isSameAs(Offset.LATEST); 
        }

        @Test
        void returnsEarliestForMinusTwo() { 
            assertThat(Offset.of(-2)).isSameAs(Offset.EARLIEST); 
        }

        @Test
        void createsNewOffsetForPositiveValue() { 
            Offset o = Offset.of(42); 
            assertThat(o.value()).isEqualTo(42L); 
        }

        @Test
        void rejectsValueBelowMinusTwoViaConstructor() { 
            assertThatThrownBy(() -> new Offset(-3)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("-3");
        }
    }

    @Nested
    @DisplayName("constants")
    class Constants {

        @Test
        void firstHasValueZero() { 
            assertThat(Offset.FIRST.value()).isEqualTo(0L); 
        }

        @Test
        void latestHasValueMinusOne() { 
            assertThat(Offset.LATEST.value()).isEqualTo(-1L); 
        }

        @Test
        void earliestHasValueMinusTwo() { 
            assertThat(Offset.EARLIEST.value()).isEqualTo(-2L); 
        }
    }

    @Nested
    @DisplayName("next()")
    class Next {

        @Test
        void incrementsRegularOffset() { 
            Offset o = Offset.of(5); 
            assertThat(o.next().value()).isEqualTo(6L); 
        }

        @Test
        void incrementsFirstOffset() { 
            assertThat(Offset.FIRST.next().value()).isEqualTo(1L); 
        }

        @Test
        void throwsOnLatest() { 
            assertThatThrownBy(Offset.LATEST::next) 
                    .isInstanceOf(IllegalStateException.class) 
                    .hasMessageContaining("special");
        }

        @Test
        void throwsOnEarliest() { 
            assertThatThrownBy(Offset.EARLIEST::next) 
                    .isInstanceOf(IllegalStateException.class) 
                    .hasMessageContaining("special");
        }
    }

    @Nested
    @DisplayName("isFirst()")
    class IsFirst {

        @Test
        void trueForZero() { 
            assertThat(Offset.FIRST.isFirst()).isTrue(); 
        }

        @Test
        void falseForPositive() { 
            assertThat(Offset.of(1).isFirst()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("isSpecial()")
    class IsSpecial {

        @Test
        void trueForLatest() { 
            assertThat(Offset.LATEST.isSpecial()).isTrue(); 
        }

        @Test
        void trueForEarliest() { 
            assertThat(Offset.EARLIEST.isSpecial()).isTrue(); 
        }

        @Test
        void falseForNormalOffset() { 
            assertThat(Offset.of(10).isSpecial()).isFalse(); 
        }

        @Test
        void falseForFirst() { 
            assertThat(Offset.FIRST.isSpecial()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("isBefore() / isAfter()")
    class Comparison {

        @Test
        void isBeforeWhenLower() { 
            assertThat(Offset.of(3).isBefore(Offset.of(7))).isTrue(); 
        }

        @Test
        void isNotBeforeWhenHigher() { 
            assertThat(Offset.of(7).isBefore(Offset.of(3))).isFalse(); 
        }

        @Test
        void isAfterWhenHigher() { 
            assertThat(Offset.of(7).isAfter(Offset.of(3))).isTrue(); 
        }

        @Test
        void isNotAfterWhenLower() { 
            assertThat(Offset.of(3).isAfter(Offset.of(7))).isFalse(); 
        }

        @Test
        void isBeforeThrowsOnSpecialThis() { 
            assertThatThrownBy(() -> Offset.LATEST.isBefore(Offset.of(1))) 
                    .isInstanceOf(IllegalStateException.class); 
        }

        @Test
        void isBeforeThrowsOnSpecialOther() { 
            assertThatThrownBy(() -> Offset.of(1).isBefore(Offset.LATEST)) 
                    .isInstanceOf(IllegalStateException.class); 
        }

        @Test
        void isBeforeThrowsOnNullOther() { 
            assertThatThrownBy(() -> Offset.of(1).isBefore(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        void isAfterThrowsOnNullOther() { 
            assertThatThrownBy(() -> Offset.of(1).isAfter(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        void compareToOrdering() { 
            assertThat(Offset.of(1).compareTo(Offset.of(2))).isNegative(); 
            assertThat(Offset.of(2).compareTo(Offset.of(1))).isPositive(); 
            assertThat(Offset.of(5).compareTo(Offset.of(5))).isZero(); 
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        @Test
        void latestRepresentation() { 
            assertThat(Offset.LATEST.toString()).isEqualTo("Offset[LATEST]");
        }

        @Test
        void earliestRepresentation() { 
            assertThat(Offset.EARLIEST.toString()).isEqualTo("Offset[EARLIEST]");
        }

        @Test
        void normalOffsetRepresentation() { 
            assertThat(Offset.of(99).toString()).isEqualTo("Offset[99]");
        }

        @Test
        void firstRepresentation() { 
            assertThat(Offset.FIRST.toString()).isEqualTo("Offset[0]");
        }
    }

    @Test
    @DisplayName("record equality")
    void recordEquality() { 
        assertThat(Offset.of(5)).isEqualTo(new Offset(5)); 
        assertThat(Offset.of(5)).isNotEqualTo(Offset.of(6)); 
    }
}
