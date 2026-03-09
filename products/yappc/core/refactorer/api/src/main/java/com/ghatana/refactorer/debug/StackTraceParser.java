package com.ghatana.refactorer.debug;

import java.util.List;
import java.util.regex.Pattern;

/**
 * SPI for parsing stack traces and failures from tool outputs (stdout/stderr, files). 
 * @doc.type interface
 * @doc.purpose Defines the contract for stack trace parser
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface StackTraceParser {
    /**
     * @param content Raw stack trace or log content
     * @return Parsed frames or empty list if not recognized
     */
    List<TraceFrame> parse(String content);

    /**
     * @return The pattern used by this parser, or null if not pattern-based
     */
    default Pattern getPattern() {
        return null;
    }

    /**
 * Basic value object for a parsed stack frame. */
    record TraceFrame(String file, int line, String function, String message) {}
}
