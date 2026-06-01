package com.ghatana.aep.pattern.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical compilation result for PatternSpec.
 *
 * <p>Represents the result of compiling a PatternSpec into an executable
 * runtime plan, including validation status, compiled graph, and metadata.
 *
 * @doc.type record
 * @doc.purpose Canonical compilation result for PatternSpec
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternCompilationResult(
        boolean success,
        String patternId,
        CompiledPattern compiledPattern,
        List<String> errors,
        List<String> warnings,
        Map<String, Object> metadata) {

    public PatternCompilationResult {
        if (success && compiledPattern == null) {
            throw new IllegalArgumentException("compiledPattern must not be null when success is true");
        }
        errors = List.copyOf(errors != null ? errors : List.of());
        warnings = List.copyOf(warnings != null ? warnings : List.of());
        metadata = Map.copyOf(metadata != null ? metadata : Map.of());
    }

    /**
     * Create a successful compilation result.
     *
     * @param patternId pattern identifier
     * @param compiledPattern compiled pattern
     * @return PatternCompilationResult instance
     */
    public static PatternCompilationResult success(String patternId, CompiledPattern compiledPattern) {
        return new PatternCompilationResult(true, patternId, compiledPattern, List.of(), List.of(), Map.of());
    }

    /**
     * Create a successful compilation result with warnings.
     *
     * @param patternId pattern identifier
     * @param compiledPattern compiled pattern
     * @param warnings list of warnings
     * @return PatternCompilationResult instance
     */
    public static PatternCompilationResult successWithWarnings(String patternId, CompiledPattern compiledPattern, List<String> warnings) {
        return new PatternCompilationResult(true, patternId, compiledPattern, List.of(), warnings, Map.of());
    }

    /**
     * Create a failed compilation result.
     *
     * @param patternId pattern identifier
     * @param errors list of errors
     * @return PatternCompilationResult instance
     */
    public static PatternCompilationResult failure(String patternId, List<String> errors) {
        return new PatternCompilationResult(false, patternId, null, errors, List.of(), Map.of());
    }

    /**
     * Create a failed compilation result with warnings.
     *
     * @param patternId pattern identifier
     * @param errors list of errors
     * @param warnings list of warnings
     * @return PatternCompilationResult instance
     */
    public static PatternCompilationResult failureWithWarnings(String patternId, List<String> errors, List<String> warnings) {
        return new PatternCompilationResult(false, patternId, null, errors, warnings, Map.of());
    }

    /**
     * Check if this result has any errors.
     *
     * @return true if there are errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Check if this result has any warnings.
     *
     * @return true if there are warnings
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Convert this PatternCompilationResult to a map representation.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("success", success);
        map.put("patternId", patternId);
        if (compiledPattern != null) {
            map.put("compiledPattern", compiledPattern);
        }
        if (!errors.isEmpty()) map.put("errors", errors);
        if (!warnings.isEmpty()) map.put("warnings", warnings);
        if (!metadata.isEmpty()) map.put("metadata", metadata);
        return java.util.Collections.unmodifiableMap(map);
    }
}
