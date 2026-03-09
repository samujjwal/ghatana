package com.ghatana.refactorer.debug;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Suggests fixes for code issues based on error patterns and available fixes. Uses ConfidenceScorer
 * to rank and filter suggestions.
 
 * @doc.type class
 * @doc.purpose Handles fix suggester operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class FixSuggester {
    private static final double MIN_CONFIDENCE_THRESHOLD =
            0.3; // Minimum confidence to show a suggestion
    private static final Logger log = LoggerFactory.getLogger(FixSuggester.class);

    private final Map<String, List<FixSuggestion>> fixSuggestions;
    private final Map<String, Pattern> compiledPatterns;
    private final ConfidenceScorer confidenceScorer;

    // Static initializer for default suggestions
    private static final Map<String, List<FixSuggestion>> DEFAULT_SUGGESTIONS = new HashMap<>();
    private static final Map<String, Pattern> DEFAULT_PATTERNS = new HashMap<>();

    static {
        loadDefaultSuggestions();
    }

    /**
 * Creates a new FixSuggester with a default ConfidenceScorer. */
    public FixSuggester() {
        this(new ConfidenceScorer());
    }

    /**
     * Creates a new FixSuggester with a custom ConfidenceScorer.
     *
     * @param confidenceScorer The scorer to use for ranking suggestions
     */
    public FixSuggester(ConfidenceScorer confidenceScorer) {
        this.fixSuggestions = new HashMap<>(DEFAULT_SUGGESTIONS);
        this.compiledPatterns = new HashMap<>(DEFAULT_PATTERNS);
        this.confidenceScorer =
                Objects.requireNonNull(confidenceScorer, "ConfidenceScorer cannot be null");
    }

    /**
     * Register a new fix suggestion for a specific error pattern.
     *
     * @param errorPattern The regex pattern to match error messages
     * @param suggestion The fix suggestion to register
     * @return This FixSuggester for method chaining
     */
    public FixSuggester registerSuggestion(String errorPattern, FixSuggestion suggestion) {
        Objects.requireNonNull(errorPattern, "Error pattern cannot be null");
        Objects.requireNonNull(suggestion, "Suggestion cannot be null");

        // Create a new suggestion with the error pattern if not set
        FixSuggestion suggestionWithPattern =
                suggestion.getErrorPattern() == null
                        ? FixSuggestion.builder()
                                .id(suggestion.getId())
                                .description(suggestion.getDescription())
                                .fixPattern(suggestion.getFixPattern())
                                .confidence(suggestion.getConfidence())
                                .language(suggestion.getLanguage())
                                .errorPattern(errorPattern)
                                .build()
                        : suggestion;

        fixSuggestions
                .computeIfAbsent(errorPattern, k -> new ArrayList<>())
                .add(suggestionWithPattern);
        compiledPatterns.putIfAbsent(errorPattern, Pattern.compile(errorPattern, Pattern.DOTALL));
        return this;
    }

    private static void registerDefaultSuggestion(String errorPattern, FixSuggestion suggestion) {
        // Ensure the suggestion has the error pattern set
        FixSuggestion suggestionWithPattern =
                suggestion.getErrorPattern() == null
                        ? FixSuggestion.builder()
                                .id(suggestion.getId())
                                .description(suggestion.getDescription())
                                .fixPattern(suggestion.getFixPattern())
                                .confidence(suggestion.getConfidence())
                                .language(suggestion.getLanguage())
                                .errorPattern(errorPattern)
                                .build()
                        : suggestion;

        DEFAULT_SUGGESTIONS
                .computeIfAbsent(errorPattern, k -> new ArrayList<>())
                .add(suggestionWithPattern);
        DEFAULT_PATTERNS.putIfAbsent(errorPattern, Pattern.compile(errorPattern, Pattern.DOTALL));
    }

    /**
     * Get suggested fixes for a given error message with default context.
     *
     * @param errorMessage The error message to analyze
     * @return A list of suggested fixes, sorted by confidence (highest first)
     */
    public List<FixSuggestion> suggestFixes(String errorMessage) {
        return suggestFixes(errorMessage, null);
    }

    /**
     * Get suggested fixes for a given error message with additional context.
     *
     * @param errorMessage The error message to analyze
     * @param context Additional context for the error (can be null)
     * @return A list of suggested fixes, sorted by confidence (highest first)
     */
    public List<FixSuggestion> suggestFixes(String errorMessage, @Nullable FixContext context) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // If no context is provided, create a minimal one
        FixContext effectiveContext = context != null ? context : new FixContext("unknown", "");

        return fixSuggestions.entrySet().stream()
                .flatMap(
                        entry -> {
                            try {
                                Pattern pattern = compiledPatterns.get(entry.getKey());
                                if (pattern == null || !pattern.matcher(errorMessage).find()) {
                                    return Stream.empty();
                                }

                                // For each matching suggestion, calculate its confidence
                                return entry.getValue().stream()
                                        .map(
                                                suggestion -> {
                                                    // Calculate dynamic confidence based on the
                                                    // context and error message
                                                    double dynamicConfidence =
                                                            confidenceScorer.calculateConfidence(
                                                                    suggestion,
                                                                    errorMessage,
                                                                    effectiveContext);

                                                    // Create a new suggestion with the updated
                                                    // confidence
                                                    return FixSuggestion.builder()
                                                            .id(suggestion.getId())
                                                            .description(
                                                                    suggestion.getDescription())
                                                            .fixPattern(suggestion.getFixPattern())
                                                            .confidence(dynamicConfidence)
                                                            .language(suggestion.getLanguage())
                                                            .errorPattern(
                                                                    suggestion.getErrorPattern())
                                                            .build();
                                                });
                            } catch (Exception e) {
                                log.warn("Error processing pattern: " + entry.getKey(), e);
                                return Stream.empty();
                            }
                        })
                .filter(suggestion -> suggestion.getConfidence() >= MIN_CONFIDENCE_THRESHOLD)
                .sorted(Comparator.comparingDouble(FixSuggestion::getConfidence).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Records the outcome of applying a fix suggestion.
     *
     * @param suggestionId The ID of the suggestion that was applied
     * @param wasSuccessful Whether the fix was successfully applied
     */
    public void recordFixOutcome(String suggestionId, boolean wasSuccessful) {
        confidenceScorer.recordFixOutcome(suggestionId, wasSuccessful);
    }

    /**
     * Load default fix suggestions for common error patterns. These serve as fallbacks when no
     * custom suggestions are registered.
     */
    private static void loadDefaultSuggestions() {
        // Java NullPointerException
        registerDefaultSuggestion(
                "java\\.lang\\.NullPointerException.*",
                FixSuggestion.builder()
                        .id("java.null.check")
                        .description("Add null check before accessing object")
                        .fixPattern("if (\\${var} != null) {\\n    \\${statement}\\n}")
                        .language("java")
                        .confidence(0.9)
                        .build());

        // Python NameError
        registerDefaultSuggestion(
                "NameError: name '(.+?)' is not defined",
                FixSuggestion.builder()
                        .id("python.undefined.variable")
                        .description(
                                "Variable is not defined. Add the missing variable or check for"
                                        + " typos.")
                        .fixPattern("# Add the missing variable definition\\n\\${1} = value")
                        .language("python")
                        .confidence(0.8)
                        .build());

        // JavaScript TypeError
        registerDefaultSuggestion(
                "TypeError: Cannot read property '(.+?)' of (undefined|null)",
                FixSuggestion.builder()
                        .id("js.null.property.access")
                        .description("Add null check before accessing object property")
                        .fixPattern(
                                "if (\\${object} && \\${object}\\.\\${property}) {\\n"
                                        + "    \\${statement}\\n"
                                        + "}")
                        .language("javascript")
                        .confidence(0.85)
                        .build());
    }

    /**
 * Get all registered fix suggestions. */
    public List<FixSuggestion> getAllSuggestions() {
        return fixSuggestions.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    /**
 * Get suggestions for a specific language. */
    public List<FixSuggestion> getSuggestionsForLanguage(String language) {
        return fixSuggestions.values().stream()
                .flatMap(List::stream)
                .filter(s -> s.getLanguage().equalsIgnoreCase(language))
                .collect(Collectors.toList());
    }
}
