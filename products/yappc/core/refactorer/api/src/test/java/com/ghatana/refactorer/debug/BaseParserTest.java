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
    protected abstract T createParser();

    /**
     * Returns the expected file name for the first frame in the test stack trace.
     *
     * @return expected file name
     */
    protected abstract String getExpectedFileName();

    /**
     * Returns the expected line number for the first frame in the test stack trace.
     *
     * @return expected line number
     */
    protected abstract int getExpectedLineNumber();

    /**
     * Returns a sample stack trace string for testing.
     *
     * @return a valid stack trace string
     */
    protected abstract String getSampleStackTrace();

    @Test
    void parse_returnsNonEmptyList() {
        T parser = createParser();
        String trace = getSampleStackTrace();

        List<StackTraceParser.TraceFrame> frames = parser.parse(trace);

        assertNotNull(frames, "Parser returned null");
        assertFalse(frames.isEmpty(), "Parser returned empty list");
    }

    @Test
    void parse_extractsCorrectFileAndLine() {
        T parser = createParser();
        String trace = getSampleStackTrace();

        List<StackTraceParser.TraceFrame> frames = parser.parse(trace);

        assertFalse(frames.isEmpty(), "No frames parsed");
        var frame = frames.get(0);
        assertEquals(getExpectedFileName(), frame.file(), "Incorrect file name");
        assertEquals(getExpectedLineNumber(), frame.line(), "Incorrect line number");
    }

    @Test
    void parse_handlesEmptyInput() {
        T parser = createParser();

        List<StackTraceParser.TraceFrame> frames = parser.parse("");

        assertNotNull(frames, "Parser returned null for empty input");
        assertTrue(frames.isEmpty(), "Parser should return empty list for empty input");
    }

    @Test
    void parse_handlesNullInput() {
        T parser = createParser();

        List<StackTraceParser.TraceFrame> frames = parser.parse(null);

        assertNotNull(frames, "Parser returned null for null input");
        assertTrue(frames.isEmpty(), "Parser should return empty list for null input");
    }
}
