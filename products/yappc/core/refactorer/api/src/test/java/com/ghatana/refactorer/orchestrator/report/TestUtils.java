/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test utilities for report generation tests.
 * @doc.type class
 * @doc.purpose Handles test utils operations
 * @doc.layer core
 * @doc.pattern Utility
*/
public final class TestUtils {
    private TestUtils() { // GH-90000
        // Prevent instantiation
    }

    /**
     * Creates a temporary directory for testing. The directory will be automatically deleted when
     * the JVM exits.
     */
    public static Path createTempDirectory(String prefix) throws IOException { // GH-90000
        Path tempDir = Files.createTempDirectory(prefix); // GH-90000
        tempDir.toFile().deleteOnExit(); // GH-90000
        return tempDir;
    }

    /** Counts the number of files in a directory (not recursive). */ // GH-90000
    public static long countFiles(Path directory) throws IOException { // GH-90000
        return Files.isDirectory(directory) // GH-90000
                ? Files.list(directory).filter(Files::isRegularFile).count() // GH-90000
                : 0;
    }

    /** Reads the content of a file as a string. */
    public static String readFileContent(Path file) throws IOException { // GH-90000
        return Files.readString(file); // GH-90000
    }
}
