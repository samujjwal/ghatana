package com.ghatana.refactorer.debug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Loads and manages stack trace patterns from a JSON configuration file. The patterns are used by
 * language-specific parsers to extract stack frame information.
 
 * @doc.type class
 * @doc.purpose Handles stacktrace patterns operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class StacktracePatterns {
    private static final String DEFAULT_PATTERNS_PATH = "/config/debug/stacktrace.patterns.json";
    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getDefaultMapper();

    private final Map<String, LanguagePatterns> languages;

    /**
     * Creates a new instance with patterns loaded from the default location.
     *
     * @return a new StacktracePatterns instance
     * @throws IOException if the patterns file cannot be read or parsed
     */
    public static StacktracePatterns load() throws IOException {
        return load(Path.of("config", "debug", "stacktrace.patterns.json"));
    }

    /**
     * Creates a new instance with patterns loaded from the specified path.
     *
     * @param configPath path to the patterns JSON file
     * @return a new StacktracePatterns instance
     * @throws IOException if the patterns file cannot be read or parsed
     */
    public static StacktracePatterns load(Path configPath) throws IOException {
        Objects.requireNonNull(configPath, "configPath cannot be null");

        if (!Files.exists(configPath)) {
            // Return empty patterns if file doesn't exist
            return new StacktracePatterns(Map.of());
        }

        try (InputStream in = Files.newInputStream(configPath)) {
            JsonNode root = OBJECT_MAPPER.readTree(in);
            Map<String, LanguagePatterns> patterns = new HashMap<>();

            // Parse each language's patterns
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String lang = entry.getKey();
                JsonNode langNode = entry.getValue();

                String framePattern = langNode.path("framePattern").asText();
                if (framePattern != null && !framePattern.isEmpty()) {
                    patterns.put(lang, new LanguagePatterns(framePattern));
                }
            }

            return new StacktracePatterns(patterns);
        } catch (Exception e) {
            throw new IOException("Failed to parse stack trace patterns: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new instance with the specified language patterns.
     *
     * @param languages map of language names to their patterns
     */
    public StacktracePatterns(Map<String, LanguagePatterns> languages) {
        this.languages = new HashMap<>(languages);
    }

    /**
     * Gets the patterns for the specified language.
     *
     * @param language language name (e.g., "java", "python")
     * @return the language patterns, or null if not found
     */
    public LanguagePatterns forLanguage(String language) {
        return languages.get(language);
    }

    /**
     * Gets the frame pattern for the specified language.
     *
     * @param language language name (e.g., "java", "python")
     * @return the compiled pattern, or null if not found
     */
    public Pattern getFramePattern(String language) {
        LanguagePatterns patterns = forLanguage(language);
        return patterns != null ? patterns.getFramePattern() : null;
    }

    /**
 * Language-specific patterns for stack trace parsing. */
    public static final class LanguagePatterns {
        private final String frameRegex;
        private transient Pattern framePattern;

        /**
         * Creates a new instance with the specified frame pattern.
         *
         * @param frameRegex the regex pattern for matching stack frames
         * @throws NullPointerException if frameRegex is null
         * @throws IllegalArgumentException if frameRegex is empty
         */
        public LanguagePatterns(String frameRegex) {
            this.frameRegex = Objects.requireNonNull(frameRegex, "frameRegex cannot be null");
            if (frameRegex.isEmpty()) {
                throw new IllegalArgumentException("frameRegex cannot be empty");
            }
        }

        /**
         * Gets the frame regex pattern string.
         *
         * @return the regex pattern string, never null
         */
        public String getFrameRegex() {
            return frameRegex;
        }

        /**
         * Gets the compiled frame pattern. The pattern is compiled lazily and cached for better
         * performance.
         *
         * @return the compiled pattern, never null
         * @throws java.util.regex.PatternSyntaxException if the regex is invalid
         */
        public Pattern getFramePattern() {
            // Double-checked locking pattern for thread safety
            Pattern result = framePattern;
            if (result == null) {
                synchronized (this) {
                    result = framePattern;
                    if (result == null) {
                        framePattern = result = Pattern.compile(frameRegex, Pattern.MULTILINE);
                    }
                }
            }
            return result;
        }

        @Override
        public String toString() {
            return "LanguagePatterns{frameRegex='" + frameRegex + "'}";
        }
    }
}
