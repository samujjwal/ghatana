package com.ghatana.refactorer.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Node.js stack trace parser that handles standard Node.js stack traces with optional
 * configuration-driven patterns.
 
 * @doc.type class
 * @doc.purpose Handles node stack trace parser operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public final class NodeStackTraceParser implements StackTraceParser {
    /**
     * Default pattern for matching Node.js stack trace frames. Matches both formats: at
     * Object.<anonymous> (/path/to/file.js:10:5) at /path/to/file.js:10:5
     */
    public static final Pattern DEFAULT_FRAME_PATTERN =
            Pattern.compile(
                    "^\\s*at\\s+(?:(.+?)\\s+\\()?([^:]+?):(\\d+)(?::(\\d+))?\\s*(?:\\)|$)",
                    Pattern.MULTILINE);

    private static final Pattern FRAME_PATTERN = DEFAULT_FRAME_PATTERN;

    private final Pattern framePattern;

    /**
 * Creates a new parser with the default pattern. */
    public NodeStackTraceParser() {
        this(null);
    }

    /**
     * Creates a new parser with the specified pattern.
     *
     * @param framePattern the pattern to use for parsing stack frames, or null to use default
     */
    public NodeStackTraceParser(Pattern framePattern) {
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
                String function =
                        matcher.group(1) != null ? matcher.group(1).trim() : "<anonymous>";
                String file = matcher.group(2);
                int lineNum = Integer.parseInt(matcher.group(3));

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
