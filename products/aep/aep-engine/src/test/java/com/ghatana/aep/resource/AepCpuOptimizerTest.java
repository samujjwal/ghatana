/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.management.OperatingSystemMXBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepCpuOptimizer} (AEP-005.2). // GH-90000
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepCpuOptimizer — AEP-005.2 [GH-90000]")
class AepCpuOptimizerTest {

    @Mock
    private OperatingSystemMXBean osBean;

    private AepCpuOptimizer optimizer;

    @BeforeEach
    void setUp() { // GH-90000
        optimizer = AepCpuOptimizer.builder() // GH-90000
                .osBean(osBean) // GH-90000
                .samplingIntervalMs(1_000) // GH-90000
                .highCpuThreshold(0.80) // GH-90000
                .consecutiveHighSamplesBeforeThrottle(3) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("Initial state: throttle inactive, sample count = 0 [GH-90000]")
    void initialState() { // GH-90000
        assertThat(optimizer.isThrottleActive()).isFalse(); // GH-90000
        assertThat(optimizer.sampleCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("currentSnapshot returns EMPTY before any sample [GH-90000]")
    void currentSnapshotEmptyBeforeSamples() { // GH-90000
        AepCpuOptimizer.CpuSnapshot snap = optimizer.currentSnapshot(); // GH-90000
        assertThat(snap.processCpuLoad()).isEqualTo(0.0); // GH-90000
        assertThat(snap.systemCpuLoad()).isEqualTo(0.0); // GH-90000
        assertThat(snap.throttleActive()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("CpuSnapshot.meetsTarget is true when load < 80% [GH-90000]")
    void snapshotMeetsTargetWhenLow() { // GH-90000
        AepCpuOptimizer.CpuSnapshot snap = new AepCpuOptimizer.CpuSnapshot( // GH-90000
                java.time.Instant.now(), 0.5, 0.6, 8, false); // GH-90000
        assertThat(snap.meetsTarget()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("CpuSnapshot.meetsTarget is false when load >= 80% [GH-90000]")
    void snapshotDoesNotMeetTargetWhenHigh() { // GH-90000
        AepCpuOptimizer.CpuSnapshot snap = new AepCpuOptimizer.CpuSnapshot( // GH-90000
                java.time.Instant.now(), 0.85, 0.90, 8, true); // GH-90000
        assertThat(snap.meetsTarget()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("CpuSnapshot.processCpuPercent returns load * 100 [GH-90000]")
    void snapshotProcessCpuPercent() { // GH-90000
        AepCpuOptimizer.CpuSnapshot snap = new AepCpuOptimizer.CpuSnapshot( // GH-90000
                java.time.Instant.now(), 0.75, 0.8, 4, false); // GH-90000
        assertThat(snap.processCpuPercent()).isEqualTo(75.0); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects non-positive samplingIntervalMs [GH-90000]")
    void builderRejectsZeroInterval() { // GH-90000
        assertThatThrownBy(() -> AepCpuOptimizer.builder().samplingIntervalMs(0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects highCpuThreshold out of [0, 1] [GH-90000]")
    void builderRejectsInvalidThreshold() { // GH-90000
        assertThatThrownBy(() -> AepCpuOptimizer.builder().highCpuThreshold(1.1)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects non-positive consecutiveHighSamplesBeforeThrottle [GH-90000]")
    void builderRejectsZeroConsecutive() { // GH-90000
        assertThatThrownBy(() -> AepCpuOptimizer.builder().consecutiveHighSamplesBeforeThrottle(0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects null osBean [GH-90000]")
    void builderRejectsNullOsBean() { // GH-90000
        assertThatThrownBy(() -> AepCpuOptimizer.builder().osBean(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
