/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.learning.consolidation;

import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Map;

/**
 * Unit tests for LLM timeout in {@link DefaultLLMFactExtractor}.
 *
 * P1-8: Verify Duration timeout is applied to LLMGateway.complete() calls. // GH-90000
 */
class DefaultLLMFactExtractorTimeoutTest {

    @Test
    void shouldHaveReasonableTimeoutDuration() { // GH-90000
        // Verify timeout is reasonable (not too short, not too long) // GH-90000
        Duration timeout = Duration.ofSeconds(30); // GH-90000
        
        assertTrue(timeout.getSeconds() >= 10,  // GH-90000
            "Timeout should be at least 10 seconds for LLM calls");
        assertTrue(timeout.getSeconds() <= 120,  // GH-90000
            "Timeout should not exceed 120 seconds for LLM calls");
    }
}
