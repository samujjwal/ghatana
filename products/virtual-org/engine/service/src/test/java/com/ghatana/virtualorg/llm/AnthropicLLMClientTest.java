package com.ghatana.virtualorg.llm;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.llm.impl.AnthropicLLMClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle tests for AnthropicLLMClient.
 */
@DisplayName("AnthropicLLMClient lifecycle")
class AnthropicLLMClientTest extends EventloopTestBase {

    private AnthropicLLMClient llmClient;

    @BeforeEach
    void setUp() {
        llmClient = new AnthropicLLMClient(
            "test-key",
            "claude-3-sonnet-20240229",
            0.7f,
            2048,
            30,
            eventloop()
        );
    }

    @Test
    @DisplayName("Client initializes healthy")
    void testClientInitialization() {
        assertNotNull(llmClient);
        assertTrue(llmClient.isRunning());
        assertTrue(runPromise(() -> llmClient.healthCheck()));
    }

    @Test
    @DisplayName("Client lifecycle toggles health")
    void testLifecycleTransitions() {
        runPromise(() -> llmClient.stop());

        assertFalse(llmClient.isRunning());
        assertFalse(runPromise(() -> llmClient.healthCheck()));

        runPromise(() -> llmClient.start());

        assertTrue(llmClient.isRunning());
        assertTrue(runPromise(() -> llmClient.healthCheck()));
    }

    @Test
    @DisplayName("Stopped client rejects unsupported embedding request before provider logic")
    void testStoppedClientRejectsEmbed() {
        runPromise(() -> llmClient.stop());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> runPromise(() -> llmClient.embed("hello")));

        assertTrue(exception.getMessage().contains("embed"));
    }
}