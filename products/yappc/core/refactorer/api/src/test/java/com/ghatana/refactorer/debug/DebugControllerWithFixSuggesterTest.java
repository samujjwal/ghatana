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
        void setUp() {
        this.fixSuggester = new FixSuggester();
                this.errorPatternManager = mock(ErrorPatternManager.class);
                when(errorPatternManager.findMatches(anyString(), anyString())).thenReturn(List.of());

        fixSuggester.registerSuggestion(
                "TestError: (.+?)",
                FixSuggestion.builder()
                        .id("test.fix")
                        .description("Test fix for demonstration")
                        .fixPattern("// Fix for ${1}")
                        .language("test")
                        .confidence(0.9)
                        .build());
        this.debugController =
                new DebugController(
                        List.of(new TestStackTraceParser()), errorPatternManager, fixSuggester);
    }

    @Test
    void testParseWithFixSuggestions() {
        String errorMessage = "TestError: Something went wrong";
        ErrorPatternManager.MatchedPattern testMatch =
                matchedPattern("test_error", errorMessage, "Test fix");
        when(errorPatternManager.findMatches(anyString(), eq("node")))
                .thenReturn(List.of(testMatch));

        DebugController.ParseResult result = debugController.parse(errorMessage);

        assertTrue(result.success(), "Should have parsed a test stack frame");
        assertFalse(result.fixSuggestions().isEmpty(), "Should have fix suggestions for test error");
        assertTrue(
                result.fixSuggestions().stream().anyMatch(s -> "test.fix".equals(s.getId())),
                "Should include the registered test fix suggestion");
    }

    @Test
    void testLanguageSpecificSuggestions() {
        fixSuggester.registerSuggestion(
                "java\\.lang\\.NullPointerException.*",
                FixSuggestion.builder()
                        .id("java.specific.fix")
                        .description("Java-specific fix")
                        .fixPattern("// Java null-safety fix")
                        .language("java")
                        .confidence(0.9)
                        .build());

        String javaError =
                "java.lang.NullPointerException: Cannot invoke \"String.length()\" because"
                        + " \"str\" is null\n"
                        + "    at com.example.Test.main(Test.java:10)";
        ErrorPatternManager.MatchedPattern javaMatch =
                matchedPattern("java_npe", javaError, "Add null checks");
        when(errorPatternManager.findMatches(anyString(), eq("java")))
                .thenReturn(List.of(javaMatch));

        DebugController.ParseResult result = debugController.parse(javaError);

        assertTrue(
                result.fixSuggestions().stream()
                        .anyMatch(s -> "java.specific.fix".equals(s.getId())),
                "Should include Java-specific suggestions when the Java pattern matches");
    }

    @Test
    void testNoSuggestionsForUnmatchedErrors() {
        String unknownError = "SomeUnknownError: This error has no registered fixes";
        when(errorPatternManager.findMatches(anyString(), anyString())).thenReturn(List.of());
        DebugController.ParseResult result = debugController.parse(unknownError);

        assertTrue(
                result.fixSuggestions().isEmpty(),
                "Should not have suggestions for unknown error patterns");
    }

    @Test
    void testAggregatesSuggestionsAcrossMultipleMatches() {
        fixSuggester.registerSuggestion(
                "SecondaryError: (.+?)",
                FixSuggestion.builder()
                        .id("secondary.fix")
                        .description("Secondary fix")
                        .fixPattern("// Secondary fix for ${1}")
                        .language("test")
                        .confidence(0.8)
                        .build());

        String combinedError = "PrimaryError: first\nSecondaryError: second";
        ErrorPatternManager.MatchedPattern primaryMatch =
                matchedPattern("primary_error", "PrimaryError: first", "Primary");
        ErrorPatternManager.MatchedPattern secondaryMatch =
                matchedPattern("secondary_error", "SecondaryError: second", "Secondary");
        when(errorPatternManager.findMatches(anyString(), eq("node")))
                .thenReturn(List.of(primaryMatch, secondaryMatch));

        DebugController.ParseResult result = debugController.parse(combinedError);

        assertFalse(result.fixSuggestions().isEmpty(), "Should aggregate suggestions across matches");
        assertTrue(
                result.fixSuggestions().stream()
                        .anyMatch(s -> "secondary.fix".equals(s.getId())),
                "Should include suggestions contributed by later matched patterns");
    }

    @Test
    void testKeepsHighestConfidenceSuggestionWhenMatchesProduceDuplicateIds() {
        fixSuggester.registerSuggestion(
                "DuplicateError: (.+?)",
                FixSuggestion.builder()
                        .id("duplicate.fix")
                        .description("Lower-confidence duplicate fix")
                        .fixPattern("// duplicate fix")
                        .language("test")
                        .confidence(0.4)
                        .build());
        fixSuggester.registerSuggestion(
                "DuplicateSecondaryError: (.+?)",
                FixSuggestion.builder()
                        .id("duplicate.fix")
                        .description("Higher-confidence duplicate fix")
                        .fixPattern("// duplicate fix improved")
                        .language("test")
                        .confidence(0.95)
                        .build());

        String combinedError = "DuplicateError: first\nDuplicateSecondaryError: second";
        ErrorPatternManager.MatchedPattern duplicateMatch =
                matchedPattern("duplicate_error", "DuplicateError: first", "Duplicate");
        ErrorPatternManager.MatchedPattern duplicateSecondaryMatch =
                matchedPattern(
                        "duplicate_secondary_error",
                        "DuplicateSecondaryError: second",
                        "Duplicate secondary");
        when(errorPatternManager.findMatches(anyString(), eq("node")))
                .thenReturn(List.of(duplicateMatch, duplicateSecondaryMatch));

        DebugController.ParseResult result = debugController.parse(combinedError);

        List<FixSuggestion> duplicateSuggestions =
                result.fixSuggestions().stream()
                        .filter(suggestion -> "duplicate.fix".equals(suggestion.getId()))
                        .toList();

        assertEquals(1, duplicateSuggestions.size(), "Duplicate suggestion IDs should be merged");
        assertEquals(
                "Higher-confidence duplicate fix",
                duplicateSuggestions.get(0).getDescription(),
                "Merged suggestion should keep the highest-confidence candidate");
    }

    private static ErrorPatternManager.MatchedPattern matchedPattern(
            String name, String matchedText, String suggestion) {
        ErrorPatternManager.ErrorPattern pattern = mock(ErrorPatternManager.ErrorPattern.class);
        when(pattern.getName()).thenReturn(name);
        when(pattern.getSuggestion()).thenReturn(suggestion);
        return new ErrorPatternManager.MatchedPattern(pattern, matchedText);
    }

    /** A test stack trace parser that recognizes a simple pattern for testing. */
    private static class TestStackTraceParser implements StackTraceParser {
        @Override
        public List<TraceFrame> parse(String content) {
            if (content.contains("NullPointerException")) {
                return List.of(
                        new TraceFrame("Test.java", 10, "main", "NullPointerException in main"));
            }
            if (content.contains("Error:")) {
                return List.of(
                        new TraceFrame("test.js", 10, "testMethod", "Test error in test.js"));
            }
            return List.of();
        }
    }
}
