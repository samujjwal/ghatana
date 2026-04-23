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
 * Tests for {@link Offset}.
 */
@DisplayName("Offset")
class OffsetTest {

    @Nested
    @DisplayName("factory method of()")
    class FactoryOf {

        @Test
        void returnsFirstForZero() { // GH-90000
            assertThat(Offset.of(0)).isSameAs(Offset.FIRST); // GH-90000
        }

        @Test
        void returnsLatestForMinusOne() { // GH-90000
            assertThat(Offset.of(-1)).isSameAs(Offset.LATEST); // GH-90000
        }

        @Test
        void returnsEarliestForMinusTwo() { // GH-90000
            assertThat(Offset.of(-2)).isSameAs(Offset.EARLIEST); // GH-90000
        }

        @Test
        void createsNewOffsetForPositiveValue() { // GH-90000
            Offset o = Offset.of(42); // GH-90000
            assertThat(o.value()).isEqualTo(42L); // GH-90000
        }

        @Test
        void rejectsValueBelowMinusTwoViaConstructor() { // GH-90000
            assertThatThrownBy(() -> new Offset(-3)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("-3");
        }
    }

    @Nested
    @DisplayName("constants")
    class Constants {

        @Test
        void firstHasValueZero() { // GH-90000
            assertThat(Offset.FIRST.value()).isEqualTo(0L); // GH-90000
        }

        @Test
        void latestHasValueMinusOne() { // GH-90000
            assertThat(Offset.LATEST.value()).isEqualTo(-1L); // GH-90000
        }

        @Test
        void earliestHasValueMinusTwo() { // GH-90000
            assertThat(Offset.EARLIEST.value()).isEqualTo(-2L); // GH-90000
        }
    }

    @Nested
    @DisplayName("next()")
    class Next {

        @Test
        void incrementsRegularOffset() { // GH-90000
            Offset o = Offset.of(5); // GH-90000
            assertThat(o.next().value()).isEqualTo(6L); // GH-90000
        }

        @Test
        void incrementsFirstOffset() { // GH-90000
            assertThat(Offset.FIRST.next().value()).isEqualTo(1L); // GH-90000
        }

        @Test
        void throwsOnLatest() { // GH-90000
            assertThatThrownBy(Offset.LATEST::next) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("special");
        }

        @Test
        void throwsOnEarliest() { // GH-90000
            assertThatThrownBy(Offset.EARLIEST::next) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("special");
        }
    }

    @Nested
    @DisplayName("isFirst()")
    class IsFirst {

        @Test
        void trueForZero() { // GH-90000
            assertThat(Offset.FIRST.isFirst()).isTrue(); // GH-90000
        }

        @Test
        void falseForPositive() { // GH-90000
            assertThat(Offset.of(1).isFirst()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("isSpecial()")
    class IsSpecial {

        @Test
        void trueForLatest() { // GH-90000
            assertThat(Offset.LATEST.isSpecial()).isTrue(); // GH-90000
        }

        @Test
        void trueForEarliest() { // GH-90000
            assertThat(Offset.EARLIEST.isSpecial()).isTrue(); // GH-90000
        }

        @Test
        void falseForNormalOffset() { // GH-90000
            assertThat(Offset.of(10).isSpecial()).isFalse(); // GH-90000
        }

        @Test
        void falseForFirst() { // GH-90000
            assertThat(Offset.FIRST.isSpecial()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("isBefore() / isAfter()")
    class Comparison {

        @Test
        void isBeforeWhenLower() { // GH-90000
            assertThat(Offset.of(3).isBefore(Offset.of(7))).isTrue(); // GH-90000
        }

        @Test
        void isNotBeforeWhenHigher() { // GH-90000
            assertThat(Offset.of(7).isBefore(Offset.of(3))).isFalse(); // GH-90000
        }

        @Test
        void isAfterWhenHigher() { // GH-90000
            assertThat(Offset.of(7).isAfter(Offset.of(3))).isTrue(); // GH-90000
        }

        @Test
        void isNotAfterWhenLower() { // GH-90000
            assertThat(Offset.of(3).isAfter(Offset.of(7))).isFalse(); // GH-90000
        }

        @Test
        void isBeforeThrowsOnSpecialThis() { // GH-90000
            assertThatThrownBy(() -> Offset.LATEST.isBefore(Offset.of(1))) // GH-90000
                    .isInstanceOf(IllegalStateException.class); // GH-90000
        }

        @Test
        void isBeforeThrowsOnSpecialOther() { // GH-90000
            assertThatThrownBy(() -> Offset.of(1).isBefore(Offset.LATEST)) // GH-90000
                    .isInstanceOf(IllegalStateException.class); // GH-90000
        }

        @Test
        void isBeforeThrowsOnNullOther() { // GH-90000
            assertThatThrownBy(() -> Offset.of(1).isBefore(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        void isAfterThrowsOnNullOther() { // GH-90000
            assertThatThrownBy(() -> Offset.of(1).isAfter(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        void compareToOrdering() { // GH-90000
            assertThat(Offset.of(1).compareTo(Offset.of(2))).isNegative(); // GH-90000
            assertThat(Offset.of(2).compareTo(Offset.of(1))).isPositive(); // GH-90000
            assertThat(Offset.of(5).compareTo(Offset.of(5))).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        @Test
        void latestRepresentation() { // GH-90000
            assertThat(Offset.LATEST.toString()).isEqualTo("Offset[LATEST]");
        }

        @Test
        void earliestRepresentation() { // GH-90000
            assertThat(Offset.EARLIEST.toString()).isEqualTo("Offset[EARLIEST]");
        }

        @Test
        void normalOffsetRepresentation() { // GH-90000
            assertThat(Offset.of(99).toString()).isEqualTo("Offset[99]");
        }

        @Test
        void firstRepresentation() { // GH-90000
            assertThat(Offset.FIRST.toString()).isEqualTo("Offset[0]");
        }
    }

    @Test
    @DisplayName("record equality")
    void recordEquality() { // GH-90000
        assertThat(Offset.of(5)).isEqualTo(new Offset(5)); // GH-90000
        assertThat(Offset.of(5)).isNotEqualTo(Offset.of(6)); // GH-90000
    }
}
