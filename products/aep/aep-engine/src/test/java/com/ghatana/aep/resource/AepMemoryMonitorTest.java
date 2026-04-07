/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AepMemoryMonitor} (AEP-005.1).
 */
@DisplayName("AepMemoryMonitor — AEP-005.1")
class AepMemoryMonitorTest {

    private MemoryMXBean mockBean;
    private AepMemoryMonitor monitor;

    @BeforeEach
    void setUp() {
        mockBean = mock(MemoryMXBean.class);
        // Heap: 800 MB used, 1024 MB max, 1024 MB committed
        when(mockBean.getHeapMemoryUsage())
                .thenReturn(new MemoryUsage(0, 800 * 1024 * 1024L, 1024 * 1024 * 1024L, 1024 * 1024 * 1024L));
        when(mockBean.getNonHeapMemoryUsage())
                .thenReturn(new MemoryUsage(0, 100 * 1024 * 1024L, 256 * 1024 * 1024L, -1));

        monitor = AepMemoryMonitor.builder()
                .memoryBean(mockBean)
                .samplingIntervalMs(100)
                .heapWarningThreshold(0.80)
                .build();
    }

    @Test
    @DisplayName("Initial snapshot is empty sentinel")
    void initialSnapshotIsEmpty() {
        assertThat(monitor.currentSnapshot().isEmpty()).isTrue();
        assertThat(monitor.sampleCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("sample() updates snapshot with correct values")
    void sampleUpdatesSnapshot() {
        monitor.sample();

        AepMemoryMonitor.MemorySnapshot snap = monitor.currentSnapshot();
        assertThat(snap.isEmpty()).isFalse();
        assertThat(snap.heapUsedBytes()).isEqualTo(800 * 1024 * 1024L);
        assertThat(snap.heapMaxBytes()).isEqualTo(1024 * 1024 * 1024L);
        assertThat(snap.heapRatio()).isEqualTo(800.0 / 1024.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(monitor.sampleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("heapUsedMb and heapMaxMb helpers work correctly")
    void heapMbHelpers() {
        monitor.sample();
        AepMemoryMonitor.MemorySnapshot snap = monitor.currentSnapshot();
        assertThat(snap.heapUsedMb()).isCloseTo(800.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(snap.heapMaxMb()).isCloseTo(1024.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("sample() at >80% heap logs warning (no exception)")
    void highHeapRatioDoesNotThrow() {
        // heap ratio = 800/1024 ≈ 78% → just below 80% threshold; use max=900MB to trigger warning
        when(mockBean.getHeapMemoryUsage())
                .thenReturn(new MemoryUsage(0, 900 * 1024 * 1024L, 1000 * 1024 * 1024L, 1000 * 1024 * 1024L));

        // Should complete without exception
        monitor.sample();
        assertThat(monitor.currentSnapshot().heapRatio()).isGreaterThan(0.80);
    }

    @Test
    @DisplayName("Builder rejects samplingIntervalMs <= 0")
    void builderRejectsNonPositiveInterval() {
        assertThatThrownBy(() -> AepMemoryMonitor.builder().samplingIntervalMs(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder rejects heapWarningThreshold out of range")
    void builderRejectsInvalidThreshold() {
        assertThatThrownBy(() -> AepMemoryMonitor.builder().heapWarningThreshold(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

