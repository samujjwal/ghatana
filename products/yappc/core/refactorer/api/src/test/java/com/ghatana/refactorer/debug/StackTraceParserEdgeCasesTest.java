package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for edge cases in stack trace parsing. 
 * @doc.type class
 * @doc.purpose Handles stack trace parser edge cases test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class StackTraceParserEdgeCasesTest {

    private JavaStackTraceParser javaParser;
    private NodeStackTraceParser nodeParser;

    @BeforeEach
    void setUp() {
        javaParser = new JavaStackTraceParser();
        nodeParser = new NodeStackTraceParser();
    }

    @Test
    void parse_windowsPaths_parsesCorrectly() throws IOException {
        // Given: A stack trace with Windows paths
        String stackTrace = readGoldenFile("windows_stacktrace.txt");

        // When: Parsing the stack trace
        List<StackTraceParser.TraceFrame> frames = javaParser.parse(stackTrace);

        // Then: Windows paths should be parsed correctly
        assertFalse(frames.isEmpty(), "Should parse frames from Windows paths");

        // Debug: Print all parsed frames
        System.out.println("Parsed frames:");
        frames.forEach(frame -> System.out.printf("- %s%n", frame));

        // Verify we can parse standard Java stack frames
        boolean foundMainFrame =
                frames.stream()
                        .anyMatch(
                                frame ->
                                        "main".equals(frame.function())
                                                && "Main.java".equals(frame.file())
                                                && frame.line() == 10);
        assertTrue(foundMainFrame, "Should parse standard Java stack frames");

        // Verify we can parse frames with line numbers and methods
        boolean foundHandleFileFrame =
                frames.stream()
                        .anyMatch(
                                frame ->
                                        "handleFile".equals(frame.function())
                                                && "WindowsPath.java".equals(frame.file())
                                                && frame.line() == 123);
        assertTrue(foundHandleFileFrame, "Should parse method with line numbers");

        // Verify we can parse accessor methods (synthetic)
        boolean foundAccessorFrame =
                frames.stream()
                        .anyMatch(
                                frame ->
                                        frame.function() != null
                                                && frame.function().startsWith("access$"));
        assertTrue(foundAccessorFrame, "Should handle accessor methods");

        // Verify we can parse anonymous class frames
        boolean foundAnonymousFrame =
                frames.stream()
                        .anyMatch(
                                frame ->
                                        frame.function() != null && frame.function().contains("$"));
        assertTrue(foundAnonymousFrame, "Should handle anonymous class frames");
    }

    @Test
    void parse_asyncNodeStackTraces_parsesCorrectly() throws IOException {
        // Given: A Node.js async stack trace
        String stackTrace = readGoldenFile("node_async_stacktrace.txt");

        // When: Parsing the stack trace
        List<StackTraceParser.TraceFrame> frames = nodeParser.parse(stackTrace);

        // Then: Async frames should be parsed correctly
        assertFalse(frames.isEmpty(), "Should parse async frames");

        // Verify async/await frames
        boolean foundAsyncFrame =
                frames.stream()
                        .anyMatch(
                                frame ->
                                        frame.function() != null
                                                && frame.function().startsWith("async "));
        assertTrue(foundAsyncFrame, "Should handle async/await frames");

        // Verify Promise.all frames
        boolean foundPromiseAll =
                frames.stream()
                        .anyMatch(
                                frame ->
                                        frame.function() != null
                                                && frame.function().contains("Promise.all"));
        assertTrue(foundPromiseAll, "Should handle Promise.all frames");
    }

    private String readGoldenFile(String filename) throws IOException {
        Path path = Paths.get("src", "test", "resources", "golden", filename);
        return Files.readString(path);
    }
}
