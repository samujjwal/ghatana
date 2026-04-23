/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.taxonomy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link CapabilityTaxonomyScanner}.
 *
 * P3-20: Verify capability taxonomy inference from agent configurations.
 */
class CapabilityTaxonomyScannerTest {

    private final CapabilityTaxonomyScanner scanner = new CapabilityTaxonomyScanner(); // GH-90000

    @Test
    void shouldInferMLCapabilityFromConfig() { // GH-90000
        Map<String, Object> config = Map.of( // GH-90000
            "model", "tensorflow",
            "task", "classification"
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-1", "ML Agent", "deliberative", config); // GH-90000
        
        assertTrue(result.inferredCapabilities().contains("ML_INFERENCE"));
        assertTrue(result.confidence() > 0.5); // GH-90000
    }

    @Test
    void shouldInferNLPCapabilityFromConfig() { // GH-90000
        Map<String, Object> config = Map.of( // GH-90000
            "model", "gpt-4",
            "task", "sentiment_analysis"
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-2", "NLP Agent", "reactive", config); // GH-90000
        
        assertTrue(result.inferredCapabilities().contains("NATURAL_LANGUAGE"));
    }

    @Test
    void shouldInferVisionCapabilityFromConfig() { // GH-90000
        Map<String, Object> config = Map.of( // GH-90000
            "model", "yolo",
            "task", "object_detection"
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-3", "Vision Agent", "reactive", config); // GH-90000
        
        assertTrue(result.inferredCapabilities().contains("VISION"));
    }

    @Test
    void shouldInferDataProcessingFromReactiveType() { // GH-90000
        Map<String, Object> config = Map.of( // GH-90000
            "stream", "kafka"
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-4", "Stream Agent", "reactive", config); // GH-90000
        
        assertTrue(result.inferredCapabilities().contains("DATA_PROCESSING"));
    }

    @Test
    void shouldInferWorkflowFromDeliberativeType() { // GH-90000
        Map<String, Object> config = Map.of(); // GH-90000
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-5", "Planning Agent", "deliberative", config); // GH-90000
        
        assertTrue(result.inferredCapabilities().contains("WORKFLOW"));
    }

    @Test
    void shouldInferIntegrationCapabilityFromAPIConfig() { // GH-90000
        Map<String, Object> config = Map.of( // GH-90000
            "api_endpoint", "https://api.example.com",
            "auth", "bearer_token"
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-6", "API Agent", "reactive", config); // GH-90000
        
        assertTrue(result.inferredCapabilities().contains("INTEGRATION"));
    }

    @Test
    void shouldInferSecurityCapabilityFromConfig() { // GH-90000
        Map<String, Object> config = Map.of( // GH-90000
            "scan_pii", true,
            "validate_input", true
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-7", "Security Agent", "reactive", config); // GH-90000
        
        assertTrue(result.inferredCapabilities().contains("SECURITY"));
    }

    @Test
    void shouldReturnLowConfidenceForMinimalConfig() { // GH-90000
        Map<String, Object> config = Map.of("name", "test"); // GH-90000
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-8", "Test Agent", "reactive", config); // GH-90000
        
        // Should infer from type but have low confidence
        assertFalse(result.inferredCapabilities().isEmpty()); // GH-90000
        assertTrue(result.confidence() < 0.5); // GH-90000
    }

    @Test
    void shouldScanMultipleAgents() { // GH-90000
        List<Map<String, Object>> agents = List.of( // GH-90000
            Map.of("id", "agent-1", "name", "ML Agent", "type", "deliberative",  // GH-90000
                  "config", Map.of("model", "tensorflow")), // GH-90000
            Map.of("id", "agent-2", "name", "NLP Agent", "type", "reactive", // GH-90000
                  "config", Map.of("model", "gpt-4")), // GH-90000
            Map.of("id", "agent-3", "name", "Stream Agent", "type", "reactive", // GH-90000
                  "config", Map.of("stream", "kafka")) // GH-90000
        );
        
        List<CapabilityTaxonomyScanner.TaxonomyResult> results = scanner.scanAgents(agents); // GH-90000
        
        assertEquals(3, results.size()); // GH-90000
        assertTrue(results.get(0).inferredCapabilities().contains("ML_INFERENCE"));
        assertTrue(results.get(1).inferredCapabilities().contains("NATURAL_LANGUAGE"));
        assertTrue(results.get(2).inferredCapabilities().contains("DATA_PROCESSING"));
    }

    @Test
    void shouldSummarizeCapabilitiesAcrossAgents() { // GH-90000
        List<Map<String, Object>> agents = List.of( // GH-90000
            Map.of("id", "agent-1", "name", "ML Agent", "type", "deliberative",  // GH-90000
                  "config", Map.of("model", "tensorflow")), // GH-90000
            Map.of("id", "agent-2", "name", "NLP Agent", "type", "reactive", // GH-90000
                  "config", Map.of("model", "gpt-4")) // GH-90000
        );
        
        List<CapabilityTaxonomyScanner.TaxonomyResult> results = scanner.scanAgents(agents); // GH-90000
        Map<String, Object> summary = scanner.summarizeCapabilities(results); // GH-90000
        
        assertEquals(2, summary.get("totalAgents"));
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> capabilityCounts = (Map<String, Integer>) summary.get("capabilityCounts");
        assertNotNull(capabilityCounts); // GH-90000
        assertTrue(capabilityCounts.containsKey("ML_INFERENCE"));
        assertTrue(capabilityCounts.containsKey("NATURAL_LANGUAGE"));
    }
}
