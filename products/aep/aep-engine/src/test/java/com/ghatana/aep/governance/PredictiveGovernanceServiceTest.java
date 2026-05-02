/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("Predictive Governance Service Tests")
class PredictiveGovernanceServiceTest {

    @Test
    @DisplayName("predicts incidents from error rate trends")
    void predictsIncidentsFromErrorRate() { 
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); 
        String tenantId = "tenant-1";
        Instant now = Instant.now(); 

        // Record increasing error rate
        for (int i = 0; i < 10; i++) { 
            double errorRate = 0.05 + (i * 0.02); // Increasing from 5% to 23% 
            service.recordMetric(tenantId, "error_rate", errorRate, now.minusSeconds((10 - i) * 60)); 
        }

        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.predictIncidents(tenantId); 

        assertThat(predictions).isNotEmpty(); 
        assertThat(predictions.stream().anyMatch(p -> p.type().equals("high_error_rate"))).isTrue();
    }

    @Test
    @DisplayName("predicts incidents from high latency")
    void predictsIncidentsFromHighLatency() { 
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); 
        String tenantId = "tenant-1";
        Instant now = Instant.now(); 

        // Record high latency
        for (int i = 0; i < 5; i++) { 
            service.recordMetric(tenantId, "latency_ms", 6000.0, now.minusSeconds((5 - i) * 60)); 
        }

        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.predictIncidents(tenantId); 

        assertThat(predictions).isNotEmpty(); 
        assertThat(predictions.stream().anyMatch(p -> p.type().equals("high_latency"))).isTrue();
    }

    @Test
    @DisplayName("predicts incidents from throughput drop")
    void predictsIncidentsFromThroughputDrop() { 
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); 
        String tenantId = "tenant-1";
        Instant now = Instant.now(); 

        // Record throughput drop
        for (int i = 0; i < 10; i++) { 
            double throughput = 1000.0 - (i * 100); // Dropping from 1000 to 100 
            service.recordMetric(tenantId, "throughput_per_second", throughput, now.minusSeconds((10 - i) * 60)); 
        }

        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.predictIncidents(tenantId); 

        assertThat(predictions).isNotEmpty(); 
        assertThat(predictions.stream().anyMatch(p -> p.type().equals("throughput_drop"))).isTrue();
    }

    @Test
    @DisplayName("predicts incidents from resource exhaustion")
    void predictsIncidentsFromResourceExhaustion() { 
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); 
        String tenantId = "tenant-1";
        Instant now = Instant.now(); 

        // Record high CPU and memory
        for (int i = 0; i < 5; i++) { 
            service.recordMetric(tenantId, "cpu_usage_percent", 95.0, now.minusSeconds((5 - i) * 60)); 
            service.recordMetric(tenantId, "memory_usage_percent", 92.0, now.minusSeconds((5 - i) * 60)); 
        }

        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.predictIncidents(tenantId); 

        assertThat(predictions).isNotEmpty(); 
        assertThat(predictions.stream().anyMatch(p -> p.type().equals("resource_exhaustion"))).isTrue();
    }

    @Test
    @DisplayName("filters predictions by threshold")
    void filtersPredictionsByThreshold() { 
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.9, 30, 100); 
        String tenantId = "tenant-1";
        Instant now = Instant.now(); 

        // Record metrics that would generate low-probability prediction
        for (int i = 0; i < 5; i++) { 
            service.recordMetric(tenantId, "error_rate", 0.08, now.minusSeconds((5 - i) * 60)); 
        }

        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.predictIncidents(tenantId); 

        // With high threshold (0.9), should not predict 
        assertThat(predictions).isEmpty(); 
    }

    @Test
    @DisplayName("retrieves predictions for tenant")
    void retrievesPredictionsForTenant() { 
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); 
        String tenantId = "tenant-1";
        Instant now = Instant.now(); 

        for (int i = 0; i < 10; i++) { 
            double errorRate = 0.05 + (i * 0.02); 
            service.recordMetric(tenantId, "error_rate", errorRate, now.minusSeconds((10 - i) * 60)); 
        }

        service.predictIncidents(tenantId); 

        List<PredictiveGovernanceService.IncidentPrediction> retrieved = service.getPredictions(tenantId); 

        assertThat(retrieved).isNotEmpty(); 
    }

    @Test
    @DisplayName("retrieves high-severity predictions")
    void retrievesHighSeverityPredictions() { 
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); 
        String tenantId = "tenant-1";
        Instant now = Instant.now(); 

        // Record metrics to generate high-probability prediction
        for (int i = 0; i < 10; i++) { 
            double errorRate = 0.05 + (i * 0.03); 
            service.recordMetric(tenantId, "error_rate", errorRate, now.minusSeconds((10 - i) * 60)); 
        }

        service.predictIncidents(tenantId); 

        List<PredictiveGovernanceService.IncidentPrediction> highSeverity = 
            service.getHighSeverityPredictions(tenantId); 

        assertThat(highSeverity).isNotEmpty(); 
        assertThat(highSeverity.stream().allMatch(p -> p.probability() > 0.8)).isTrue(); 
    }

    @Test
    @DisplayName("clears tenant data")
    void clearsTenantData() { 
        PredictiveGovernanceService service = new PredictiveGovernanceService(); 
        String tenantId = "tenant-1";

        service.recordMetric(tenantId, "error_rate", 0.1, Instant.now()); 
        service.predictIncidents(tenantId); 

        assertThat(service.getPredictions(tenantId)).isNotEmpty(); 

        service.clearTenant(tenantId); 

        assertThat(service.getPredictions(tenantId)).isEmpty(); 
    }

    @Test
    @DisplayName("clears all data")
    void clearsAllData() { 
        PredictiveGovernanceService service = new PredictiveGovernanceService(); 

        service.recordMetric("tenant-1", "error_rate", 0.1, Instant.now()); 
        service.recordMetric("tenant-2", "error_rate", 0.1, Instant.now()); 
        service.predictIncidents("tenant-1");
        service.predictIncidents("tenant-2");

        service.clearAll(); 

        assertThat(service.getPredictions("tenant-1")).isEmpty();
        assertThat(service.getPredictions("tenant-2")).isEmpty();
    }

    @Test
    @DisplayName("prunes old predictions")
    void prunesOldPredictions() { 
        PredictiveGovernanceService service = new PredictiveGovernanceService(0.5, 30, 100); 
        String tenantId = "tenant-1";

        // Record metrics and predict
        service.recordMetric(tenantId, "error_rate", 0.2, Instant.now()); 
        service.predictIncidents(tenantId); 

        // Predictions should be recent
        List<PredictiveGovernanceService.IncidentPrediction> predictions = service.getPredictions(tenantId); 
        assertThat(predictions).allMatch(p -> p.predictedTime().isAfter(Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS))); 
    }
}
