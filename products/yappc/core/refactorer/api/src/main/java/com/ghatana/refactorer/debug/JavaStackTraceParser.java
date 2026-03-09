package com.ghatana.refactorer.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java stack trace parser that handles standard Java stack traces with optional
 * configuration-driven patterns.
 
 * @doc.type class
 * @doc.purpose Handles java stack trace parser operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public final class JavaStackTraceParser implements StackTraceParser {
    /**
     * Default pattern for matching Java stack trace frames. Matches: at
     * com.example.Foo.bar(Foo.java:42) at com.example.App.main(App.java:10)
     */
    /**
     * Default pattern for matching Java stack trace frames. Matches: at
     * com.example.Foo.bar(Foo.java:42) at com.example.App.main(App.java:10)
     */
    public static final Pattern DEFAULT_FRAME_PATTERN =
            Pattern.compile(
                    "^\\s*at\\s+([.\\w$<>]+(?:\\([^)]*\\))?)\\s*\\(([\\w/\\\\.]+):(\\d+)\\)\\s*$",
                    Pattern.MULTILINE);

    private static final Pattern FRAME_PATTERN = DEFAULT_FRAME_PATTERN;

    private final Pattern framePattern;

    /**
 * Creates a new parser with the default pattern. */
    public JavaStackTraceParser() {
        this(null);
    }

    /**
     * Creates a new parser with the specified pattern.
     *
     * @param framePattern the pattern to use for parsing stack frames, or null to use default
     */
    public JavaStackTraceParser(Pattern framePattern) {
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
                String method = matcher.group(1); // Full method name with class
                String file = matcher.group(2); // File name
                int lineNum = Integer.parseInt(matcher.group(3)); // Line number

                // For test compatibility, use just the filename without path
                String simpleFileName =
                        file.contains("/") ? file.substring(file.lastIndexOf('/') + 1) : file;

                // Extract simple class name from method (first part before first dot)
                String className = method.split("\\.")[0];

                // Extract simple method name (last part after last dot)
                String methodName = method;
                int lastDot = methodName.lastIndexOf('.');
                if (lastDot >= 0 && lastDot < methodName.length() - 1) {
                    methodName = methodName.substring(lastDot + 1);
                }

                frames.add(new TraceFrame(simpleFileName, lineNum, methodName, className));
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
