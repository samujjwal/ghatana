package com.ghatana.agent.learning.retention;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.Validity;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link UtilityBasedRetentionManager} and decay functions.
 *
 * @doc.type class
 * @doc.purpose Tests for retention scoring and decay functions
 * @doc.layer agent-learning
 * @doc.pattern Test
 */
@DisplayName("UtilityBasedRetentionManager Tests")
@ExtendWith(MockitoExtension.class)
class UtilityBasedRetentionManagerTest extends EventloopTestBase {

    private static final String AGENT_ID = "agent-retention-001";

    @Mock
    private MemoryPlane memoryPlane;

    private UtilityBasedRetentionManager retentionManager;

    @BeforeEach
    void setUp() {
        retentionManager = new UtilityBasedRetentionManager(memoryPlane);
    }

    // ─── Helper to create a mock MemoryItem ─────────────────────────────────────

    private MemoryItem createMockItem(String id, Instant createdAt, double confidence) {
        MemoryItem item = mock(MemoryItem.class);
        lenient().when(item.getId()).thenReturn(id);
        when(item.getCreatedAt()).thenReturn(createdAt);
        Validity validity = Validity.builder().confidence(confidence).build();
        when(item.getValidity()).thenReturn(validity);
        return item;
    }

    private MemoryItem createMockItemWithNullValidity(String id, Instant createdAt) {
        MemoryItem item = mock(MemoryItem.class);
        lenient().when(item.getId()).thenReturn(id);
        when(item.getCreatedAt()).thenReturn(createdAt);
        when(item.getValidity()).thenReturn(null);
        return item;
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should reject null memoryPlane")
        void shouldRejectNullMemoryPlane() {
            assertThatThrownBy(() -> new UtilityBasedRetentionManager(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("applyRetention() — item classification")
    class ItemClassification {

        @Test
        @DisplayName("should keep items with high utility")
        void shouldKeepItemsWithHighUtility() {
            // Recent item (created 1 hour ago) with high confidence
            MemoryItem recentHighConf = createMockItem("item-1", Instant.now().minus(Duration.ofHours(1)), 0.95);
            when(memoryPlane.readItems(any(MemoryQuery.class)))
                    .thenReturn(Promise.of(List.of(recentHighConf)));

            RetentionConfig config = RetentionConfig.builder()
                    .minUtility(0.3)
                    .evictionThreshold(0.05)
                    .recencyWeight(0.4)
                    .confidenceWeight(0.6)
                    .decayFunction(ExponentialDecay.sevenDay())
                    .build();

            RetentionResult result = runPromise(() -> retentionManager.applyRetention(AGENT_ID, config));

            assertThat(result.getKept()).isEqualTo(1);
            assertThat(result.getDecayed()).isZero();
            assertThat(result.getEvicted()).isZero();
        }

        @Test
        @DisplayName("should evict items below eviction threshold")
        void shouldEvictItemsBelowEvictionThreshold() {
            // Very old item (created 365 days ago) with zero confidence
            MemoryItem oldLowConf = createMockItem("item-old", Instant.now().minus(Duration.ofDays(365)), 0.0);
            when(memoryPlane.readItems(any(MemoryQuery.class)))
                    .thenReturn(Promise.of(List.of(oldLowConf)));

            RetentionConfig config = RetentionConfig.builder()
                    .minUtility(0.3)
                    .evictionThreshold(0.05)
                    .recencyWeight(0.4)
                    .confidenceWeight(0.6)
                    .decayFunction(ExponentialDecay.sevenDay())
                    .build();

            RetentionResult result = runPromise(() -> retentionManager.applyRetention(AGENT_ID, config));

            assertThat(result.getEvicted()).isEqualTo(1);
            assertThat(result.getKept()).isZero();
        }

        @Test
        @DisplayName("should decay items between eviction threshold and min utility")
        void shouldDecayItemsBetweenThresholds() {
            // Item with moderate age and low confidence — utility between eviction and min
            // Using a step decay to make utility predictable
            MemoryItem moderateItem = createMockItem("item-mod",
                    Instant.now().minus(Duration.ofDays(14)), 0.15);
            when(memoryPlane.readItems(any(MemoryQuery.class)))
                    .thenReturn(Promise.of(List.of(moderateItem)));

            RetentionConfig config = RetentionConfig.builder()
                    .minUtility(0.3)
                    .evictionThreshold(0.05)
                    .recencyWeight(0.4)
                    .confidenceWeight(0.6)
                    .decayFunction(new StepDecay(
                            new double[]{24.0, 168.0, 720.0},
                            new double[]{1.0, 0.7, 0.3, 0.1}))
                    .build();

            // 14 days = 336 hours -> step decay = 0.3
            // utility = 0.4*0.3 + 0.6*0.15 = 0.12 + 0.09 = 0.21
            // 0.05 < 0.21 < 0.3 => decayed
            RetentionResult result = runPromise(() -> retentionManager.applyRetention(AGENT_ID, config));

            assertThat(result.getDecayed()).isEqualTo(1);
            assertThat(result.getKept()).isZero();
            assertThat(result.getEvicted()).isZero();
        }

        @Test
        @DisplayName("should handle mixed items — kept, decayed, and evicted")
        void shouldHandleMixedItems() {
            MemoryItem fresh = createMockItem("fresh", Instant.now().minus(Duration.ofHours(1)), 0.9);
            MemoryItem moderate = createMockItem("moderate", Instant.now().minus(Duration.ofDays(14)), 0.15);
            MemoryItem stale = createMockItem("stale", Instant.now().minus(Duration.ofDays(365)), 0.0);

            when(memoryPlane.readItems(any(MemoryQuery.class)))
                    .thenReturn(Promise.of(List.of(fresh, moderate, stale)));

            RetentionConfig config = RetentionConfig.builder()
                    .minUtility(0.3)
                    .evictionThreshold(0.05)
                    .recencyWeight(0.4)
                    .confidenceWeight(0.6)
                    .decayFunction(new StepDecay(
                            new double[]{24.0, 168.0, 720.0},
                            new double[]{1.0, 0.7, 0.3, 0.1}))
                    .build();

            RetentionResult result = runPromise(() -> retentionManager.applyRetention(AGENT_ID, config));

            assertThat(result.getKept()).isEqualTo(1);
            assertThat(result.getDecayed()).isEqualTo(1);
            assertThat(result.getEvicted()).isEqualTo(1);
            assertThat(result.total()).isEqualTo(3);
        }

        @Test
        @DisplayName("should handle empty memory items list")
        void shouldHandleEmptyMemoryItemsList() {
            when(memoryPlane.readItems(any(MemoryQuery.class)))
                    .thenReturn(Promise.of(List.of()));

            RetentionConfig config = RetentionConfig.builder().build();

            RetentionResult result = runPromise(() -> retentionManager.applyRetention(AGENT_ID, config));

            assertThat(result.getKept()).isZero();
            assertThat(result.getDecayed()).isZero();
            assertThat(result.getEvicted()).isZero();
            assertThat(result.getAgentId()).isEqualTo(AGENT_ID);
        }

        @Test
        @DisplayName("should use 0.5 confidence fallback when validity is null")
        void shouldUseFallbackConfidenceWhenValidityIsNull() {
            // Recent item with null validity → confidence defaults to 0.5
            MemoryItem nullValidity = createMockItemWithNullValidity("null-v", Instant.now().minus(Duration.ofHours(1)));
            when(memoryPlane.readItems(any(MemoryQuery.class)))
                    .thenReturn(Promise.of(List.of(nullValidity)));

            RetentionConfig config = RetentionConfig.builder()
                    .minUtility(0.3)
                    .evictionThreshold(0.05)
                    .recencyWeight(0.4)
                    .confidenceWeight(0.6)
                    .decayFunction(ExponentialDecay.sevenDay())
                    .build();

            // utility ≈ 0.4 * ~1.0 + 0.6 * 0.5 = ~0.70 => kept
            RetentionResult result = runPromise(() -> retentionManager.applyRetention(AGENT_ID, config));

            assertThat(result.getKept()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("applyRetention() — error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should propagate memoryPlane read failure")
        void shouldPropagateMemoryPlaneReadFailure() {
            when(memoryPlane.readItems(any(MemoryQuery.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("DB down")));

            RetentionConfig config = RetentionConfig.builder().build();

            assertThatThrownBy(() -> runPromise(() -> retentionManager.applyRetention(AGENT_ID, config)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB down");

            clearFatalError();
        }
    }

    // =========================================================================
    // Decay Function Tests
    // =========================================================================

    @Nested
    @DisplayName("ExponentialDecay")
    class ExponentialDecayTests {

        @Test
        @DisplayName("should return 1.0 for age 0")
        void shouldReturnOneForAgeZero() {
            ExponentialDecay decay = ExponentialDecay.sevenDay();
            assertThat(decay.compute(0.0)).isCloseTo(1.0, offset(0.001));
        }

        @Test
        @DisplayName("should return ~0.5 at half-life")
        void shouldReturnHalfAtHalfLife() {
            double halfLifeHours = 7.0 * 24.0; // 168 hours
            ExponentialDecay decay = new ExponentialDecay(halfLifeHours);
            assertThat(decay.compute(halfLifeHours)).isCloseTo(0.5, offset(0.001));
        }

        @Test
        @DisplayName("should return ~0.25 at 2x half-life")
        void shouldReturnQuarterAtDoubleHalfLife() {
            double halfLifeHours = 7.0 * 24.0;
            ExponentialDecay decay = new ExponentialDecay(halfLifeHours);
            assertThat(decay.compute(2 * halfLifeHours)).isCloseTo(0.25, offset(0.001));
        }

        @Test
        @DisplayName("should approach 0 for very large age")
        void shouldApproachZeroForLargeAge() {
            ExponentialDecay decay = ExponentialDecay.sevenDay();
            assertThat(decay.compute(10_000.0)).isCloseTo(0.0, offset(0.01));
        }

        @Test
        @DisplayName("should reject non-positive half-life")
        void shouldRejectNonPositiveHalfLife() {
            assertThatThrownBy(() -> new ExponentialDecay(0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ExponentialDecay(-1.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("PowerLawDecay")
    class PowerLawDecayTests {

        @Test
        @DisplayName("should return 1.0 for age 0")
        void shouldReturnOneForAgeZero() {
            PowerLawDecay decay = PowerLawDecay.defaultDecay();
            assertThat(decay.compute(0.0)).isCloseTo(1.0, offset(0.001));
        }

        @Test
        @DisplayName("should decay slower than exponential for old items (long tail)")
        void shouldDecaySlowerThanExponentialForOldItems() {
            PowerLawDecay powerLaw = PowerLawDecay.defaultDecay();
            ExponentialDecay exponential = ExponentialDecay.sevenDay();

            // At 30 days (720 hours), power law retains more than exponential
            double powerLawValue = powerLaw.compute(720.0);
            double exponentialValue = exponential.compute(720.0);

            assertThat(powerLawValue).isGreaterThan(exponentialValue);
        }

        @Test
        @DisplayName("should reject non-positive scale")
        void shouldRejectNonPositiveScale() {
            assertThatThrownBy(() -> new PowerLawDecay(0.0, 1.5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject non-positive exponent")
        void shouldRejectNonPositiveExponent() {
            assertThatThrownBy(() -> new PowerLawDecay(168.0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should decrease monotonically")
        void shouldDecreaseMonotonically() {
            PowerLawDecay decay = PowerLawDecay.defaultDecay();
            double prev = 1.0;
            for (double h = 0; h <= 1000; h += 100) {
                double current = decay.compute(h);
                assertThat(current).isLessThanOrEqualTo(prev);
                prev = current;
            }
        }
    }

    @Nested
    @DisplayName("StepDecay")
    class StepDecayTests {

        @Test
        @DisplayName("should return tier 0 value for age below first threshold")
        void shouldReturnTier0ValueForYoungItems() {
            StepDecay decay = StepDecay.defaultTiered();
            // 0-24h tier → 1.0
            assertThat(decay.compute(12.0)).isCloseTo(1.0, offset(0.001));
        }

        @Test
        @DisplayName("should return tier 1 value for age between first and second threshold")
        void shouldReturnTier1ValueForMiddleItems() {
            StepDecay decay = StepDecay.defaultTiered();
            // 24-168h tier → 0.7
            assertThat(decay.compute(100.0)).isCloseTo(0.7, offset(0.001));
        }

        @Test
        @DisplayName("should return tier 2 value for age between second and third threshold")
        void shouldReturnTier2ValueForOlderItems() {
            StepDecay decay = StepDecay.defaultTiered();
            // 168-720h tier → 0.3
            assertThat(decay.compute(500.0)).isCloseTo(0.3, offset(0.001));
        }

        @Test
        @DisplayName("should return last tier value for age beyond all thresholds")
        void shouldReturnLastTierForVeryOldItems() {
            StepDecay decay = StepDecay.defaultTiered();
            // 720h+ tier → 0.1
            assertThat(decay.compute(1000.0)).isCloseTo(0.1, offset(0.001));
        }

        @Test
        @DisplayName("should reject mismatched thresholds and values length")
        void shouldRejectMismatchedLengths() {
            assertThatThrownBy(() -> new StepDecay(new double[]{24.0}, new double[]{1.0}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("thresholdHours.length + 1");
        }

        @Test
        @DisplayName("should handle single-tier configuration")
        void shouldHandleSingleTierConfiguration() {
            // No thresholds, single value
            StepDecay decay = new StepDecay(new double[]{}, new double[]{0.5});
            assertThat(decay.compute(0.0)).isCloseTo(0.5, offset(0.001));
            assertThat(decay.compute(1000.0)).isCloseTo(0.5, offset(0.001));
        }
    }

    @Nested
    @DisplayName("RetentionResult")
    class RetentionResultTests {

        @Test
        @DisplayName("should compute total correctly")
        void shouldComputeTotalCorrectly() {
            RetentionResult result = RetentionResult.builder()
                    .agentId(AGENT_ID)
                    .kept(5)
                    .decayed(3)
                    .evicted(2)
                    .build();

            assertThat(result.total()).isEqualTo(10);
        }

        @Test
        @DisplayName("should set completedAt to approximately now by default")
        void shouldSetCompletedAtByDefault() {
            Instant before = Instant.now();
            RetentionResult result = RetentionResult.builder()
                    .agentId(AGENT_ID)
                    .kept(0)
                    .decayed(0)
                    .evicted(0)
                    .build();
            Instant after = Instant.now();

            assertThat(result.getCompletedAt()).isBetween(before, after);
        }
    }
}
