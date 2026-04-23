package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles confidence scorer test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class ConfidenceScorerTest {

    private ConfidenceScorer scorer;
    private FixContext context;

    @BeforeEach
    void setUp() { // GH-90000
        scorer = new ConfidenceScorer(); // GH-90000
        context = new FixContext("java", "Test.java"); // GH-90000
    }

    @Test
    void testCalculateConfidence_WithPerfectMatch() { // GH-90000
        FixSuggestion suggestion =
                FixSuggestion.builder() // GH-90000
                        .id("test.id")
                        .description("Test suggestion")
                        .fixPattern("${statement}")
                        .language("java")
                        .errorPattern("NullPointerException")
                        .confidence(0.8) // GH-90000
                        .build(); // GH-90000

        double confidence =
                scorer.calculateConfidence( // GH-90000
                        suggestion,
                        "java.lang.NullPointerException: Cannot invoke \"Object.toString()\"" // GH-90000
                                + " because \"obj\" is null",
                        context);

        assertTrue(confidence > 0.7, "Confidence should be high for a perfect match"); // GH-90000
    }

    @Test
    void testCalculateConfidence_WithNoMatch() { // GH-90000
        FixSuggestion suggestion =
                FixSuggestion.builder() // GH-90000
                        .id("test.id")
                        .description("Test suggestion")
                        .fixPattern("${statement}")
                        .language("java")
                        .errorPattern("PatternThatWontMatch")
                        .confidence(0.8) // GH-90000
                        .build(); // GH-90000

        double confidence =
                scorer.calculateConfidence( // GH-90000
                        suggestion,
                        "java.lang.NullPointerException: Cannot invoke \"Object.toString()\"" // GH-90000
                                + " because \"obj\" is null",
                        context);

        assertTrue(confidence < 0.5, "Confidence should be low for no match"); // GH-90000
    }

    @Test
    void testRecordAndUseHistoricalData() { // GH-90000
        String suggestionId = "test.historical";

        // Initial confidence with no history
        FixSuggestion suggestion = createTestSuggestion(suggestionId); // GH-90000
        double initialConfidence = scorer.calculateConfidence(suggestion, "Some error", context); // GH-90000

        // Record some successful outcomes
        for (int i = 0; i < 5; i++) { // GH-90000
            scorer.recordFixOutcome(suggestionId, true); // GH-90000
        }

        // Confidence should increase with successful history
        double improvedConfidence =
                scorer.calculateConfidence( // GH-90000
                        createTestSuggestion(suggestionId), "Some error", context); // GH-90000

        assertTrue( // GH-90000
                improvedConfidence > initialConfidence,
                "Confidence should improve with successful history");

        // Record some failures
        for (int i = 0; i < 5; i++) { // GH-90000
            scorer.recordFixOutcome(suggestionId, false); // GH-90000
        }

        // Confidence should decrease with failures
        double reducedConfidence =
                scorer.calculateConfidence( // GH-90000
                        createTestSuggestion(suggestionId), "Some error", context); // GH-90000

        assertTrue( // GH-90000
                reducedConfidence < improvedConfidence,
                "Confidence should decrease with failed attempts");
    }

    @Test
    void testLanguageMismatch() { // GH-90000
        FixSuggestion suggestion =
                FixSuggestion.builder() // GH-90000
                        .id("test.language")
                        .description("Test suggestion")
                        .fixPattern("${statement}")
                        .language("python") // Different from context language (java)
                        .errorPattern(".*")
                        .confidence(0.8) // GH-90000
                        .build(); // GH-90000

        double confidence = scorer.calculateConfidence(suggestion, "Some error", context); // GH-90000

        assertTrue(confidence < 0.6, "Confidence should be lower for language mismatch"); // GH-90000
    }

    @Test
    void testParsedStackTraceContextImprovesConfidence() { // GH-90000
        FixSuggestion suggestion =
                FixSuggestion.builder() // GH-90000
                        .id("test.node")
                        .description("Node suggestion")
                        .fixPattern("${statement}")
                        .language("node")
                        .errorPattern("TypeError")
                        .confidence(0.8) // GH-90000
                        .build(); // GH-90000

        FixContext parsedContext = new FixContext("node", "file:///app/dist/index.mjs"); // GH-90000
        parsedContext.setMetadata("stackTraceParsed", true); // GH-90000
        parsedContext.setMetadata("parsedFrameCount", 3); // GH-90000

        FixContext unparsedContext = new FixContext("node", ""); // GH-90000

        double parsedConfidence =
                scorer.calculateConfidence( // GH-90000
                        suggestion,
                        "TypeError: x is not a function",
                        parsedContext);
        double unparsedConfidence =
                scorer.calculateConfidence( // GH-90000
                        suggestion,
                        "TypeError: x is not a function",
                        unparsedContext);

        assertTrue( // GH-90000
                parsedConfidence > unparsedConfidence,
                "Parsed stack trace context should improve confidence");
    }

    @Test
    void testNodeTypeScriptModuleExtensionsAreTreatedAsMatchingContext() { // GH-90000
        FixSuggestion suggestion =
                FixSuggestion.builder() // GH-90000
                        .id("test.node.ts")
                        .description("Node TypeScript suggestion")
                        .fixPattern("${statement}")
                        .language("node")
                        .errorPattern("TypeError")
                        .confidence(0.8) // GH-90000
                        .build(); // GH-90000

        FixContext mtsContext = new FixContext("node", "file:///app/dist/index.mts"); // GH-90000
        FixContext ctsContext = new FixContext("node", "file:///app/dist/index.cts"); // GH-90000
        FixContext javaContext = new FixContext("node", "Test.java"); // GH-90000

        double mtsConfidence =
                scorer.calculateConfidence( // GH-90000
                        suggestion,
                        "TypeError: x is not a function",
                        mtsContext);
        double ctsConfidence =
                scorer.calculateConfidence( // GH-90000
                        suggestion,
                        "TypeError: x is not a function",
                        ctsContext);
        double mismatchedConfidence =
                scorer.calculateConfidence( // GH-90000
                        suggestion,
                        "TypeError: x is not a function",
                        javaContext);

        assertTrue( // GH-90000
                mtsConfidence > mismatchedConfidence,
                "MTS files should be treated as matching Node/TypeScript context");
        assertTrue( // GH-90000
                ctsConfidence > mismatchedConfidence,
                "CTS files should be treated as matching Node/TypeScript context");
    }

    private FixSuggestion createTestSuggestion(String id) { // GH-90000
        return FixSuggestion.builder() // GH-90000
                .id(id) // GH-90000
                .description("Test suggestion")
                .fixPattern("${statement}")
                .language("java")
                .errorPattern(".*")
                .confidence(0.5) // GH-90000
                .build(); // GH-90000
    }
}
