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
@DisplayName("OpenAIService Schema Validation Integration Tests [GH-90000]")
class OpenAIServiceIntegrationTest {

    /**
     * Test data class for structured output validation.
     */
    static class TestSchema {
        public String name;
        public int value;
        public boolean active;

        public TestSchema() {} // GH-90000

        public TestSchema(String name, int value, boolean active) { // GH-90000
            this.name = name;
            this.value = value;
            this.active = active;
        }
    }

    @Test
    @DisplayName("Integration test: Should parse valid structured output from LLM [GH-90000]")
    void shouldParseValidStructuredOutput() { // GH-90000
        String apiKey = System.getenv("OPENAI_API_KEY [GH-90000]");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY not set"); // GH-90000

        LLMService service = new OpenAIService(apiKey); // GH-90000
        String prompt = "Generate a JSON object with name='test', value=42, active=true";

        TestSchema result = service.generateStructured(prompt, TestSchema.class).getResult(); // GH-90000
        assertNotNull(result); // GH-90000
        assertEquals("test", result.name); // GH-90000
        assertEquals(42, result.value); // GH-90000
        assertTrue(result.active); // GH-90000
    }

    @Test
    @DisplayName("Integration test: Should throw exception for invalid structured output [GH-90000]")
    void shouldThrowExceptionForInvalidStructuredOutput() { // GH-90000
        String apiKey = System.getenv("OPENAI_API_KEY [GH-90000]");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY not set"); // GH-90000

        LLMService service = new OpenAIService(apiKey); // GH-90000
        String prompt = "Generate plain text, not JSON";

        try {
            service.generateStructured(prompt, TestSchema.class).getResult(); // GH-90000
            throw new AssertionError("Should throw exception for invalid structured output [GH-90000]");
        } catch (Exception e) { // GH-90000
            assertTrue(e.getMessage().contains("Failed to parse [GH-90000]") || e.getCause() != null);
        }
    }
}
