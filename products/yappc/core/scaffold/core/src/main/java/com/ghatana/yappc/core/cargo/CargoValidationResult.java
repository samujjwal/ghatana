package com.ghatana.yappc.core.cargo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Result of Cargo build specification validation.
 * @doc.type class
 * @doc.purpose Result of Cargo build specification validation.
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public class CargoValidationResult {

    private final boolean valid;
    private final List<ValidationIssue> errors;
    private final List<ValidationIssue> warnings;
    private final List<String> recommendations;
    private final double score;

    @JsonCreator
    public CargoValidationResult(
            @JsonProperty("valid") boolean valid,
            @JsonProperty("errors") List<ValidationIssue> errors,
            @JsonProperty("warnings") List<ValidationIssue> warnings,
            @JsonProperty("recommendations") List<String> recommendations,
            @JsonProperty("score") double score) {
        this.valid = valid;
        this.errors = errors != null ? List.copyOf(errors) : List.of();
        this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
        this.recommendations = recommendations != null ? List.copyOf(recommendations) : List.of();
        this.score = score;
    }

    public boolean isValid() {
        return valid;
    }

    public List<ValidationIssue> getErrors() {
        return errors;
    }

    public List<ValidationIssue> getWarnings() {
        return warnings;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public double getScore() {
        return score;
    }

    /**
 * Individual validation issue */
    public static class ValidationIssue {
        private final String code;
        private final String message;
        private final String field;
        private final String severity;

        @JsonCreator
        public ValidationIssue(
                @JsonProperty("code") String code,
                @JsonProperty("message") String message,
                @JsonProperty("field") String field,
                @JsonProperty("severity") String severity) {
            this.code = Objects.requireNonNull(code, "validation code cannot be null");
            this.message = Objects.requireNonNull(message, "validation message cannot be null");
            this.field = field;
            this.severity = severity != null ? severity : "ERROR";
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public String getField() {
            return field;
        }

        public String getSeverity() {
            return severity;
        }
    }
}
