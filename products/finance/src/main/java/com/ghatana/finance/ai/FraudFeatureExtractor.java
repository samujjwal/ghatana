package com.ghatana.finance.ai;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Shared finance fraud feature extraction.
 *
 * @doc.type class
 * @doc.purpose Extracts normalized fraud features for finance inference paths
 * @doc.layer product
 * @doc.pattern Service
 */
public class FraudFeatureExtractor {

    private static final String UNKNOWN = "UNKNOWN";

    public Map<String, Object> extractTransactionFeatures(Map<String, Object> transactionData) {
        Objects.requireNonNull(transactionData, "transactionData cannot be null");

        String transactionId = getString(transactionData, "id", getString(transactionData, "transaction_id", "unknown"));
        String accountId = getString(transactionData, "account_id", getString(transactionData, "tenant_id", "unknown"));
        double amount = getDouble(transactionData, "amount", 0.0);
        String currency = getString(transactionData, "currency", "USD");
        String location = getString(transactionData, "location", UNKNOWN);
        String merchantCategory = getString(transactionData, "merchant_category", UNKNOWN);
        String counterpartyCountry = getString(transactionData, "counterparty_country", UNKNOWN);
        String paymentMethod = getString(transactionData, "payment_method", UNKNOWN);
        double velocityScore = getDouble(transactionData, "velocity", getDouble(transactionData, "velocity_score", 0.0));
        Instant timestamp = getInstant(transactionData.get("timestamp"));

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("transaction_id", transactionId);
        features.put("account_id", accountId);
        features.put("amount", amount);
        features.put("currency", currency);
        features.put("location", location);
        features.put("merchant_category", merchantCategory);
        features.put("counterparty_country", counterpartyCountry);
        features.put("payment_method", paymentMethod);
        features.put("velocity_score", velocityScore);
        features.put("hour_of_day", timestamp == null ? -1 : timestamp.atZone(ZoneOffset.UTC).getHour());
        features.put("day_of_week", timestamp == null ? UNKNOWN : timestamp.atZone(ZoneOffset.UTC).getDayOfWeek().name());
        features.put("amount_factor", amount > 50000 ? 0.5 : amount > 10000 ? 0.25 : 0.05);
        features.put("geolocation_risk", calculateGeolocationRisk(location, counterpartyCountry));
        features.put("merchant_risk", isHighRiskMerchant(merchantCategory) ? 0.2 : 0.05);
        features.put("counterparty_risk", calculateCounterpartyRisk(counterpartyCountry));
        features.put("payment_method_risk", calculatePaymentMethodRisk(paymentMethod));
        features.put("location_mismatch_risk", calculateLocationMismatchRisk(location, counterpartyCountry));
        features.put("time_risk", calculateTimeRisk(timestamp));
        return Map.copyOf(features);
    }

    public Map<String, Object> extractTradeEventFeatures(TradeEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        double marketPrice = event.getMarketPrice() > 0.0 ? event.getMarketPrice() : event.getPrice();
        double priceDeviation = marketPrice > 0.0
            ? Math.abs(event.getPrice() - marketPrice) / marketPrice
            : 0.0;
        double amount = event.getQuantity() * event.getPrice();
        double volumeAnomaly = event.getQuantity() / 1000.0;
        String market = event.getMarket() == null ? UNKNOWN : event.getMarket();
        String marketRegion = event.getMarketRegion() == null ? deriveMarketRegion(market) : event.getMarketRegion();
        String counterpartyCountry = event.getCounterpartyCountry() == null
            ? (event.getFeatures() == null ? UNKNOWN : getString(event.getFeatures(), "counterparty_country", UNKNOWN))
            : event.getCounterpartyCountry();
        String executionChannel = event.getExecutionChannel() == null
            ? (event.getFeatures() == null ? UNKNOWN : getString(event.getFeatures(), "execution_channel", UNKNOWN))
            : event.getExecutionChannel();

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("trade_id", event.getTradeId());
        features.put("account_id", event.getAccountId());
        features.put("amount", amount);
        features.put("price_deviation", priceDeviation);
        features.put("volume_anomaly", volumeAnomaly);
        features.put("market", market);
        features.put("market_region", marketRegion);
        features.put("counterparty_country", counterpartyCountry);
        features.put("execution_channel", executionChannel);
        features.put("velocity_score", event.getFeatures() == null ? 0.0 : getDouble(event.getFeatures(), "velocity_score", 0.0));
        features.put("market_correlation", 0.5);
        features.put("counterparty_risk", calculateCounterpartyRisk(counterpartyCountry));
        features.put("execution_channel_risk", calculateExecutionChannelRisk(executionChannel));
        features.put("hour_of_day", event.getTimestamp() == null ? -1 : event.getTimestamp().atZone(ZoneOffset.UTC).getHour());
        features.put("day_of_week", event.getTimestamp() == null ? UNKNOWN : event.getTimestamp().atZone(ZoneOffset.UTC).getDayOfWeek().name());
        features.put("time_risk", calculateTimeRisk(event.getTimestamp()));
        features.put("geolocation_risk", calculateGeolocationRisk(marketRegion, counterpartyCountry));
        return Map.copyOf(features);
    }

    private static boolean isHighRiskMerchant(String merchantCategory) {
        String normalized = merchantCategory == null ? "" : merchantCategory.toUpperCase();
        return normalized.contains("CRYPTO")
            || normalized.contains("GAMBLING")
            || normalized.contains("WIRE");
    }

    private static double calculateGeolocationRisk(String location, String counterpartyCountry) {
        if (UNKNOWN.equalsIgnoreCase(location) || UNKNOWN.equalsIgnoreCase(counterpartyCountry)) {
            return 0.12;
        }

        String normalizedLocation = location.toUpperCase();
        String normalizedCountry = counterpartyCountry.toUpperCase();
        if (isHighRiskCountry(normalizedCountry)) {
            return 0.3;
        }
        return normalizedLocation.contains(normalizedCountry) ? 0.05 : 0.15;
    }

    private static double calculateCounterpartyRisk(String counterpartyCountry) {
        if (counterpartyCountry == null || counterpartyCountry.isBlank() || UNKNOWN.equalsIgnoreCase(counterpartyCountry)) {
            return 0.05;
        }
        return isHighRiskCountry(counterpartyCountry.toUpperCase()) ? 0.22 : 0.05;
    }

    private static double calculatePaymentMethodRisk(String paymentMethod) {
        String normalized = paymentMethod == null ? "" : paymentMethod.toUpperCase();
        if (normalized.contains("CRYPTO") || normalized.contains("WIRE") || normalized.contains("PREPAID")) {
            return 0.18;
        }
        return normalized.isBlank() || UNKNOWN.equals(normalized) ? 0.03 : 0.05;
    }

    private static double calculateLocationMismatchRisk(String location, String counterpartyCountry) {
        if (UNKNOWN.equalsIgnoreCase(location) || UNKNOWN.equalsIgnoreCase(counterpartyCountry)) {
            return 0.02;
        }
        return location.toUpperCase().contains(counterpartyCountry.toUpperCase()) ? 0.02 : 0.16;
    }

    private static double calculateExecutionChannelRisk(String executionChannel) {
        String normalized = executionChannel == null ? "" : executionChannel.toUpperCase();
        if (normalized.contains("API") || normalized.contains("BOT") || normalized.contains("SCRIPT")) {
            return 0.18;
        }
        return normalized.isBlank() || UNKNOWN.equals(normalized) ? 0.04 : 0.05;
    }

    private static boolean isHighRiskCountry(String country) {
        return "IR".equalsIgnoreCase(country)
            || "KP".equalsIgnoreCase(country)
            || "RU".equalsIgnoreCase(country)
            || "SY".equalsIgnoreCase(country);
    }

    private static String deriveMarketRegion(String market) {
        String normalized = market == null ? "" : market.toUpperCase();
        if (normalized.contains("NASDAQ") || normalized.contains("NYSE")) {
            return "US";
        }
        if (normalized.contains("LSE") || normalized.contains("EURONEXT")) {
            return "EU";
        }
        return UNKNOWN;
    }

    private static double calculateTimeRisk(Instant timestamp) {
        if (timestamp == null) {
            return 0.05;
        }

        int hour = timestamp.atZone(ZoneOffset.UTC).getHour();
        boolean weekend = timestamp.atZone(ZoneOffset.UTC).getDayOfWeek().getValue() >= 6;
        return (hour < 9 || hour > 16 || weekend) ? 0.2 : 0.05;
    }

    private static double getDouble(Map<String, Object> values, String key, double defaultValue) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String getString(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static Instant getInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String stringValue) {
            try {
                return Instant.parse(stringValue);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}