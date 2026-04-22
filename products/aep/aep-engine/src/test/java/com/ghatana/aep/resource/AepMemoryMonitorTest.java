/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Unit tests for {@link AepMemoryMonitor} (AEP-005.1). // GH-90000
 */
@DisplayName("AepMemoryMonitor — AEP-005.1 [GH-90000]")
class AepMemoryMonitorTest {

    private MemoryMXBean mockBean;
    private AepMemoryMonitor monitor;

    @BeforeEach
    void setUp() { // GH-90000
        mockBean = mock(MemoryMXBean.class); // GH-90000
        // Heap: 800 MB used, 1024 MB max, 1024 MB committed
        when(mockBean.getHeapMemoryUsage()) // GH-90000
                .thenReturn(new MemoryUsage(0, 800 * 1024 * 1024L, 1024 * 1024 * 1024L, 1024 * 1024 * 1024L)); // GH-90000
        when(mockBean.getNonHeapMemoryUsage()) // GH-90000
                .thenReturn(new MemoryUsage(0, 100 * 1024 * 1024L, 256 * 1024 * 1024L, -1)); // GH-90000

        monitor = AepMemoryMonitor.builder() // GH-90000
                .memoryBean(mockBean) // GH-90000
                .samplingIntervalMs(100) // GH-90000
                .heapWarningThreshold(0.80) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("Initial snapshot is empty sentinel [GH-90000]")
    void initialSnapshotIsEmpty() { // GH-90000
        assertThat(monitor.currentSnapshot().isEmpty()).isTrue(); // GH-90000
        assertThat(monitor.sampleCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("sample() updates snapshot with correct values [GH-90000]")
    void sampleUpdatesSnapshot() { // GH-90000
        monitor.sample(); // GH-90000

        AepMemoryMonitor.MemorySnapshot snap = monitor.currentSnapshot(); // GH-90000
        assertThat(snap.isEmpty()).isFalse(); // GH-90000
        assertThat(snap.heapUsedBytes()).isEqualTo(800 * 1024 * 1024L); // GH-90000
        assertThat(snap.heapMaxBytes()).isEqualTo(1024 * 1024 * 1024L); // GH-90000
        assertThat(snap.heapRatio()).isEqualTo(800.0 / 1024.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
        assertThat(monitor.sampleCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("heapUsedMb and heapMaxMb helpers work correctly [GH-90000]")
    void heapMbHelpers() { // GH-90000
        monitor.sample(); // GH-90000
        AepMemoryMonitor.MemorySnapshot snap = monitor.currentSnapshot(); // GH-90000
        assertThat(snap.heapUsedMb()).isCloseTo(800.0, org.assertj.core.data.Offset.offset(0.01)); // GH-90000
        assertThat(snap.heapMaxMb()).isCloseTo(1024.0, org.assertj.core.data.Offset.offset(0.01)); // GH-90000
    }

    @Test
    @DisplayName("sample() at >80% heap logs warning (no exception) [GH-90000]")
    void highHeapRatioDoesNotThrow() { // GH-90000
        // heap ratio = 800/1024 ≈ 78% → just below 80% threshold; use max=900MB to trigger warning
        when(mockBean.getHeapMemoryUsage()) // GH-90000
                .thenReturn(new MemoryUsage(0, 900 * 1024 * 1024L, 1000 * 1024 * 1024L, 1000 * 1024 * 1024L)); // GH-90000

        // Should complete without exception
        monitor.sample(); // GH-90000
        assertThat(monitor.currentSnapshot().heapRatio()).isGreaterThan(0.80); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects samplingIntervalMs <= 0 [GH-90000]")
    void builderRejectsNonPositiveInterval() { // GH-90000
        assertThatThrownBy(() -> AepMemoryMonitor.builder().samplingIntervalMs(0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects heapWarningThreshold out of range [GH-90000]")
    void builderRejectsInvalidThreshold() { // GH-90000
        assertThatThrownBy(() -> AepMemoryMonitor.builder().heapWarningThreshold(1.5)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
}
