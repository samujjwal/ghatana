/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    private final CapabilityTaxonomyScanner scanner = new CapabilityTaxonomyScanner(); 

    @Test
    void shouldInferMLCapabilityFromConfig() { 
        Map<String, Object> config = Map.of( 
            "model", "tensorflow",
            "task", "classification"
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-1", "ML Agent", "deliberative", config); 
        
        assertTrue(result.inferredCapabilities().contains("ML_INFERENCE"));
        assertTrue(result.confidence() > 0.5); 
    }

    @Test
    void shouldInferNLPCapabilityFromConfig() { 
        Map<String, Object> config = Map.of( 
            "model", "gpt-4",
            "task", "sentiment_analysis"
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-2", "NLP Agent", "reactive", config); 
        
        assertTrue(result.inferredCapabilities().contains("NATURAL_LANGUAGE"));
    }

    @Test
    void shouldInferVisionCapabilityFromConfig() { 
        Map<String, Object> config = Map.of( 
            "model", "yolo",
            "task", "object_detection"
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-3", "Vision Agent", "reactive", config); 
        
        assertTrue(result.inferredCapabilities().contains("VISION"));
    }

    @Test
    void shouldInferDataProcessingFromReactiveType() { 
        Map<String, Object> config = Map.of( 
            "stream", "kafka"
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-4", "Stream Agent", "reactive", config); 
        
        assertTrue(result.inferredCapabilities().contains("DATA_PROCESSING"));
    }

    @Test
    void shouldInferWorkflowFromDeliberativeType() { 
        Map<String, Object> config = Map.of(); 
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-5", "Planning Agent", "deliberative", config); 
        
        assertTrue(result.inferredCapabilities().contains("WORKFLOW"));
    }

    @Test
    void shouldInferIntegrationCapabilityFromAPIConfig() { 
        Map<String, Object> config = Map.of( 
            "api_endpoint", "https://api.example.com",
            "auth", "bearer_token"
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-6", "API Agent", "reactive", config); 
        
        assertTrue(result.inferredCapabilities().contains("INTEGRATION"));
    }

    @Test
    void shouldInferSecurityCapabilityFromConfig() { 
        Map<String, Object> config = Map.of( 
            "scan_pii", true,
            "validate_input", true
        );
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-7", "Security Agent", "reactive", config); 
        
        assertTrue(result.inferredCapabilities().contains("SECURITY"));
    }

    @Test
    void shouldReturnLowConfidenceForMinimalConfig() { 
        Map<String, Object> config = Map.of("name", "test"); 
        
        CapabilityTaxonomyScanner.TaxonomyResult result = 
            scanner.scanAgent("agent-8", "Test Agent", "reactive", config); 
        
        // Should infer from type but have low confidence
        assertFalse(result.inferredCapabilities().isEmpty()); 
        assertTrue(result.confidence() < 0.5); 
    }

    @Test
    void shouldScanMultipleAgents() { 
        List<Map<String, Object>> agents = List.of( 
            Map.of("id", "agent-1", "name", "ML Agent", "type", "deliberative",  
                  "config", Map.of("model", "tensorflow")), 
            Map.of("id", "agent-2", "name", "NLP Agent", "type", "reactive", 
                  "config", Map.of("model", "gpt-4")), 
            Map.of("id", "agent-3", "name", "Stream Agent", "type", "reactive", 
                  "config", Map.of("stream", "kafka")) 
        );
        
        List<CapabilityTaxonomyScanner.TaxonomyResult> results = scanner.scanAgents(agents); 
        
        assertEquals(3, results.size()); 
        assertTrue(results.get(0).inferredCapabilities().contains("ML_INFERENCE"));
        assertTrue(results.get(1).inferredCapabilities().contains("NATURAL_LANGUAGE"));
        assertTrue(results.get(2).inferredCapabilities().contains("DATA_PROCESSING"));
    }

    @Test
    void shouldSummarizeCapabilitiesAcrossAgents() { 
        List<Map<String, Object>> agents = List.of( 
            Map.of("id", "agent-1", "name", "ML Agent", "type", "deliberative",  
                  "config", Map.of("model", "tensorflow")), 
            Map.of("id", "agent-2", "name", "NLP Agent", "type", "reactive", 
                  "config", Map.of("model", "gpt-4")) 
        );
        
        List<CapabilityTaxonomyScanner.TaxonomyResult> results = scanner.scanAgents(agents); 
        Map<String, Object> summary = scanner.summarizeCapabilities(results); 
        
        assertEquals(2, summary.get("totalAgents"));
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> capabilityCounts = (Map<String, Integer>) summary.get("capabilityCounts");
        assertNotNull(capabilityCounts); 
        assertTrue(capabilityCounts.containsKey("ML_INFERENCE"));
        assertTrue(capabilityCounts.containsKey("NATURAL_LANGUAGE"));
    }
}
