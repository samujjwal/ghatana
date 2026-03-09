package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
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
 * @doc.purpose Handles debug controller test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DebugControllerTest {

    @Mock private StackTraceParser mockParser;
    @Mock private ErrorPatternManager mockPatternManager;

    private DebugController controller;

    @BeforeEach
    void setUp() {
        controller = new DebugController(List.of(mockParser), mockPatternManager);
    }

    @Test
    void parse_withMatchingParser_returnsFrames() {
        // Arrange
        String trace = "some stack trace";
        var expectedFrame = new StackTraceParser.TraceFrame("file.java", 42, "method", "raw");
        when(mockParser.parse(anyString())).thenReturn(List.of(expectedFrame));

        // Act
        var result = controller.parse(trace);

        // Assert
        assertTrue(result.success(), "Expected successful parse");
        assertEquals(1, result.frames().size(), "Expected one frame");
        assertEquals(expectedFrame, result.frames().get(0), "Unexpected frame");
        verify(mockParser).parse(trace);
    }

    @Test
    void parse_noMatchingParser_returnsEmptyFrames() {
        // Arrange
        String trace = "some stack trace";
        when(mockParser.parse(anyString())).thenReturn(List.of());

        // Act
        var result = controller.parse(trace);

        // Assert
        assertFalse(result.success(), "Expected unsuccessful parse");
        assertTrue(result.frames().isEmpty(), "Expected no frames");
        verify(mockParser).parse(trace);
    }

    @Test
    void parse_nullInput_returnsEmptyFrames() {
        // Act
        var result = controller.parse(null);

        // Assert
        assertFalse(result.success(), "Expected unsuccessful parse");
        assertTrue(result.frames().isEmpty(), "Expected no frames");
        verifyNoInteractions(mockParser);
    }

    @Test
    void parse_emptyInput_returnsEmptyFrames() {
        // Act
        var result = controller.parse("");

        // Assert
        assertFalse(result.success(), "Expected unsuccessful parse");
        assertTrue(result.frames().isEmpty(), "Expected no frames");
        verifyNoInteractions(mockParser);
    }

    @Test
    void parse_withErrorPatterns_includesMatchedPatterns() {
        // Arrange
        String trace = "some stack trace";
        var frame = new StackTraceParser.TraceFrame("file.java", 42, "method", trace);
        when(mockParser.parse(anyString())).thenReturn(List.of(frame));

        // Create a mock ErrorPattern
        var errorPattern = mock(ErrorPatternManager.ErrorPattern.class);
        when(errorPattern.getName()).thenReturn("test:error");
        when(errorPattern.getSuggestion()).thenReturn("Test suggestion");

        // Create MatchedPattern with correct constructor
        var matchedPattern = new ErrorPatternManager.MatchedPattern(errorPattern, "matched text");
        when(mockPatternManager.findMatches(anyString(), anyString()))
                .thenReturn(List.of(matchedPattern));

        // Act
        var result = controller.parse(trace);

        // Assert
        assertTrue(result.success(), "Expected successful parse");
        assertEquals(1, result.frames().size(), "Expected one frame");
        assertFalse(result.matches().isEmpty(), "Expected matched patterns");
        assertEquals("test:error", result.matches().get(0).getPattern().getName());
        assertEquals("Test suggestion", result.matches().get(0).getPattern().getSuggestion());
    }

    @Test
    void createDefault_returnsControllerWithAllParsers() {
        // Act
        var defaultController = DebugController.createDefault();

        // Assert - Should have all standard parsers
        var result = defaultController.parse("");
        assertFalse(result.success(), "Expected unsuccessful parse for empty input");
    }

    @Test
    void detectLanguage_javaFileExtension_returnsJava() {
        // Arrange
        var frame =
                new StackTraceParser.TraceFrame("Test.java", 10, "main", "Test.main(Test.java:10)");

        // Act
        String language = controller.detectLanguage(List.of(frame), "");

        // Assert
        assertEquals("java", language, "Expected Java language detection");
    }

    @Test
    void detectLanguage_pythonFileExtension_returnsPython() {
        // Arrange
        var frame =
                new StackTraceParser.TraceFrame(
                        "test.py", 5, "<module>", "File \"test.py\", line 5, in <module>");

        // Act
        String language = controller.detectLanguage(List.of(frame), "");

        // Assert
        assertEquals("python", language, "Expected Python language detection");
    }

    @Test
    void detectLanguage_nodeJsFileExtension_returnsNode() {
        // Arrange
        var frame =
                new StackTraceParser.TraceFrame(
                        "app.js",
                        10,
                        "Object.<anonymous>",
                        "at Object.<anonymous> (/path/to/app.js:10:15)");

        // Act
        String language = controller.detectLanguage(List.of(frame), "");

        // Assert
        assertEquals("node", language, "Expected Node.js language detection");
    }

    @Test
    void detectLanguage_goPanic_returnsGo() {
        // Act
        String language =
                controller.detectLanguage(
                        List.of(),
                        "panic: runtime error: index out of range [5] with length 3\n"
                                + "goroutine 1 [running]:\n"
                                + "main.main()\n\t/tmp/sandbox1234/main.go:8 +0x1b");

        // Assert
        assertEquals("go", language, "Expected Go language detection from panic");
    }

    @Test
    void detectLanguage_rustPanic_returnsRust() {
        // Act
        String language =
                controller.detectLanguage(
                        List.of(),
                        "thread 'main' panicked at 'index out of bounds: the len is 3 but the index"
                            + " is 5', src/main.rs:4:5\n"
                            + "note: run with `RUST_BACKTRACE=1` environment variable to display a"
                            + " backtrace");

        // Assert
        assertEquals("rust", language, "Expected Rust language detection from panic");
    }

    @Test
    void detectLanguage_noFramesOrContent_returnsJavaAsDefault() {
        // Act
        String language = controller.detectLanguage(List.of(), "");

        // Assert
        assertEquals("java", language, "Expected Java as default language");
    }
}
