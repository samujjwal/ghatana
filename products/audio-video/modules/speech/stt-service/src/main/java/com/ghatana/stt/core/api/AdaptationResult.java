package com.ghatana.stt.core.api;

/**
 * Result of an adaptation operation.
 * 
 * @doc.type record
 * @doc.purpose Adaptation operation result
 * @doc.layer api
 */
public record AdaptationResult(
    /** Whether the adaptation was successful */
    boolean success,
    
    /** Human-readable message */
    String message,
    
    /** Number of interactions processed */
    int interactionsProcessed,
    
    /** WER before adaptation (if measured) */
    Float werBefore,
    
    /** WER after adaptation (if measured) */
    Float werAfter,
    
    /** Number of new vocabulary terms added */
    int newVocabularyTerms,
    
    /** Processing time in milliseconds */
    long processingTimeMs
) {
    /**
     * Create a successful result.
     */
    public static AdaptationResult success(int interactionsProcessed, int newTerms) {
        return new AdaptationResult(
            true,
            "Adaptation completed successfully",
            interactionsProcessed,
            null,
            null,
            newTerms,
            0
        );
    }

    /**
     * Create a failed result.
     */
    public static AdaptationResult failure(String message) {
        return new AdaptationResult(false, message, 0, null, null, 0, 0);
    }

    /**
     * Calculate WER improvement percentage.
     */
    public Float werImprovement() {
        if (werBefore != null && werAfter != null && werBefore > 0) {
            return ((werBefore - werAfter) / werBefore) * 100;
        }
        return null;
    }
}
