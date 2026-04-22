/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for predictive governance service.
 *
 * @doc.type class
 * @doc.purpose Unit tests for predictive governance
 * @doc.layer test
 */
@DisplayName("Predictive Governance Service Tests [GH-90000]")
class PredictiveGovernanceServiceTest {

    @Test
    @DisplayName("predicts incidents from error rate trends [GH-90000]")
    void predictsIncidentsFromErrorRate() { // GH-90000
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); // GH-90000
        String tenantId = "tenant-1";
        Instant now = Instant.now(); // GH-90000

        // Record increasing error rate
        for (int i = 0; i < 10; i++) { // GH-90000
            double errorRate = 0.05 + (i * 0.02); // Increasing from 5% to 23% // GH-90000
            service.recordMetric(tenantId, "error_rate", errorRate, now.minusSeconds((10 - i) * 60)); // GH-90000
        }

        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.predictIncidents(tenantId); // GH-90000

        assertThat(predictions).isNotEmpty(); // GH-90000
        assertThat(predictions.stream().anyMatch(p -> p.type().equals("high_error_rate [GH-90000]"))).isTrue();
    }

    @Test
    @DisplayName("predicts incidents from high latency [GH-90000]")
    void predictsIncidentsFromHighLatency() { // GH-90000
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); // GH-90000
        String tenantId = "tenant-1";
        Instant now = Instant.now(); // GH-90000

        // Record high latency
        for (int i = 0; i < 5; i++) { // GH-90000
            service.recordMetric(tenantId, "latency_ms", 6000.0, now.minusSeconds((5 - i) * 60)); // GH-90000
        }

        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.predictIncidents(tenantId); // GH-90000

        assertThat(predictions).isNotEmpty(); // GH-90000
        assertThat(predictions.stream().anyMatch(p -> p.type().equals("high_latency [GH-90000]"))).isTrue();
    }

    @Test
    @DisplayName("predicts incidents from throughput drop [GH-90000]")
    void predictsIncidentsFromThroughputDrop() { // GH-90000
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); // GH-90000
        String tenantId = "tenant-1";
        Instant now = Instant.now(); // GH-90000

        // Record throughput drop
        for (int i = 0; i < 10; i++) { // GH-90000
            double throughput = 1000.0 - (i * 100); // Dropping from 1000 to 100 // GH-90000
            service.recordMetric(tenantId, "throughput_per_second", throughput, now.minusSeconds((10 - i) * 60)); // GH-90000
        }

        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.predictIncidents(tenantId); // GH-90000

        assertThat(predictions).isNotEmpty(); // GH-90000
        assertThat(predictions.stream().anyMatch(p -> p.type().equals("throughput_drop [GH-90000]"))).isTrue();
    }

    @Test
    @DisplayName("predicts incidents from resource exhaustion [GH-90000]")
    void predictsIncidentsFromResourceExhaustion() { // GH-90000
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); // GH-90000
        String tenantId = "tenant-1";
        Instant now = Instant.now(); // GH-90000

        // Record high CPU and memory
        for (int i = 0; i < 5; i++) { // GH-90000
            service.recordMetric(tenantId, "cpu_usage_percent", 95.0, now.minusSeconds((5 - i) * 60)); // GH-90000
            service.recordMetric(tenantId, "memory_usage_percent", 92.0, now.minusSeconds((5 - i) * 60)); // GH-90000
        }

        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.predictIncidents(tenantId); // GH-90000

        assertThat(predictions).isNotEmpty(); // GH-90000
        assertThat(predictions.stream().anyMatch(p -> p.type().equals("resource_exhaustion [GH-90000]"))).isTrue();
    }

    @Test
    @DisplayName("filters predictions by threshold [GH-90000]")
    void filtersPredictionsByThreshold() { // GH-90000
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.9, 30, 100); // GH-90000
        String tenantId = "tenant-1";
        Instant now = Instant.now(); // GH-90000

        // Record metrics that would generate low-probability prediction
        for (int i = 0; i < 5; i++) { // GH-90000
            service.recordMetric(tenantId, "error_rate", 0.08, now.minusSeconds((5 - i) * 60)); // GH-90000
        }

        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.predictIncidents(tenantId); // GH-90000

        // With high threshold (0.9), should not predict // GH-90000
        assertThat(predictions).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("retrieves predictions for tenant [GH-90000]")
    void retrievesPredictionsForTenant() { // GH-90000
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); // GH-90000
        String tenantId = "tenant-1";
        Instant now = Instant.now(); // GH-90000

        for (int i = 0; i < 10; i++) { // GH-90000
            double errorRate = 0.05 + (i * 0.02); // GH-90000
            service.recordMetric(tenantId, "error_rate", errorRate, now.minusSeconds((10 - i) * 60)); // GH-90000
        }

        service.predictIncidents(tenantId); // GH-90000

        List<PredictiveGovernanceService.IncidentPrediction> retrieved = service.getPredictions(tenantId); // GH-90000

        assertThat(retrieved).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("retrieves high-severity predictions [GH-90000]")
    void retrievesHighSeverityPredictions() { // GH-90000
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); // GH-90000
        String tenantId = "tenant-1";
        Instant now = Instant.now(); // GH-90000

        // Record metrics to generate high-probability prediction
        for (int i = 0; i < 10; i++) { // GH-90000
            double errorRate = 0.05 + (i * 0.03); // GH-90000
            service.recordMetric(tenantId, "error_rate", errorRate, now.minusSeconds((10 - i) * 60)); // GH-90000
        }

        service.predictIncidents(tenantId); // GH-90000

        List<PredictiveGovernanceService.IncidentPrediction> highSeverity = 
            service.getHighSeverityPredictions(tenantId); // GH-90000

        assertThat(highSeverity).isNotEmpty(); // GH-90000
        assertThat(highSeverity.stream().allMatch(p -> p.probability() > 0.8)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("clears tenant data [GH-90000]")
    void clearsTenantData() { // GH-90000
        PredictiveGovernanceService service = new PredictiveGovernanceService(); // GH-90000
        String tenantId = "tenant-1";

        service.recordMetric(tenantId, "error_rate", 0.1, Instant.now()); // GH-90000
        service.predictIncidents(tenantId); // GH-90000

        assertThat(service.getPredictions(tenantId)).isNotEmpty(); // GH-90000

        service.clearTenant(tenantId); // GH-90000

        assertThat(service.getPredictions(tenantId)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("clears all data [GH-90000]")
    void clearsAllData() { // GH-90000
        PredictiveGovernanceService service = new PredictiveGovernanceService(); // GH-90000

        service.recordMetric("tenant-1", "error_rate", 0.1, Instant.now()); // GH-90000
        service.recordMetric("tenant-2", "error_rate", 0.1, Instant.now()); // GH-90000
        service.predictIncidents("tenant-1 [GH-90000]");
        service.predictIncidents("tenant-2 [GH-90000]");

        service.clearAll(); // GH-90000

        assertThat(service.getPredictions("tenant-1 [GH-90000]")).isEmpty();
        assertThat(service.getPredictions("tenant-2 [GH-90000]")).isEmpty();
    }

    @Test
    @DisplayName("prunes old predictions [GH-90000]")
    void prunesOldPredictions() { // GH-90000
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); // GH-90000
        String tenantId = "tenant-1";

        // Record metrics and predict
        service.recordMetric(tenantId, "error_rate", 0.2, Instant.now()); // GH-90000
        service.predictIncidents(tenantId); // GH-90000

        // Predictions should be recent
        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.getPredictions(tenantId); // GH-90000
        assertThat(predictions).allMatch(p -> p.predictedTime().isAfter(Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS))); // GH-90000
    }
}
