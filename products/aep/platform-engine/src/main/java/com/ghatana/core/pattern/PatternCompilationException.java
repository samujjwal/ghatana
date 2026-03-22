package com.ghatana.core.pattern;

import java.util.List;

/**
 * Exception thrown when pattern compilation fails.
 *
 * @see AdvancedPatternCompiler
 * @doc.type class
 * @doc.purpose Pattern compilation error handling
 * @doc.layer core
 * @doc.pattern Exception
 */
public class PatternCompilationException extends RuntimeException {

    private final List<String> compilationErrors;

    /**
     * Create compilation exception with message.
     */
    public PatternCompilationException(String message) {
        super(message);
        this.compilationErrors = List.of(message);
    }

    /**
     * Create compilation exception with message and cause.
     */
    public PatternCompilationException(String message, Throwable cause) {
        super(message, cause);
        this.compilationErrors = List.of(message);
    }

    /**
     * Create compilation exception with multiple errors.
     */
    public PatternCompilationException(String message, List<String> compilationErrors) {
        super(message);
        this.compilationErrors = compilationErrors;
    }

    /**
     * Get compilation errors.
     */
    public List<String> getCompilationErrors() {
        return compilationErrors;
    }

    /**
     * Get number of compilation errors.
     */
    public int getErrorCount() {
        return compilationErrors.size();
    }

    @Override
    public String toString() {
        if (compilationErrors.size() == 1) {
            return super.toString();
        }
        return String.format("PatternCompilationException: %s (%d errors)", 
                getMessage(), compilationErrors.size());
    }
}
