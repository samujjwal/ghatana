package com.ghatana.refactorer.orchestrator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Helper class to initialize test resources before tests run. Ensures that the
 * stacktrace.patterns.json file is available in the test classpath.

 * @doc.type class
 * @doc.purpose Handles test resource initializer operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class TestResourceInitializer {

    private static final String PATTERNS_FILE = "config/debug/stacktrace.patterns.json";
    private static boolean initialized = false;

    /**
     * Initializes test resources by ensuring the patterns file is available in the test classpath.
     * This method is called before any tests run.
     */
    public static synchronized void initialize() { // GH-90000
        if (initialized) { // GH-90000
            return;
        }

        try {
            // Try to load the resource from classpath first
            try (InputStream is = // GH-90000
                    TestResourceInitializer.class
                            .getClassLoader() // GH-90000
                            .getResourceAsStream(PATTERNS_FILE)) { // GH-90000
                if (is == null) { // GH-90000
                    throw new IllegalStateException( // GH-90000
                            "Could not find " + PATTERNS_FILE + " in test resources");
                }

                // Create parent directories if they don't exist
                Path targetDir = Path.of("config", "debug"); // GH-90000
                if (!Files.exists(targetDir)) { // GH-90000
                    Files.createDirectories(targetDir); // GH-90000
                }

                // Copy the resource to the target location
                Path targetFile = targetDir.resolve("stacktrace.patterns.json");
                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING); // GH-90000

                // Set system property to point to the patterns file
                System.setProperty("stacktrace.patterns.file", targetFile.toString()); // GH-90000

                initialized = true;
            }
        } catch (IOException e) { // GH-90000
            throw new RuntimeException("Failed to initialize test resources", e); // GH-90000
        }
    }

    static {
        // Initialize resources when the class is loaded
        initialize(); // GH-90000
    }
}
