package com.ghatana.refactorer.debug;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates parsing stack traces from text content and classifies errors. Uses a set of parsers
 * to extract stack frames and an ErrorPatternManager to identify common error patterns and provide
 * suggestions.
 
 * @doc.type class
 * @doc.purpose Handles debug controller operations
 * @doc.layer core
 * @doc.pattern Controller
*/
public final class DebugController {
    private static final Logger log = LoggerFactory.getLogger(DebugController.class);

    private final List<StackTraceParser> parsers;
    private final ErrorPatternManager patternManager;
    private final FixSuggester fixSuggester;

    /**
     * Creates a new DebugController with the specified parsers and pattern manager.
     *
     * @param parsers the list of stack trace parsers to use
     * @param patternManager the error pattern manager for classifying errors
     * @throws NullPointerException if parsers or patternManager is null
     */
    public DebugController(List<StackTraceParser> parsers, ErrorPatternManager patternManager) {
        this(parsers, patternManager, FixSuggesterFactory.getInstance().getSuggester("java"));
    }

    public DebugController(
            List<StackTraceParser> parsers,
            ErrorPatternManager patternManager,
            FixSuggester fixSuggester) {
        this.parsers = Objects.requireNonNull(parsers, "parsers");
        this.patternManager = Objects.requireNonNull(patternManager, "patternManager");
        this.fixSuggester = Objects.requireNonNull(fixSuggester, "fixSuggester");
    }

    /**
     * Creates a new DebugController with the specified parsers and a default pattern manager.
     *
     * @param parsers the list of stack trace parsers to use
     * @throws NullPointerException if parsers is null
     */
    public DebugController(List<StackTraceParser> parsers) {
        this(parsers, new ErrorPatternManager());
    }

    /**
     * Creates a new DebugController with default parsers for common languages and a default
     * ErrorPatternManager.
     *
     * @return a new DebugController instance
     */
    public static DebugController createDefault() {
        return new DebugController(
                List.of(
                        new JavaStackTraceParser(),
                        new PyStackTraceParser(),
                        new NodeStackTraceParser(),
                        new GoStackTraceParser(),
                        new RustStackTraceParser()),
                new ErrorPatternManager(),
                FixSuggesterFactory.getInstance().getSuggester("java"));
    }

    /**
     * Parses the given content using all available parsers and classifies any errors.
     *
     * @param content the content to parse
     * @return the parse result containing any found stack trace frames and error classifications
     * @throws NullPointerException if content is null
     */
    public ParseResult parse(String content) {
        // Gracefully handle null/empty content: do not invoke parsers, return unsuccessful result
        if (content == null || content.trim().isEmpty()) {
            return new ParseResult(
                    List.of(), content == null ? "" : content, false, List.of(), List.of());
        }

        // Try to find a parser that can handle this content
        for (StackTraceParser parser : parsers) {
            List<StackTraceParser.TraceFrame> frames = parser.parse(content);
            if (!frames.isEmpty()) {
                // We found frames, now classify any errors
                List<ErrorPatternManager.MatchedPattern> matches = classifyErrors(content, frames);
                String language = detectLanguage(frames, content);
                List<FixSuggestion> suggestions = getFixSuggestions(content, matches, language);
                return new ParseResult(frames, content, true, matches, suggestions);
            }
        }

        // No parser could extract frames, but we might still identify error patterns
        if (!content.trim().isEmpty()) {
            List<ErrorPatternManager.MatchedPattern> matches = classifyErrors(content, List.of());
            if (!matches.isEmpty()) {
                String language = detectLanguage(List.of(), content);
                List<FixSuggestion> suggestions = getFixSuggestions(content, matches, language);
                return new ParseResult(List.of(), content, false, matches, suggestions);
            }
        }

        return new ParseResult(List.of(), content, false, List.of(), List.of());
    }

    /**
 * Get fix suggestions for the given error message and matches. */
    private List<FixSuggestion> getFixSuggestions(
            String content, List<ErrorPatternManager.MatchedPattern> matches, String language) {
        if (matches.isEmpty()) {
            return List.of();
        }

        // Get the most relevant error message (usually the first one)
        String errorMessage = matches.get(0).getMatchedText();

        // Only return suggestions if we have a known error pattern match
        // (not just the catch-all 'unknown_error' pattern)
        boolean hasKnownPattern =
                matches.stream().anyMatch(m -> !"unknown_error".equals(m.getPattern().getName()));

        if (!hasKnownPattern) {
            return List.of();
        }

        // Get language-specific suggestions
        List<FixSuggestion> suggestions = fixSuggester.getSuggestionsForLanguage(language);

        // Also get general suggestions
        List<FixSuggestion> generalSuggestions = fixSuggester.suggestFixes(errorMessage);

        // Combine and deduplicate suggestions
        Set<String> seenIds = new HashSet<>();
        return Stream.concat(suggestions.stream(), generalSuggestions.stream())
                .filter(s -> seenIds.add(s.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Classifies errors in the given content using the pattern manager.
     *
     * @param content the raw content to analyze
     * @param frames the parsed stack trace frames, if any
     * @return a list of matched error patterns
     */
    private List<ErrorPatternManager.MatchedPattern> classifyErrors(
            String content, List<StackTraceParser.TraceFrame> frames) {
        try {
            // Try to detect language from frames
            String language = detectLanguage(frames, content);
            return patternManager.findMatches(content, language);
        } catch (Exception e) {
            log.warn("Error classifying errors in content", e);
            return List.of();
        }
    }

    /**
 * Attempts to detect the programming language from stack trace frames or content. */
    /* package-private */ String detectLanguage(
            List<StackTraceParser.TraceFrame> frames, String content) {
        // Check file extensions in frames first
        if (frames != null && !frames.isEmpty()) {
            for (var frame : frames) {
                String file = frame.file();
                if (file != null) {
                    if (file.endsWith(".java")) return "java";
                    if (file.endsWith(".py")) return "python";
                    if (file.endsWith(".js") || file.endsWith(".ts")) return "node";
                    if (file.endsWith(".go")) return "go";
                    if (file.endsWith(".rs")) return "rust";
                }
            }
        }

        // Fall back to content-based detection
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("java") || lowerContent.contains("exception")) return "java";
        if (lowerContent.contains("python") || lowerContent.contains("traceback")) return "python";
        if (lowerContent.contains("node") || lowerContent.contains("javascript")) return "node";
        if (lowerContent.contains("panic:") || lowerContent.contains("goroutine")) return "go";
        if (lowerContent.contains("thread '") && lowerContent.contains("panicked at"))
            return "rust";

        // Default to Java if we can't determine
        return "java";
    }

    /**
     * The result of parsing content for stack traces, error patterns, and fix suggestions.
     *
     * @param frames the list of parsed stack trace frames (empty if none found)
     * @param raw the original raw content that was parsed
     * @param success whether any stack trace frames were found
     * @param matches the list of matched error patterns (may be empty)
     * @param fixSuggestions the list of suggested fixes for the errors (may be empty)
     */
    public record ParseResult(
            List<StackTraceParser.TraceFrame> frames,
            String raw,
            boolean success,
            List<ErrorPatternManager.MatchedPattern> matches,
            List<FixSuggestion> fixSuggestions) {

        /**
 * Creates a new ParseResult with no pattern matches or fix suggestions. */
        public ParseResult(List<StackTraceParser.TraceFrame> frames, String raw, boolean success) {
            this(frames, raw, success, List.of(), List.of());
        }

        /**
 * Creates a new ParseResult with pattern matches but no fix suggestions. */
        public ParseResult(
                List<StackTraceParser.TraceFrame> frames,
                String raw,
                boolean success,
                List<ErrorPatternManager.MatchedPattern> matches) {
            this(frames, raw, success, matches, List.of());
        }

        /**
         * Creates a new ParseResult.
         *
         * @param frames the list of parsed stack trace frames
         * @param raw the original raw content
         * @param success whether parsing was successful
         * @param matches the list of matched error patterns (may be empty)
         * @param fixSuggestions the list of suggested fixes for the errors (may be empty)
         */
        public ParseResult {
            Objects.requireNonNull(frames, "frames");
            Objects.requireNonNull(raw, "raw");
            Objects.requireNonNull(matches, "matches");
            Objects.requireNonNull(fixSuggestions, "fixSuggestions");
        }
    }
}
