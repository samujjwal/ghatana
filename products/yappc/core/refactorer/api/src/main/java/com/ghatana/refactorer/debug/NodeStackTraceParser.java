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
     * Object.&lt;anonymous&gt; (/path/to/file.js:10:5) at /path/to/file.js:10:5
     */
    public static final Pattern DEFAULT_FRAME_PATTERN =
            Pattern.compile(
                "^\\s*at\\s+(?:(.+?)\\s+\\()?(.+?):(\\d+)(?::(\\d+))?\\s*(?:\\)|$)",
                    Pattern.MULTILINE);

        private static final Pattern FALLBACK_FRAME_PATTERN =
            Pattern.compile("^(?:(.+?)\\s+)?(.+?):(\\d+)(?::(\\d+))?$");

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

        List<String> seenFrameKeys = new ArrayList<>();

        // Use the pattern to find all matches in the content
        Matcher matcher = framePattern.matcher(content);
        while (matcher.find()) {
            TraceFrame frame = buildFrame(matcher.group(1), matcher.group(2), matcher.group(3));
            if (frame != null) {
                addIfAbsent(frames, seenFrameKeys, frame);
            }
        }

        for (String line : content.split("\\R")) {
            TraceFrame frame = parseFallbackFrame(line);
            if (frame != null) {
                addIfAbsent(frames, seenFrameKeys, frame);
            }
        }

        return frames;
    }

    private TraceFrame parseFallbackFrame(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("at ")) {
            return null;
        }

        String remainder = trimmed.substring(3).trim();
        Matcher matcher = FALLBACK_FRAME_PATTERN.matcher(remainder);
        if (!matcher.matches()) {
            return null;
        }

        return buildFrame(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    private TraceFrame buildFrame(String functionGroup, String fileGroup, String lineGroup) {
        try {
            String function =
                    functionGroup != null && !functionGroup.isBlank()
                            ? functionGroup.trim()
                            : "<anonymous>";
            String file = fileGroup != null ? fileGroup.trim() : null;
            if (file == null || file.isBlank()) {
                return null;
            }

            int lineNum = Integer.parseInt(lineGroup);
            return new TraceFrame(file, lineNum, function, function);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void addIfAbsent(
            List<TraceFrame> frames,
            List<String> seenFrameKeys,
            TraceFrame frame) {
        String frameKey = frame.file() + ":" + frame.line() + ":" + frame.function();
        if (!seenFrameKeys.contains(frameKey)) {
            seenFrameKeys.add(frameKey);
            frames.add(frame);
        }
    }

    @Override
    public Pattern getPattern() {
        return framePattern;
    }
}
