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
    void setUp() throws IOException { // GH-90000
        // Load the test patterns file from resources
        try (InputStream is = // GH-90000
                getClass().getClassLoader().getResourceAsStream("stacktrace.patterns.json")) {
            if (is == null) { // GH-90000
                throw new IllegalStateException("Test patterns file not found in resources");
            }
            // Create a temporary file with the patterns
            Path tempFile = Files.createTempFile("test-patterns", ".json"); // GH-90000
            tempFile.toFile().deleteOnExit(); // GH-90000
            Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING); // GH-90000

            // Set the system property to point to our test patterns file
            System.setProperty("stacktrace.patterns.file", tempFile.toString()); // GH-90000

            // Create a new instance for each test
            manager = new ErrorPatternManager(); // GH-90000
        }
    }

    @Test
    void findMatches_javaNpe() { // GH-90000
        String error =
                "java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"str\"" // GH-90000
                        + " is null";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "java"); // GH-90000

        assertFalse(matches.isEmpty(), "Expected to find NPE pattern match"); // GH-90000
        assertEquals("npe", matches.get(0).getName()); // GH-90000
        assertEquals("java:npe", matches.get(0).getCategory()); // GH-90000
        assertEquals(ErrorPatternManager.Severity.HIGH, matches.get(0).getSeverity()); // GH-90000
        assertTrue(matches.get(0).getSuggestion().contains("Check for null references"));
    }

    @Test
    void findMatches_pythonImportError() { // GH-90000
        String error = "ImportError: No module named 'nonexistent_module'";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "python"); // GH-90000

        assertFalse(matches.isEmpty(), "Expected to find import error pattern match"); // GH-90000
        assertEquals("import_error", matches.get(0).getName()); // GH-90000
        assertEquals("python:import_error", matches.get(0).getCategory()); // GH-90000
        assertTrue(matches.get(0).getSuggestion().contains("Install the missing package"));
    }

    @Test
    void findMatches_nodeTypeError() { // GH-90000
        String error = "TypeError: Cannot read property 'length' of undefined";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "node"); // GH-90000

        assertFalse(matches.isEmpty(), "Expected to find type error pattern match"); // GH-90000
        assertEquals("type_error", matches.get(0).getName()); // GH-90000
        assertEquals("node:type_error", matches.get(0).getCategory()); // GH-90000
        assertTrue(matches.get(0).getSuggestion().contains("Check the types of variables"));
    }

    @Test
    void findMatches_goPanic() { // GH-90000
        String error = "panic: runtime error: index out of range [5] with length 3";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "go"); // GH-90000

        assertFalse(matches.isEmpty(), "Expected to find index out of range pattern match"); // GH-90000
        assertEquals("index_out_of_range", matches.get(0).getName()); // GH-90000
        assertEquals("go:index_out_of_range", matches.get(0).getCategory()); // GH-90000
        assertTrue(matches.get(0).getSuggestion().contains("Check slice/array bounds"));
    }

    @Test
    void findMatches_rustPanic() { // GH-90000
        String error =
                "thread 'main' panicked at 'index out of bounds: the len is 3 but the index is 5',"
                        + " src/main.rs:10:5";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "rust"); // GH-90000

        assertFalse(matches.isEmpty(), "Expected to find panic pattern match"); // GH-90000
        assertEquals("panic", matches.get(0).getName()); // GH-90000
        assertEquals("rust:panic", matches.get(0).getCategory()); // GH-90000
        assertTrue(matches.get(0).getSuggestion().contains("Handle the error case"));
    }

    @Test
    void findMatches_commonPattern_outOfMemory() { // GH-90000
        String error = "java.lang.OutOfMemoryError: Java heap space";

        List<ErrorPatternManager.MatchedPattern> matches = manager.findMatches(error, "java"); // GH-90000

        assertFalse(matches.isEmpty(), "Expected to find out of memory pattern match"); // GH-90000
        assertEquals("out_of_memory", matches.get(0).getName()); // GH-90000
        assertEquals("system:out_of_memory", matches.get(0).getCategory()); // GH-90000
        assertEquals(ErrorPatternManager.Severity.CRITICAL, matches.get(0).getSeverity()); // GH-90000
        assertTrue(matches.get(0).getSuggestion().contains("Increase JVM heap size"));
    }

    @Test
    void getFrameRegex_java() { // GH-90000
        Optional<String> regex = manager.getFrameRegex("java");
        assertTrue(regex.isPresent(), "Expected to get Java frame regex"); // GH-90000
        assertTrue(regex.get().contains("at"), "Regex should contain 'at' for Java stack traces");
    }

    @Test
    void getFrameRegex_unknownLanguage() { // GH-90000
        Optional<String> regex = manager.getFrameRegex("nonexistent");
        assertTrue(regex.isEmpty(), "Expected empty optional for unknown language"); // GH-90000
    }

    @Test
    void findMatches_nullOrEmptyInput() { // GH-90000
        assertTrue( // GH-90000
                manager.findMatches(null, "java").isEmpty(), "Expected empty list for null input"); // GH-90000
        assertTrue( // GH-90000
                manager.findMatches("", "java").isEmpty(), "Expected empty list for empty input"); // GH-90000
        assertTrue( // GH-90000
                manager.findMatches("   ", "java").isEmpty(), // GH-90000
                "Expected empty list for whitespace input");
    }

    @Test
    void findMatches_unknownLanguage() { // GH-90000
        String error = "Some error message";
        List<ErrorPatternManager.MatchedPattern> matches =
                manager.findMatches(error, "nonexistent"); // GH-90000

        // Should only match common patterns
        assertTrue( // GH-90000
                matches.isEmpty() // GH-90000
                        || matches.stream().allMatch(m -> m.getCategory().startsWith("system:")),
                "Should only match common patterns for unknown language");
    }
}
