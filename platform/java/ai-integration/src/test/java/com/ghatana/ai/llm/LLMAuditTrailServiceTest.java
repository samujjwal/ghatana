/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("LLM Audit Trail Service Tests [GH-90000]")
class LLMAuditTrailServiceTest {

    @Test
    @DisplayName("logs LLM call with full content [GH-90000]")
    void logsCallWithFullContent() { // GH-90000
        LLMAuditTrailService auditService = new LLMAuditTrailService(1000, true); // GH-90000
        
        auditService.logCall( // GH-90000
            "tenant-1",
            "req-123",
            "openai",
            "gpt-4",
            "Test prompt",
            "Test response",
            100,
            50,
            500L,
            Map.of("finishReason", "stop") // GH-90000
        );
        
        assertThat(auditService.getAuditEntryCount("tenant-1 [GH-90000]")).isEqualTo(1);
        
        LLMAuditTrailService.AuditEntry entry = auditService.getAuditEntries("tenant-1 [GH-90000]").get(0);
        assertThat(entry.prompt()).isEqualTo("Test prompt [GH-90000]");
        assertThat(entry.response()).isEqualTo("Test response [GH-90000]");
        assertThat(entry.provider()).isEqualTo("openai [GH-90000]");
        assertThat(entry.model()).isEqualTo("gpt-4 [GH-90000]");
        assertThat(entry.promptTokens()).isEqualTo(100); // GH-90000
        assertThat(entry.completionTokens()).isEqualTo(50); // GH-90000
        assertThat(entry.latencyMs()).isEqualTo(500L); // GH-90000
    }

    @Test
    @DisplayName("logs LLM call with hashed content when full content logging disabled [GH-90000]")
    void logsCallWithHashedContent() { // GH-90000
        LLMAuditTrailService auditService = new LLMAuditTrailService(1000, false); // GH-90000
        
        auditService.logCall( // GH-90000
            "tenant-1",
            "req-123",
            "openai",
            "gpt-4",
            "Test prompt",
            "Test response",
            100,
            50,
            500L,
            Map.of() // GH-90000
        );
        
        LLMAuditTrailService.AuditEntry entry = auditService.getAuditEntries("tenant-1 [GH-90000]").get(0);
        
        // Should be hashed, not the original text
        assertThat(entry.prompt()).isNotEqualTo("Test prompt [GH-90000]");
        assertThat(entry.response()).isNotEqualTo("Test response [GH-90000]");
        // But should contain the hash
        assertThat(entry.prompt()).isNotEmpty(); // GH-90000
        assertThat(entry.response()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("aggregates audit entries by tenant [GH-90000]")
    void aggregatesByTenant() { // GH-90000
        LLMAuditTrailService auditService = new LLMAuditTrailService(); // GH-90000
        
        auditService.logCall("tenant-1", "req-1", "openai", "gpt-4", "p1", "r1", 100, 50, 500L, Map.of()); // GH-90000
        auditService.logCall("tenant-1", "req-2", "openai", "gpt-4", "p2", "r2", 200, 100, 600L, Map.of()); // GH-90000
        auditService.logCall("tenant-2", "req-3", "openai", "gpt-4", "p3", "r3", 150, 75, 550L, Map.of()); // GH-90000
        
        assertThat(auditService.getAuditEntryCount("tenant-1 [GH-90000]")).isEqualTo(2);
        assertThat(auditService.getAuditEntryCount("tenant-2 [GH-90000]")).isEqualTo(1);
    }

    @Test
    @DisplayName("filters audit entries by time range [GH-90000]")
    void filtersByTimeRange() { // GH-90000
        LLMAuditTrailService auditService = new LLMAuditTrailService(); // GH-90000
        
        Instant from = Instant.now().minus(java.time.Duration.ofDays(1)); // GH-90000
        
        auditService.logCall("tenant-1", "req-1", "openai", "gpt-4", "p1", "r1", 100, 50, 500L, Map.of()); // GH-90000
        auditService.logCall("tenant-1", "req-2", "openai", "gpt-4", "p2", "r2", 200, 100, 600L, Map.of()); // GH-90000
        Instant to = Instant.now(); // GH-90000
        
        java.util.List<LLMAuditTrailService.AuditEntry> entries = 
            auditService.getAuditEntries("tenant-1", from, to); // GH-90000
        
        assertThat(entries).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("retrieves audit entries by request ID [GH-90000]")
    void retrievesByRequestId() { // GH-90000
        LLMAuditTrailService auditService = new LLMAuditTrailService(); // GH-90000
        
        auditService.logCall("tenant-1", "req-123", "openai", "gpt-4", "p1", "r1", 100, 50, 500L, Map.of()); // GH-90000
        auditService.logCall("tenant-1", "req-456", "openai", "gpt-4", "p2", "r2", 200, 100, 600L, Map.of()); // GH-90000
        
        java.util.List<LLMAuditTrailService.AuditEntry> entries = 
            auditService.getAuditEntriesForRequest("tenant-1", "req-123"); // GH-90000
        
        assertThat(entries).hasSize(1); // GH-90000
        assertThat(entries.get(0).requestId()).isEqualTo("req-123 [GH-90000]");
    }

    @Test
    @DisplayName("clears audit entries for tenant [GH-90000]")
    void clearsAuditEntriesForTenant() { // GH-90000
        LLMAuditTrailService auditService = new LLMAuditTrailService(); // GH-90000
        
        auditService.logCall("tenant-1", "req-1", "openai", "gpt-4", "p1", "r1", 100, 50, 500L, Map.of()); // GH-90000
        
        assertThat(auditService.getAuditEntryCount("tenant-1 [GH-90000]")).isEqualTo(1);
        
        auditService.clearAuditEntries("tenant-1 [GH-90000]");
        
        assertThat(auditService.getAuditEntryCount("tenant-1 [GH-90000]")).isEqualTo(0);
    }

    @Test
    @DisplayName("decorator gateway logs audit trail [GH-90000]")
    void decoratorGatewayLogsAuditTrail() { // GH-90000
        LLMAuditTrailService auditService = new LLMAuditTrailService(1000, true); // GH-90000
        LLMGateway mockGateway = createMockGateway(); // GH-90000
        
        AuditTrailLLMGateway auditGateway = new AuditTrailLLMGateway(mockGateway, auditService); // GH-90000
        
        assertThat(auditGateway.getAuditService()).isEqualTo(auditService); // GH-90000
        assertThat(auditGateway.getDelegate()).isEqualTo(mockGateway); // GH-90000
    }

    @Test
    @DisplayName("prunes old entries when over limit [GH-90000]")
    void prunesOldEntriesWhenOverLimit() { // GH-90000
        LLMAuditTrailService auditService = new LLMAuditTrailService(5, true); // GH-90000
        
        for (int i = 0; i < 10; i++) { // GH-90000
            auditService.logCall("tenant-1", "req-" + i, "openai", "gpt-4", "p", "r", 100, 50, 500L, Map.of()); // GH-90000
        }
        
        // Should prune to max 5 entries
        assertThat(auditService.getAuditEntryCount("tenant-1 [GH-90000]")).isEqualTo(5);
    }

    // Helper method

    private LLMGateway createMockGateway() { // GH-90000
        return new LLMGateway() { // GH-90000
            @Override
            public Promise<CompletionResult> complete(CompletionRequest request) { // GH-90000
                return Promise.of(CompletionResult.builder() // GH-90000
                    .text("Mock response [GH-90000]")
                    .promptTokens(100) // GH-90000
                    .completionTokens(50) // GH-90000
                    .tokensUsed(150) // GH-90000
                    .modelUsed("gpt-4 [GH-90000]")
                    .build()); // GH-90000
            }

            @Override
            public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) { // GH-90000
                return Promise.of(CompletionResult.of("Mock response [GH-90000]"));
            }

            @Override
            public Promise<CompletionResult> continueWithToolResults(CompletionRequest request, List<ToolCallResult> toolResults) { // GH-90000
                return Promise.of(CompletionResult.of("Mock response [GH-90000]"));
            }

            @Override
            public Promise<EmbeddingResult> embed(String text) { // GH-90000
                return Promise.of(new EmbeddingResult(text, new float[0], "model")); // GH-90000
            }

            @Override
            public Promise<TokenStream> stream(CompletionRequest request) { // GH-90000
                return Promise.of(new DefaultTokenStream()); // GH-90000
            }

            @Override
            public Promise<List<EmbeddingResult>> embedBatch(List<String> texts) { // GH-90000
                return Promise.of(List.of()); // GH-90000
            }

            @Override
            public MetricsCollector getMetrics() { // GH-90000
                return NoopMetricsCollector.getInstance(); // GH-90000
            }

            @Override
            public String getDefaultProvider() { // GH-90000
                return "openai";
            }

            @Override
            public List<String> getAvailableProviders() { // GH-90000
                return List.of("openai [GH-90000]");
            }

            @Override
            public boolean isProviderAvailable(String providerName) { // GH-90000
                return true;
            }
        };
    }
}
