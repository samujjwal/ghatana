/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OperatorExecutionOptimizer} (AEP-006.2). // GH-90000
 */
@DisplayName("OperatorExecutionOptimizer — AEP-006.2")
class OperatorExecutionOptimizerTest {

    private OperatorExecutionOptimizer optimizer;

    @BeforeEach
    void setUp() { // GH-90000
        optimizer = OperatorExecutionOptimizer.builder() // GH-90000
                .slaThresholdMs(10.0) // GH-90000
                .maxSamplesPerOperator(100) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("statsFor returns empty stats when no samples recorded")
    void statsForNoSamples() { // GH-90000
        OperatorExecutionOptimizer.OperatorStats stats = optimizer.statsFor("op-unknown");
        assertThat(stats.sampleCount()).isEqualTo(0); // GH-90000
        assertThat(stats.avgMs()).isEqualTo(0.0); // GH-90000
        assertThat(stats.meetsTarget()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("statsFor returns correct stats after recording samples")
    void statsForWithSamples() { // GH-90000
        optimizer.record("op-1", Duration.ofMillis(5)); // GH-90000
        optimizer.record("op-1", Duration.ofMillis(7)); // GH-90000
        optimizer.record("op-1", Duration.ofMillis(9)); // GH-90000

        OperatorExecutionOptimizer.OperatorStats stats = optimizer.statsFor("op-1");
        assertThat(stats.sampleCount()).isEqualTo(3); // GH-90000
        assertThat(stats.avgMs()).isCloseTo(7.0, org.assertj.core.data.Offset.offset(0.1)); // GH-90000
        assertThat(stats.minMs()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.1)); // GH-90000
        assertThat(stats.maxMs()).isCloseTo(9.0, org.assertj.core.data.Offset.offset(0.1)); // GH-90000
        assertThat(stats.meetsTarget()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("SLA violation counted when execution exceeds threshold")
    void slaViolationCounted() { // GH-90000
        optimizer.record("op-slow", Duration.ofMillis(5));  // ok // GH-90000
        optimizer.record("op-slow", Duration.ofMillis(15)); // violation // GH-90000
        optimizer.record("op-slow", Duration.ofMillis(20)); // violation // GH-90000

        OperatorExecutionOptimizer.OperatorStats stats = optimizer.statsFor("op-slow");
        assertThat(stats.violations()).isEqualTo(2); // GH-90000
        assertThat(stats.meetsTarget()).isFalse(); // GH-90000
        assertThat(optimizer.totalViolations()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("allStats returns stats for all operators")
    void allStats() { // GH-90000
        optimizer.record("op-a", Duration.ofMillis(5)); // GH-90000
        optimizer.record("op-b", Duration.ofMillis(8)); // GH-90000

        Map<String, OperatorExecutionOptimizer.OperatorStats> all = optimizer.allStats(); // GH-90000
        assertThat(all).containsKeys("op-a", "op-b"); // GH-90000
    }

    @Test
    @DisplayName("bottlenecks returns only operators exceeding SLA threshold")
    void bottlenecksFilter() { // GH-90000
        optimizer.record("fast-op", Duration.ofMillis(3)); // GH-90000
        optimizer.record("slow-op", Duration.ofMillis(25)); // GH-90000

        List<OperatorExecutionOptimizer.OperatorStats> bottlenecks = optimizer.bottlenecks(); // GH-90000
        assertThat(bottlenecks).hasSize(1); // GH-90000
        assertThat(bottlenecks.get(0).operatorId()).isEqualTo("slow-op");
    }

    @Test
    @DisplayName("record(String, long) with negative ns throws")
    void negativeNsThrows() { // GH-90000
        assertThatThrownBy(() -> optimizer.record("op", -1L)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects non-positive slaThresholdMs")
    void builderRejectsZeroSla() { // GH-90000
        assertThatThrownBy(() -> OperatorExecutionOptimizer.builder().slaThresholdMs(0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
}
