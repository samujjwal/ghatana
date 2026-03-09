package com.ghatana.refactorer.debug;

import java.util.Objects;

/**
 * Represents a suggested fix for a code issue with associated metadata for scoring confidence and
 * applying the fix.
 
 * @doc.type class
 * @doc.purpose Handles fix suggestion operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class FixSuggestion {
    private final String id;
    private final String description;
    private final String fixPattern;
    private final double confidence;
    private final String language;
    private final String errorPattern;

    private FixSuggestion(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Fix ID cannot be null");
        this.description =
                Objects.requireNonNull(builder.description, "Description cannot be null");
        this.fixPattern = Objects.requireNonNull(builder.fixPattern, "Fix pattern cannot be null");
        this.confidence = builder.confidence;
        this.language = Objects.requireNonNull(builder.language, "Language cannot be null");
        this.errorPattern = builder.errorPattern; // Can be null

        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getFixPattern() {
        return fixPattern;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getLanguage() {
        return language;
    }

    /**
     * Gets the error pattern that this fix suggestion matches against.
     *
     * @return The error pattern, or null if not specified
     */
    public String getErrorPattern() {
        return errorPattern;
    }

    @Override
    public String toString() {
        return String.format(
                "FixSuggestion{id='%s', description='%s', confidence=%.2f}",
                id, description, confidence);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String description;
        private String fixPattern;
        private double confidence = 0.8; // Default confidence
        private String language;
        private String errorPattern;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder fixPattern(String fixPattern) {
            this.fixPattern = fixPattern;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * Sets the error pattern that this fix suggestion matches against.
         *
         * @param errorPattern A regex pattern to match error messages
         * @return This builder for method chaining
         */
        public Builder errorPattern(String errorPattern) {
            this.errorPattern = errorPattern;
            return this;
        }

        public FixSuggestion build() {
            return new FixSuggestion(this);
        }
    }
}
