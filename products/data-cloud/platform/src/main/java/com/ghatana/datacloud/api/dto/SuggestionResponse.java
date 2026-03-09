package com.ghatana.datacloud.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for workflow suggestions.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates suggestion results returned to client.
 * Includes multiple suggestions with confidence scores.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SuggestionResponse response = new SuggestionResponse(
 *   List.of(
 *     new Suggestion("sugg-1", "Use price field", 0.95, "field")
 *   ),
 *   0.95
 * );
 * }</pre>
 *
 * @param suggestions list of suggestions (required)
 * @param overallConfidence overall confidence score (0.0-1.0)
 *
 * @doc.type record
 * @doc.purpose Suggestion response DTO
 * @doc.layer api
 * @doc.pattern Data Transfer Object (API Layer)
 */
public record SuggestionResponse(
    @JsonProperty("suggestions")
    List<Suggestion> suggestions,

    @JsonProperty("overallConfidence")
    double overallConfidence
) {
    /**
     * Individual suggestion.
     *
     * @param id unique suggestion identifier
     * @param text suggestion text/description
     * @param confidence confidence score (0.0-1.0)
     * @param type suggestion type (field, transformation, validation)
     * @param explanation detailed explanation (optional)
     *
     * @doc.type record
     * @doc.purpose Individual suggestion
     */
    public record Suggestion(
        @JsonProperty("id")
        String id,

        @JsonProperty("text")
        String text,

        @JsonProperty("confidence")
        double confidence,

        @JsonProperty("type")
        String type,

        @JsonProperty("explanation")
        String explanation
    ) {
        /**
         * Creates a suggestion without explanation.
         *
         * @param id unique identifier
         * @param text suggestion text
         * @param confidence confidence score
         * @param type suggestion type
         * @return new suggestion
         */
        public static Suggestion of(String id, String text, double confidence, String type) {
            return new Suggestion(id, text, confidence, type, null);
        }

        /**
         * Validates confidence score.
         *
         * @return true if confidence is between 0.0 and 1.0
         */
        public boolean isValidConfidence() {
            return confidence >= 0.0 && confidence <= 1.0;
        }

        /**
         * Checks if suggestion is high confidence.
         *
         * @return true if confidence > 0.8
         */
        public boolean isHighConfidence() {
            return confidence > 0.8;
        }
    }

    /**
     * Creates a response with a single suggestion.
     *
     * @param suggestion the suggestion
     * @return new response
     */
    public static SuggestionResponse of(Suggestion suggestion) {
        return new SuggestionResponse(List.of(suggestion), suggestion.confidence());
    }

    /**
     * Creates a response with multiple suggestions.
     *
     * @param suggestions list of suggestions
     * @return new response
     */
    public static SuggestionResponse of(List<Suggestion> suggestions) {
        double avgConfidence = suggestions.isEmpty()
            ? 0.0
            : suggestions.stream()
                .mapToDouble(Suggestion::confidence)
                .average()
                .orElse(0.0);
        return new SuggestionResponse(suggestions, avgConfidence);
    }
}
