package com.ghatana.tutorputor.contentgeneration;

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

    /** Personally identifiable information */
    PII,

    /** Hate speech, discrimination, violence promotion */
    HATE_SPEECH,

    /** Not-safe-for-work content */
    NSFW,

    /** Sexual or graphic content */
    EXPLICIT,

    /** Minimum content quality threshold */
    QUALITY_THRESHOLD,

    /** Unwanted promotional or spam content */
    SPAM,

    /** Phishing attempts, credential theft */
    PHISHING,

    /** Copyright or trademark violations */
    COPYRIGHT,

    /** Misinformation or false claims */
    MISINFORMATION
}
