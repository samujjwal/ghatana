package com.ghatana.finance.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @doc.type class
 * @doc.purpose Tests trade event builder coverage for fraud inference metadata
 * @doc.layer product
 * @doc.pattern Test
 */
class TradeEventTest {

    @Test
    void storesExtendedFraudMetadata() {
        TradeEvent event = TradeEvent.builder()
            .tradeId("trade-77")
            .accountId("account-77")
            .symbol("MSFT")
            .quantity(10.0)
            .price(100.0)
            .marketPrice(99.0)
            .timestamp(Instant.parse("2026-04-06T12:00:00Z"))
            .market("NASDAQ")
            .marketRegion("US")
            .counterpartyCountry("IR")
            .executionChannel("API")
            .eventType("trade.executed")
            .features(Map.of("velocity_score", 3.0))
            .build();

        assertEquals("US", event.getMarketRegion());
        assertEquals("IR", event.getCounterpartyCountry());
        assertEquals("API", event.getExecutionChannel());
        assertEquals(3.0, event.getFeatures().get("velocity_score"));
    }
}
