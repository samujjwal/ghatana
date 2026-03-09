package com.ghatana.refactorer.debug.testutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ghatana.refactorer.debug.StackTraceParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Test utilities for the debug module. 
 * @doc.type class
 * @doc.purpose Handles debug test utils operations
 * @doc.layer core
 * @doc.pattern Utility
*/
public final class DebugTestUtils {
    private static final Path TEST_RESOURCES = Paths.get("src/test/resources");
    private static final Path FIXTURES_DIR = TEST_RESOURCES.resolve("fixtures");

    private DebugTestUtils() {}

    /**
     * Loads test fixture content from the fixtures directory.
     *
     * @param filename name of the fixture file
     * @return file content as string
     * @throws IOException if the file cannot be read
     */
    public static String loadFixture(String filename) throws IOException {
        return Files.readString(FIXTURES_DIR.resolve(filename));
    }

    /**
     * Asserts that the given frames have the expected file and line number.
     *
     * @param frames list of frames to check
     * @param expectedFile expected file name
     * @param expectedLine expected line number
     */
    public static void assertFrameMatches(
            List<StackTraceParser.TraceFrame> frames, String expectedFile, int expectedLine) {
        assertFalse(frames.isEmpty(), "No frames to check");
        var frame = frames.get(0);
        assertEquals(expectedFile, frame.file(), "Unexpected file");
        assertEquals(expectedLine, frame.line(), "Unexpected line number");
    }

    /**
     * Asserts that the given frames have the expected function name.
     *
     * @param frames list of frames to check
     * @param expectedFunction expected function name
     */
    public static void assertFrameFunctionMatches(
            List<StackTraceParser.TraceFrame> frames, String expectedFunction) {
        assertFalse(frames.isEmpty(), "No frames to check");
        var frame = frames.get(0);
        assertEquals(expectedFunction, frame.function(), "Unexpected function name");
    }

    public static Path createTempFile(String content) throws IOException {
        Path tempFile = Files.createTempFile("polyfix", ".tmp");
        Files.writeString(tempFile, content);
        return tempFile;
    }
}
