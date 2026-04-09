package com.ghatana.finance.ai;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Tests risk feature extraction and alert publishing for finance risk agent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RiskAssessmentAgent")
class RiskAssessmentAgentTest extends EventloopTestBase {

    @Test
    @DisplayName("skips non-portfolio events during perception")
    void skipsNonPortfolioEvents() {
        RiskAssessmentAgent agent = createAgent(new AtomicReference<>());

        PortfolioUpdate perceived = agent.perceive(PortfolioUpdate.builder()
            .portfolioId("portfolio-1")
            .accountId("account-1")
            .timestamp(Instant.now())
            .eventType("portfolio.deleted")
            .build(), null);

        assertNull(perceived);
    }

    @Test
    @DisplayName("extracts derived risk features from tagged exposures")
    void extractsDerivedRiskFeatures() {
        RiskAssessmentAgent agent = createAgent(new AtomicReference<>());

        PortfolioUpdate perceived = agent.perceive(PortfolioUpdate.builder()
            .portfolioId("portfolio-1")
            .accountId("account-1")
            .timestamp(Instant.now())
            .eventType("portfolio.updated")
            .positions(Map.of(
                "EQUITY:US:AAPL", 90.0,
                "EQUITY:US:MSFT", 10.0,
                "FX:EU:EURUSD", 5.0
            ))
            .marketValues(Map.of(
                "EQUITY:US:AAPL", 900.0,
                "EQUITY:US:MSFT", 100.0,
                "FX:EU:EURUSD", 50.0
            ))
            .build(), null);

        assertNotNull(perceived);
        assertEquals("portfolio.updated", perceived.getEventType());
        assertEquals(3, perceived.getRiskFeatures().get("position_count"));
        assertEquals(1050.0, (Double) perceived.getRiskFeatures().get("total_value"));
        assertTrue((Double) perceived.getRiskFeatures().get("sector_concentration") > 0.8);
        assertTrue((Double) perceived.getRiskFeatures().get("geography_concentration") > 0.8);
        assertEquals(0.0, (Double) perceived.getRiskFeatures().get("margin_utilization"));
    }

    @Test
    @DisplayName("publishes risk update during act phase")
    void publishesRiskUpdate() {
        AtomicReference<RiskUpdate> publishedUpdate = new AtomicReference<>();
        RiskAssessmentAgent agent = createAgent(publishedUpdate);
        RiskAssessmentResult output = RiskAssessmentResult.create(
            "portfolio-1",
            0.11,
            0.18,
            0.21,
            1.07,
            0.35,
            0.12,
            0.09,
            0.28,
            0.91
        );

        RiskAssessmentResult actedOutput = runPromise(() -> agent.act(output, null));

        assertEquals(output, actedOutput);
        assertNotNull(publishedUpdate.get());
    }

    private static RiskAssessmentAgent createAgent(AtomicReference<RiskUpdate> publishedUpdate) {
        return new RiskAssessmentAgent(
            modelId -> io.activej.promise.Promise.complete(),
            (modelId, features) -> RiskAssessmentResult.create(
                "portfolio-1",
                0.1,
                0.2,
                0.25,
                1.1,
                0.3,
                0.1,
                0.05,
                0.22,
                0.9
            ),
            update -> {
                publishedUpdate.set(update);
                return io.activej.promise.Promise.complete();
            }
        );
    }
}
