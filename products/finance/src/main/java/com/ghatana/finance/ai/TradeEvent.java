package com.ghatana.finance.ai;

import java.time.Instant;
import java.util.Map;

/**
 * Trade event data for fraud detection.
  * @doc.type class
 * @doc.purpose Provides trade event functionality.
 * @doc.layer product
 * @doc.pattern Event
*/
public class TradeEvent {
    private final String tradeId;
    private final String accountId;
    private final String symbol;
    private final double quantity;
    private final double price;
    private final double marketPrice;
    private final Instant timestamp;
    private final String market;
    private final String marketRegion;
    private final String counterpartyCountry;
    private final String executionChannel;
    private final String eventType;
    private final Map<String, Object> features;

    private TradeEvent(Builder builder) {
        this.tradeId = builder.tradeId;
        this.accountId = builder.accountId;
        this.symbol = builder.symbol;
        this.quantity = builder.quantity;
        this.price = builder.price;
        this.marketPrice = builder.marketPrice;
        this.timestamp = builder.timestamp;
        this.market = builder.market;
        this.marketRegion = builder.marketRegion;
        this.counterpartyCountry = builder.counterpartyCountry;
        this.executionChannel = builder.executionChannel;
        this.eventType = builder.eventType;
        this.features = builder.features;
    }

    public String getTradeId() { return tradeId; }
    public String getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public double getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public double getMarketPrice() { return marketPrice; }
    public Instant getTimestamp() { return timestamp; }
    public String getMarket() { return market; }
    public String getMarketRegion() { return marketRegion; }
    public String getCounterpartyCountry() { return counterpartyCountry; }
    public String getExecutionChannel() { return executionChannel; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getFeatures() { return features; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tradeId;
        private String accountId;
        private String symbol;
        private double quantity;
        private double price;
        private double marketPrice;
        private Instant timestamp;
        private String market;
        private String marketRegion;
        private String counterpartyCountry;
        private String executionChannel;
        private String eventType;
        private Map<String, Object> features;

        public Builder tradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder quantity(double quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder price(double price) {
            this.price = price;
            return this;
        }

        public Builder marketPrice(double marketPrice) {
            this.marketPrice = marketPrice;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder market(String market) {
            this.market = market;
            return this;
        }

        public Builder marketRegion(String marketRegion) {
            this.marketRegion = marketRegion;
            return this;
        }

        public Builder counterpartyCountry(String counterpartyCountry) {
            this.counterpartyCountry = counterpartyCountry;
            return this;
        }

        public Builder executionChannel(String executionChannel) {
            this.executionChannel = executionChannel;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder features(Map<String, Object> features) {
            this.features = features;
            return this;
        }

        public TradeEvent build() {
            return new TradeEvent(this);
        }
    }
}
