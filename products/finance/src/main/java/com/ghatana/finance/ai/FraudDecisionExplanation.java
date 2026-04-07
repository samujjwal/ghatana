package com.ghatana.finance.ai;

import java.util.List;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Structured explanation for finance fraud decisions and top contributing factors
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class FraudDecisionExplanation {

    public record Factor(String key, double contribution, String rationale) {
        public Factor {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(rationale, "rationale cannot be null");
        }
    }

    private final String summary;
    private final String primaryReason;
    private final List<Factor> topFactors;

    public FraudDecisionExplanation(String summary, String primaryReason, List<Factor> topFactors) {
        this.summary = Objects.requireNonNull(summary, "summary cannot be null");
        this.primaryReason = Objects.requireNonNull(primaryReason, "primaryReason cannot be null");
        this.topFactors = List.copyOf(Objects.requireNonNull(topFactors, "topFactors cannot be null"));
    }

    public String getSummary() {
        return summary;
    }

    public String getPrimaryReason() {
        return primaryReason;
    }

    public List<Factor> getTopFactors() {
        return topFactors;
    }
}