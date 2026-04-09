package com.ghatana.virtualorg.llm;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.llm.impl.OpenAILLMClient;
import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.TaskTypeProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle tests for OpenAILLMClient that do not require a live API key.
 */
@DisplayName("OpenAILLMClient lifecycle")
class OpenAILLMClientLifecycleTest extends EventloopTestBase {

    private OpenAILLMClient llmClient;

    @BeforeEach
    void setUp() {
        llmClient = new OpenAILLMClient(
            "test-key",
            "gpt-4",
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
    @DisplayName("Stopped client rejects reasoning before provider calls")
    void testStoppedClientRejectsReasoning() {
        TaskProto task = TaskProto.newBuilder()
            .setTaskId("stopped-client")
            .setTitle("Test task")
            .setType(TaskTypeProto.TASK_TYPE_FEATURE_IMPLEMENTATION)
            .build();

        runPromise(() -> llmClient.stop());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> runPromise(() -> llmClient.reason(task, "", List.of())));

        assertTrue(exception.getMessage().contains("reason"));
    }
}
