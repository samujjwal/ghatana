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

/**
 * @doc.type class
 * @doc.purpose Tests fraud detection GAA agent perception and alerting behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FraudDetectionAgent")
class FraudDetectionAgentTest extends EventloopTestBase {

    @Test
    @DisplayName("skips non-trade events during perception")
    void skipsNonTradeEvents() {
        FraudDetectionAgent agent = createAgent(new AtomicReference<>());

        TradeEvent perceived = agent.perceive(TradeEvent.builder()
            .tradeId("trade-1")
            .accountId("account-1")
            .eventType("trade.cancelled")
            .timestamp(Instant.now())
            .build(), null);

        assertNull(perceived);
    }

    @Test
    @DisplayName("enriches trade events with normalized fraud features")
    void enrichesTradeEventFeatures() {
        FraudDetectionAgent agent = createAgent(new AtomicReference<>());

        TradeEvent perceived = agent.perceive(TradeEvent.builder()
            .tradeId("trade-2")
            .accountId("account-9")
            .symbol("AAPL")
            .quantity(200.0)
            .price(150.0)
            .marketPrice(145.0)
            .timestamp(Instant.parse("2026-04-06T21:15:30Z"))
            .market("NASDAQ")
            .marketRegion("US")
            .counterpartyCountry("RU")
            .executionChannel("API")
            .eventType("trade.executed")
            .features(Map.of("velocity_score", 8.0))
            .build(), null);

        assertNotNull(perceived);
        assertEquals("trade-2", perceived.getFeatures().get("trade_id"));
        assertEquals("account-9", perceived.getFeatures().get("account_id"));
        assertEquals("RU", perceived.getFeatures().get("counterparty_country"));
        assertEquals("API", perceived.getFeatures().get("execution_channel"));
        assertEquals(18, perceived.getFeatures().size());
    }

    @Test
    @DisplayName("publishes alerts for suspicious results during act phase")
    void publishesAlertsForSuspiciousResults() {
        AtomicReference<Alert> publishedAlert = new AtomicReference<>();
        FraudDetectionAgent agent = new FraudDetectionAgent(
            modelId -> io.activej.promise.Promise.complete(),
            (modelId, features) -> FraudDetectionResult.suspicious("trade-3", "account-3", "velocity-anomaly", 0.91),
            alert -> {
                publishedAlert.set(alert);
                return io.activej.promise.Promise.complete();
            }
        );

        FraudDetectionResult acted = runPromise(() -> agent.act(
            FraudDetectionResult.suspicious("trade-3", "account-3", "velocity-anomaly", 0.91),
            null
        ));

        assertEquals("velocity-anomaly", acted.getFraudType());
        assertEquals("LOCAL_RULES", acted.getInferenceSource());
        assertNotNull(publishedAlert.get());
        assertEquals("trade-3", publishedAlert.get().getTradeId());
    }

    private static FraudDetectionAgent createAgent(AtomicReference<Alert> publishedAlert) {
        return new FraudDetectionAgent(
            modelId -> io.activej.promise.Promise.complete(),
            (modelId, features) -> FraudDetectionResult.clean(
                String.valueOf(features.getOrDefault("trade_id", "trade-default")),
                String.valueOf(features.getOrDefault("account_id", "account-default"))
            ),
            alert -> {
                publishedAlert.set(alert);
                return io.activej.promise.Promise.complete();
            }
        );
    }
}