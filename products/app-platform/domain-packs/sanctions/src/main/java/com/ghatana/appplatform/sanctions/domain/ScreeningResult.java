package com.ghatana.appplatform.sanctions.domain;

import java.time.Instant;
import java.util.List;

/**
 * @doc.type    Record (Immutable Value Object)
 * @doc.purpose Result of a screening operation (D14-001, D14-003).
 *              Aggregates matches across all sanctions lists.
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public record ScreeningResult(
        String resultId,
        String requestId,
        boolean matchFound,
        List<MatchResult> matches,
        ScreeningDecision decision,   // AUTO_BLOCK, HIGH, MEDIUM, LOW based on score (D14-003)
        double highestScore,
        Instant screenedAt,
        String referenceId            // order_id, onboarding_id, etc.
) {
    /**
     * @doc.purpose Individual match detail for a specific sanctions list entry.
     */
    public record MatchResult(
            SanctionsListType list,
            String entryId,
            String matchedName,        // which alias/primary name was matched
            double score,              // 0.0 – 1.0
            MatchType matchType
    ) {}
}
