package com.ghatana.refactorer.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python stack trace parser that handles standard Python stack traces with optional
 * configuration-driven patterns.
 
 * @doc.type class
 * @doc.purpose Handles py stack trace parser operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public final class PyStackTraceParser implements StackTraceParser {
    /**
     * Default pattern for matching Python stack trace frames. Matches: File "/path/to/file.py",
     * line 42, in function_name
     */
    public static final Pattern DEFAULT_FRAME_PATTERN =
            Pattern.compile(
                    "^\\s*File \"([^\"]+)\", line (\\d+)(?:, in (.+))?$", Pattern.MULTILINE);

    private static final Pattern FRAME_PATTERN = DEFAULT_FRAME_PATTERN;

    private final Pattern framePattern;

    /**
 * Creates a new parser with the default pattern. */
    public PyStackTraceParser() {
        this(null);
    }

    /**
     * Creates a new parser with the specified pattern.
     *
     * @param framePattern the pattern to use for parsing stack frames, or null to use default
     */
    public PyStackTraceParser(Pattern framePattern) {
        this.framePattern = framePattern != null ? framePattern : FRAME_PATTERN;
    }

    @Override
    public List<TraceFrame> parse(String content) {
        List<TraceFrame> frames = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return frames;
        }

        // Use the pattern to find all matches in the content
        Matcher matcher = framePattern.matcher(content);
        while (matcher.find()) {
            try {
                String file = matcher.group(1);
                int lineNum = Integer.parseInt(matcher.group(2));
                String function = matcher.group(3) != null ? matcher.group(3) : "<module>";

                frames.add(new TraceFrame(file, lineNum, function, function));
            } catch (NumberFormatException e) {
                // Skip invalid line numbers
                continue;
            } catch (Exception e) {
                // Skip malformed frames
                continue;
            }
        }

        return frames;
    }

    @Override
    public Pattern getPattern() {
        return framePattern;
    }
}
