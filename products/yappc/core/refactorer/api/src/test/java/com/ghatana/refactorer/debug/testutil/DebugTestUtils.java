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
    private static final Path TEST_RESOURCES = Paths.get("src/test/resources [GH-90000]");
    private static final Path FIXTURES_DIR = TEST_RESOURCES.resolve("fixtures [GH-90000]");

    private DebugTestUtils() {} // GH-90000

    /**
     * Loads test fixture content from the fixtures directory.
     *
     * @param filename name of the fixture file
     * @return file content as string
     * @throws IOException if the file cannot be read
     */
    public static String loadFixture(String filename) throws IOException { // GH-90000
        return Files.readString(FIXTURES_DIR.resolve(filename)); // GH-90000
    }

    /**
     * Asserts that the given frames have the expected file and line number.
     *
     * @param frames list of frames to check
     * @param expectedFile expected file name
     * @param expectedLine expected line number
     */
    public static void assertFrameMatches( // GH-90000
            List<StackTraceParser.TraceFrame> frames, String expectedFile, int expectedLine) {
        assertFalse(frames.isEmpty(), "No frames to check"); // GH-90000
        var frame = frames.get(0); // GH-90000
        assertEquals(expectedFile, frame.file(), "Unexpected file"); // GH-90000
        assertEquals(expectedLine, frame.line(), "Unexpected line number"); // GH-90000
    }

    /**
     * Asserts that the given frames have the expected function name.
     *
     * @param frames list of frames to check
     * @param expectedFunction expected function name
     */
    public static void assertFrameFunctionMatches( // GH-90000
            List<StackTraceParser.TraceFrame> frames, String expectedFunction) {
        assertFalse(frames.isEmpty(), "No frames to check"); // GH-90000
        var frame = frames.get(0); // GH-90000
        assertEquals(expectedFunction, frame.function(), "Unexpected function name"); // GH-90000
    }

    public static Path createTempFile(String content) throws IOException { // GH-90000
        Path tempFile = Files.createTempFile("polyfix", ".tmp"); // GH-90000
        Files.writeString(tempFile, content); // GH-90000
        return tempFile;
    }
}
