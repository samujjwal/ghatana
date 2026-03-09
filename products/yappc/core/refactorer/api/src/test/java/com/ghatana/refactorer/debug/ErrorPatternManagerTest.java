package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link ErrorPatternManager} that verify error pattern matching functionality. 
 * @doc.type class
 * @doc.purpose Handles error pattern manager test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class ErrorPatternManagerTest {

    private ErrorPatternManager manager;

    @BeforeEach
    void setUp() throws IOException {
        // Load the test patterns file from resources
        try (InputStream is =
                getClass().getClassLoader().getResourceAsStream("stacktrace.patterns.json")) {
            if (is == null) {
                throw new IllegalStateException("Test patterns file not found in resources");
            }
            // Create a temporary file with the patterns
            Path tempFile = Files.createTempFile("test-patterns", ".json");
            tempFile.toFile().deleteOnExit();
            Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Set the system property to point to our test patterns file
            System.setProperty("stacktrace.patterns.file", tempFile.toString());

            // Create a new instance for each test
            manager = new ErrorPatternManager();
        }
    }

    @Test
    void findMatches_javaNpe() {
        String error =
                "java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"str\""
                        + " is null";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "java");

        assertFalse(matches.isEmpty(), "Expected to find NPE pattern match");
        assertEquals("npe", matches.get(0).getName());
        assertEquals("java:npe", matches.get(0).getCategory());
        assertEquals(ErrorPatternManager.Severity.HIGH, matches.get(0).getSeverity());
        assertTrue(matches.get(0).getSuggestion().contains("Check for null references"));
    }

    @Test
    void findMatches_pythonImportError() {
        String error = "ImportError: No module named 'nonexistent_module'";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "python");

        assertFalse(matches.isEmpty(), "Expected to find import error pattern match");
        assertEquals("import_error", matches.get(0).getName());
        assertEquals("python:import_error", matches.get(0).getCategory());
        assertTrue(matches.get(0).getSuggestion().contains("Install the missing package"));
    }

    @Test
    void findMatches_nodeTypeError() {
        String error = "TypeError: Cannot read property 'length' of undefined";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "node");

        assertFalse(matches.isEmpty(), "Expected to find type error pattern match");
        assertEquals("type_error", matches.get(0).getName());
        assertEquals("node:type_error", matches.get(0).getCategory());
        assertTrue(matches.get(0).getSuggestion().contains("Check the types of variables"));
    }

    @Test
    void findMatches_goPanic() {
        String error = "panic: runtime error: index out of range [5] with length 3";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "go");

        assertFalse(matches.isEmpty(), "Expected to find index out of range pattern match");
        assertEquals("index_out_of_range", matches.get(0).getName());
        assertEquals("go:index_out_of_range", matches.get(0).getCategory());
        assertTrue(matches.get(0).getSuggestion().contains("Check slice/array bounds"));
    }

    @Test
    void findMatches_rustPanic() {
        String error =
                "thread 'main' panicked at 'index out of bounds: the len is 3 but the index is 5',"
                        + " src/main.rs:10:5";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "rust");

        assertFalse(matches.isEmpty(), "Expected to find panic pattern match");
        assertEquals("panic", matches.get(0).getName());
        assertEquals("rust:panic", matches.get(0).getCategory());
        assertTrue(matches.get(0).getSuggestion().contains("Handle the error case"));
    }

    @Test
    void findMatches_commonPattern_outOfMemory() {
        String error = "java.lang.OutOfMemoryError: Java heap space";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "java");

        assertFalse(matches.isEmpty(), "Expected to find out of memory pattern match");
        assertEquals("out_of_memory", matches.get(0).getName());
        assertEquals("system:out_of_memory", matches.get(0).getCategory());
        assertEquals(ErrorPatternManager.Severity.CRITICAL, matches.get(0).getSeverity());
        assertTrue(matches.get(0).getSuggestion().contains("Increase JVM heap size"));
    }

    @Test
    void getFrameRegex_java() {
        Optional<String> regex = manager.getFrameRegex("java");
        assertTrue(regex.isPresent(), "Expected to get Java frame regex");
        assertTrue(regex.get().contains("at"), "Regex should contain 'at' for Java stack traces");
    }

    @Test
    void getFrameRegex_unknownLanguage() {
        Optional<String> regex = manager.getFrameRegex("nonexistent");
        assertTrue(regex.isEmpty(), "Expected empty optional for unknown language");
    }

    @Test
    void findMatches_nullOrEmptyInput() {
        assertTrue(
                manager.findMatches(null, "java").isEmpty(), "Expected empty list for null input");
        assertTrue(
                manager.findMatches("", "java").isEmpty(), "Expected empty list for empty input");
        assertTrue(
                manager.findMatches("   ", "java").isEmpty(),
                "Expected empty list for whitespace input");
    }

    @Test
    void findMatches_unknownLanguage() {
        String error = "Some error message";
        List<ErrorPatternManager.MatchedPattern> matches =
                manager.findMatches(error, "nonexistent");

        // Should only match common patterns
        assertTrue(
                matches.isEmpty()
                        || matches.stream().allMatch(m -> m.getCategory().startsWith("system:")),
                "Should only match common patterns for unknown language");
    }
}
