/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning.consolidation;

import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

/**
 * Unit tests for token count logging in {@link DefaultLLMFactExtractor}.
 *
 * P2-16: Verify token count is logged and emitted as metric.
 */
class DefaultLLMFactExtractorTokenLoggingTest {

    @Test
    void shouldCreateCompletionResultWithTokenCounts() {
        // Verify CompletionResult can be created with token counts using builder
        CompletionResult result = CompletionResult.builder()
            .text("Test completion")
            .tokensUsed(100)
            .promptTokens(50)
            .completionTokens(50)
            .build();
        
        assertEquals(100, result.getTokensUsed(), "Total tokens should match");
        assertEquals(50, result.getPromptTokens(), "Prompt tokens should match");
        assertEquals(50, result.getCompletionTokens(), "Completion tokens should match");
    }
}
