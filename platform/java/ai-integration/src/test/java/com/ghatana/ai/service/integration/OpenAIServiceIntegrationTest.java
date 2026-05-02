package com.ghatana.ai.service.integration;

import com.ghatana.ai.service.LLMService;
import com.ghatana.ai.service.OpenAIService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for OpenAIService generateStructured with schema validation.
 *
 * @doc.type class
 * @doc.purpose Integration tests for OpenAIService schema validation
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("OpenAIService Schema Validation Integration Tests")
class OpenAIServiceIntegrationTest {

    /**
     * Test data class for structured output validation.
     */
    static class TestSchema {
        public String name;
        public int value;
        public boolean active;

        public TestSchema() {} 

        public TestSchema(String name, int value, boolean active) { 
            this.name = name;
            this.value = value;
            this.active = active;
        }
    }

    @Test
    @DisplayName("Integration test: Should parse valid structured output from LLM")
    void shouldParseValidStructuredOutput() { 
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY not set"); 

        LLMService service = new OpenAIService(apiKey); 
        String prompt = "Generate a JSON object with name='test', value=42, active=true";

        TestSchema result = service.generateStructured(prompt, TestSchema.class).getResult(); 
        assertNotNull(result); 
        assertEquals("test", result.name); 
        assertEquals(42, result.value); 
        assertTrue(result.active); 
    }

    @Test
    @DisplayName("Integration test: Should throw exception for invalid structured output")
    void shouldThrowExceptionForInvalidStructuredOutput() { 
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY not set"); 

        LLMService service = new OpenAIService(apiKey); 
        String prompt = "Generate plain text, not JSON";

        try {
            service.generateStructured(prompt, TestSchema.class).getResult(); 
            throw new AssertionError("Should throw exception for invalid structured output");
        } catch (Exception e) { 
            assertTrue(e.getMessage().contains("Failed to parse") || e.getCause() != null);
        }
    }
}
