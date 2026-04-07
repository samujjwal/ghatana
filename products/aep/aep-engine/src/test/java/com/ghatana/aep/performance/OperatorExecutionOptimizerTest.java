/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Unit tests for {@link OperatorExecutionOptimizer} (AEP-006.2).
 */
@DisplayName("OperatorExecutionOptimizer — AEP-006.2")
class OperatorExecutionOptimizerTest {

    private OperatorExecutionOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = OperatorExecutionOptimizer.builder()
                .slaThresholdMs(10.0)
                .maxSamplesPerOperator(100)
                .build();
    }

    @Test
    @DisplayName("statsFor returns empty stats when no samples recorded")
    void statsForNoSamples() {
        OperatorExecutionOptimizer.OperatorStats stats = optimizer.statsFor("op-unknown");
        assertThat(stats.sampleCount()).isEqualTo(0);
        assertThat(stats.avgMs()).isEqualTo(0.0);
        assertThat(stats.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("statsFor returns correct stats after recording samples")
    void statsForWithSamples() {
        optimizer.record("op-1", Duration.ofMillis(5));
        optimizer.record("op-1", Duration.ofMillis(7));
        optimizer.record("op-1", Duration.ofMillis(9));

        OperatorExecutionOptimizer.OperatorStats stats = optimizer.statsFor("op-1");
        assertThat(stats.sampleCount()).isEqualTo(3);
        assertThat(stats.avgMs()).isCloseTo(7.0, org.assertj.core.data.Offset.offset(0.1));
        assertThat(stats.minMs()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.1));
        assertThat(stats.maxMs()).isCloseTo(9.0, org.assertj.core.data.Offset.offset(0.1));
        assertThat(stats.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("SLA violation counted when execution exceeds threshold")
    void slaViolationCounted() {
        optimizer.record("op-slow", Duration.ofMillis(5));  // ok
        optimizer.record("op-slow", Duration.ofMillis(15)); // violation
        optimizer.record("op-slow", Duration.ofMillis(20)); // violation

        OperatorExecutionOptimizer.OperatorStats stats = optimizer.statsFor("op-slow");
        assertThat(stats.violations()).isEqualTo(2);
        assertThat(stats.meetsTarget()).isFalse();
        assertThat(optimizer.totalViolations()).isEqualTo(2);
    }

    @Test
    @DisplayName("allStats returns stats for all operators")
    void allStats() {
        optimizer.record("op-a", Duration.ofMillis(5));
        optimizer.record("op-b", Duration.ofMillis(8));

        Map<String, OperatorExecutionOptimizer.OperatorStats> all = optimizer.allStats();
        assertThat(all).containsKeys("op-a", "op-b");
    }

    @Test
    @DisplayName("bottlenecks returns only operators exceeding SLA threshold")
    void bottlenecksFilter() {
        optimizer.record("fast-op", Duration.ofMillis(3));
        optimizer.record("slow-op", Duration.ofMillis(25));

        List<OperatorExecutionOptimizer.OperatorStats> bottlenecks = optimizer.bottlenecks();
        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).operatorId()).isEqualTo("slow-op");
    }

    @Test
    @DisplayName("record(String, long) with negative ns throws")
    void negativeNsThrows() {
        assertThatThrownBy(() -> optimizer.record("op", -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder rejects non-positive slaThresholdMs")
    void builderRejectsZeroSla() {
        assertThatThrownBy(() -> OperatorExecutionOptimizer.builder().slaThresholdMs(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

