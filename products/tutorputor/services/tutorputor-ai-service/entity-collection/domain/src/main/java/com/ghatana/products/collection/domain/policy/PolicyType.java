package com.ghatana.products.collection.domain.policy;

/**
 * Enumeration of content policy types for validation.
 *
 * <p><b>Purpose</b><br>
 * Defines categories of content policies that can be enforced
 * during entity creation, update, and enrichment operations.
 *
 * @doc.type enum
 * @doc.purpose Content policy type enumeration
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum PolicyType {
    /** Offensive language, profanity, slurs */
    PROFANITY,

    /** Hate speech, discrimination, violence promotion */
    HATE_SPEECH,

    /** Sexual or graphic content */
    EXPLICIT,

    /** Unwanted promotional or spam content */
    SPAM,

    /** Phishing attempts, credential theft */
    PHISHING,

    /** Copyright or trademark violations */
    COPYRIGHT,

    /** Misinformation or false claims */
    MISINFORMATION
}
