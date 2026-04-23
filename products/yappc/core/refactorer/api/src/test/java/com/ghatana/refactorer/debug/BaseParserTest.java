package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Base test class for all stack trace parser implementations.
 * @doc.type class
 * @doc.purpose Handles base parser test operations
 * @doc.layer core
 * @doc.pattern Test
*/
public abstract class BaseParserTest<T extends StackTraceParser> {

    /**
     * Creates a new instance of the parser under test.
     *
     * @return a new parser instance
     */
    protected abstract T createParser(); // GH-90000

    /**
     * Returns the expected file name for the first frame in the test stack trace.
     *
     * @return expected file name
     */
    protected abstract String getExpectedFileName(); // GH-90000

    /**
     * Returns the expected line number for the first frame in the test stack trace.
     *
     * @return expected line number
     */
    protected abstract int getExpectedLineNumber(); // GH-90000

    /**
     * Returns a sample stack trace string for testing.
     *
     * @return a valid stack trace string
     */
    protected abstract String getSampleStackTrace(); // GH-90000

    @Test
    void parse_returnsNonEmptyList() { // GH-90000
        T parser = createParser(); // GH-90000
        String trace = getSampleStackTrace(); // GH-90000

        List<StackTraceParser.TraceFrame> frames = parser.parse(trace); // GH-90000

        assertNotNull(frames, "Parser returned null"); // GH-90000
        assertFalse(frames.isEmpty(), "Parser returned empty list"); // GH-90000
    }

    @Test
    void parse_extractsCorrectFileAndLine() { // GH-90000
        T parser = createParser(); // GH-90000
        String trace = getSampleStackTrace(); // GH-90000

        List<StackTraceParser.TraceFrame> frames = parser.parse(trace); // GH-90000

        assertFalse(frames.isEmpty(), "No frames parsed"); // GH-90000
        var frame = frames.get(0); // GH-90000
        assertEquals(getExpectedFileName(), frame.file(), "Incorrect file name"); // GH-90000
        assertEquals(getExpectedLineNumber(), frame.line(), "Incorrect line number"); // GH-90000
    }

    @Test
    void parse_handlesEmptyInput() { // GH-90000
        T parser = createParser(); // GH-90000

        List<StackTraceParser.TraceFrame> frames = parser.parse("");

        assertNotNull(frames, "Parser returned null for empty input"); // GH-90000
        assertTrue(frames.isEmpty(), "Parser should return empty list for empty input"); // GH-90000
    }

    @Test
    void parse_handlesNullInput() { // GH-90000
        T parser = createParser(); // GH-90000

        List<StackTraceParser.TraceFrame> frames = parser.parse(null); // GH-90000

        assertNotNull(frames, "Parser returned null for null input"); // GH-90000
        assertTrue(frames.isEmpty(), "Parser should return empty list for null input"); // GH-90000
    }
}
