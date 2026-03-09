package com.ghatana.core.event.cloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Version} value object.
 */
@DisplayName("Version")
class VersionTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        void shouldCreateValidVersion() {
            Version v = new Version(1, 2);
            assertThat(v.major()).isEqualTo(1);
            assertThat(v.minor()).isEqualTo(2);
        }

        @Test
        void shouldAllowZeroVersion() {
            Version v = new Version(0, 0);
            assertThat(v.major()).isZero();
            assertThat(v.minor()).isZero();
        }

        @Test
        void shouldRejectNegativeMajor() {
            assertThatThrownBy(() -> new Version(-1, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Major version must be non-negative");
        }

        @Test
        void shouldRejectNegativeMinor() {
            assertThatThrownBy(() -> new Version(0, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Minor version must be non-negative");
        }
    }

    @Nested
    @DisplayName("Parsing")
    class Parsing {

        @Test
        void shouldParseValidVersion() {
            Version v = Version.parse("3.7");
            assertThat(v.major()).isEqualTo(3);
            assertThat(v.minor()).isEqualTo(7);
        }

        @Test
        void shouldRejectSingleComponent() {
            assertThatThrownBy(() -> Version.parse("1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid version format");
        }

        @Test
        void shouldRejectThreeComponents() {
            assertThatThrownBy(() -> Version.parse("1.2.3"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid version format");
        }

        @Test
        void shouldRejectNonNumeric() {
            assertThatThrownBy(() -> Version.parse("a.b"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid version format");
        }
    }

    @Nested
    @DisplayName("Comparison")
    class Comparison {

        @Test
        void shouldCompareMajorVersions() {
            assertThat(new Version(2, 0)).isGreaterThan(new Version(1, 0));
            assertThat(new Version(1, 0)).isLessThan(new Version(2, 0));
        }

        @Test
        void shouldCompareMinorVersionsWhenMajorEqual() {
            assertThat(new Version(1, 2)).isGreaterThan(new Version(1, 1));
            assertThat(new Version(1, 0)).isLessThan(new Version(1, 5));
        }

        @Test
        void shouldBeEqualWhenSameVersion() {
            assertThat(new Version(1, 2).compareTo(new Version(1, 2))).isZero();
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        void shouldFormatAsMajorDotMinor() {
            assertThat(new Version(1, 2).toString()).isEqualTo("1.2");
        }

        @Test
        void shouldFormatZeroVersion() {
            assertThat(new Version(0, 0).toString()).isEqualTo("0.0");
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        void shouldBeEqualForSameValues() {
            assertThat(new Version(1, 2)).isEqualTo(new Version(1, 2));
        }

        @Test
        void shouldNotBeEqualForDifferentValues() {
            assertThat(new Version(1, 2)).isNotEqualTo(new Version(1, 3));
        }

        @Test
        void shouldHaveSameHashCodeForEqualVersions() {
            assertThat(new Version(1, 2).hashCode()).isEqualTo(new Version(1, 2).hashCode());
        }
    }
}
