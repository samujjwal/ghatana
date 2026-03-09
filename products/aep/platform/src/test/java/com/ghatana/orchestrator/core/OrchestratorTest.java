package com.ghatana.orchestrator.core;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.ghatana.orchestrator.cache.PipelineCache;
import com.ghatana.orchestrator.client.AgentRegistryClient;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import com.ghatana.orchestrator.config.OrchestratorConfig;
import com.ghatana.orchestrator.loader.SpecFormatLoader;
import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import com.ghatana.platform.observability.MetricsCollector;

import io.activej.promise.Promise;

/**
 * Day 24 Implementation: Basic unit tests for Orchestrator
 */
class OrchestratorTest {

    @Mock private PipelineCache pipelineCache;
    @Mock private AgentRegistryClient agentRegistryClient;
    @Mock private PipelineRegistryClient pipelineRegistryClient;
    @Mock private OrchestratorConfig config;
    @Mock private MetricsCollector metrics;
    @Mock private SpecFormatLoader specFormatLoader;

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock configuration
        when(config.getRefreshInterval()).thenReturn(Duration.ofMinutes(5));
        when(config.getMaxConcurrentRefreshes()).thenReturn(3);
        
        orchestrator = new Orchestrator(
                pipelineCache, agentRegistryClient, pipelineRegistryClient,
                config, metrics, specFormatLoader
        );
    }

    @Test
    void testStart() {
        // Mock dependencies
        when(pipelineRegistryClient.listAllPipelines()).thenReturn(Promise.of(Collections.emptyList()));
        when(pipelineCache.putAll(any())).thenReturn(Promise.of(null));
        
        // Test start
        Promise<Void> result = orchestrator.start();
        
        assertNotNull(result);
        verify(pipelineRegistryClient).listAllPipelines();
        verify(metrics).recordTimer("orch.startup", 0L);
    }

    @Test
    void testStop() {
        // Mock dependencies
        when(pipelineCache.clear()).thenReturn(Promise.of(null));
        
        // Test stop
        Promise<Void> result = orchestrator.stop();
        
        assertNotNull(result);
    }

    @Test
    void testHealthCheck() {
        // Mock health checks
        when(agentRegistryClient.isHealthy()).thenReturn(Promise.of(true));
        when(pipelineRegistryClient.isHealthy()).thenReturn(Promise.of(true));
        
        // Test health check (not started)
        Promise<Boolean> result = orchestrator.isHealthy();
        assertNotNull(result);
        
        // Start orchestrator first
        when(pipelineRegistryClient.listAllPipelines()).thenReturn(Promise.of(Collections.emptyList()));
        when(pipelineCache.putAll(any())).thenReturn(Promise.of(null));
        orchestrator.start();
        
        // Test health check (started)
        result = orchestrator.isHealthy();
        assertNotNull(result);
    }

    @Test
    void testGetStatus() {
        when(pipelineCache.size()).thenReturn(5);
        
        Orchestrator.OrchestratorStatus status = orchestrator.getStatus();
        
        assertNotNull(status);
        assertEquals(5, status.getCachedPipelines());
    }

    @Test
    void testGetPipeline() {
        String pipelineId = "test-pipeline";
        OrchestratorPipelineEntity mockPipeline = new OrchestratorPipelineEntity();
        mockPipeline.id = pipelineId;
        
        when(pipelineCache.get(pipelineId)).thenReturn(Promise.of(Optional.of(mockPipeline)));
        
        Promise<OrchestratorPipelineEntity> result = orchestrator.getPipeline(pipelineId);
        
        assertNotNull(result);
        verify(pipelineCache).get(pipelineId);
    }

    @Test
    void testListPipelines() {
        when(pipelineCache.getAllPipelines()).thenReturn(Promise.of(Collections.emptyList()));
        
        Promise<java.util.List<OrchestratorPipelineEntity>> result = orchestrator.listPipelines();
        
        assertNotNull(result);
        verify(pipelineCache).getAllPipelines();
    }
}