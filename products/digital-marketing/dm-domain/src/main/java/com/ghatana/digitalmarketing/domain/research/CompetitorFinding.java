package com.ghatana.digitalmarketing.domain.research;

import java.util.Objects;

/**
 * An observed or inferred fact about a competitor with clear source provenance.
 *
 * @doc.type class
 * @doc.purpose DMOS competitor finding distinguishing observed facts from AI interpretation for F1-011
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CompetitorFinding(
    String competitorDomain,
    String observedFact,
    String interpretation,
    boolean isInferred,
    String source
) {
    public CompetitorFinding {
        if (competitorDomain == null || competitorDomain.isBlank()) {
            throw new IllegalArgumentException("competitorDomain must not be blank");
        }
        if (observedFact == null || observedFact.isBlank()) {
            throw new IllegalArgumentException("observedFact must not be blank");
        }
        if (interpretation == null || interpretation.isBlank()) {
            throw new IllegalArgumentException("interpretation must not be blank");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
    }
}
