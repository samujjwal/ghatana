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
    void setUp() { 
        scorer = new ConfidenceScorer(); 
        context = new FixContext("java", "Test.java"); 
    }

    @Test
    void testCalculateConfidence_WithPerfectMatch() { 
        FixSuggestion suggestion =
                FixSuggestion.builder() 
                        .id("test.id")
                        .description("Test suggestion")
                        .fixPattern("${statement}")
                        .language("java")
                        .errorPattern("NullPointerException")
                        .confidence(0.8) 
                        .build(); 

        double confidence =
                scorer.calculateConfidence( 
                        suggestion,
                        "java.lang.NullPointerException: Cannot invoke \"Object.toString()\"" 
                                + " because \"obj\" is null",
                        context);

        assertTrue(confidence > 0.7, "Confidence should be high for a perfect match"); 
    }

    @Test
    void testCalculateConfidence_WithNoMatch() { 
        FixSuggestion suggestion =
                FixSuggestion.builder() 
                        .id("test.id")
                        .description("Test suggestion")
                        .fixPattern("${statement}")
                        .language("java")
                        .errorPattern("PatternThatWontMatch")
                        .confidence(0.8) 
                        .build(); 

        double confidence =
                scorer.calculateConfidence( 
                        suggestion,
                        "java.lang.NullPointerException: Cannot invoke \"Object.toString()\"" 
                                + " because \"obj\" is null",
                        context);

        assertTrue(confidence < 0.5, "Confidence should be low for no match"); 
    }

    @Test
    void testRecordAndUseHistoricalData() { 
        String suggestionId = "test.historical";

        // Initial confidence with no history
        FixSuggestion suggestion = createTestSuggestion(suggestionId); 
        double initialConfidence = scorer.calculateConfidence(suggestion, "Some error", context); 

        // Record some successful outcomes
        for (int i = 0; i < 5; i++) { 
            scorer.recordFixOutcome(suggestionId, true); 
        }

        // Confidence should increase with successful history
        double improvedConfidence =
                scorer.calculateConfidence( 
                        createTestSuggestion(suggestionId), "Some error", context); 

        assertTrue( 
                improvedConfidence > initialConfidence,
                "Confidence should improve with successful history");

        // Record some failures
        for (int i = 0; i < 5; i++) { 
            scorer.recordFixOutcome(suggestionId, false); 
        }

        // Confidence should decrease with failures
        double reducedConfidence =
                scorer.calculateConfidence( 
                        createTestSuggestion(suggestionId), "Some error", context); 

        assertTrue( 
                reducedConfidence < improvedConfidence,
                "Confidence should decrease with failed attempts");
    }

    @Test
    void testLanguageMismatch() { 
        FixSuggestion suggestion =
                FixSuggestion.builder() 
                        .id("test.language")
                        .description("Test suggestion")
                        .fixPattern("${statement}")
                        .language("python") // Different from context language (java)
                        .errorPattern(".*")
                        .confidence(0.8) 
                        .build(); 

        double confidence = scorer.calculateConfidence(suggestion, "Some error", context); 

        assertTrue(confidence < 0.6, "Confidence should be lower for language mismatch"); 
    }

    @Test
    void testParsedStackTraceContextImprovesConfidence() { 
        FixSuggestion suggestion =
                FixSuggestion.builder() 
                        .id("test.node")
                        .description("Node suggestion")
                        .fixPattern("${statement}")
                        .language("node")
                        .errorPattern("TypeError")
                        .confidence(0.8) 
                        .build(); 

        FixContext parsedContext = new FixContext("node", "file:///app/dist/index.mjs"); 
        parsedContext.setMetadata("stackTraceParsed", true); 
        parsedContext.setMetadata("parsedFrameCount", 3); 

        FixContext unparsedContext = new FixContext("node", ""); 

        double parsedConfidence =
                scorer.calculateConfidence( 
                        suggestion,
                        "TypeError: x is not a function",
                        parsedContext);
        double unparsedConfidence =
                scorer.calculateConfidence( 
                        suggestion,
                        "TypeError: x is not a function",
                        unparsedContext);

        assertTrue( 
                parsedConfidence > unparsedConfidence,
                "Parsed stack trace context should improve confidence");
    }

    @Test
    void testNodeTypeScriptModuleExtensionsAreTreatedAsMatchingContext() { 
        FixSuggestion suggestion =
                FixSuggestion.builder() 
                        .id("test.node.ts")
                        .description("Node TypeScript suggestion")
                        .fixPattern("${statement}")
                        .language("node")
                        .errorPattern("TypeError")
                        .confidence(0.8) 
                        .build(); 

        FixContext mtsContext = new FixContext("node", "file:///app/dist/index.mts"); 
        FixContext ctsContext = new FixContext("node", "file:///app/dist/index.cts"); 
        FixContext javaContext = new FixContext("node", "Test.java"); 

        double mtsConfidence =
                scorer.calculateConfidence( 
                        suggestion,
                        "TypeError: x is not a function",
                        mtsContext);
        double ctsConfidence =
                scorer.calculateConfidence( 
                        suggestion,
                        "TypeError: x is not a function",
                        ctsContext);
        double mismatchedConfidence =
                scorer.calculateConfidence( 
                        suggestion,
                        "TypeError: x is not a function",
                        javaContext);

        assertTrue( 
                mtsConfidence > mismatchedConfidence,
                "MTS files should be treated as matching Node/TypeScript context");
        assertTrue( 
                ctsConfidence > mismatchedConfidence,
                "CTS files should be treated as matching Node/TypeScript context");
    }

    private FixSuggestion createTestSuggestion(String id) { 
        return FixSuggestion.builder() 
                .id(id) 
                .description("Test suggestion")
                .fixPattern("${statement}")
                .language("java")
                .errorPattern(".*")
                .confidence(0.5) 
                .build(); 
    }
}
