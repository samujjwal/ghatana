/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Unit tests for {@link AepCapacityPlanningService} (AEP-011.4).
 */
@DisplayName("AepCapacityPlanningService — AEP-011.4")
class AepCapacityPlanningServiceTest {

    private AepCapacityPlanningService service;

    @BeforeEach
    void setUp() {
        service = AepCapacityPlanningService.builder()
                .safetyMargin(0.15)
                .forecastHorizonSteps(10)
                .build();
    }

    @Test
    @DisplayName("Safe resource plan is returned when usage is low")
    void safePlan() {
        // Usage at 20/100 = 20%
        List<Double> history = List.of(18.0, 19.0, 20.0);
        AepCapacityPlanningService.CapacityPlan plan = service.analyze("t", "heap_mb", history, 100.0);

        assertThat(plan.usageRatio()).isCloseTo(0.20, offset(0.01));
        assertThat(plan.isSafe()).isTrue();
        assertThat(plan.recommendation()).containsIgnoringCase("OK");
    }

    @Test
    @DisplayName("Imminent exhaustion is flagged with WARNING")
    void imminentExhaustionWarning() {
        // Rapid growth: 60, 65, 70, 75, 80 — hitting safe capacity (85) in ~1 step
        List<Double> history = List.of(60.0, 65.0, 70.0, 75.0, 80.0);
        AepCapacityPlanningService.CapacityPlan plan = service.analyze("t", "throughput", history, 100.0);

        assertThat(plan.stepsToExhaustion()).isGreaterThanOrEqualTo(1);
        assertThat(plan.recommendation())
                .matches(r -> r.toLowerCase().contains("warning") || r.toLowerCase().contains("critical"),
                        "recommendation should contain WARNING or CRITICAL");
    }

    @Test
    @DisplayName("CRITICAL recommendation when current usage >= 90%")
    void criticalWhenUsageVeryHigh() {
        List<Double> history = List.of(88.0, 90.0, 91.0);
        AepCapacityPlanningService.CapacityPlan plan = service.analyze("t", "heap_mb", history, 100.0);

        assertThat(plan.usageRatio()).isGreaterThanOrEqualTo(0.90);
        assertThat(plan.recommendation()).containsIgnoringCase("CRITICAL");
        assertThat(plan.isSafe()).isFalse();
    }

    @Test
    @DisplayName("Forecast values are populated for forecastHorizonSteps")
    void forecastValueCount() {
        List<Double> history = List.of(10.0, 11.0, 12.0);
        AepCapacityPlanningService.CapacityPlan plan = service.analyze("t", "metric", history, 1000.0);

        assertThat(plan.forecastUsage()).hasSize(10);
    }

    @Test
    @DisplayName("stepsToExhaustion is -1 when usage is far below capacity")
    void stepsToExhaustionNegativeWhenFarFromCapacity() {
        List<Double> history = List.of(10.0, 10.5, 11.0);
        AepCapacityPlanningService.CapacityPlan plan = service.analyze("t", "m", history, 10_000.0);

        assertThat(plan.stepsToExhaustion()).isEqualTo(-1);
    }

    @Test
    @DisplayName("analyze rejects non-positive capacity")
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> service.analyze("t", "m", List.of(10.0, 12.0), 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("analyze rejects fewer than 2 history points")
    void rejectsTooFewHistoryPoints() {
        assertThatThrownBy(() -> service.analyze("t", "m", List.of(10.0), 100.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder rejects safety margin >= 1")
    void builderRejectsMarginGe1() {
        assertThatThrownBy(() -> AepCapacityPlanningService.builder().safetyMargin(1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
