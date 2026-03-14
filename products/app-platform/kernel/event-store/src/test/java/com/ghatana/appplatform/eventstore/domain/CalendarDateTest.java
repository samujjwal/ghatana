package com.ghatana.appplatform.eventstore.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CalendarDate}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for CalendarDate value object
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CalendarDate — Unit Tests")
class CalendarDateTest {

    @Test
    @DisplayName("of_validBsDate — parses a valid BS date string")
    void parsesValidDate() {
        CalendarDate cd = CalendarDate.of("2082-11-13");
        assertThat(cd.value()).isEqualTo("2082-11-13");
        assertThat(cd.toString()).isEqualTo("2082-11-13");
    }

    @Test
    @DisplayName("of_invalidFormat — throws IllegalArgumentException")
    void rejectsInvalidFormat() {
        assertThatThrownBy(() -> CalendarDate.of("13-11-2082"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    @DisplayName("of_null — throws NullPointerException")
    void rejectsNull() {
        assertThatThrownBy(() -> CalendarDate.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("equals_sameValue — two CalendarDates with same value are equal")
    void equalsSameValue() {
        assertThat(CalendarDate.of("2082-01-01")).isEqualTo(CalendarDate.of("2082-01-01"));
    }

    @Test
    @DisplayName("equals_differentValue — two CalendarDates with different values are not equal")
    void equalsDifferentValue() {
        assertThat(CalendarDate.of("2082-01-01")).isNotEqualTo(CalendarDate.of("2082-01-02"));
    }
}
