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
    private TestUtils() {
        // Prevent instantiation
    }

    /**
     * Creates a temporary directory for testing. The directory will be automatically deleted when
     * the JVM exits.
     */
    public static Path createTempDirectory(String prefix) throws IOException {
        Path tempDir = Files.createTempDirectory(prefix);
        tempDir.toFile().deleteOnExit();
        return tempDir;
    }

    /** Counts the number of files in a directory (not recursive). */
    public static long countFiles(Path directory) throws IOException {
        return Files.isDirectory(directory)
                ? Files.list(directory).filter(Files::isRegularFile).count()
                : 0;
    }

    /** Reads the content of a file as a string. */
    public static String readFileContent(Path file) throws IOException {
        return Files.readString(file);
    }
}
