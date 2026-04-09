/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Unit tests for {@link AepCpuOptimizer} (AEP-005.2).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepCpuOptimizer — AEP-005.2")
class AepCpuOptimizerTest {

    @Mock
    private OperatingSystemMXBean osBean;

    private AepCpuOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = AepCpuOptimizer.builder()
                .osBean(osBean)
                .samplingIntervalMs(1_000)
                .highCpuThreshold(0.80)
                .consecutiveHighSamplesBeforeThrottle(3)
                .build();
    }

    @Test
    @DisplayName("Initial state: throttle inactive, sample count = 0")
    void initialState() {
        assertThat(optimizer.isThrottleActive()).isFalse();
        assertThat(optimizer.sampleCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("currentSnapshot returns EMPTY before any sample")
    void currentSnapshotEmptyBeforeSamples() {
        AepCpuOptimizer.CpuSnapshot snap = optimizer.currentSnapshot();
        assertThat(snap.processCpuLoad()).isEqualTo(0.0);
        assertThat(snap.systemCpuLoad()).isEqualTo(0.0);
        assertThat(snap.throttleActive()).isFalse();
    }

    @Test
    @DisplayName("CpuSnapshot.meetsTarget is true when load < 80%")
    void snapshotMeetsTargetWhenLow() {
        AepCpuOptimizer.CpuSnapshot snap = new AepCpuOptimizer.CpuSnapshot(
                java.time.Instant.now(), 0.5, 0.6, 8, false);
        assertThat(snap.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("CpuSnapshot.meetsTarget is false when load >= 80%")
    void snapshotDoesNotMeetTargetWhenHigh() {
        AepCpuOptimizer.CpuSnapshot snap = new AepCpuOptimizer.CpuSnapshot(
                java.time.Instant.now(), 0.85, 0.90, 8, true);
        assertThat(snap.meetsTarget()).isFalse();
    }

    @Test
    @DisplayName("CpuSnapshot.processCpuPercent returns load * 100")
    void snapshotProcessCpuPercent() {
        AepCpuOptimizer.CpuSnapshot snap = new AepCpuOptimizer.CpuSnapshot(
                java.time.Instant.now(), 0.75, 0.8, 4, false);
        assertThat(snap.processCpuPercent()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("Builder rejects non-positive samplingIntervalMs")
    void builderRejectsZeroInterval() {
        assertThatThrownBy(() -> AepCpuOptimizer.builder().samplingIntervalMs(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder rejects highCpuThreshold out of [0, 1]")
    void builderRejectsInvalidThreshold() {
        assertThatThrownBy(() -> AepCpuOptimizer.builder().highCpuThreshold(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder rejects non-positive consecutiveHighSamplesBeforeThrottle")
    void builderRejectsZeroConsecutive() {
        assertThatThrownBy(() -> AepCpuOptimizer.builder().consecutiveHighSamplesBeforeThrottle(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder rejects null osBean")
    void builderRejectsNullOsBean() {
        assertThatThrownBy(() -> AepCpuOptimizer.builder().osBean(null))
                .isInstanceOf(NullPointerException.class);
    }
}
