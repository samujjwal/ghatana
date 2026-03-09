package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
/**
 * @doc.type class
 * @doc.purpose Handles test failure classifier test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class TestFailureClassifierTest {

    @Mock private ErrorPatternManager mockPatternManager;

    private TestFailureClassifier classifier;

    @BeforeEach
    void setUp() {
        // Create a real classifier by default, but tests can override with mock if needed
        classifier = new TestFailureClassifier();
    }

    @Test
    void javaNpe_isClassified() {
        String raw =
                "java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"str\""
                        + " is null\n"
                        + "\tat com.example.Foo.bar(Foo.java:42)";
        var frames =
                List.of(
                        new StackTraceParser.TraceFrame(
                                "Foo.java", 42, "com.example.Foo.bar", raw));

        var suggestions = classifier.classify(frames, raw);
        assertFalse(suggestions.isEmpty(), "No suggestions for NPE");
        assertTrue(
                suggestions.stream().anyMatch(s -> s.category().startsWith("java:npe")),
                "Expected NPE suggestion");

        // Verify the suggestion has the correct file/line info
        var npeSuggestion =
                suggestions.stream()
                        .filter(s -> s.category().startsWith("java:npe"))
                        .findFirst()
                        .orElseThrow();

        assertEquals("Foo.java", npeSuggestion.file());
        assertEquals(42, npeSuggestion.line());
    }

    @Test
    void usesCustomPatternManagerWhenProvided() {
        // Setup mock pattern
        var mockErrorPattern = mock(ErrorPatternManager.ErrorPattern.class);
        when(mockErrorPattern.getName()).thenReturn("test-pattern");
        when(mockErrorPattern.getCategory()).thenReturn("test:category");
        when(mockErrorPattern.getSeverity()).thenReturn(ErrorPatternManager.Severity.MEDIUM);
        when(mockErrorPattern.getSuggestion()).thenReturn("Test suggestion");

        var mockMatch = new ErrorPatternManager.MatchedPattern(mockErrorPattern, "test error");

        when(mockPatternManager.findMatches(anyString(), anyString()))
                .thenReturn(List.of(mockMatch));

        // Create classifier with mock
        classifier = new TestFailureClassifier(mockPatternManager);

        // Test
        var suggestions = classifier.classify(List.of(), "test error");

        // Verify
        verify(mockPatternManager).findMatches("test error", "java"); // Default language
        assertEquals(1, suggestions.size());
        assertEquals("test:category", suggestions.get(0).category());
        assertEquals("Test suggestion", suggestions.get(0).detail());
    }

    @Test
    void pythonImportError_isClassified() {
        String raw =
                "ImportError: cannot import name 'x'\n"
                        + "  File \"/work/app.py\", line 10, in <module>";
        var frames = List.of(new StackTraceParser.TraceFrame("/work/app.py", 10, "<module>", raw));

        var suggestions = classifier.classify(frames, raw);
        assertFalse(suggestions.isEmpty(), "No suggestions for ImportError");
        assertTrue(
                suggestions.stream().anyMatch(s -> s.category().startsWith("python:import")),
                "Expected import error suggestion");
    }

    @Test
    void pythonAttributeError_isClassified() {
        String raw =
                "AttributeError: 'NoneType' object has no attribute 'foo'\n"
                        + "  File \"/work/app.py\", line 15, in <module>";
        var frames = List.of(new StackTraceParser.TraceFrame("/work/app.py", 15, "<module>", raw));

        var suggestions = classifier.classify(frames, raw);
        assertFalse(suggestions.isEmpty(), "No suggestions for AttributeError");
        assertTrue(
                suggestions.stream().anyMatch(s -> s.category().startsWith("python:attribute")),
                "Expected attribute error suggestion");
    }

    @Test
    void nodeTypeError_isClassified() {
        String raw = "TypeError: x is not a function\n    at doThing (/app/index.js:15:5)";
        var frames = List.of(new StackTraceParser.TraceFrame("/app/index.js", 15, "doThing", raw));

        var suggestions = classifier.classify(frames, raw);
        assertFalse(suggestions.isEmpty(), "No suggestions for TypeError");
        assertTrue(
                suggestions.stream().anyMatch(s -> s.category().startsWith("node:type")),
                "Expected type error suggestion");
    }

    @Test
    void goPanic_isClassified() {
        String raw =
                "panic: runtime error: index out of range [5] with length 3\n\n"
                        + "goroutine 1 [running]:\n"
                        + "main.main()\n\t/Users/example/go/src/example.com/app/main.go:10 +0x1b5";
        var frames =
                List.of(
                        new StackTraceParser.TraceFrame(
                                "/Users/example/go/src/example.com/app/main.go",
                                10,
                                "main.main",
                                raw));

        var suggestions = classifier.classify(frames, raw);
        assertFalse(suggestions.isEmpty(), "No suggestions for Go panic");
        assertTrue(
                suggestions.stream().anyMatch(s -> s.category().startsWith("go:panic")),
                "Expected Go panic suggestion");
    }

    @Test
    void rustPanic_isClassified() {
        String raw =
                "thread 'main' panicked at 'index out of bounds: the len is 3 but the index is 5',"
                        + " src/main.rs:10:5\n"
                        + "note: run with `RUST_BACKTRACE=1` environment variable to display a"
                        + " backtrace";
        var frames = List.of(new StackTraceParser.TraceFrame("src/main.rs", 10, "main", raw));

        var suggestions = classifier.classify(frames, raw);
        assertFalse(suggestions.isEmpty(), "No suggestions for Rust panic");
        assertTrue(
                suggestions.stream().anyMatch(s -> s.category().startsWith("rust:panic")),
                "Expected Rust panic suggestion");
    }

    @Test
    void emptyFrames_returnsEmptySuggestions() {
        var suggestions = classifier.classify(List.of(), "");
        assertTrue(suggestions.isEmpty(), "Expected no suggestions for empty frames");
    }

    @Test
    void nullInput_returnsEmptySuggestions() {
        var suggestions = classifier.classify(null, null);
        assertTrue(suggestions.isEmpty(), "Expected no suggestions for null input");
    }

    @Test
    void unknownError_returnsGenericSuggestion() {
        String raw = "SomeUnknownError: Something unexpected happened\n    at file.js:10:5";
        var frames = List.of(new StackTraceParser.TraceFrame("file.js", 10, "<unknown>", raw));

        var suggestions = classifier.classify(frames, raw);
        assertFalse(suggestions.isEmpty(), "Expected at least a generic suggestion");
        assertTrue(
                suggestions.stream().anyMatch(s -> s.category().equals("generic")),
                "Expected generic error suggestion");
    }
}
