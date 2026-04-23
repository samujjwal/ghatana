package com.ghatana.yappc.ai.agent;

import com.ghatana.ai.service.LLMService;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for BaseAgent.
 */
@DisplayName("BaseAgent Tests")
/**
 * @doc.type class
 * @doc.purpose Handles base agent test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class BaseAgentTest extends EventloopTestBase {

    @Mock
    private LLMService llmService;

    private TestAgent testAgent;

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        testAgent = new TestAgent(llmService); // GH-90000
    }

    @Test
    @DisplayName("Should execute agent with input")
    void shouldExecuteAgent() { // GH-90000
        // Given
        String input = "Analyze this code";
        String response = "Analysis result";

        when(llmService.chat(anyString(), anyString())) // GH-90000
            .thenReturn(Promise.of(response)); // GH-90000

        // When
        Promise<String> result = testAgent.execute(input); // GH-90000

        // Then
        verify(llmService).chat(anyString(), anyString()); // GH-90000
    }

    @Test
    @DisplayName("Should return agent name")
    void shouldReturnAgentName() { // GH-90000
        // Given/When
        String name = testAgent.getName(); // GH-90000

        // Then
        assert name.equals("TestAgent");
    }

    /**
     * Test implementation of BaseAgent.
     */
    private static class TestAgent extends BaseAgent {
        public TestAgent(LLMService llmService) { // GH-90000
            super(llmService, "TestAgent", "You are a test agent"); // GH-90000
        }

        @org.jetbrains.annotations.NotNull
        public String getCapabilities() { // GH-90000
            return "Test agent capabilities";
        }
    }
}
