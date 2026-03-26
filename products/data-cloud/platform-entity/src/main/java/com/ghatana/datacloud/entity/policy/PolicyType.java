package com.ghatana.datacloud.entity.policy;

/**
 * Content policy types used for text validation.
 *
 * <p>Defines the canonical set of content policies that can be
 * checked against user-generated content. Used by ContentPolicyChecker
 * implementations to determine which validations to apply.
 *
 * @see ContentPolicyChecker
 * @doc.type enum
 * @doc.purpose Content policy type definitions
 * @doc.layer domain
 * @doc.pattern Value Object (Enum)
 */
public enum PolicyType {
    PROFANITY,
    PII,
    SPAM,
    HATE_SPEECH,
    NSFW,
    QUALITY_THRESHOLD
}
