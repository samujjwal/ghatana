package com.ghatana.virtualorg.llm;

import com.ghatana.platform.testing.activej.EventloopTestBase;

import com.ghatana.virtualorg.llm.impl.OpenAILLMClient;
import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.TaskTypeProto;
import com.ghatana.virtualorg.v1.ToolProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OpenAILLMClient.
 *
 * Note: These are integration tests that require an OpenAI API key.
 * Set OPENAI_API_KEY environment variable to run these tests.
 */
@DisplayName("OpenAILLMClientTest Tests")
@Tag("integration")
class OpenAILLMClientTest extends EventloopTestBase {

    private OpenAILLMClient llmClient;

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            apiKey = "test-key"; // Will skip tests without actual key
        }

        llmClient = new OpenAILLMClient(
            apiKey,
            "gpt-4",
            0.7f,
            2048,
            30,
            eventloop()
        );
    }

    @Test
    @DisplayName("Client initializes with valid configuration")
    void testClientInitialization() {
        assertNotNull(llmClient);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    @DisplayName("Generate response for simple task")
    void testSimpleReasoning() {
        // Given
        TaskProto task = TaskProto.newBuilder()
            .setTaskId("test-1")
            .setTitle("Write a hello world function")
            .setType(TaskTypeProto.TASK_TYPE_FEATURE_IMPLEMENTATION)
            .setDescription("Create a simple hello world function in Java")
            .build();

        String context = "";
        List<ToolProto> tools = List.of();

        // When
        LLMResponse response = runPromise(() -> llmClient.reason(task, context, tools));

        // Then
        assertNotNull(response);
        assertNotNull(response.reasoning());
        assertFalse(response.reasoning().isBlank());
        assertTrue(response.tokensUsed() > 0);
        assertTrue(response.confidence() >= 0.0f && response.confidence() <= 1.0f);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    @DisplayName("Generate embeddings for text")
    void testEmbeddingGeneration() {
        // Given
        String text = "This is a test document about software engineering and AI agents.";

        // When
        float[] embedding = runPromise(() -> llmClient.embed(text));

        // Then
        assertNotNull(embedding);
        assertEquals(1536, embedding.length); // text-embedding-ada-002 dimension

        // Check that embeddings are normalized (optional, depending on model)
        double magnitude = 0.0;
        for (float v : embedding) {
            magnitude += v * v;
        }
        magnitude = Math.sqrt(magnitude);
        assertTrue(magnitude > 0.0);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    @DisplayName("Handle complex task with context")
    void testReasoningWithContext() {
        // Given
        TaskProto task = TaskProto.newBuilder()
            .setTaskId("test-2")
            .setTitle("Refactor authentication service")
            .setType(TaskTypeProto.TASK_TYPE_REFACTORING)
            .setDescription("Refactor the authentication service to use JWT tokens instead of sessions")
            .build();

        String context = "Previous implementation used server-side sessions stored in Redis. " +
                        "Users complained about session timeout issues.";

        // When
        LLMResponse response = runPromise(() -> llmClient.reason(task, context, List.of()));

        // Then
        assertNotNull(response);
        assertTrue(response.reasoning().toLowerCase().contains("jwt") ||
                   response.reasoning().toLowerCase().contains("token"));
        assertTrue(response.tokensUsed() > 0);
    }

    @Test
    @Tag("e2e")
    @DisplayName("Handle invalid API key gracefully")
    void testInvalidAPIKey() {
        // Given
        OpenAILLMClient invalidClient = new OpenAILLMClient(
            "invalid-key",
            "gpt-4",
            0.7f,
            2048,
            30,
            eventloop()
        );

        TaskProto task = TaskProto.newBuilder()
            .setTaskId("test-fail")
            .setTitle("Test task")
            .setType(TaskTypeProto.TASK_TYPE_FEATURE_IMPLEMENTATION)
            .build();

        // When/Then
        assertThrows(Exception.class, () -> {
            runPromise(() -> invalidClient.reason(task, "", List.of()));
        });
        
        // Clear the fatal error recorded by the eventloop runner, as we expected this failure
        clearFatalError();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    @DisplayName("Response quality varies with temperature")
    void testTemperatureEffect() {
        // Given - Low temperature (more deterministic)
        OpenAILLMClient deterministicClient = new OpenAILLMClient(
            System.getenv("OPENAI_API_KEY"),
            "gpt-4",
            0.1f, // Low temperature
            1024,
            30,
            eventloop()
        );

        TaskProto task = TaskProto.newBuilder()
            .setTaskId("test-temp")
            .setTitle("What is 2+2?")
            .setType(TaskTypeProto.TASK_TYPE_FEATURE_IMPLEMENTATION)
            .build();

        // When
        LLMResponse response1 = runPromise(() -> deterministicClient.reason(task, "", List.of()));
        LLMResponse response2 = runPromise(() -> deterministicClient.reason(task, "", List.of()));

        // Then - Responses should be similar (low temperature = more deterministic)
        assertNotNull(response1);
        assertNotNull(response2);
        // Both should mention "4" somewhere
        assertTrue(response1.reasoning().contains("4") || response2.reasoning().contains("4"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    @DisplayName("Token usage is tracked correctly")
    void testTokenTracking() {
        // Given
        TaskProto shortTask = TaskProto.newBuilder()
            .setTaskId("short")
            .setTitle("Hi")
            .setType(TaskTypeProto.TASK_TYPE_FEATURE_IMPLEMENTATION)
            .build();

        TaskProto longTask = TaskProto.newBuilder()
            .setTaskId("long")
            .setTitle("Write comprehensive documentation")
            .setType(TaskTypeProto.TASK_TYPE_DOCUMENTATION)
            .setDescription("Write comprehensive documentation for the entire authentication system including setup, configuration, API reference, security best practices, troubleshooting guide, and examples.")
            .build();

        // When
        LLMResponse shortResponse = runPromise(() -> llmClient.reason(shortTask, "", List.of()));
        LLMResponse longResponse = runPromise(() -> llmClient.reason(longTask, "", List.of()));

        // Then
        assertTrue(shortResponse.tokensUsed() > 0);
        assertTrue(longResponse.tokensUsed() > 0);
        // Long task should generally use more tokens
        assertTrue(longResponse.tokensUsed() >= shortResponse.tokensUsed());
    }
}
