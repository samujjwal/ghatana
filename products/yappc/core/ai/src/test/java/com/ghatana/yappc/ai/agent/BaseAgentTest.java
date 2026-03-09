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
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testAgent = new TestAgent(llmService);
    }
    
    @Test
    @DisplayName("Should execute agent with input")
    void shouldExecuteAgent() {
        // Given
        String input = "Analyze this code";
        String response = "Analysis result";
        
        when(llmService.chat(anyString(), anyString()))
            .thenReturn(Promise.of(response));
        
        // When
        Promise<String> result = testAgent.execute(input);
        
        // Then
        verify(llmService).chat(anyString(), anyString());
    }
    
    @Test
    @DisplayName("Should return agent name")
    void shouldReturnAgentName() {
        // Given/When
        String name = testAgent.getName();
        
        // Then
        assert name.equals("TestAgent");
    }
    
    /**
     * Test implementation of BaseAgent.
     */
    private static class TestAgent extends BaseAgent {
        public TestAgent(LLMService llmService) {
            super(llmService, "TestAgent", "You are a test agent");
        }

        @org.jetbrains.annotations.NotNull
        public String getCapabilities() {
            return "Test agent capabilities";
        }
    }
}
