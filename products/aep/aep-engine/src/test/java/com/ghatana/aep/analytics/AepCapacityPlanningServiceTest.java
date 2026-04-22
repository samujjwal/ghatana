/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

/**
 * Unit tests for {@link AepCapacityPlanningService} (AEP-011.4). // GH-90000
 */
@DisplayName("AepCapacityPlanningService — AEP-011.4 [GH-90000]")
class AepCapacityPlanningServiceTest {

    private AepCapacityPlanningService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = AepCapacityPlanningService.builder() // GH-90000
                .safetyMargin(0.15) // GH-90000
                .forecastHorizonSteps(10) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("Safe resource plan is returned when usage is low [GH-90000]")
    void safePlan() { // GH-90000
        // Usage at 20/100 = 20%
        List<Double> history = List.of(18.0, 19.0, 20.0); // GH-90000
        AepCapacityPlanningService.CapacityPlan plan = service.analyze("t", "heap_mb", history, 100.0); // GH-90000

        assertThat(plan.usageRatio()).isCloseTo(0.20, offset(0.01)); // GH-90000
        assertThat(plan.isSafe()).isTrue(); // GH-90000
        assertThat(plan.recommendation()).containsIgnoringCase("OK [GH-90000]");
    }

    @Test
    @DisplayName("Imminent exhaustion is flagged with WARNING [GH-90000]")
    void imminentExhaustionWarning() { // GH-90000
        // Rapid growth: 60, 65, 70, 75, 80 — hitting safe capacity (85) in ~1 step // GH-90000
        List<Double> history = List.of(60.0, 65.0, 70.0, 75.0, 80.0); // GH-90000
        AepCapacityPlanningService.CapacityPlan plan = service.analyze("t", "throughput", history, 100.0); // GH-90000

        assertThat(plan.stepsToExhaustion()).isGreaterThanOrEqualTo(1); // GH-90000
        assertThat(plan.recommendation()) // GH-90000
                .matches(r -> r.toLowerCase().contains("warning [GH-90000]") || r.toLowerCase().contains("critical [GH-90000]"),
                        "recommendation should contain WARNING or CRITICAL");
    }

    @Test
    @DisplayName("CRITICAL recommendation when current usage >= 90% [GH-90000]")
    void criticalWhenUsageVeryHigh() { // GH-90000
        List<Double> history = List.of(88.0, 90.0, 91.0); // GH-90000
        AepCapacityPlanningService.CapacityPlan plan = service.analyze("t", "heap_mb", history, 100.0); // GH-90000

        assertThat(plan.usageRatio()).isGreaterThanOrEqualTo(0.90); // GH-90000
        assertThat(plan.recommendation()).containsIgnoringCase("CRITICAL [GH-90000]");
        assertThat(plan.isSafe()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Forecast values are populated for forecastHorizonSteps [GH-90000]")
    void forecastValueCount() { // GH-90000
        List<Double> history = List.of(10.0, 11.0, 12.0); // GH-90000
        AepCapacityPlanningService.CapacityPlan plan = service.analyze("t", "metric", history, 1000.0); // GH-90000

        assertThat(plan.forecastUsage()).hasSize(10); // GH-90000
    }

    @Test
    @DisplayName("stepsToExhaustion is -1 when usage is far below capacity [GH-90000]")
    void stepsToExhaustionNegativeWhenFarFromCapacity() { // GH-90000
        List<Double> history = List.of(10.0, 10.5, 11.0); // GH-90000
        AepCapacityPlanningService.CapacityPlan plan = service.analyze("t", "m", history, 10_000.0); // GH-90000

        assertThat(plan.stepsToExhaustion()).isEqualTo(-1); // GH-90000
    }

    @Test
    @DisplayName("analyze rejects non-positive capacity [GH-90000]")
    void rejectsNonPositiveCapacity() { // GH-90000
        assertThatThrownBy(() -> service.analyze("t", "m", List.of(10.0, 12.0), 0.0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("analyze rejects fewer than 2 history points [GH-90000]")
    void rejectsTooFewHistoryPoints() { // GH-90000
        assertThatThrownBy(() -> service.analyze("t", "m", List.of(10.0), 100.0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects safety margin >= 1 [GH-90000]")
    void builderRejectsMarginGe1() { // GH-90000
        assertThatThrownBy(() -> AepCapacityPlanningService.builder().safetyMargin(1.0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
}
