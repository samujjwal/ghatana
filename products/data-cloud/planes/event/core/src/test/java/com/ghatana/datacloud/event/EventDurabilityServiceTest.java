/**
 * @doc.type class
 * @doc.purpose Verify DurabilityLevel strength ordering invariants after the fix
 *             that replaced ordinal-based comparison with explicit strength values.
 * @doc.layer products
 * @doc.pattern Test
 */
package com.ghatana.datacloud.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link EventDurabilityService.DurabilityLevel} strength ordering guarantees.
 *
 * <p>These tests pin the explicit {@code strength()} values that protect
 * {@link EventDurabilityService.DurabilityResult#meetsLevel} from silent regressions
 * caused by enum declaration reordering.
 */
@DisplayName("DurabilityLevel strength ordering")
class EventDurabilityServiceTest {

    // ─── Individual strength values ───────────────────────────────────────────

    @Test
    @DisplayName("NONE has strength 0")
    void noneHasStrengthZero() {
        assertThat(EventDurabilityService.DurabilityLevel.NONE.strength()).isEqualTo(0);
    }

    @Test
    @DisplayName("LEADER_ACK has strength 1")
    void leaderAckHasStrengthOne() {
        assertThat(EventDurabilityService.DurabilityLevel.LEADER_ACK.strength()).isEqualTo(1);
    }

    @Test
    @DisplayName("MAJORITY_ACK has strength 2")
    void majorityAckHasStrengthTwo() {
        assertThat(EventDurabilityService.DurabilityLevel.MAJORITY_ACK.strength()).isEqualTo(2);
    }

    @Test
    @DisplayName("ALL_ACK has strength 3")
    void allAckHasStrengthThree() {
        assertThat(EventDurabilityService.DurabilityLevel.ALL_ACK.strength()).isEqualTo(3);
    }

    @Test
    @DisplayName("FSYNC_ACK has strength 4")
    void fsyncAckHasStrengthFour() {
        assertThat(EventDurabilityService.DurabilityLevel.FSYNC_ACK.strength()).isEqualTo(4);
    }

    // ─── All values have distinct strengths ──────────────────────────────────

    @Test
    @DisplayName("All levels have distinct strength values")
    void allLevelsHaveDistinctStrengths() {
        EventDurabilityService.DurabilityLevel[] levels = EventDurabilityService.DurabilityLevel.values();
        long distinctCount = java.util.Arrays.stream(levels)
                .mapToInt(EventDurabilityService.DurabilityLevel::strength)
                .distinct()
                .count();
        assertThat(distinctCount)
                .as("Every DurabilityLevel must have a unique strength value")
                .isEqualTo(levels.length);
    }

    // ─── Monotonic ordering ───────────────────────────────────────────────────

    @Test
    @DisplayName("Strengths are strictly monotonically increasing from NONE to FSYNC_ACK")
    void strengthsAreMonotonicallyIncreasing() {
        EventDurabilityService.DurabilityLevel[] expected = {
            EventDurabilityService.DurabilityLevel.NONE,
            EventDurabilityService.DurabilityLevel.LEADER_ACK,
            EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
            EventDurabilityService.DurabilityLevel.ALL_ACK,
            EventDurabilityService.DurabilityLevel.FSYNC_ACK
        };

        for (int i = 1; i < expected.length; i++) {
            assertThat(expected[i].strength())
                    .as("%s must have higher strength than %s", expected[i], expected[i - 1])
                    .isGreaterThan(expected[i - 1].strength());
        }
    }

    // ─── meetsLevel matrix ────────────────────────────────────────────────────

    @ParameterizedTest(name = "achieved={0} meets required={1} → {2}")
    @MethodSource("meetsLevelMatrix")
    @DisplayName("meetsLevel uses strength comparison, not ordinal")
    void meetsLevelUsesStrengthNotOrdinal(
            EventDurabilityService.DurabilityLevel achieved,
            EventDurabilityService.DurabilityLevel required,
            boolean expected) {

        EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult(
                    "test-event-id",
                    0L,
                    achieved,
                    0L,
                    0L,
                    true
                );

        assertThat(result.meetsLevel(required)).isEqualTo(expected);
    }

    static Stream<Arguments> meetsLevelMatrix() {
        EventDurabilityService.DurabilityLevel[] levels = EventDurabilityService.DurabilityLevel.values();
        Stream.Builder<Arguments> builder = Stream.builder();
        for (EventDurabilityService.DurabilityLevel achieved : levels) {
            for (EventDurabilityService.DurabilityLevel required : levels) {
                builder.accept(Arguments.of(achieved, required,
                        achieved.strength() >= required.strength()));
            }
        }
        return builder.build();
    }

    // ─── strength() does not throw ────────────────────────────────────────────

    @Test
    @DisplayName("strength() does not throw for any level")
    void strengthDoesNotThrowForAnyLevel() {
        for (EventDurabilityService.DurabilityLevel level : EventDurabilityService.DurabilityLevel.values()) {
            assertThatCode(level::strength).doesNotThrowAnyException();
        }
    }
}
