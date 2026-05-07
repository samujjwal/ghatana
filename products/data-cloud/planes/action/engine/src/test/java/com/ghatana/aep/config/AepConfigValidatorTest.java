/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.aep.config;

import com.ghatana.aep.Aep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepConfigValidator}.
 *
 * <p>Note: {@link Aep.AepConfig} has a compact constructor that normalises
 * certain out-of-range primitives before the object is created (e.g. workerThreads ≤ 0 
 * is replaced with {@code availableProcessors()}, maxPipelinesPerTenant ≤ 0 is 
 * replaced with 100).  Tests for those fields therefore reach the validator only
 * when the raw value supplied to the builder is:
 * <ul>
 *   <li>positive but above the upper bound (maxPipelinesPerTenant > 10 000), or</li> 
 *   <li>backed by direct record construction for negative/zero checks via
 *       {@code new Aep.AepConfig(…)} with the workerThreads slot carefully 
 *       controlled.</li>
 * </ul>
 * All other fields ({@code anomalyThreshold}, {@code instanceId}, {@code customConfig}) 
 * are passed verbatim to the record and can be tested through the builder as usual.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AepConfig fail-fast validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepConfigValidator")
class AepConfigValidatorTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a valid config that passes all constraints; individual tests override
     * specific fields.
     */
    private static Aep.AepConfig valid() { 
        return new Aep.AepConfig("test-instance-01", 4, 100, false, false, 0.9, Map.of()); 
    }

    // ── anomalyThreshold ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("anomalyThreshold validation")
    class AnomalyThresholdValidation {

        @Test
        @DisplayName("valid threshold (0.5) passes")
        void validThreshold_passes() { 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 10, false, false, 0.5, Map.of()); 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }

        @Test
        @DisplayName("threshold of 0.0 is rejected (exclusive lower boundary)")
        void zeroBoundary_rejected() { 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 10, false, false, 0.0, Map.of()); 
            assertThatThrownBy(() -> AepConfigValidator.validate(cfg)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("anomalyThreshold");
        }

        @Test
        @DisplayName("threshold of 1.0 is rejected (exclusive upper boundary)")
        void oneBoundary_rejected() { 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 10, false, false, 1.0, Map.of()); 
            assertThatThrownBy(() -> AepConfigValidator.validate(cfg)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("anomalyThreshold");
        }

        @Test
        @DisplayName("threshold just above lower boundary (0.001) passes")
        void justAboveLower_passes() { 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 10, false, false, 0.001, Map.of()); 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }

        @Test
        @DisplayName("threshold just below upper boundary (0.999) passes")
        void justBelowUpper_passes() { 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 10, false, false, 0.999, Map.of()); 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }
    }

    // ── maxPipelinesPerTenant ────────────────────────────────────────────────

    @Nested
    @DisplayName("maxPipelinesPerTenant validation")
    class MaxPipelinesValidation {

        @Test
        @DisplayName("value of 1 (lower inclusive boundary) passes")
        void lowerBoundary_passes() { 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 1, false, false, 0.9, Map.of()); 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }

        @Test
        @DisplayName("value of 10 000 (upper inclusive boundary) passes")
        void upperBoundary_passes() { 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 10_000, false, false, 0.9, Map.of()); 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }

        @Test
        @DisplayName("value of 10 001 (above upper bound) is rejected")
        void aboveUpperBound_rejected() { 
            // compact constructor does NOT normalise values > 0, so 10_001 survives
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 10_001, false, false, 0.9, Map.of()); 
            assertThatThrownBy(() -> AepConfigValidator.validate(cfg)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("maxPipelinesPerTenant");
        }

        @Test
        @DisplayName("large pathological value (1_000_000) is rejected")
        void extremeValue_rejected() { 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 1_000_000, false, false, 0.9, Map.of()); 
            assertThatThrownBy(() -> AepConfigValidator.validate(cfg)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("maxPipelinesPerTenant");
        }
    }

    // ── workerThreads ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("workerThreads validation")
    class WorkerThreadsValidation {

        @Test
        @DisplayName("value of 1 (minimum allowed) passes")
        void minimumValue_passes() { 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 1, 10, false, false, 0.9, Map.of()); 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }

        @Test
        @DisplayName("large value (64) passes")
        void largeValue_passes() { 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 64, 10, false, false, 0.9, Map.of()); 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }

        /**
         * The compact constructor normalises {@code workerThreads <= 0} to
         * {@code availableProcessors()}, so the validator never sees zero or 
         * negative values through the public API.  This behaviour is intentional:
         * "0" means "auto-detect" for callers who don't know the host CPU
         * count at config time.
         *
         * <p>This test documents the contract rather than asserting a rejection.
         */
        @Test
        @DisplayName("value of 0 is normalised by compact constructor to availableProcessors()")
        void zeroIsNormalisedToAvailableProcessors() { 
            Aep.AepConfig cfg = Aep.AepConfig.builder() 
                .anomalyThreshold(0.9).maxPipelinesPerTenant(10).workerThreads(0).build(); 
            // compact constructor normalises it — validator must NOT reject
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }
    }

    // ── instanceId ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("instanceId validation")
    class InstanceIdValidation {

        @Test
        @DisplayName("null instanceId is allowed (compact constructor generates a UUID)")
        void nullInstanceId_allowed() { 
            // compact constructor replaces null with a UUID, so validator sees a UUID string
            Aep.AepConfig cfg = Aep.AepConfig.builder() 
                .anomalyThreshold(0.9).maxPipelinesPerTenant(10).workerThreads(2) 
                .instanceId(null).customConfig(Map.of()).build(); 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }

        @Test
        @DisplayName("blank instanceId (whitespace) is rejected")
        void blankInstanceId_rejected() { 
            // compact constructor does NOT normalise blank strings — only null
            Aep.AepConfig cfg = new Aep.AepConfig("   ", 2, 10, false, false, 0.9, Map.of()); 
            assertThatThrownBy(() -> AepConfigValidator.validate(cfg)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("instanceId");
        }

        @Test
        @DisplayName("empty string instanceId is rejected")
        void emptyInstanceId_rejected() { 
            Aep.AepConfig cfg = new Aep.AepConfig("", 2, 10, false, false, 0.9, Map.of()); 
            assertThatThrownBy(() -> AepConfigValidator.validate(cfg)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("instanceId");
        }

        @Test
        @DisplayName("valid non-blank instanceId passes")
        void validInstanceId_passes() { 
            Aep.AepConfig cfg = new Aep.AepConfig("prod-node-01", 2, 10, false, false, 0.9, Map.of()); 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }
    }

    // ── customConfig ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("customConfig validation")
    class CustomConfigValidation {

        @Test
        @DisplayName("empty customConfig passes")
        void emptyMap_passes() { 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(valid())); 
        }

        @Test
        @DisplayName("customConfig with valid non-null, non-blank entries passes")
        void validEntries_passes() { 
            java.util.Map<String, Object> map = Map.of("feature.x", "enabled", "timeout.ms", 5000); 
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 10, false, false, 0.9, map); 
            assertThatNoException().isThrownBy(() -> AepConfigValidator.validate(cfg)); 
        }

        @Test
        @DisplayName("customConfig with a null value is rejected, message names the key")
        void nullValue_rejected() { 
            java.util.Map<String, Object> map = new java.util.HashMap<>(); 
            map.put("some.key", null); 
            // Wrap in another map because Map.copyOf() in compact constructor rejects null values 
            // — pass directly via record constructor to test the validator, not the compact ctor
            // NOTE: compact constructor calls Map.copyOf(customConfig) which THROWS on null values. 
            // Therefore: the validator's null-value check is a belt-and-suspenders guard for code
            // that constructs AepConfig without going through the compact constructor.
            // We verify the validator static method handles malformed input gracefully.
            Aep.AepConfig cfg = org.mockito.Mockito.mock(Aep.AepConfig.class); 
            org.mockito.Mockito.when(cfg.instanceId()).thenReturn("t");
            org.mockito.Mockito.when(cfg.workerThreads()).thenReturn(2); 
            org.mockito.Mockito.when(cfg.maxPipelinesPerTenant()).thenReturn(10); 
            org.mockito.Mockito.when(cfg.anomalyThreshold()).thenReturn(0.9); 
            org.mockito.Mockito.when(cfg.customConfig()).thenReturn(map); 
            assertThatThrownBy(() -> AepConfigValidator.validate(cfg)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("customConfig")
                .hasMessageContaining("some.key");
        }

        @Test
        @DisplayName("customConfig with a blank key is rejected")
        void blankKey_rejected() { 
            java.util.Map<String, Object> map = new java.util.HashMap<>(); 
            map.put("  ", "value"); 
            Aep.AepConfig cfg = org.mockito.Mockito.mock(Aep.AepConfig.class); 
            org.mockito.Mockito.when(cfg.instanceId()).thenReturn("t");
            org.mockito.Mockito.when(cfg.workerThreads()).thenReturn(2); 
            org.mockito.Mockito.when(cfg.maxPipelinesPerTenant()).thenReturn(10); 
            org.mockito.Mockito.when(cfg.anomalyThreshold()).thenReturn(0.9); 
            org.mockito.Mockito.when(cfg.customConfig()).thenReturn(map); 
            assertThatThrownBy(() -> AepConfigValidator.validate(cfg)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("customConfig");
        }
    }

    // ── Multiple violations ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Multiple violation reporting")
    class MultipleViolations {

        @Test
        @DisplayName("anomalyThreshold + maxPipelinesPerTenant violations both reported")
        void twoViolations_allReported() { 
            // anomalyThreshold=0 rejected; maxPipelinesPerTenant=10_001 rejected
            Aep.AepConfig cfg = new Aep.AepConfig("t", 2, 10_001, false, false, 0.0, Map.of()); 
            assertThatThrownBy(() -> AepConfigValidator.validate(cfg)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("anomalyThreshold")
                .hasMessageContaining("maxPipelinesPerTenant");
        }

        @Test
        @DisplayName("anomalyThreshold + blank instanceId violations both reported")
        void thresholdAndInstanceId_allReported() { 
            Aep.AepConfig cfg = new Aep.AepConfig("  ", 2, 10, false, false, 0.0, Map.of()); 
            assertThatThrownBy(() -> AepConfigValidator.validate(cfg)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("anomalyThreshold")
                .hasMessageContaining("instanceId");
        }
    }

    // ── Null guard ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Null config guard")
    class NullGuard {

        @Test
        @DisplayName("null config throws NullPointerException immediately")
        void nullConfig_throwsNpe() { 
            assertThatThrownBy(() -> AepConfigValidator.validate(null)) 
                .isInstanceOf(NullPointerException.class); 
        }
    }
}
