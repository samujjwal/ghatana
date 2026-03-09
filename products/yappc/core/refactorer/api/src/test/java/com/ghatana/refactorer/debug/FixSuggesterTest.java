package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**

 * @doc.type class

 * @doc.purpose Handles fix suggester test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class FixSuggesterTest {
    private FixSuggester fixSuggester;
    private FixContext javaContext;
    private FixContext pythonContext;

    @BeforeEach
    void setUp() {
        fixSuggester = new FixSuggester();
        javaContext = new FixContext("java", "Test.java");
        pythonContext = new FixContext("python", "test.py");
    }

    @Test
    void testSuggestFixes_NoMatches() {
        var suggestions = fixSuggester.suggestFixes("This is not an error message");
        assertTrue(suggestions.isEmpty(), "Should not suggest fixes for non-matching messages");
    }

    @Test
    void testSuggestFixes_JavaNullPointer() {
        String errorMessage =
                "java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"str\""
                        + " is null";
        var suggestions = fixSuggester.suggestFixes(errorMessage, javaContext);

        assertFalse(suggestions.isEmpty(), "Should suggest fixes for NullPointerException");
        assertEquals(
                "java.null.check", suggestions.get(0).getId(), "Should suggest null check fix");
        assertTrue(
                suggestions.get(0).getConfidence() > 0.5,
                "Should have high confidence for exact match");
    }

    @Test
    void testRegisterAndRetrieveSuggestion() {
        String customPattern = "CustomError: (.+?)";
        FixSuggestion suggestion =
                FixSuggestion.builder()
                        .id("custom.fix")
                        .description("Custom fix for testing")
                        .fixPattern("// Fix for ${1}")
                        .language("test")
                        .confidence(0.7)
                        .build();

        fixSuggester.registerSuggestion(customPattern, suggestion);

        var suggestions =
                fixSuggester.suggestFixes(
                        "CustomError: Something went wrong", new FixContext("test", "test.file"));

        assertFalse(suggestions.isEmpty(), "Should find custom suggestion");
        assertEquals("custom.fix", suggestions.get(0).getId(), "Should return custom fix");
        assertTrue(suggestions.get(0).getConfidence() > 0.5, "Should have reasonable confidence");
    }

    @Test
    void testGetAllSuggestions() {
        var allSuggestions = fixSuggester.getAllSuggestions();
        assertFalse(allSuggestions.isEmpty(), "Should return all registered suggestions");
    }

    @Test
    void testGetSuggestionsForLanguage() {
        var javaSuggestions = fixSuggester.getSuggestionsForLanguage("java");
        assertFalse(javaSuggestions.isEmpty(), "Should return Java suggestions");

        var pythonSuggestions = fixSuggester.getSuggestionsForLanguage("python");
        assertFalse(pythonSuggestions.isEmpty(), "Should return Python suggestions");

        var unknownSuggestions = fixSuggester.getSuggestionsForLanguage("nonexistent");
        assertTrue(unknownSuggestions.isEmpty(), "Should return empty list for unknown language");
    }

    @Test
    void testSuggestFixes_WithContext() {
        String errorMessage = "NameError: name 'undefined_var' is not defined";

        // Test with Python context (should match)
        var pythonSuggestions = fixSuggester.suggestFixes(errorMessage, pythonContext);
        assertFalse(pythonSuggestions.isEmpty(), "Should find Python suggestions");
        assertTrue(
                pythonSuggestions.get(0).getConfidence() > 0.5,
                "Should have high confidence for matching language");

        // Test with Java context (should have lower confidence or be empty)
        var javaSuggestions = fixSuggester.suggestFixes(errorMessage, javaContext);
        // Either no suggestions or lower confidence
        if (!javaSuggestions.isEmpty()) {
            // If we get suggestions, they should have lower confidence
            assertTrue(
                    javaSuggestions.get(0).getConfidence()
                            < pythonSuggestions.get(0).getConfidence(),
                    "Should have lower confidence for language mismatch");
        }
    }

    @Test
    void testRecordFixOutcome() {
        String suggestionId = "test.record.outcome";
        String errorPattern = "TestPattern: (.+?)";

        // Register a test suggestion
        FixSuggestion suggestion =
                FixSuggestion.builder()
                        .id(suggestionId)
                        .description("Test suggestion")
                        .fixPattern("// ${1}")
                        .language("java")
                        .confidence(0.5)
                        .build();

        fixSuggester.registerSuggestion(errorPattern, suggestion);

        // Get initial confidence
        var initialSuggestions = fixSuggester.suggestFixes("TestPattern: test", javaContext);
        double initialConfidence =
                initialSuggestions.isEmpty() ? 0 : initialSuggestions.get(0).getConfidence();

        // Record some successful outcomes
        for (int i = 0; i < 3; i++) {
            fixSuggester.recordFixOutcome(suggestionId, true);
        }

        // Get new confidence (should be higher)
        var updatedSuggestions = fixSuggester.suggestFixes("TestPattern: test", javaContext);
        double updatedConfidence =
                updatedSuggestions.isEmpty() ? 0 : updatedSuggestions.get(0).getConfidence();

        assertTrue(
                updatedConfidence > initialConfidence,
                "Confidence should increase after successful fixes");
    }

    @ParameterizedTest
    @CsvSource({
        "java.lang.NullPointerException",
        "NameError: name 'x' is not defined",
        "TypeError: Cannot read property 'x' of undefined"
    })
    void testDefaultSuggestions(String error) {
        var suggestions = fixSuggester.suggestFixes(error, new FixContext("unknown", "test.file"));

        // Just verify that we get some suggestions for each error pattern
        assertFalse(suggestions.isEmpty(), "Should find suggestion for: " + error);

        // Verify the confidence is reasonable
        assertTrue(
                suggestions.get(0).getConfidence() > 0.3,
                "Should have reasonable confidence for: " + error);
    }
}
