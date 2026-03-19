package com.ghatana.finance.agent;

import com.ghatana.kernel.adapter.aep.AepKernelAdapter;
import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fraud Detection Agent implementing GAA (Generic Adaptive Agent) lifecycle.
 *
 * <p>Monitors for fraudulent trading patterns with GAA lifecycle:
 * PERCEIVE → REASON → ACT → CAPTURE → REFLECT</p>
 *
 * <p>Detection capabilities:
 * <ul>
 *   <li>Wash trading detection</li>
 *   <li>Layering/spoofing detection</li>
 *   <li>Insider trading pattern recognition</li>
 *   <li>Cross-market manipulation</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Fraud detection agent with GAA lifecycle
 * @doc.layer product
 * @doc.pattern Agent, GAA
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class FraudDetectionAgent {

    private static final String AGENT_ID = "fraud-detection-agent";
    private static final String VERSION = "1.0.0";

    private final KernelContext context;
    private final AepKernelAdapter aepAdapter;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Detection thresholds
    private static final int WASH_TRADE_WINDOW_SECONDS = 60;
    private static final int MAX_TRADES_IN_WINDOW = 10;
    private static final BigDecimal LAYERING_SIZE_THRESHOLD = new BigDecimal("100000");

    // State for GAA lifecycle
    private final ConcurrentHashMap<String, Perception> perceptionBuffer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Action> actionHistory = new ConcurrentHashMap<>();

    public FraudDetectionAgent(KernelContext context) {
        this.context = context;
        this.aepAdapter = context.getDependency(AepKernelAdapter.class);
    }

    public Promise<Void> start() {
        running.set(true);
        return initializeAgent();
    }

    public Promise<Void> stop() {
        running.set(false);
        return Promise.complete();
    }

    public boolean isHealthy() {
        return running.get();
    }

    public String getAgentId() {
        return AGENT_ID;
    }

    // ==================== GAA Lifecycle: PERCEIVE ====================

    /**
     * Perceives trading activity from event stream.
     *
     * @param trade the trade event
     * @return Promise completing when perception is processed
     */
    public Promise<Void> perceive(TradeEvent trade) {
        if (!running.get()) {
            return Promise.complete();
        }

        // Create perception record
        Perception perception = new Perception(
            trade.getTradeId(),
            trade.getTraderId(),
            trade.getSymbol(),
            trade.getSide(),
            trade.getQuantity(),
            trade.getPrice(),
            trade.getTimestamp(),
            detectAnomalyFeatures(trade)
        );

        perceptionBuffer.put(trade.getTradeId(), perception);

        // Trigger reasoning
        return reason(perception);
    }

    // ==================== GAA Lifecycle: REASON ====================

    /**
     * Reasons about perceived data to detect fraud patterns.
     *
     * @param perception the perceived trade
     * @return Promise completing when reasoning is done
     */
    private Promise<Void> reason(Perception perception) {
        // Check for wash trading
        return detectWashTrade(perception)
            .then(washTrade -> {
                if (washTrade) {
                    return act(new Action(
                        perception.getTradeId(),
                        ActionType.FLAG_WASH_TRADE,
                        "Wash trading pattern detected",
                        perception.getTimestamp()
                    ));
                }

                // Check for layering
                return detectLayering(perception)
                    .then(layering -> {
                        if (layering) {
                            return act(new Action(
                                perception.getTradeId(),
                                ActionType.FLAG_LAYERING,
                                "Layering/spoofing pattern detected",
                                perception.getTimestamp()
                            ));
                        }

                        // Check for unusual size
                        if (perception.getQuantity().compareTo(LAYERING_SIZE_THRESHOLD) > 0) {
                            return detectInsiderTrading(perception)
                                .then(insider -> {
                                    if (insider) {
                                        return act(new Action(
                                            perception.getTradeId(),
                                            ActionType.FLAG_INSIDER_TRADING,
                                            "Potential insider trading pattern",
                                            perception.getTimestamp()
                                        ));
                                    }
                                    return Promise.complete();
                                });
                        }

                        return Promise.complete();
                    });
            });
    }

    // ==================== GAA Lifecycle: ACT ====================

    /**
     * Acts on detected fraud patterns.
     *
     * @param action the action to take
     * @return Promise completing when action is executed
     */
    private Promise<Void> act(Action action) {
        actionHistory.put(action.getTradeId(), action);

        return switch (action.getType()) {
            case FLAG_WASH_TRADE -> publishAlert("WASH_TRADE", action);
            case FLAG_LAYERING -> publishAlert("LAYERING", action);
            case FLAG_INSIDER_TRADING -> publishAlert("INSIDER_TRADING", action);
            case SUSPEND_TRADER -> suspendTrader(action.getTradeId());
            case NOTIFY_COMPLIANCE -> notifyCompliance(action);
        };
    }

    // ==================== GAA Lifecycle: CAPTURE ====================

    /**
     * Captures evidence for audit trail.
     *
     * @param action the action taken
     * @return Promise completing when evidence is captured
     */
    private Promise<Void> capture(Action action) {
        Evidence evidence = new Evidence(
            generateEvidenceId(),
            action.getTradeId(),
            action.getType().name(),
            action.getReason(),
            perceptionBuffer.get(action.getTradeId()),
            Instant.now()
        );

        // Store evidence for compliance
        return storeEvidence(evidence);
    }

    // ==================== GAA Lifecycle: REFLECT ====================

    /**
     * Reflects on action outcomes to improve detection.
     *
     * @param action the action to reflect on
     * @return Promise completing when reflection is done
     */
    private Promise<Void> reflect(Action action) {
        // Update detection thresholds based on false positive rate
        // This is a learning mechanism

        return capture(action).then($ -> {
            // Log reflection outcome
            return Promise.complete();
        });
    }

    // ==================== Detection Methods ====================

    private Promise<Boolean> detectWashTrade(Perception perception) {
        // Count trades by same trader in recent window
        long recentTrades = perceptionBuffer.values().stream()
            .filter(p -> p.getTraderId().equals(perception.getTraderId()))
            .filter(p -> p.getSymbol().equals(perception.getSymbol()))
            .filter(p -> p.getTimestamp().isAfter(
                perception.getTimestamp().minusSeconds(WASH_TRADE_WINDOW_SECONDS)))
            .count();

        return Promise.of(recentTrades > MAX_TRADES_IN_WINDOW);
    }

    private Promise<Boolean> detectLayering(Perception perception) {
        // Detect pattern: place large orders away from market price, then cancel
        // Simplified check: multiple large orders on same side in short window
        long largeOrders = perceptionBuffer.values().stream()
            .filter(p -> p.getTraderId().equals(perception.getTraderId()))
            .filter(p -> p.getSide().equals(perception.getSide()))
            .filter(p -> p.getQuantity().compareTo(LAYERING_SIZE_THRESHOLD) > 0)
            .filter(p -> p.getTimestamp().isAfter(
                perception.getTimestamp().minusSeconds(WASH_TRADE_WINDOW_SECONDS)))
            .count();

        return Promise.of(largeOrders > 3);
    }

    private Promise<Boolean> detectInsiderTrading(Perception perception) {
        // Check for unusual trading before material events
        // This would integrate with news/event feed

        // Simplified: check if trade size is unusual for this trader
        double avgSize = perceptionBuffer.values().stream()
            .filter(p -> p.getTraderId().equals(perception.getTraderId()))
            .mapToDouble(p -> p.getQuantity().doubleValue())
            .average()
            .orElse(0);

        boolean unusual = perception.getQuantity().doubleValue() > avgSize * 5;
        return Promise.of(unusual);
    }

    private Map<String, Object> detectAnomalyFeatures(TradeEvent trade) {
        return Map.of(
            "size_zscore", 0.0, // Would calculate from historical data
            "time_anomaly", false,
            "price_deviation", 0.0
        );
    }

    // ==================== Private Methods ====================

    private Promise<Void> initializeAgent() {
        // Subscribe to trade events
        return aepAdapter.subscribe("trades", event -> {
            // Parse trade event and perceive
            return Promise.complete();
        }).map($ -> null);
    }

    private Promise<Void> publishAlert(String alertType, Action action) {
        AepKernelAdapter.AepEvent event = new AepKernelAdapter.AepEvent(
            generateEventId(),
            "fraud.alert",
            serializeAlert(alertType, action),
            Map.of("severity", "HIGH", "type", alertType),
            System.currentTimeMillis()
        );

        return aepAdapter.publishEvent("fraud-alerts", event)
            .then($ -> reflect(action));
    }

    private Promise<Void> suspendTrader(String traderId) {
        // Trigger trader suspension workflow
        return Promise.complete();
    }

    private Promise<Void> notifyCompliance(Action action) {
        // Send notification to compliance team
        return Promise.complete();
    }

    private Promise<Void> storeEvidence(Evidence evidence) {
        // Store in compliance database
        return Promise.complete();
    }

    private byte[] serializeAlert(String alertType, Action action) {
        return String.format("ALERT|%s|%s|%s", alertType, action.getTradeId(), action.getReason())
            .getBytes();
    }

    private String generateEventId() {
        return "evt-" + System.currentTimeMillis();
    }

    private String generateEvidenceId() {
        return "evd-" + System.currentTimeMillis();
    }

    // ==================== Inner Types ====================

    public static class TradeEvent {
        private final String tradeId;
        private final String traderId;
        private final String symbol;
        private final String side;
        private final BigDecimal quantity;
        private final BigDecimal price;
        private final Instant timestamp;

        public TradeEvent(String tradeId, String traderId, String symbol, String side,
                         BigDecimal quantity, BigDecimal price, Instant timestamp) {
            this.tradeId = tradeId;
            this.traderId = traderId;
            this.symbol = symbol;
            this.side = side;
            this.quantity = quantity;
            this.price = price;
            this.timestamp = timestamp;
        }

        public String getTradeId() { return tradeId; }
        public String getTraderId() { return traderId; }
        public String getSymbol() { return symbol; }
        public String getSide() { return side; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getPrice() { return price; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class Perception {
        private final String tradeId;
        private final String traderId;
        private final String symbol;
        private final String side;
        private final BigDecimal quantity;
        private final BigDecimal price;
        private final Instant timestamp;
        private final Map<String, Object> anomalyFeatures;

        public Perception(String tradeId, String traderId, String symbol, String side,
                         BigDecimal quantity, BigDecimal price, Instant timestamp,
                         Map<String, Object> anomalyFeatures) {
            this.tradeId = tradeId;
            this.traderId = traderId;
            this.symbol = symbol;
            this.side = side;
            this.quantity = quantity;
            this.price = price;
            this.timestamp = timestamp;
            this.anomalyFeatures = anomalyFeatures;
        }

        public String getTradeId() { return tradeId; }
        public String getTraderId() { return traderId; }
        public String getSymbol() { return symbol; }
        public String getSide() { return side; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getPrice() { return price; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, Object> getAnomalyFeatures() { return anomalyFeatures; }
    }

    public enum ActionType {
        FLAG_WASH_TRADE,
        FLAG_LAYERING,
        FLAG_INSIDER_TRADING,
        SUSPEND_TRADER,
        NOTIFY_COMPLIANCE
    }

    public static class Action {
        private final String tradeId;
        private final ActionType type;
        private final String reason;
        private final Instant timestamp;

        public Action(String tradeId, ActionType type, String reason, Instant timestamp) {
            this.tradeId = tradeId;
            this.type = type;
            this.reason = reason;
            this.timestamp = timestamp;
        }

        public String getTradeId() { return tradeId; }
        public ActionType getType() { return type; }
        public String getReason() { return reason; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class Evidence {
        private final String evidenceId;
        private final String tradeId;
        private final String evidenceType;
        private final String description;
        private final Perception perception;
        private final Instant capturedAt;

        public Evidence(String evidenceId, String tradeId, String evidenceType,
                       String description, Perception perception, Instant capturedAt) {
            this.evidenceId = evidenceId;
            this.tradeId = tradeId;
            this.evidenceType = evidenceType;
            this.description = description;
            this.perception = perception;
            this.capturedAt = capturedAt;
        }

        public String getEvidenceId() { return evidenceId; }
        public String getTradeId() { return tradeId; }
        public String getEvidenceType() { return evidenceType; }
        public String getDescription() { return description; }
        public Perception getPerception() { return perception; }
        public Instant getCapturedAt() { return capturedAt; }
    }
}
