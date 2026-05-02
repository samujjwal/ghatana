package com.ghatana.digitalmarketing.domain.research;

import java.util.Objects;

/**
 * A keyword entry with intent, relevance, evidence, and suggested campaign use.
 *
 * @doc.type class
 * @doc.purpose DMOS keyword finding value object with source provenance for F1-011
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record KeywordFinding(
    String keyword,
    KeywordIntent intent,
    double relevanceScore,
    String suggestedCampaignUse,
    String evidence,
    String source
) {
    public KeywordFinding {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        Objects.requireNonNull(intent, "intent must not be null");
        if (relevanceScore < 0.0 || relevanceScore > 1.0) {
            throw new IllegalArgumentException("relevanceScore must be between 0.0 and 1.0");
        }
        if (suggestedCampaignUse == null || suggestedCampaignUse.isBlank()) {
            throw new IllegalArgumentException("suggestedCampaignUse must not be blank");
        }
        if (evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("evidence must not be blank");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
    }
}
