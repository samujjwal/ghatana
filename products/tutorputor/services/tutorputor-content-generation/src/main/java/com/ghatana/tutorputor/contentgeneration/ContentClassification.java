package com.ghatana.tutorputor.contentgeneration;

/**
 * Classification of content for policy enforcement purposes.
 *
 * @param category the top-level category (e.g. "SAFE", "SPAM", "HATE_SPEECH")
 * @param confidence confidence score in the classification (0.0 to 1.0)
 * @param reason optional human-readable reason for the classification
 *
 * @doc.type record
 * @doc.purpose Content classification result for policy checks
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ContentClassification(String category, double confidence, String reason) {

    /** Convenience factory for a safe classification. */
    public static ContentClassification safe() {
        return new ContentClassification("SAFE", 1.0, null);
    }
}
