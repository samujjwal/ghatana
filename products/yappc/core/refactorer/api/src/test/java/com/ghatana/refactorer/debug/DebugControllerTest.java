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

@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        controller = new DebugController(List.of(mockParser), mockPatternManager); // GH-90000
    }

    @Test
    void parse_withMatchingParser_returnsFrames() { // GH-90000
        // Arrange
        String trace = "some stack trace";
        var expectedFrame = new StackTraceParser.TraceFrame("file.java", 42, "method", "raw"); // GH-90000
        when(mockParser.parse(anyString())).thenReturn(List.of(expectedFrame)); // GH-90000

        // Act
        var result = controller.parse(trace); // GH-90000

        // Assert
        assertTrue(result.success(), "Expected successful parse"); // GH-90000
        assertEquals(1, result.frames().size(), "Expected one frame"); // GH-90000
        assertEquals(expectedFrame, result.frames().get(0), "Unexpected frame"); // GH-90000
        verify(mockParser).parse(trace); // GH-90000
    }

    @Test
    void parse_noMatchingParser_returnsEmptyFrames() { // GH-90000
        // Arrange
        String trace = "some stack trace";
        when(mockParser.parse(anyString())).thenReturn(List.of()); // GH-90000

        // Act
        var result = controller.parse(trace); // GH-90000

        // Assert
        assertFalse(result.success(), "Expected unsuccessful parse"); // GH-90000
        assertTrue(result.frames().isEmpty(), "Expected no frames"); // GH-90000
        verify(mockParser).parse(trace); // GH-90000
    }

    @Test
    void parse_nullInput_returnsEmptyFrames() { // GH-90000
        // Act
        var result = controller.parse(null); // GH-90000

        // Assert
        assertFalse(result.success(), "Expected unsuccessful parse"); // GH-90000
        assertTrue(result.frames().isEmpty(), "Expected no frames"); // GH-90000
        verifyNoInteractions(mockParser); // GH-90000
    }

    @Test
    void parse_emptyInput_returnsEmptyFrames() { // GH-90000
        // Act
        var result = controller.parse("");

        // Assert
        assertFalse(result.success(), "Expected unsuccessful parse"); // GH-90000
        assertTrue(result.frames().isEmpty(), "Expected no frames"); // GH-90000
        verifyNoInteractions(mockParser); // GH-90000
    }

    @Test
    void parse_withErrorPatterns_includesMatchedPatterns() { // GH-90000
        // Arrange
        String trace = "some stack trace";
        var frame = new StackTraceParser.TraceFrame("file.java", 42, "method", trace); // GH-90000
        when(mockParser.parse(anyString())).thenReturn(List.of(frame)); // GH-90000

        // Create a mock ErrorPattern
        var errorPattern = mock(ErrorPatternManager.ErrorPattern.class); // GH-90000
        when(errorPattern.getName()).thenReturn("test:error");
        when(errorPattern.getSuggestion()).thenReturn("Test suggestion");

        // Create MatchedPattern with correct constructor
        var matchedPattern = new ErrorPatternManager.MatchedPattern(errorPattern, "matched text"); // GH-90000
        when(mockPatternManager.findMatches(anyString(), anyString())) // GH-90000
                .thenReturn(List.of(matchedPattern)); // GH-90000

        // Act
        var result = controller.parse(trace); // GH-90000

        // Assert
        assertTrue(result.success(), "Expected successful parse"); // GH-90000
        assertEquals(1, result.frames().size(), "Expected one frame"); // GH-90000
        assertFalse(result.matches().isEmpty(), "Expected matched patterns"); // GH-90000
        assertEquals("test:error", result.matches().get(0).getPattern().getName()); // GH-90000
        assertEquals("Test suggestion", result.matches().get(0).getPattern().getSuggestion()); // GH-90000
    }

    @Test
    void createDefault_returnsControllerWithAllParsers() { // GH-90000
        // Act
        var defaultController = DebugController.createDefault(); // GH-90000

        // Assert - Should have all standard parsers
        var result = defaultController.parse("");
        assertFalse(result.success(), "Expected unsuccessful parse for empty input"); // GH-90000
    }

    @Test
    void detectLanguage_javaFileExtension_returnsJava() { // GH-90000
        // Arrange
        var frame =
                new StackTraceParser.TraceFrame("Test.java", 10, "main", "Test.main(Test.java:10)"); // GH-90000

        // Act
        String language = controller.detectLanguage(List.of(frame), ""); // GH-90000

        // Assert
        assertEquals("java", language, "Expected Java language detection"); // GH-90000
    }

    @Test
    void detectLanguage_pythonFileExtension_returnsPython() { // GH-90000
        // Arrange
        var frame =
                new StackTraceParser.TraceFrame( // GH-90000
                        "test.py", 5, "<module>", "File \"test.py\", line 5, in <module>");

        // Act
        String language = controller.detectLanguage(List.of(frame), ""); // GH-90000

        // Assert
        assertEquals("python", language, "Expected Python language detection"); // GH-90000
    }

    @Test
    void detectLanguage_nodeJsFileExtension_returnsNode() { // GH-90000
        // Arrange
        var frame =
                new StackTraceParser.TraceFrame( // GH-90000
                        "app.js",
                        10,
                        "Object.<anonymous>",
                        "at Object.<anonymous> (/path/to/app.js:10:15)"); // GH-90000

        // Act
        String language = controller.detectLanguage(List.of(frame), ""); // GH-90000

        // Assert
        assertEquals("node", language, "Expected Node.js language detection"); // GH-90000
    }

    @Test
    void detectLanguage_goPanic_returnsGo() { // GH-90000
        // Act
        String language =
                controller.detectLanguage( // GH-90000
                        List.of(), // GH-90000
                        "panic: runtime error: index out of range [5] with length 3\n"
                                + "goroutine 1 [running]:\n"
                                + "main.main()\n\t/tmp/sandbox1234/main.go:8 +0x1b"); // GH-90000

        // Assert
        assertEquals("go", language, "Expected Go language detection from panic"); // GH-90000
    }

    @Test
    void detectLanguage_rustPanic_returnsRust() { // GH-90000
        // Act
        String language =
                controller.detectLanguage( // GH-90000
                        List.of(), // GH-90000
                        "thread 'main' panicked at 'index out of bounds: the len is 3 but the index"
                            + " is 5', src/main.rs:4:5\n"
                            + "note: run with `RUST_BACKTRACE=1` environment variable to display a"
                            + " backtrace");

        // Assert
        assertEquals("rust", language, "Expected Rust language detection from panic"); // GH-90000
    }

    @Test
    void detectLanguage_noFramesOrContent_returnsJavaAsDefault() { // GH-90000
        // Act
        String language = controller.detectLanguage(List.of(), ""); // GH-90000

        // Assert
        assertEquals("java", language, "Expected Java as default language"); // GH-90000
    }
}
