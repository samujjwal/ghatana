package com.ghatana.refactorer.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating DebugController instances with default configuration. 
 * @doc.type class
 * @doc.purpose Handles debug controller factory operations
 * @doc.layer core
 * @doc.pattern Factory
*/
public final class DebugControllerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DebugControllerFactory.class);

    private DebugControllerFactory() {}

    /**
     * Creates a DebugController with default parsers.
     *
     * @return a new DebugController instance
     */
    public static DebugController create() {
        // Use the current working directory as the root directory
        Path rootDir = Paths.get(".").toAbsolutePath().normalize();

        // Load stack trace patterns
        StacktracePatterns patterns = loadPatterns(rootDir);

        // Create parsers with loaded patterns or default patterns
        List<StackTraceParser> parsers = new ArrayList<>();
        try {
            parsers.add(createParser(patterns, "java", JavaStackTraceParser.DEFAULT_FRAME_PATTERN));
            parsers.add(createParser(patterns, "python", PyStackTraceParser.DEFAULT_FRAME_PATTERN));
            parsers.add(createParser(patterns, "node", NodeStackTraceParser.DEFAULT_FRAME_PATTERN));
            parsers.add(createParser(patterns, "rust", RustStackTraceParser.DEFAULT_FRAME_PATTERN));
            parsers.add(createParser(patterns, "go", GoStackTraceParser.DEFAULT_FRAME_PATTERN));

            LOG.info("Loaded {} stack trace parsers", parsers.size());
            return new DebugController(parsers);

        } catch (Exception e) {
            LOG.error("Failed to initialize stack trace parsers: {}", e.getMessage(), e);
            // Fall back to default parsers if there's an error
            return new DebugController(
                    List.of(
                            new JavaStackTraceParser(),
                            new PyStackTraceParser(),
                            new NodeStackTraceParser(),
                            new RustStackTraceParser(),
                            new GoStackTraceParser()));
        }
    }

    private static StacktracePatterns loadPatterns(Path rootDir) {
        try {
            Path configPath =
                    rootDir.resolve("config").resolve("debug").resolve("stacktrace.patterns.json");
            if (!Files.exists(configPath)) {
                LOG.warn("Stack trace patterns file not found at: {}", configPath);
                return new StacktracePatterns(Map.of());
            }
            return StacktracePatterns.load(configPath);
        } catch (Exception e) {
            LOG.warn("Failed to load stack trace patterns: {}", e.getMessage());
            LOG.debug("Stack trace:", e);
            return new StacktracePatterns(Map.of());
        }
    }

    /**
     * Creates a parser with the specified language pattern.
     *
     * @param patterns The loaded patterns
     * @param language The language to get patterns for
     * @param defaultPattern The default pattern to use if none is found
     * @return A new parser instance
     */
    private static StackTraceParser createParser(
            StacktracePatterns patterns, String language, Pattern defaultPattern) {
        Pattern pattern = patterns.getFramePattern(language);
        if (pattern == null) {
            LOG.debug("Using default pattern for {}", language);
            pattern = defaultPattern;
        } else {
            LOG.debug("Using custom pattern for {}: {}", language, pattern.pattern());
        }

        switch (language) {
            case "java":
                return new JavaStackTraceParser(pattern);
            case "python":
                return new PyStackTraceParser(pattern);
            case "node":
                return new NodeStackTraceParser(pattern);
            case "rust":
                return new RustStackTraceParser(pattern);
            case "go":
                return new GoStackTraceParser(pattern, null);
            default:
                throw new IllegalArgumentException("Unsupported language: " + language);
        }
    }
}
