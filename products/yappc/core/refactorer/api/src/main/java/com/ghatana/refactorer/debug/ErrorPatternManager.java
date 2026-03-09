package com.ghatana.refactorer.debug;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages error patterns for different programming languages and provides
 * pattern matching functionality to classify errors in stack traces.
 
 * @doc.type class
 * @doc.purpose Handles error pattern manager operations
 * @doc.layer core
 * @doc.pattern Manager
*/
public class ErrorPatternManager {

    private static final Logger log = LoggerFactory.getLogger(ErrorPatternManager.class);
    private static final String DEFAULT_PATTERNS_PATH
            = System.getProperty("stacktrace.patterns.file", "config/debug/stacktrace.patterns.json");

    private final Map<String, LanguagePatterns> languagePatterns;
    private final List<ErrorPattern> commonPatterns;

    /**
     * Creates a new ErrorPatternManager with the default configuration.
     *
     * @throws IllegalStateException if the configuration cannot be loaded
     */
    public ErrorPatternManager() {
        // Try to load patterns from classpath first (useful in tests)
        try (InputStream is
                = Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream("stacktrace.patterns.json")) {
                    if (is != null) {
                        ObjectMapper mapper = JsonUtils.getDefaultMapper();
                        Config config = mapper.readValue(is, Config.class);

                        this.languagePatterns
                                = config.languages.entrySet().stream()
                                        .collect(
                                                Collectors.toMap(
                                                        Map.Entry::getKey,
                                                        e -> new LanguagePatterns(e.getValue())));

                        this.commonPatterns
                                = config.commonPatterns != null
                                        ? config.commonPatterns.stream()
                                                .map(ErrorPattern::new)
                                                .collect(Collectors.toList())
                                        : List.of();

                        log.info(
                                "Loaded error patterns from classpath resource 'stacktrace.patterns.json'"
                                + " for languages: {}",
                                languagePatterns.keySet());
                        return;
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "Failed to load error patterns from classpath resource"
                            + " 'stacktrace.patterns.json'",
                            e);
                }

                // Fallback to filesystem path
                Path configFile = Path.of(DEFAULT_PATTERNS_PATH);
                requireNonNull(configFile, "configFile cannot be null");
                if (!Files.exists(configFile)) {
                    throw new IllegalArgumentException("Patterns file does not exist: " + configFile);
                }

                try (InputStream is = Files.newInputStream(configFile)) {
                    ObjectMapper mapper = JsonUtils.getDefaultMapper();
                    Config config = mapper.readValue(is, Config.class);

                    this.languagePatterns
                            = config.languages.entrySet().stream()
                                    .collect(
                                            Collectors.toMap(
                                                    Map.Entry::getKey,
                                                    e -> new LanguagePatterns(e.getValue())));

                    this.commonPatterns
                            = config.commonPatterns != null
                                    ? config.commonPatterns.stream()
                                            .map(ErrorPattern::new)
                                            .collect(Collectors.toList())
                                    : List.of();

                    log.info(
                            "Loaded error patterns for languages: {} from {}",
                            languagePatterns.keySet(),
                            configFile);

                } catch (IOException e) {
                    throw new IllegalStateException("Failed to load error patterns from " + configFile, e);
                }
    }

    /**
     * Creates a new ErrorPatternManager with patterns loaded from the specified
     * file.
     *
     * @param configFile the file containing the pattern configuration
     * @throws IllegalStateException if the configuration cannot be loaded
     */
    public ErrorPatternManager(Path configFile) {
        requireNonNull(configFile, "configFile cannot be null");
        if (!Files.exists(configFile)) {
            throw new IllegalArgumentException("Patterns file does not exist: " + configFile);
        }

        try (InputStream is = Files.newInputStream(configFile)) {
            ObjectMapper mapper = JsonUtils.getDefaultMapper();
            Config config = mapper.readValue(is, Config.class);

            this.languagePatterns
                    = config.languages.entrySet().stream()
                            .collect(
                                    Collectors.toMap(
                                            Map.Entry::getKey,
                                            e -> new LanguagePatterns(e.getValue())));

            this.commonPatterns
                    = config.commonPatterns != null
                            ? config.commonPatterns.stream()
                                    .map(ErrorPattern::new)
                                    .collect(Collectors.toList())
                            : List.of();

            log.info("Loaded error patterns for languages: {}", languagePatterns.keySet());

        } catch (IOException e) {
            throw new IllegalStateException("Failed to load error patterns from " + configFile, e);
        }
    }

    /**
     * Finds matching error patterns in the given text for the specified
     * language.
     *
     * @param text the text to search for error patterns
     * @param language the programming language (e.g., "java", "python")
     * @return a list of matching error patterns, ordered by severity (highest
     * first)
     */
    public List<MatchedPattern> findMatches(String text, String language) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("Empty or null text provided to findMatches");
            return List.of();
        }

        log.info(
                "Searching for patterns. Language: {}. Available languages: {}",
                language,
                languagePatterns.keySet());

        // Check language-specific patterns first
        List<MatchedPattern> matches = new ArrayList<>();
        if (language != null && languagePatterns.containsKey(language)) {
            log.info("Checking language-specific patterns for: {}", language);
            matches.addAll(languagePatterns.get(language).findMatches(text));
            log.debug("Found {} language-specific matches", matches.size());
        } else {
            log.debug("No language-specific patterns found for: {}", language);
        }

        // Then check common patterns
        log.info("Checking common patterns");
        int commonMatches = 0;
        for (ErrorPattern pattern : commonPatterns) {
            Optional<MatchedPattern> match = pattern.match(text);
            if (match.isPresent()) {
                matches.add(match.get());
                commonMatches++;
                log.info("Matched common pattern: {}", pattern.getName());
            }
        }
        log.info("Found {} common pattern matches", commonMatches);

        // Sort by severity level (higher first: CRITICAL > HIGH > MEDIUM > LOW) and then by pattern
        // name
        matches.sort(
                Comparator.comparingInt((MatchedPattern m) -> m.getSeverity().getLevel())
                        .reversed()
                        .thenComparing(m -> m.getPattern().name));

        return matches;
    }

    /**
     * Gets the frame regex pattern for the specified language, if available.
     *
     * @param language the programming language (e.g., "java", "python")
     * @return the frame regex pattern, or empty if not found
     */
    public Optional<String> getFrameRegex(String language) {
        if (language == null || !languagePatterns.containsKey(language)) {
            return Optional.empty();
        }
        return Optional.ofNullable(languagePatterns.get(language).frameRegex);
    }

    // Configuration classes for JSON deserialization
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Config {

        public int version;
        public Map<String, LanguageConfig> languages;
        public List<PatternConfig> commonPatterns;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LanguageConfig {

        public String frameRegex;
        public List<PatternConfig> errorPatterns;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PatternConfig {

        public String name;
        public String pattern;
        public String category;
        public String severity = "medium";
        public String suggestion;
    }

    /**
     * Represents a compiled error pattern with its associated metadata.
     */
    public static class ErrorPattern {

        private final String name;
        private final Pattern pattern;
        private final String category;
        private final Severity severity;
        private final String suggestion;

        public ErrorPattern(PatternConfig config) {
            this.name = config.name;
            this.pattern = Pattern.compile(config.pattern, Pattern.DOTALL);
            this.category = config.category;
            this.severity = Severity.fromString(config.severity);
            this.suggestion = config.suggestion != null ? config.suggestion : "";
        }

        /**
         * Attempts to match this pattern against the given text.
         *
         * @param text the text to match against
         * @return a MatchedPattern if the pattern matches, or empty otherwise
         */
        public Optional<MatchedPattern> match(String text) {
            if (text == null) {
                log.debug("Match failed: text is null");
                return Optional.empty();
            }

            log.info("[ErrorPatternManager] Trying pattern '{}' regex: {}", name, pattern.pattern());

            java.util.regex.Matcher matcher = pattern.matcher(text);
            boolean found = matcher.find();

            if (found) {
                log.info("[ErrorPatternManager] Pattern '{}' MATCHED at {}-{}", name, matcher.start(), matcher.end());
                return Optional.of(new MatchedPattern(this, text));
            } else {
                log.info("[ErrorPatternManager] Pattern '{}' did NOT match", name);
            }
            return Optional.empty();
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getSuggestion() {
            return suggestion;
        }
    }

    /**
     * Represents a successful pattern match.
     */
    public static class MatchedPattern {

        private final ErrorPattern pattern;
        private final String matchedText;

        public MatchedPattern(ErrorPattern pattern, String matchedText) {
            this.pattern = pattern;
            this.matchedText = matchedText;
        }

        public ErrorPattern getPattern() {
            return pattern;
        }

        public String getMatchedText() {
            return matchedText;
        }

        public String getName() {
            return pattern.getName();
        }

        public String getCategory() {
            return pattern.getCategory();
        }

        public Severity getSeverity() {
            return pattern.getSeverity();
        }

        public String getSuggestion() {
            return pattern.getSuggestion();
        }

        @Override
        public String toString() {
            return String.format(
                    "%s [%s]: %s",
                    pattern.getName(), pattern.getSeverity(), pattern.getSuggestion());
        }
    }

    /**
     * Represents the severity level of an error pattern.
     */
    public enum Severity {
        CRITICAL(4),
        HIGH(3),
        MEDIUM(2),
        LOW(1);

        private final int level;

        Severity(int level) {
            this.level = level;
        }

        public static Severity fromString(String value) {
            if (value == null) {
                return MEDIUM;
            }
            try {
                return Severity.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown severity level: {}", value);
                return MEDIUM;
            }
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * Groups together all patterns for a specific language.
     */
    private static class LanguagePatterns {

        private static final Logger log = LoggerFactory.getLogger(LanguagePatterns.class);
        private final String frameRegex;
        private final List<ErrorPattern> errorPatterns;

        public LanguagePatterns(LanguageConfig config) {
            this.frameRegex = config.frameRegex;
            this.errorPatterns
                    = config.errorPatterns != null
                            ? config.errorPatterns.stream()
                                    .map(ErrorPattern::new)
                                    .collect(Collectors.toList())
                            : List.of();

            log.debug(
                    "Created LanguagePatterns with frameRegex: '{}' and {} patterns",
                    frameRegex,
                    errorPatterns.size());
            for (ErrorPattern pattern : errorPatterns) {
                log.debug("  Pattern '{}' loaded", pattern.getName());
            }
        }

        public List<MatchedPattern> findMatches(String text) {
            List<MatchedPattern> matches = new ArrayList<>();
            log.debug(
                    "Checking {} patterns against text: {}",
                    errorPatterns.size(),
                    text.length() > 50 ? text.substring(0, 50) + "..." : text);

            for (ErrorPattern pattern : errorPatterns) {
                Optional<MatchedPattern> match = pattern.match(text);
                if (match.isPresent()) {
                    log.debug("Pattern '{}' matched", pattern.getName());
                    matches.add(match.get());
                } else {
                    log.debug("Pattern '{}' did not match", pattern.getName());
                }
            }
            log.debug("Found {} matches in language patterns", matches.size());
            return matches;
        }
    }
}
