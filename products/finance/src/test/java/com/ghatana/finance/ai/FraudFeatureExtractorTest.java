package com.ghatana.finance.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies finance fraud feature extraction for transaction and trade flows
 * @doc.layer product
 * @doc.pattern Test
 */
class FraudFeatureExtractorTest {

    private final FraudFeatureExtractor extractor = new FraudFeatureExtractor();

    @Test
    void extractsTransactionFeatures() {
        Map<String, Object> features = extractor.extractTransactionFeatures(Map.of(
            "id", "txn-900",
            "tenant_id", "tenant-9",
            "amount", 75000.0,
            "currency", "BTC",
            "location", "US-NY",
            "merchant_category", "CRYPTO_EXCHANGE",
            "counterparty_country", "RU",
            "payment_method", "WIRE_TRANSFER",
            "velocity", 12.0,
            "timestamp", "2026-04-06T20:15:30Z"
        ));

        assertEquals("txn-900", features.get("transaction_id"));
        assertEquals("tenant-9", features.get("account_id"));
        assertEquals(75000.0, features.get("amount"));
        assertEquals("BTC", features.get("currency"));
        assertEquals("US-NY", features.get("location"));
        assertEquals("CRYPTO_EXCHANGE", features.get("merchant_category"));
        assertEquals("RU", features.get("counterparty_country"));
        assertEquals("WIRE_TRANSFER", features.get("payment_method"));
        assertEquals(12.0, features.get("velocity_score"));
        assertEquals(20, features.get("hour_of_day"));
        assertEquals("MONDAY", features.get("day_of_week"));
        assertEquals(0.5, features.get("amount_factor"));
        assertEquals(0.3, features.get("geolocation_risk"));
        assertEquals(0.2, features.get("merchant_risk"));
        assertEquals(0.22, features.get("counterparty_risk"));
        assertEquals(0.18, features.get("payment_method_risk"));
        assertEquals(0.16, features.get("location_mismatch_risk"));
        assertEquals(0.2, features.get("time_risk"));
    }

    @Test
    void extractsTradeEventFeatures() {
        TradeEvent event = TradeEvent.builder()
            .tradeId("trade-1")
            .accountId("account-1")
            .symbol("AAPL")
            .quantity(500.0)
            .price(110.0)
            .marketPrice(100.0)
            .timestamp(Instant.parse("2026-04-06T12:00:00Z"))
            .market("NASDAQ")
            .marketRegion("US")
            .counterpartyCountry("IR")
            .executionChannel("API")
            .features(Map.of("velocity_score", 2.0))
            .build();

        Map<String, Object> features = extractor.extractTradeEventFeatures(event);

        assertEquals("trade-1", features.get("trade_id"));
        assertEquals("account-1", features.get("account_id"));
        assertEquals(55000.0, features.get("amount"));
        assertEquals(0.1, features.get("price_deviation"));
        assertEquals(0.5, features.get("volume_anomaly"));
        assertEquals("NASDAQ", features.get("market"));
        assertEquals("US", features.get("market_region"));
        assertEquals("IR", features.get("counterparty_country"));
        assertEquals("API", features.get("execution_channel"));
        assertEquals(2.0, features.get("velocity_score"));
        assertEquals(0.5, features.get("market_correlation"));
        assertEquals(0.22, features.get("counterparty_risk"));
        assertEquals(0.18, features.get("execution_channel_risk"));
        assertEquals(12, features.get("hour_of_day"));
        assertEquals("MONDAY", features.get("day_of_week"));
        assertEquals(0.05, features.get("time_risk"));
        assertEquals(0.3, features.get("geolocation_risk"));
    }

    @Test
    void defaultsMissingTransactionValues() {
        Map<String, Object> features = extractor.extractTransactionFeatures(Map.of());

        assertEquals(0.0, features.get("amount"));
        assertEquals("USD", features.get("currency"));
        assertEquals("UNKNOWN", features.get("location"));
        assertEquals("unknown", features.get("transaction_id"));
        assertTrue(((Double) features.get("time_risk")) >= 0.0);
    }
}
