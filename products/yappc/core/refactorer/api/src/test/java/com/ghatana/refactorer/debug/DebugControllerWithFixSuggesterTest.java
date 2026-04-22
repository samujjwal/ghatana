package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles debug controller with fix suggester test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class DebugControllerWithFixSuggesterTest {
    private DebugController debugController;
    private FixSuggester fixSuggester;
        private ErrorPatternManager errorPatternManager;

    @BeforeEach
        void setUp() { // GH-90000
        this.fixSuggester = new FixSuggester(); // GH-90000
                this.errorPatternManager = mock(ErrorPatternManager.class); // GH-90000
                when(errorPatternManager.findMatches(anyString(), anyString())).thenReturn(List.of()); // GH-90000

        fixSuggester.registerSuggestion( // GH-90000
                "TestError: (.+?)", // GH-90000
                FixSuggestion.builder() // GH-90000
                        .id("test.fix [GH-90000]")
                        .description("Test fix for demonstration [GH-90000]")
                        .fixPattern("// Fix for ${1} [GH-90000]")
                        .language("test [GH-90000]")
                        .confidence(0.9) // GH-90000
                        .build()); // GH-90000
        this.debugController =
                new DebugController( // GH-90000
                        List.of(new TestStackTraceParser()), errorPatternManager, fixSuggester); // GH-90000
    }

    @Test
    void testParseWithFixSuggestions() { // GH-90000
        String errorMessage = "TestError: Something went wrong";
        ErrorPatternManager.MatchedPattern testMatch =
                matchedPattern("test_error", errorMessage, "Test fix"); // GH-90000
        when(errorPatternManager.findMatches(anyString(), eq("node [GH-90000]")))
                .thenReturn(List.of(testMatch)); // GH-90000

        DebugController.ParseResult result = debugController.parse(errorMessage); // GH-90000

        assertTrue(result.success(), "Should have parsed a test stack frame"); // GH-90000
        assertFalse(result.fixSuggestions().isEmpty(), "Should have fix suggestions for test error"); // GH-90000
        assertTrue( // GH-90000
                result.fixSuggestions().stream().anyMatch(s -> "test.fix".equals(s.getId())), // GH-90000
                "Should include the registered test fix suggestion");
    }

    @Test
    void testLanguageSpecificSuggestions() { // GH-90000
        fixSuggester.registerSuggestion( // GH-90000
                "java\\.lang\\.NullPointerException.*",
                FixSuggestion.builder() // GH-90000
                        .id("java.specific.fix [GH-90000]")
                        .description("Java-specific fix [GH-90000]")
                        .fixPattern("// Java null-safety fix [GH-90000]")
                        .language("java [GH-90000]")
                        .confidence(0.9) // GH-90000
                        .build()); // GH-90000

        String javaError =
                "java.lang.NullPointerException: Cannot invoke \"String.length()\" because" // GH-90000
                        + " \"str\" is null\n"
                        + "    at com.example.Test.main(Test.java:10)"; // GH-90000
        ErrorPatternManager.MatchedPattern javaMatch =
                matchedPattern("java_npe", javaError, "Add null checks"); // GH-90000
        when(errorPatternManager.findMatches(anyString(), eq("java [GH-90000]")))
                .thenReturn(List.of(javaMatch)); // GH-90000

        DebugController.ParseResult result = debugController.parse(javaError); // GH-90000

        assertTrue( // GH-90000
                result.fixSuggestions().stream() // GH-90000
                        .anyMatch(s -> "java.specific.fix".equals(s.getId())), // GH-90000
                "Should include Java-specific suggestions when the Java pattern matches");
    }

    @Test
    void testNoSuggestionsForUnmatchedErrors() { // GH-90000
        String unknownError = "SomeUnknownError: This error has no registered fixes";
        when(errorPatternManager.findMatches(anyString(), anyString())).thenReturn(List.of()); // GH-90000
        DebugController.ParseResult result = debugController.parse(unknownError); // GH-90000

        assertTrue( // GH-90000
                result.fixSuggestions().isEmpty(), // GH-90000
                "Should not have suggestions for unknown error patterns");
    }

    @Test
    void testAggregatesSuggestionsAcrossMultipleMatches() { // GH-90000
        fixSuggester.registerSuggestion( // GH-90000
                "SecondaryError: (.+?)", // GH-90000
                FixSuggestion.builder() // GH-90000
                        .id("secondary.fix [GH-90000]")
                        .description("Secondary fix [GH-90000]")
                        .fixPattern("// Secondary fix for ${1} [GH-90000]")
                        .language("test [GH-90000]")
                        .confidence(0.8) // GH-90000
                        .build()); // GH-90000

        String combinedError = "PrimaryError: first\nSecondaryError: second";
        ErrorPatternManager.MatchedPattern primaryMatch =
                matchedPattern("primary_error", "PrimaryError: first", "Primary"); // GH-90000
        ErrorPatternManager.MatchedPattern secondaryMatch =
                matchedPattern("secondary_error", "SecondaryError: second", "Secondary"); // GH-90000
        when(errorPatternManager.findMatches(anyString(), eq("node [GH-90000]")))
                .thenReturn(List.of(primaryMatch, secondaryMatch)); // GH-90000

        DebugController.ParseResult result = debugController.parse(combinedError); // GH-90000

        assertFalse(result.fixSuggestions().isEmpty(), "Should aggregate suggestions across matches"); // GH-90000
        assertTrue( // GH-90000
                result.fixSuggestions().stream() // GH-90000
                        .anyMatch(s -> "secondary.fix".equals(s.getId())), // GH-90000
                "Should include suggestions contributed by later matched patterns");
    }

    @Test
    void testKeepsHighestConfidenceSuggestionWhenMatchesProduceDuplicateIds() { // GH-90000
        fixSuggester.registerSuggestion( // GH-90000
                "DuplicateError: (.+?)", // GH-90000
                FixSuggestion.builder() // GH-90000
                        .id("duplicate.fix [GH-90000]")
                        .description("Lower-confidence duplicate fix [GH-90000]")
                        .fixPattern("// duplicate fix [GH-90000]")
                        .language("test [GH-90000]")
                        .confidence(0.4) // GH-90000
                        .build()); // GH-90000
        fixSuggester.registerSuggestion( // GH-90000
                "DuplicateSecondaryError: (.+?)", // GH-90000
                FixSuggestion.builder() // GH-90000
                        .id("duplicate.fix [GH-90000]")
                        .description("Higher-confidence duplicate fix [GH-90000]")
                        .fixPattern("// duplicate fix improved [GH-90000]")
                        .language("test [GH-90000]")
                        .confidence(0.95) // GH-90000
                        .build()); // GH-90000

        String combinedError = "DuplicateError: first\nDuplicateSecondaryError: second";
        ErrorPatternManager.MatchedPattern duplicateMatch =
                matchedPattern("duplicate_error", "DuplicateError: first", "Duplicate"); // GH-90000
        ErrorPatternManager.MatchedPattern duplicateSecondaryMatch =
                matchedPattern( // GH-90000
                        "duplicate_secondary_error",
                        "DuplicateSecondaryError: second",
                        "Duplicate secondary");
        when(errorPatternManager.findMatches(anyString(), eq("node [GH-90000]")))
                .thenReturn(List.of(duplicateMatch, duplicateSecondaryMatch)); // GH-90000

        DebugController.ParseResult result = debugController.parse(combinedError); // GH-90000

        List<FixSuggestion> duplicateSuggestions =
                result.fixSuggestions().stream() // GH-90000
                        .filter(suggestion -> "duplicate.fix".equals(suggestion.getId())) // GH-90000
                        .toList(); // GH-90000

        assertEquals(1, duplicateSuggestions.size(), "Duplicate suggestion IDs should be merged"); // GH-90000
        assertEquals( // GH-90000
                "Higher-confidence duplicate fix",
                duplicateSuggestions.get(0).getDescription(), // GH-90000
                "Merged suggestion should keep the highest-confidence candidate");
    }

    private static ErrorPatternManager.MatchedPattern matchedPattern( // GH-90000
            String name, String matchedText, String suggestion) {
        ErrorPatternManager.ErrorPattern pattern = mock(ErrorPatternManager.ErrorPattern.class); // GH-90000
        when(pattern.getName()).thenReturn(name); // GH-90000
        when(pattern.getSuggestion()).thenReturn(suggestion); // GH-90000
        return new ErrorPatternManager.MatchedPattern(pattern, matchedText); // GH-90000
    }

    /** A test stack trace parser that recognizes a simple pattern for testing. */
    private static class TestStackTraceParser implements StackTraceParser {
        @Override
        public List<TraceFrame> parse(String content) { // GH-90000
            if (content.contains("NullPointerException [GH-90000]")) {
                return List.of( // GH-90000
                        new TraceFrame("Test.java", 10, "main", "NullPointerException in main")); // GH-90000
            }
            if (content.contains("Error: [GH-90000]")) {
                return List.of( // GH-90000
                        new TraceFrame("test.js", 10, "testMethod", "Test error in test.js")); // GH-90000
            }
            return List.of(); // GH-90000
        }
    }
}
