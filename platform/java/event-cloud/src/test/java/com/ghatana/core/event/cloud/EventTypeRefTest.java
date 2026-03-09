package com.ghatana.core.event.cloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link EventTypeRef} value object.
 */
@DisplayName("EventTypeRef")
class EventTypeRefTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        void shouldCreateFromNameAndVersion() {
            EventTypeRef ref = new EventTypeRef("order.created", new Version(1, 0));
            assertThat(ref.name()).isEqualTo("order.created");
            assertThat(ref.version()).isEqualTo(new Version(1, 0));
        }

        @Test
        void shouldCreateViaFactoryMethod() {
            EventTypeRef ref = EventTypeRef.of("sensor.reading", 2, 1);
            assertThat(ref.name()).isEqualTo("sensor.reading");
            assertThat(ref.version().major()).isEqualTo(2);
            assertThat(ref.version().minor()).isEqualTo(1);
        }

        @Test
        void shouldRejectNullName() {
            assertThatThrownBy(() -> new EventTypeRef(null, new Version(1, 0)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectBlankName() {
            assertThatThrownBy(() -> new EventTypeRef("  ", new Version(1, 0)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be blank");
        }

        @Test
        void shouldRejectNullVersion() {
            assertThatThrownBy(() -> new EventTypeRef("order.created", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        void shouldFormatAsNameAtVersion() {
            EventTypeRef ref = EventTypeRef.of("order.created", 1, 0);
            assertThat(ref.toString()).isEqualTo("order.created@1.0");
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        void shouldBeEqualForSameNameAndVersion() {
            EventTypeRef a = EventTypeRef.of("order.created", 1, 0);
            EventTypeRef b = EventTypeRef.of("order.created", 1, 0);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void shouldNotBeEqualForDifferentName() {
            assertThat(EventTypeRef.of("a", 1, 0)).isNotEqualTo(EventTypeRef.of("b", 1, 0));
        }

        @Test
        void shouldNotBeEqualForDifferentVersion() {
            assertThat(EventTypeRef.of("a", 1, 0)).isNotEqualTo(EventTypeRef.of("a", 2, 0));
        }
    }
}
