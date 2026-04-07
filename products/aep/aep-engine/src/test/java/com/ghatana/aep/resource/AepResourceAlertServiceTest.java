/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Unit tests for {@link AepResourceAlertService} (AEP-005.4).
 */
@DisplayName("AepResourceAlertService — AEP-005.4")
class AepResourceAlertServiceTest {

    private AepResourceAlertService service;
    private List<AepResourceAlertService.ResourceAlert> receivedAlerts;

    @BeforeEach
    void setUp() {
        receivedAlerts = new ArrayList<>();
        service = AepResourceAlertService.builder()
                .heapAlertThreshold(0.85)
                .cpuAlertThreshold(0.80)
                .build();
        service.addListener(alert -> receivedAlerts.add(alert));
    }

    @Test
    @DisplayName("No alert fired when resources are below thresholds")
    void noAlertBelowThresholds() {
        AepMemoryMonitor.MemorySnapshot memSnap = buildMemSnap(0.70);
        AepCpuOptimizer.CpuSnapshot cpuSnap = buildCpuSnap(0.50);

        service.evaluate(memSnap, cpuSnap);

        assertThat(receivedAlerts).isEmpty();
        assertThat(service.isHeapAlertActive()).isFalse();
        assertThat(service.isCpuAlertActive()).isFalse();
    }

    @Test
    @DisplayName("Heap alert fires when heap exceeds threshold")
    void heapAlertFires() {
        service.evaluate(buildMemSnap(0.90), buildCpuSnap(0.30));

        assertThat(receivedAlerts).hasSize(1);
        assertThat(receivedAlerts.get(0).type())
                .isEqualTo(AepResourceAlertService.AlertType.HIGH_HEAP_USAGE);
        assertThat(service.isHeapAlertActive()).isTrue();
    }

    @Test
    @DisplayName("CPU alert fires when CPU exceeds threshold")
    void cpuAlertFires() {
        service.evaluate(buildMemSnap(0.50), buildCpuSnap(0.85));

        assertThat(receivedAlerts).hasSize(1);
        assertThat(receivedAlerts.get(0).type())
                .isEqualTo(AepResourceAlertService.AlertType.HIGH_CPU_USAGE);
        assertThat(service.isCpuAlertActive()).isTrue();
    }

    @Test
    @DisplayName("Alert fires only once per breach window (no spam)")
    void alertFiresOncePerBreachWindow() {
        service.evaluate(buildMemSnap(0.90), buildCpuSnap(0.30));
        service.evaluate(buildMemSnap(0.92), buildCpuSnap(0.30)); // still high
        service.evaluate(buildMemSnap(0.95), buildCpuSnap(0.30)); // still high

        // Heap alert should fire only once
        long heapAlerts = receivedAlerts.stream()
                .filter(a -> a.type() == AepResourceAlertService.AlertType.HIGH_HEAP_USAGE)
                .count();
        assertThat(heapAlerts).isEqualTo(1);
    }

    @Test
    @DisplayName("Alert clears when resource drops below threshold")
    void alertClears() {
        service.evaluate(buildMemSnap(0.90), buildCpuSnap(0.30)); // fire alert
        service.evaluate(buildMemSnap(0.70), buildCpuSnap(0.30)); // clear

        assertThat(service.isHeapAlertActive()).isFalse();
    }

    @Test
    @DisplayName("Alert refires after clearing if threshold is breached again")
    void alertRefiresAfterClear() {
        service.evaluate(buildMemSnap(0.90), buildCpuSnap(0.30)); // fire
        service.evaluate(buildMemSnap(0.70), buildCpuSnap(0.30)); // clear
        service.evaluate(buildMemSnap(0.92), buildCpuSnap(0.30)); // re-fire

        long heapAlerts = receivedAlerts.stream()
                .filter(a -> a.type() == AepResourceAlertService.AlertType.HIGH_HEAP_USAGE)
                .count();
        assertThat(heapAlerts).isEqualTo(2);
    }

    @Test
    @DisplayName("Alert history includes all fired alerts")
    void alertHistoryIsAccumulated() {
        service.evaluate(buildMemSnap(0.90), buildCpuSnap(0.90)); // two alerts
        assertThat(service.alertHistory()).hasSize(2);
    }

    @Test
    @DisplayName("Null memSnap throws NullPointerException")
    void nullMemSnapThrows() {
        assertThatThrownBy(() -> service.evaluate(null, buildCpuSnap(0.5)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Builder rejects threshold out of range")
    void builderRejectsOutOfRangeThreshold() {
        assertThatThrownBy(() -> AepResourceAlertService.builder().heapAlertThreshold(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static AepMemoryMonitor.MemorySnapshot buildMemSnap(double heapRatio) {
        long max = 1024L * 1024 * 1024; // 1 GB
        long used = (long) (max * heapRatio);
        return new AepMemoryMonitor.MemorySnapshot(
                Instant.now(), used, max, max, 0, -1, heapRatio, 0, 0);
    }

    private static AepCpuOptimizer.CpuSnapshot buildCpuSnap(double processCpu) {
        return new AepCpuOptimizer.CpuSnapshot(Instant.now(), processCpu, processCpu, 4, false);
    }
}

