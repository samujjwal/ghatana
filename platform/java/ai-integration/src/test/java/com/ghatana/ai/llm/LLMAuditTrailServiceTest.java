/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.ai.llm;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LLM audit trail service.
 *
 * @doc.type class
 * @doc.purpose Unit tests for LLM audit trail logging
 * @doc.layer test
 */
@DisplayName("LLM Audit Trail Service Tests")
class LLMAuditTrailServiceTest {

    @Test
    @DisplayName("logs LLM call with full content")
    void logsCallWithFullContent() {
        LLMAuditTrailService auditService = new LLMAuditTrailService(1000, true);
        
        auditService.logCall(
            "tenant-1",
            "req-123",
            "openai",
            "gpt-4",
            "Test prompt",
            "Test response",
            100,
            50,
            500L,
            Map.of("finishReason", "stop")
        );
        
        assertThat(auditService.getAuditEntryCount("tenant-1")).isEqualTo(1);
        
        LLMAuditTrailService.AuditEntry entry = auditService.getAuditEntries("tenant-1").get(0);
        assertThat(entry.prompt()).isEqualTo("Test prompt");
        assertThat(entry.response()).isEqualTo("Test response");
        assertThat(entry.provider()).isEqualTo("openai");
        assertThat(entry.model()).isEqualTo("gpt-4");
        assertThat(entry.promptTokens()).isEqualTo(100);
        assertThat(entry.completionTokens()).isEqualTo(50);
        assertThat(entry.latencyMs()).isEqualTo(500L);
    }

    @Test
    @DisplayName("logs LLM call with hashed content when full content logging disabled")
    void logsCallWithHashedContent() {
        LLMAuditTrailService auditService = new LLMAuditTrailService(1000, false);
        
        auditService.logCall(
            "tenant-1",
            "req-123",
            "openai",
            "gpt-4",
            "Test prompt",
            "Test response",
            100,
            50,
            500L,
            Map.of()
        );
        
        LLMAuditTrailService.AuditEntry entry = auditService.getAuditEntries("tenant-1").get(0);
        
        // Should be hashed, not the original text
        assertThat(entry.prompt()).isNotEqualTo("Test prompt");
        assertThat(entry.response()).isNotEqualTo("Test response");
        // But should contain the hash
        assertThat(entry.prompt()).isNotEmpty();
        assertThat(entry.response()).isNotEmpty();
    }

    @Test
    @DisplayName("aggregates audit entries by tenant")
    void aggregatesByTenant() {
        LLMAuditTrailService auditService = new LLMAuditTrailService();
        
        auditService.logCall("tenant-1", "req-1", "openai", "gpt-4", "p1", "r1", 100, 50, 500L, Map.of());
        auditService.logCall("tenant-1", "req-2", "openai", "gpt-4", "p2", "r2", 200, 100, 600L, Map.of());
        auditService.logCall("tenant-2", "req-3", "openai", "gpt-4", "p3", "r3", 150, 75, 550L, Map.of());
        
        assertThat(auditService.getAuditEntryCount("tenant-1")).isEqualTo(2);
        assertThat(auditService.getAuditEntryCount("tenant-2")).isEqualTo(1);
    }

    @Test
    @DisplayName("filters audit entries by time range")
    void filtersByTimeRange() {
        LLMAuditTrailService auditService = new LLMAuditTrailService();
        
        Instant now = Instant.now();
        Instant yesterday = now.minus(java.time.Duration.ofDays(1));
        
        auditService.logCall("tenant-1", "req-1", "openai", "gpt-4", "p1", "r1", 100, 50, 500L, Map.of());
        auditService.logCall("tenant-1", "req-2", "openai", "gpt-4", "p2", "r2", 200, 100, 600L, Map.of());
        
        java.util.List<LLMAuditTrailService.AuditEntry> entries = 
            auditService.getAuditEntries("tenant-1", yesterday, now);
        
        assertThat(entries).hasSize(2);
    }

    @Test
    @DisplayName("retrieves audit entries by request ID")
    void retrievesByRequestId() {
        LLMAuditTrailService auditService = new LLMAuditTrailService();
        
        auditService.logCall("tenant-1", "req-123", "openai", "gpt-4", "p1", "r1", 100, 50, 500L, Map.of());
        auditService.logCall("tenant-1", "req-456", "openai", "gpt-4", "p2", "r2", 200, 100, 600L, Map.of());
        
        java.util.List<LLMAuditTrailService.AuditEntry> entries = 
            auditService.getAuditEntriesForRequest("tenant-1", "req-123");
        
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).requestId()).isEqualTo("req-123");
    }

    @Test
    @DisplayName("clears audit entries for tenant")
    void clearsAuditEntriesForTenant() {
        LLMAuditTrailService auditService = new LLMAuditTrailService();
        
        auditService.logCall("tenant-1", "req-1", "openai", "gpt-4", "p1", "r1", 100, 50, 500L, Map.of());
        
        assertThat(auditService.getAuditEntryCount("tenant-1")).isEqualTo(1);
        
        auditService.clearAuditEntries("tenant-1");
        
        assertThat(auditService.getAuditEntryCount("tenant-1")).isEqualTo(0);
    }

    @Test
    @DisplayName("decorator gateway logs audit trail")
    void decoratorGatewayLogsAuditTrail() {
        LLMAuditTrailService auditService = new LLMAuditTrailService(1000, true);
        LLMGateway mockGateway = createMockGateway();
        
        AuditTrailLLMGateway auditGateway = new AuditTrailLLMGateway(mockGateway, auditService);
        
        assertThat(auditGateway.getAuditService()).isEqualTo(auditService);
        assertThat(auditGateway.getDelegate()).isEqualTo(mockGateway);
    }

    @Test
    @DisplayName("prunes old entries when over limit")
    void prunesOldEntriesWhenOverLimit() {
        LLMAuditTrailService auditService = new LLMAuditTrailService(5, true);
        
        for (int i = 0; i < 10; i++) {
            auditService.logCall("tenant-1", "req-" + i, "openai", "gpt-4", "p", "r", 100, 50, 500L, Map.of());
        }
        
        // Should prune to max 5 entries
        assertThat(auditService.getAuditEntryCount("tenant-1")).isEqualTo(5);
    }

    // Helper method

    private LLMGateway createMockGateway() {
        return new LLMGateway() {
            @Override
            public Promise<CompletionResult> complete(CompletionRequest request) {
                return Promise.of(CompletionResult.builder()
                    .text("Mock response")
                    .promptTokens(100)
                    .completionTokens(50)
                    .tokensUsed(150)
                    .modelUsed("gpt-4")
                    .build());
            }

            @Override
            public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) {
                return Promise.of(CompletionResult.of("Mock response"));
            }

            @Override
            public Promise<CompletionResult> continueWithToolResults(CompletionRequest request, List<ToolCallResult> toolResults) {
                return Promise.of(CompletionResult.of("Mock response"));
            }

            @Override
            public Promise<EmbeddingResult> embed(String text) {
                return Promise.of(new EmbeddingResult(text, new float[0], "model"));
            }

            @Override
            public Promise<TokenStream> stream(CompletionRequest request) {
                return Promise.of(new DefaultTokenStream());
            }

            @Override
            public Promise<List<EmbeddingResult>> embedBatch(List<String> texts) {
                return Promise.of(List.of());
            }

            @Override
            public MetricsCollector getMetrics() {
                return NoopMetricsCollector.getInstance();
            }

            @Override
            public String getDefaultProvider() {
                return "openai";
            }

            @Override
            public List<String> getAvailableProviders() {
                return List.of("openai");
            }

            @Override
            public boolean isProviderAvailable(String providerName) {
                return true;
            }
        };
    }
}
