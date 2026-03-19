package com.ghatana.finance.extension;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.extension.KernelExtension;
import com.ghatana.kernel.module.KernelModule;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real-time risk management extension for trading operations.
 *
 * <p>Calculates portfolio risk, position limits, VaR (Value at Risk),
 * and provides real-time risk monitoring with alerting capabilities.</p>
 *
 * @doc.type class
 * @doc.purpose Real-time risk management with portfolio and position risk calculations
 * @doc.layer product
 * @doc.pattern Extension
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class RiskManagementKernelExtension implements KernelExtension {

    private static final String EXTENSION_ID = "risk-management-realtime";
    private static final String VERSION = "1.0.0";

    private volatile KernelContext context;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, PositionRisk> positionRisks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PortfolioRisk> portfolioRisks = new ConcurrentHashMap<>();
    private final AtomicReference<RiskLimits> riskLimits = new AtomicReference<>();

    @Override
    public String getExtensionId() {
        return EXTENSION_ID;
    }

    @Override
    public String getName() {
        return "Real-time Risk Management";
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return KernelDescriptor.builder()
            .descriptorId(EXTENSION_ID)
            .name(getName())
            .version(VERSION)
            .description("Real-time risk management with VaR, position limits, and portfolio monitoring")
            .type(KernelDescriptor.ComponentType.EXTENSION)
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of(
            new KernelCapability(
                "risk.management",
                "Risk Management",
                "Real-time risk calculation and monitoring",
                KernelCapability.CapabilityType.AI_ML,
                Map.of(
                    "real_time", "true",
                    "supports_var", "true",
                    "position_limits", "true",
                    "portfolio_monitoring", "true"
                )
            )
        );
    }

    @Override
    public void onModuleInitialized(KernelContext context) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        this.context = context;
        initializeRiskLimits();
    }

    @Override
    public void onModuleStarted(KernelContext context) {
        started.set(true);
    }

    @Override
    public void onModuleStopped(KernelContext context) {
        started.set(false);
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        return hostModule.getCapabilities().stream()
            .anyMatch(c -> c.getCapabilityId().equals("event.processing"));
    }

    @Override
    public int getPriority() {
        return 200; // Critical priority
    }

    // ==================== Risk Calculation API ====================

    /**
     * Calculates position risk for a trading position.
     *
     * @param positionId the position identifier
     * @param quantity the position quantity
     * @param currentPrice the current market price
     * @param avgCost the average cost basis
     * @return Promise containing position risk metrics
     */
    public Promise<PositionRisk> calculatePositionRisk(String positionId, BigDecimal quantity,
                                                      BigDecimal currentPrice, BigDecimal avgCost) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Extension not started"));
        }

        // Calculate unrealized P&L
        BigDecimal marketValue = quantity.multiply(currentPrice);
        BigDecimal costBasis = quantity.multiply(avgCost);
        BigDecimal unrealizedPnL = marketValue.subtract(costBasis);

        // Calculate position delta (price sensitivity)
        BigDecimal delta = quantity; // Simplified - real would use option Greeks for derivatives

        // Calculate notional exposure
        BigDecimal notionalExposure = marketValue.abs();

        PositionRisk risk = new PositionRisk(
            positionId,
            quantity,
            currentPrice,
            avgCost,
            unrealizedPnL,
            delta,
            notionalExposure,
            calculatePositionLimitUtilization(positionId, notionalExposure),
            Instant.now()
        );

        positionRisks.put(positionId, risk);

        // Check limits and alert if breached
        checkPositionLimits(positionId, risk);

        return Promise.of(risk);
    }

    /**
     * Calculates portfolio-level risk metrics.
     *
     * @param portfolioId the portfolio identifier
     * @param positionRisks map of position risks
     * @return Promise containing portfolio risk metrics
     */
    public Promise<PortfolioRisk> calculatePortfolioRisk(String portfolioId,
                                                        Map<String, PositionRisk> positionRisks) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Extension not started"));
        }

        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPnL = BigDecimal.ZERO;
        BigDecimal totalNotionalExposure = BigDecimal.ZERO;

        for (PositionRisk risk : positionRisks.values()) {
            totalMarketValue = totalMarketValue.add(
                risk.getQuantity().multiply(risk.getCurrentPrice())
            );
            totalUnrealizedPnL = totalUnrealizedPnL.add(risk.getUnrealizedPnL());
            totalNotionalExposure = totalNotionalExposure.add(risk.getNotionalExposure());
        }

        // Calculate VaR (Value at Risk) - 95% confidence, 1-day horizon
        BigDecimal portfolioVaR = calculateVaR(positionRisks, new BigDecimal("0.95"), 1);

        // Calculate concentration risk
        BigDecimal concentrationRisk = calculateConcentrationRisk(positionRisks, totalMarketValue);

        PortfolioRisk portfolioRisk = new PortfolioRisk(
            portfolioId,
            totalMarketValue,
            totalUnrealizedPnL,
            totalNotionalExposure,
            portfolioVaR,
            concentrationRisk,
            positionRisks.size(),
            Instant.now()
        );

        this.portfolioRisks.put(portfolioId, portfolioRisk);

        // Check portfolio limits
        checkPortfolioLimits(portfolioId, portfolioRisk);

        return Promise.of(portfolioRisk);
    }

    /**
     * Updates risk limits.
     *
     * @param limits the new risk limits
     * @return Promise completing when limits are updated
     */
    public Promise<Void> updateRiskLimits(RiskLimits limits) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Extension not started"));
        }

        riskLimits.set(limits);
        return Promise.complete();
    }

    /**
     * Gets current risk limits.
     *
     * @return Current risk limits
     */
    public RiskLimits getRiskLimits() {
        return riskLimits.get();
    }

    /**
     * Gets position risk by ID.
     *
     * @param positionId the position identifier
     * @return Position risk if found
     */
    public PositionRisk getPositionRisk(String positionId) {
        return positionRisks.get(positionId);
    }

    /**
     * Gets portfolio risk by ID.
     *
     * @param portfolioId the portfolio identifier
     * @return Portfolio risk if found
     */
    public PortfolioRisk getPortfolioRisk(String portfolioId) {
        return portfolioRisks.get(portfolioId);
    }

    // ==================== Private Methods ====================

    private void initializeRiskLimits() {
        riskLimits.set(new RiskLimits(
            new BigDecimal("10000000"), // Max position notional: 10M
            new BigDecimal("50000000"), // Max portfolio notional: 50M
            new BigDecimal("0.05"),     // Max VaR (5%)
            new BigDecimal("0.25"),     // Max concentration (25%)
            new BigDecimal("1000000")   // Max single position loss: 1M
        ));
    }

    private BigDecimal calculatePositionLimitUtilization(String positionId, BigDecimal notionalExposure) {
        RiskLimits limits = riskLimits.get();
        if (limits == null || limits.getMaxPositionNotional().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return notionalExposure.divide(limits.getMaxPositionNotional(), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateVaR(Map<String, PositionRisk> positionRisks, BigDecimal confidence, int days) {
        // Simplified VaR calculation - real implementation uses historical simulation or parametric method
        BigDecimal totalExposure = BigDecimal.ZERO;
        for (PositionRisk risk : positionRisks.values()) {
            totalExposure = totalExposure.add(risk.getNotionalExposure());
        }

        // Assumed 2% daily volatility
        BigDecimal dailyVol = new BigDecimal("0.02");
        BigDecimal zScore = new BigDecimal("1.645"); // 95% confidence

        return totalExposure.multiply(dailyVol).multiply(zScore)
            .multiply(BigDecimal.valueOf(Math.sqrt(days)));
    }

    private BigDecimal calculateConcentrationRisk(Map<String, PositionRisk> positionRisks,
                                                   BigDecimal totalMarketValue) {
        if (totalMarketValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // Find largest position as % of portfolio
        BigDecimal maxPosition = BigDecimal.ZERO;
        for (PositionRisk risk : positionRisks.values()) {
            BigDecimal positionValue = risk.getQuantity().multiply(risk.getCurrentPrice()).abs();
            if (positionValue.compareTo(maxPosition) > 0) {
                maxPosition = positionValue;
            }
        }

        return maxPosition.divide(totalMarketValue, 4, RoundingMode.HALF_UP);
    }

    private void checkPositionLimits(String positionId, PositionRisk risk) {
        RiskLimits limits = riskLimits.get();
        if (limits == null) return;

        if (risk.getLimitUtilization().compareTo(BigDecimal.ONE) > 0) {
            publishRiskAlert("POSITION_LIMIT_BREACH", positionId, risk.getLimitUtilization());
        }
    }

    private void checkPortfolioLimits(String portfolioId, PortfolioRisk risk) {
        RiskLimits limits = riskLimits.get();
        if (limits == null) return;

        // Check VaR limit
        if (risk.getPortfolioVaR().compareTo(limits.getMaxPortfolioVaR()) > 0) {
            publishRiskAlert("PORTFOLIO_VAR_BREACH", portfolioId, risk.getPortfolioVaR());
        }

        // Check concentration limit
        if (risk.getConcentrationRisk().compareTo(limits.getMaxConcentration()) > 0) {
            publishRiskAlert("CONCENTRATION_BREACH", portfolioId, risk.getConcentrationRisk());
        }
    }

    private void publishRiskAlert(String alertType, String entityId, BigDecimal value) {
        // Publish risk alert to event system
    }

    // ==================== Inner Types ====================

    public static class PositionRisk {
        private final String positionId;
        private final BigDecimal quantity;
        private final BigDecimal currentPrice;
        private final BigDecimal avgCost;
        private final BigDecimal unrealizedPnL;
        private final BigDecimal delta;
        private final BigDecimal notionalExposure;
        private final BigDecimal limitUtilization;
        private final Instant calculatedAt;

        public PositionRisk(String positionId, BigDecimal quantity, BigDecimal currentPrice,
                           BigDecimal avgCost, BigDecimal unrealizedPnL, BigDecimal delta,
                           BigDecimal notionalExposure, BigDecimal limitUtilization, Instant calculatedAt) {
            this.positionId = positionId;
            this.quantity = quantity;
            this.currentPrice = currentPrice;
            this.avgCost = avgCost;
            this.unrealizedPnL = unrealizedPnL;
            this.delta = delta;
            this.notionalExposure = notionalExposure;
            this.limitUtilization = limitUtilization;
            this.calculatedAt = calculatedAt;
        }

        public String getPositionId() { return positionId; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public BigDecimal getAvgCost() { return avgCost; }
        public BigDecimal getUnrealizedPnL() { return unrealizedPnL; }
        public BigDecimal getDelta() { return delta; }
        public BigDecimal getNotionalExposure() { return notionalExposure; }
        public BigDecimal getLimitUtilization() { return limitUtilization; }
        public Instant getCalculatedAt() { return calculatedAt; }
    }

    public static class PortfolioRisk {
        private final String portfolioId;
        private final BigDecimal totalMarketValue;
        private final BigDecimal totalUnrealizedPnL;
        private final BigDecimal totalNotionalExposure;
        private final BigDecimal portfolioVaR;
        private final BigDecimal concentrationRisk;
        private final int positionCount;
        private final Instant calculatedAt;

        public PortfolioRisk(String portfolioId, BigDecimal totalMarketValue,
                             BigDecimal totalUnrealizedPnL, BigDecimal totalNotionalExposure,
                             BigDecimal portfolioVaR, BigDecimal concentrationRisk,
                             int positionCount, Instant calculatedAt) {
            this.portfolioId = portfolioId;
            this.totalMarketValue = totalMarketValue;
            this.totalUnrealizedPnL = totalUnrealizedPnL;
            this.totalNotionalExposure = totalNotionalExposure;
            this.portfolioVaR = portfolioVaR;
            this.concentrationRisk = concentrationRisk;
            this.positionCount = positionCount;
            this.calculatedAt = calculatedAt;
        }

        public String getPortfolioId() { return portfolioId; }
        public BigDecimal getTotalMarketValue() { return totalMarketValue; }
        public BigDecimal getTotalUnrealizedPnL() { return totalUnrealizedPnL; }
        public BigDecimal getTotalNotionalExposure() { return totalNotionalExposure; }
        public BigDecimal getPortfolioVaR() { return portfolioVaR; }
        public BigDecimal getConcentrationRisk() { return concentrationRisk; }
        public int getPositionCount() { return positionCount; }
        public Instant getCalculatedAt() { return calculatedAt; }
    }

    public static class RiskLimits {
        private final BigDecimal maxPositionNotional;
        private final BigDecimal maxPortfolioNotional;
        private final BigDecimal maxPortfolioVaR;
        private final BigDecimal maxConcentration;
        private final BigDecimal maxPositionLoss;

        public RiskLimits(BigDecimal maxPositionNotional, BigDecimal maxPortfolioNotional,
                         BigDecimal maxPortfolioVaR, BigDecimal maxConcentration,
                         BigDecimal maxPositionLoss) {
            this.maxPositionNotional = maxPositionNotional;
            this.maxPortfolioNotional = maxPortfolioNotional;
            this.maxPortfolioVaR = maxPortfolioVaR;
            this.maxConcentration = maxConcentration;
            this.maxPositionLoss = maxPositionLoss;
        }

        public BigDecimal getMaxPositionNotional() { return maxPositionNotional; }
        public BigDecimal getMaxPortfolioNotional() { return maxPortfolioNotional; }
        public BigDecimal getMaxPortfolioVaR() { return maxPortfolioVaR; }
        public BigDecimal getMaxConcentration() { return maxConcentration; }
        public BigDecimal getMaxPositionLoss() { return maxPositionLoss; }
    }
}
