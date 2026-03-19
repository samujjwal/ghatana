package com.ghatana.finance.agent;

import com.ghatana.kernel.adapter.aep.AepKernelAdapter;
import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Risk Assessment Agent implementing GAA (Generic Adaptive Agent) lifecycle.
 *
 * <p>Monitors portfolio risk with GAA lifecycle:
 * PERCEIVE → REASON → ACT → CAPTURE → REFLECT</p>
 *
 * <p>Assessment capabilities:
 * <ul>
 *   <li>Real-time position monitoring</li>
 *   <li>VaR (Value at Risk) calculation</li>
 *   <li>Stress testing</li>
 *   <li>Concentration risk analysis</li>
 *   <li>Margin utilization tracking</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Risk assessment agent with GAA lifecycle
 * @doc.layer product
 * @doc.pattern Agent, GAA
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class RiskAssessmentAgent {

    private static final String AGENT_ID = "risk-assessment-agent";
    private static final String VERSION = "1.0.0";

    private final KernelContext context;
    private final AepKernelAdapter aepAdapter;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Risk thresholds
    private static final BigDecimal VAR_THRESHOLD = new BigDecimal("0.05"); // 5% VaR
    private static final BigDecimal MARGIN_THRESHOLD = new BigDecimal("0.80"); // 80% margin
    private static final BigDecimal CONCENTRATION_THRESHOLD = new BigDecimal("0.25"); // 25%

    // State for GAA lifecycle
    private final ConcurrentHashMap<String, RiskPerception> perceptionBuffer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RiskAction> actionHistory = new ConcurrentHashMap<>();

    public RiskAssessmentAgent(KernelContext context) {
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
     * Perceives position and market data changes.
     *
     * @param positionUpdate the position update event
     * @return Promise completing when perception is processed
     */
    public Promise<Void> perceive(PositionUpdate positionUpdate) {
        if (!running.get()) {
            return Promise.complete();
        }

        RiskPerception perception = new RiskPerception(
            positionUpdate.getTraderId(),
            positionUpdate.getSymbol(),
            positionUpdate.getPosition(),
            positionUpdate.getMarketPrice(),
            positionUpdate.getPortfolioValue(),
            positionUpdate.getMarginUsed(),
            positionUpdate.getMarginAvailable(),
            positionUpdate.getTimestamp()
        );

        perceptionBuffer.put(positionUpdate.getTraderId(), perception);

        return reason(perception);
    }

    // ==================== GAA Lifecycle: REASON ====================

    /**
     * Reasons about risk levels and triggers.
     *
     * @param perception the perceived position data
     * @return Promise completing when reasoning is done
     */
    private Promise<Void> reason(RiskPerception perception) {
        // Calculate concentration risk
        BigDecimal concentration = perception.getPosition()
            .multiply(perception.getMarketPrice())
            .divide(perception.getPortfolioValue(), 4, RoundingMode.HALF_UP);

        // Check concentration threshold
        if (concentration.compareTo(CONCENTRATION_THRESHOLD) > 0) {
            return act(new RiskAction(
                perception.getTraderId(),
                RiskActionType.CONCENTRATION_ALERT,
                "Concentration risk: " + concentration.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "% in " + perception.getSymbol(),
                perception.getTimestamp()
            ));
        }

        // Check margin utilization
        BigDecimal marginUtilization = perception.getMarginUsed()
            .divide(perception.getMarginUsed().add(perception.getMarginAvailable()), 4, RoundingMode.HALF_UP);

        if (marginUtilization.compareTo(MARGIN_THRESHOLD) > 0) {
            return act(new RiskAction(
                perception.getTraderId(),
                RiskActionType.MARGIN_WARNING,
                "Margin utilization: " + marginUtilization.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%",
                perception.getTimestamp()
            ));
        }

        // Check for position limit breach
        return checkPositionLimits(perception)
            .then(breach -> {
                if (breach) {
                    return act(new RiskAction(
                        perception.getTraderId(),
                        RiskActionType.POSITION_LIMIT_BREACH,
                        "Position limit breached for " + perception.getSymbol(),
                        perception.getTimestamp()
                    ));
                }
                return Promise.complete();
            });
    }

    // ==================== GAA Lifecycle: ACT ====================

    /**
     * Acts on detected risk conditions.
     *
     * @param action the risk action to take
     * @return Promise completing when action is executed
     */
    private Promise<Void> act(RiskAction action) {
        actionHistory.put(action.getTraderId() + ":" + System.currentTimeMillis(), action);

        return switch (action.getType()) {
            case CONCENTRATION_ALERT -> publishRiskAlert("CONCENTRATION", action);
            case MARGIN_WARNING -> publishRiskAlert("MARGIN", action);
            case POSITION_LIMIT_BREACH -> publishRiskAlert("POSITION_LIMIT", action)
                .then($ -> triggerAutoHedge(action.getTraderId()));
            case VAR_BREACH -> publishRiskAlert("VAR", action);
            case STRESS_TEST_FAIL -> notifyRiskManager(action);
        };
    }

    // ==================== GAA Lifecycle: CAPTURE ====================

    /**
     * Captures risk data for audit and learning.
     *
     * @param action the action taken
     * @return Promise completing when data is captured
     */
    private Promise<Void> capture(RiskAction action) {
        RiskEvidence evidence = new RiskEvidence(
            generateEvidenceId(),
            action.getTraderId(),
            action.getType().name(),
            action.getReason(),
            perceptionBuffer.get(action.getTraderId()),
            Instant.now()
        );

        return storeRiskEvidence(evidence);
    }

    // ==================== GAA Lifecycle: REFLECT ====================

    /**
     * Reflects on risk assessment accuracy and adjusts thresholds.
     *
     * @param action the action to reflect on
     * @return Promise completing when reflection is done
     */
    private Promise<Void> reflect(RiskAction action) {
        // Analyze if alerts were appropriate
        // Adjust thresholds based on false positive rate

        return capture(action).then($ -> {
            // Update risk models
            return Promise.complete();
        });
    }

    // ==================== Risk Calculation Methods ====================

    /**
     * Calculates VaR for a trader's portfolio.
     *
     * @param traderId the trader identifier
     * @param confidence the confidence level (e.g., 0.95)
     * @param timeHorizon the time horizon in days
     * @return Promise containing VaR value
     */
    public Promise<BigDecimal> calculateVaR(String traderId, double confidence, int timeHorizon) {
        if (!running.get()) {
            return Promise.of(BigDecimal.ZERO);
        }

        // Simplified VaR calculation using historical simulation
        return getHistoricalReturns(traderId, 252) // 1 year of data
            .then(returns -> {
                if (returns.isEmpty()) {
                    return Promise.of(BigDecimal.ZERO);
                }

                // Sort returns and find the percentile
                returns.sort(BigDecimal::compareTo);
                int index = (int) Math.floor((1 - confidence) * returns.size());
                BigDecimal var = returns.get(Math.max(0, index)).abs();

                // Scale by time horizon (square root rule)
                var = var.multiply(BigDecimal.valueOf(Math.sqrt(timeHorizon)));

                return Promise.of(var);
            });
    }

    /**
     * Performs stress test on portfolio.
     *
     * @param traderId the trader identifier
     * @param scenario the stress scenario name
     * @return Promise containing stress test result
     */
    public Promise<StressTestResult> runStressTest(String traderId, String scenario) {
        if (!running.get()) {
            return Promise.of(StressTestResult.empty());
        }

        // Define shock scenarios
        BigDecimal shock = switch (scenario) {
            case "MARKET_CRASH" -> new BigDecimal("-0.20"); // -20%
            case "LIQUIDITY_CRISIS" -> new BigDecimal("-0.15");
            case "INTEREST_RATE_SPIKE" -> new BigDecimal("-0.10");
            default -> new BigDecimal("-0.10");
        };

        return getCurrentPortfolio(traderId)
            .then(portfolio -> {
                BigDecimal portfolioValue = portfolio.getTotalValue();
                BigDecimal loss = portfolioValue.multiply(shock);
                BigDecimal newValue = portfolioValue.add(loss);

                StressTestResult result = new StressTestResult(
                    scenario,
                    portfolioValue,
                    loss,
                    newValue,
                    shock.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%",
                    Instant.now()
                );

                // Check if loss exceeds VaR threshold
                if (loss.abs().divide(portfolioValue, 4, RoundingMode.HALF_UP).compareTo(VAR_THRESHOLD) > 0) {
                    return act(new RiskAction(
                        traderId,
                        RiskActionType.STRESS_TEST_FAIL,
                        "Stress test " + scenario + " failed: " + result.getShockDescription(),
                        Instant.now()
                    )).map($ -> result);
                }

                return Promise.of(result);
            });
    }

    // ==================== Private Methods ====================

    private Promise<Void> initializeAgent() {
        // Subscribe to position updates
        return aepAdapter.subscribe("position-updates", event -> {
            return Promise.complete();
        }).map($ -> null);
    }

    private Promise<Boolean> checkPositionLimits(RiskPerception perception) {
        // Check against configured position limits
        BigDecimal positionValue = perception.getPosition()
            .multiply(perception.getMarketPrice());

        // Default limit: 10% of portfolio
        BigDecimal limit = perception.getPortfolioValue()
            .multiply(new BigDecimal("0.10"));

        return Promise.of(positionValue.compareTo(limit) > 0);
    }

    private Promise<List<BigDecimal>> getHistoricalReturns(String traderId, int days) {
        // Query historical returns from data store
        return Promise.of(List.of()); // Placeholder
    }

    private Promise<Portfolio> getCurrentPortfolio(String traderId) {
        // Query current portfolio from data store
        return Promise.of(new Portfolio(BigDecimal.ZERO)); // Placeholder
    }

    private Promise<Void> publishRiskAlert(String alertType, RiskAction action) {
        AepKernelAdapter.AepEvent event = new AepKernelAdapter.AepEvent(
            generateEventId(),
            "risk.alert",
            serializeAlert(alertType, action),
            Map.of("severity", "HIGH", "type", alertType, "traderId", action.getTraderId()),
            System.currentTimeMillis()
        );

        return aepAdapter.publishEvent("risk-alerts", event)
            .then($ -> reflect(action));
    }

    private Promise<Void> triggerAutoHedge(String traderId) {
        // Trigger automatic hedging workflow
        return Promise.complete();
    }

    private Promise<Void> notifyRiskManager(RiskAction action) {
        // Send notification to risk manager
        return Promise.complete();
    }

    private Promise<Void> storeRiskEvidence(RiskEvidence evidence) {
        // Store in risk database
        return Promise.complete();
    }

    private byte[] serializeAlert(String alertType, RiskAction action) {
        return String.format("RISK|%s|%s|%s", alertType, action.getTraderId(), action.getReason())
            .getBytes();
    }

    private String generateEventId() {
        return "evt-" + System.currentTimeMillis();
    }

    private String generateEvidenceId() {
        return "evd-" + System.currentTimeMillis();
    }

    // ==================== Inner Types ====================

    public static class PositionUpdate {
        private final String traderId;
        private final String symbol;
        private final BigDecimal position;
        private final BigDecimal marketPrice;
        private final BigDecimal portfolioValue;
        private final BigDecimal marginUsed;
        private final BigDecimal marginAvailable;
        private final Instant timestamp;

        public PositionUpdate(String traderId, String symbol, BigDecimal position,
                             BigDecimal marketPrice, BigDecimal portfolioValue,
                             BigDecimal marginUsed, BigDecimal marginAvailable,
                             Instant timestamp) {
            this.traderId = traderId;
            this.symbol = symbol;
            this.position = position;
            this.marketPrice = marketPrice;
            this.portfolioValue = portfolioValue;
            this.marginUsed = marginUsed;
            this.marginAvailable = marginAvailable;
            this.timestamp = timestamp;
        }

        public String getTraderId() { return traderId; }
        public String getSymbol() { return symbol; }
        public BigDecimal getPosition() { return position; }
        public BigDecimal getMarketPrice() { return marketPrice; }
        public BigDecimal getPortfolioValue() { return portfolioValue; }
        public BigDecimal getMarginUsed() { return marginUsed; }
        public BigDecimal getMarginAvailable() { return marginAvailable; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class RiskPerception {
        private final String traderId;
        private final String symbol;
        private final BigDecimal position;
        private final BigDecimal marketPrice;
        private final BigDecimal portfolioValue;
        private final BigDecimal marginUsed;
        private final BigDecimal marginAvailable;
        private final Instant timestamp;

        public RiskPerception(String traderId, String symbol, BigDecimal position,
                             BigDecimal marketPrice, BigDecimal portfolioValue,
                             BigDecimal marginUsed, BigDecimal marginAvailable,
                             Instant timestamp) {
            this.traderId = traderId;
            this.symbol = symbol;
            this.position = position;
            this.marketPrice = marketPrice;
            this.portfolioValue = portfolioValue;
            this.marginUsed = marginUsed;
            this.marginAvailable = marginAvailable;
            this.timestamp = timestamp;
        }

        public String getTraderId() { return traderId; }
        public String getSymbol() { return symbol; }
        public BigDecimal getPosition() { return position; }
        public BigDecimal getMarketPrice() { return marketPrice; }
        public BigDecimal getPortfolioValue() { return portfolioValue; }
        public BigDecimal getMarginUsed() { return marginUsed; }
        public BigDecimal getMarginAvailable() { return marginAvailable; }
        public Instant getTimestamp() { return timestamp; }
    }

    public enum RiskActionType {
        CONCENTRATION_ALERT,
        MARGIN_WARNING,
        POSITION_LIMIT_BREACH,
        VAR_BREACH,
        STRESS_TEST_FAIL
    }

    public static class RiskAction {
        private final String traderId;
        private final RiskActionType type;
        private final String reason;
        private final Instant timestamp;

        public RiskAction(String traderId, RiskActionType type, String reason, Instant timestamp) {
            this.traderId = traderId;
            this.type = type;
            this.reason = reason;
            this.timestamp = timestamp;
        }

        public String getTraderId() { return traderId; }
        public RiskActionType getType() { return type; }
        public String getReason() { return reason; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class RiskEvidence {
        private final String evidenceId;
        private final String traderId;
        private final String evidenceType;
        private final String description;
        private final RiskPerception perception;
        private final Instant capturedAt;

        public RiskEvidence(String evidenceId, String traderId, String evidenceType,
                           String description, RiskPerception perception, Instant capturedAt) {
            this.evidenceId = evidenceId;
            this.traderId = traderId;
            this.evidenceType = evidenceType;
            this.description = description;
            this.perception = perception;
            this.capturedAt = capturedAt;
        }

        public String getEvidenceId() { return evidenceId; }
        public String getTraderId() { return traderId; }
        public String getEvidenceType() { return evidenceType; }
        public String getDescription() { return description; }
        public RiskPerception getPerception() { return perception; }
        public Instant getCapturedAt() { return capturedAt; }
    }

    public static class Portfolio {
        private final BigDecimal totalValue;

        public Portfolio(BigDecimal totalValue) {
            this.totalValue = totalValue;
        }

        public BigDecimal getTotalValue() { return totalValue; }
    }

    public static class StressTestResult {
        private final String scenario;
        private final BigDecimal initialValue;
        private final BigDecimal projectedLoss;
        private final BigDecimal projectedValue;
        private final String shockDescription;
        private final Instant timestamp;

        public StressTestResult(String scenario, BigDecimal initialValue, BigDecimal projectedLoss,
                               BigDecimal projectedValue, String shockDescription,
                               Instant timestamp) {
            this.scenario = scenario;
            this.initialValue = initialValue;
            this.projectedLoss = projectedLoss;
            this.projectedValue = projectedValue;
            this.shockDescription = shockDescription;
            this.timestamp = timestamp;
        }

        public static StressTestResult empty() {
            return new StressTestResult(null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, Instant.now());
        }

        public String getScenario() { return scenario; }
        public BigDecimal getInitialValue() { return initialValue; }
        public BigDecimal getProjectedLoss() { return projectedLoss; }
        public BigDecimal getProjectedValue() { return projectedValue; }
        public String getShockDescription() { return shockDescription; }
        public Instant getTimestamp() { return timestamp; }
    }
}
