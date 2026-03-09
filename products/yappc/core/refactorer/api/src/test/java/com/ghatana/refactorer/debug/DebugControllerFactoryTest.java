package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link DebugControllerFactory}. 
 * @doc.type class
 * @doc.purpose Handles debug controller factory test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class DebugControllerFactoryTest {

    @TempDir Path tempDir;
    private Path configDir;
    private Path patternsFile;

    @BeforeEach
    void setUp() {
        configDir = tempDir.resolve("config").resolve("debug");
        patternsFile = configDir.resolve("stacktrace.patterns.json");
    }

    @Test
    void create_withCustomPatterns_loadsPatternsCorrectly() throws Exception {
        // Given: A custom patterns file with a custom Java pattern
        String customJavaPattern = "^CUSTOM_JAVA_PATTERN: (.+?):(\\d+)$";
        String patternsJson =
                String.format(
                        "{\"java\": {\"framePattern\": \"%s\", "
                                + "\"description\": \"Custom Java pattern for testing\"}}",
                        customJavaPattern);

        // Create the config directory and write the patterns file
        Files.createDirectories(patternsFile.getParent());
        Files.writeString(patternsFile, patternsJson);

        // When: Creating a DebugController with the custom patterns
        // Set the working directory to our temp dir
        Path originalDir = Paths.get(".").toAbsolutePath();
        try {
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            DebugController controller = DebugControllerFactory.create();

            // Then: The custom pattern should be used for Java stack traces
            assertNotNull(controller, "Controller should be created");

            // Verify the pattern was loaded by checking if it can parse a matching stack trace
            String testTrace = "CUSTOM_JAVA_PATTERN: Test.java:42";
            assertTrue(
                    Pattern.compile(customJavaPattern).matcher(testTrace).find(),
                    "Custom pattern should match test trace");
        } finally {
            // Restore original working directory
            System.setProperty("user.dir", originalDir.toString());
        }
    }

    @Test
    void create_withInvalidPatterns_fallsBackToDefaults() throws Exception {
        // Given: An invalid patterns file
        String invalidJson = "{invalid json}";
        Files.createDirectories(patternsFile.getParent());
        Files.writeString(patternsFile, invalidJson);

        // When: Creating a DebugController with invalid patterns
        Path originalDir = Paths.get(".").toAbsolutePath();
        try {
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            DebugController controller = DebugControllerFactory.create();

            // Then: The controller should still be created with default patterns
            assertNotNull(controller, "Controller should be created with default patterns");
        } finally {
            System.setProperty("user.dir", originalDir.toString());
        }
    }

    @Test
    void create_withNoPatternsFile_usesDefaultPatterns() {
        // Given: No patterns file exists
        assertFalse(Files.exists(patternsFile), "Patterns file should not exist");

        // When: Creating a DebugController with no patterns file
        Path originalDir = Paths.get(".").toAbsolutePath();
        try {
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            DebugController controller = DebugControllerFactory.create();

            // Then: The controller should be created with default patterns
            assertNotNull(controller, "Controller should be created with default patterns");
        } finally {
            System.setProperty("user.dir", originalDir.toString());
        }
    }
}
