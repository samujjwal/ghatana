package com.ghatana.finance.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies structured explainability output for finance fraud decisions
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceFraudExplanationUtilsTest {

    @Test
    void derivesTopContributingFactorsForFraudulentDecision() {
        FraudDecisionExplanation explanation = FinanceFraudExplanationUtils.explain(
            Map.of(
                "counterparty_risk", 0.22,
                "merchant_risk", 0.20,
                "velocity_score", 12.0,
                "amount_factor", 0.5
            ),
            "cross-border-anomaly",
            "HIGH",
            true,
            "REMOTE"
        );

        assertTrue(explanation.getSummary().contains("HIGH fraud risk"));
        assertEquals("elevated transaction velocity", explanation.getPrimaryReason());
        assertEquals(3, explanation.getTopFactors().size());
        assertEquals("velocity_score", explanation.getTopFactors().get(0).key());
    }

    @Test
    void returnsBalancedSummaryForLowRiskDecision() {
        FraudDecisionExplanation explanation = FinanceFraudExplanationUtils.explain(
            Map.of("amount_factor", 0.05),
            null,
            "LOW",
            false,
            "FALLBACK"
        );

        assertEquals("balanced transaction signals", explanation.getPrimaryReason());
        assertTrue(explanation.getSummary().contains("Low fraud risk"));
    }
}
