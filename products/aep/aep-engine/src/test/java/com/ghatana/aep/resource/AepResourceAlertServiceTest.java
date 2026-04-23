/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepResourceAlertService} (AEP-005.4). // GH-90000
 */
@DisplayName("AepResourceAlertService — AEP-005.4")
class AepResourceAlertServiceTest {

    private AepResourceAlertService service;
    private List<AepResourceAlertService.ResourceAlert> receivedAlerts;

    @BeforeEach
    void setUp() { // GH-90000
        receivedAlerts = new ArrayList<>(); // GH-90000
        service = AepResourceAlertService.builder() // GH-90000
                .heapAlertThreshold(0.85) // GH-90000
                .cpuAlertThreshold(0.80) // GH-90000
                .build(); // GH-90000
        service.addListener(alert -> receivedAlerts.add(alert)); // GH-90000
    }

    @Test
    @DisplayName("No alert fired when resources are below thresholds")
    void noAlertBelowThresholds() { // GH-90000
        AepMemoryMonitor.MemorySnapshot memSnap = buildMemSnap(0.70); // GH-90000
        AepCpuOptimizer.CpuSnapshot cpuSnap = buildCpuSnap(0.50); // GH-90000

        service.evaluate(memSnap, cpuSnap); // GH-90000

        assertThat(receivedAlerts).isEmpty(); // GH-90000
        assertThat(service.isHeapAlertActive()).isFalse(); // GH-90000
        assertThat(service.isCpuAlertActive()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Heap alert fires when heap exceeds threshold")
    void heapAlertFires() { // GH-90000
        service.evaluate(buildMemSnap(0.90), buildCpuSnap(0.30)); // GH-90000

        assertThat(receivedAlerts).hasSize(1); // GH-90000
        assertThat(receivedAlerts.get(0).type()) // GH-90000
                .isEqualTo(AepResourceAlertService.AlertType.HIGH_HEAP_USAGE); // GH-90000
        assertThat(service.isHeapAlertActive()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("CPU alert fires when CPU exceeds threshold")
    void cpuAlertFires() { // GH-90000
        service.evaluate(buildMemSnap(0.50), buildCpuSnap(0.85)); // GH-90000

        assertThat(receivedAlerts).hasSize(1); // GH-90000
        assertThat(receivedAlerts.get(0).type()) // GH-90000
                .isEqualTo(AepResourceAlertService.AlertType.HIGH_CPU_USAGE); // GH-90000
        assertThat(service.isCpuAlertActive()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Alert fires only once per breach window (no spam)")
    void alertFiresOncePerBreachWindow() { // GH-90000
        service.evaluate(buildMemSnap(0.90), buildCpuSnap(0.30)); // GH-90000
        service.evaluate(buildMemSnap(0.92), buildCpuSnap(0.30)); // still high // GH-90000
        service.evaluate(buildMemSnap(0.95), buildCpuSnap(0.30)); // still high // GH-90000

        // Heap alert should fire only once
        long heapAlerts = receivedAlerts.stream() // GH-90000
                .filter(a -> a.type() == AepResourceAlertService.AlertType.HIGH_HEAP_USAGE) // GH-90000
                .count(); // GH-90000
        assertThat(heapAlerts).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Alert clears when resource drops below threshold")
    void alertClears() { // GH-90000
        service.evaluate(buildMemSnap(0.90), buildCpuSnap(0.30)); // fire alert // GH-90000
        service.evaluate(buildMemSnap(0.70), buildCpuSnap(0.30)); // clear // GH-90000

        assertThat(service.isHeapAlertActive()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Alert refires after clearing if threshold is breached again")
    void alertRefiresAfterClear() { // GH-90000
        service.evaluate(buildMemSnap(0.90), buildCpuSnap(0.30)); // fire // GH-90000
        service.evaluate(buildMemSnap(0.70), buildCpuSnap(0.30)); // clear // GH-90000
        service.evaluate(buildMemSnap(0.92), buildCpuSnap(0.30)); // re-fire // GH-90000

        long heapAlerts = receivedAlerts.stream() // GH-90000
                .filter(a -> a.type() == AepResourceAlertService.AlertType.HIGH_HEAP_USAGE) // GH-90000
                .count(); // GH-90000
        assertThat(heapAlerts).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("Alert history includes all fired alerts")
    void alertHistoryIsAccumulated() { // GH-90000
        service.evaluate(buildMemSnap(0.90), buildCpuSnap(0.90)); // two alerts // GH-90000
        assertThat(service.alertHistory()).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("Null memSnap throws NullPointerException")
    void nullMemSnapThrows() { // GH-90000
        assertThatThrownBy(() -> service.evaluate(null, buildCpuSnap(0.5))) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects threshold out of range")
    void builderRejectsOutOfRangeThreshold() { // GH-90000
        assertThatThrownBy(() -> AepResourceAlertService.builder().heapAlertThreshold(1.5)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static AepMemoryMonitor.MemorySnapshot buildMemSnap(double heapRatio) { // GH-90000
        long max = 1024L * 1024 * 1024; // 1 GB
        long used = (long) (max * heapRatio); // GH-90000
        return new AepMemoryMonitor.MemorySnapshot( // GH-90000
                Instant.now(), used, max, max, 0, -1, heapRatio, 0, 0); // GH-90000
    }

    private static AepCpuOptimizer.CpuSnapshot buildCpuSnap(double processCpu) { // GH-90000
        return new AepCpuOptimizer.CpuSnapshot(Instant.now(), processCpu, processCpu, 4, false); // GH-90000
    }
}
