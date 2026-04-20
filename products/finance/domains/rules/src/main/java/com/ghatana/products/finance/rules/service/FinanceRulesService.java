/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.rules.service;

import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.Map;

/**
 * Finance Rules Service.
 *
 * <p>Finance-specific rules engine service. Contains business rules for trade validation,
 * compliance checking, risk assessment, and other finance-specific rule processing.
 * This service uses kernel capabilities for generic rule processing and implements
 * finance-specific business logic.</p>
 *
 * @doc.type class
 * @doc.purpose Finance rules service - finance-specific business rules processing
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceRulesService {

    private static final Logger log = LoggerFactory.getLogger(FinanceRulesService.class);

    private final KernelContext context;
    private final Executor executor;
    private volatile boolean started = false;

    /**
     * Creates a new finance rules service.
     *
     * @param context the kernel context
     */
    public FinanceRulesService(KernelContext context) {
        this.context = context;
        this.executor = context.getExecutor("finance-rules");
    }

    /**
     * Starts the finance rules service.
     */
    public void start() {
        log.info("Starting finance rules service");
        started = true;
        log.info("Finance rules service started");
    }

    /**
     * Stops the finance rules service.
     */
    public void stop() {
        log.info("Stopping finance rules service");
        started = false;
        log.info("Finance rules service stopped");
    }

    /**
     * Checks if the service is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return started;
    }

    /**
     * Validates a trade using finance-specific business rules.
     *
     * @param tenantId tenant identifier
     * @param tradeData trade data to validate
     * @return Promise containing validation result
     */
    public Promise<TradeValidationResult> validateTrade(String tenantId, Map<String, Object> tradeData) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Finance rules service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Validating trade for tenant: {}", tenantId);

            // Finance-specific trade validation logic
            boolean valid = validateTradeRules(tradeData);
            String reason = valid ? "Trade valid" : "Trade violates business rules";

            TradeValidationResult result = new TradeValidationResult(valid, reason);

            if (valid) {
                log.info("Trade validation successful for tenant: {}", tenantId);
            } else {
                log.warn("Trade validation failed for tenant: {} - {}", tenantId, reason);
            }

            return result;
        });
    }

    /**
     * Performs compliance checking using finance-specific rules.
     *
     * @param tenantId tenant identifier
     * @param complianceData compliance data to check
     * @return Promise containing compliance result
     */
    public Promise<ComplianceResult> checkCompliance(String tenantId, Map<String, Object> complianceData) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Finance rules service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Checking compliance for tenant: {}", tenantId);

            // Finance-specific compliance checking logic
            boolean compliant = checkComplianceRules(complianceData);
            String reason = compliant ? "Compliant" : "Non-compliant";

            ComplianceResult result = new ComplianceResult(compliant, reason);

            if (compliant) {
                log.info("Compliance check successful for tenant: {}", tenantId);
            } else {
                log.warn("Compliance check failed for tenant: {} - {}", tenantId, reason);
            }

            return result;
        });
    }

    /**
     * Performs risk assessment using finance-specific rules.
     *
     * @param tenantId tenant identifier
     * @param riskData risk data to assess
     * @return Promise containing risk assessment result
     */
    public Promise<RiskAssessmentResult> assessRisk(String tenantId, Map<String, Object> riskData) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Finance rules service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Assessing risk for tenant: {}", tenantId);

            // Finance-specific risk assessment logic
            String riskLevel = calculateRiskLevel(riskData);
            double riskScore = calculateRiskScore(riskData);

            RiskAssessmentResult result = new RiskAssessmentResult(riskLevel, riskScore);

            log.info("Risk assessment completed for tenant: {} - level: {}, score: {}",
                    tenantId, riskLevel, riskScore);

            return result;
        });
    }

    // ==================== Private Methods ====================

    private boolean validateTradeRules(Map<String, Object> tradeData) {
        // Finance-specific trade validation rules
        // Basic validation logic - can be extended with OPA policy evaluation

        if (tradeData == null || tradeData.isEmpty()) {
            return false;
        }

        // Check trade amount limits
        Object amountObj = tradeData.get("amount");
        if (amountObj == null) {
            log.warn("Trade validation failed: missing amount");
            return false;
        }
        
        double amount;
        try {
            amount = ((Number) amountObj).doubleValue();
        } catch (ClassCastException e) {
            log.warn("Trade validation failed: invalid amount format");
            return false;
        }

        if (amount <= 0) {
            log.warn("Trade validation failed: amount must be positive, got {}", amount);
            return false;
        }

        // Check for maximum trade amount limit (configurable, default $10M)
        double maxAmount = Double.parseDouble(System.getenv().getOrDefault("MAX_TRADE_AMOUNT", "10000000"));
        if (amount > maxAmount) {
            log.warn("Trade validation failed: amount {} exceeds maximum {}", amount, maxAmount);
            return false;
        }

        // Validate instrument eligibility
        Object instrumentObj = tradeData.get("instrument");
        if (instrumentObj == null || instrumentObj.toString().isBlank()) {
            log.warn("Trade validation failed: missing instrument");
            return false;
        }

        // Validate counterparty
        Object counterpartyObj = tradeData.get("counterparty");
        if (counterpartyObj == null || counterpartyObj.toString().isBlank()) {
            log.warn("Trade validation failed: missing counterparty");
            return false;
        }

        // Validate trading hours (basic check - can be enhanced with market calendar)
        Object timestampObj = tradeData.get("timestamp");
        if (timestampObj != null) {
            // Could add market hours validation here
            log.debug("Trade timestamp present: {}", timestampObj);
        }

        return true;
    }

    private boolean checkComplianceRules(Map<String, Object> complianceData) {
        // Finance-specific compliance checking rules
        // Basic compliance logic - can be extended with OPA policy evaluation

        if (complianceData == null || complianceData.isEmpty()) {
            return false;
        }

        // Check KYC (Know Your Customer) status
        Object kycVerifiedObj = complianceData.get("kycVerified");
        boolean kycVerified = kycVerifiedObj != null && Boolean.parseBoolean(kycVerifiedObj.toString());
        if (!kycVerified) {
            log.warn("Compliance check failed: KYC not verified");
            return false;
        }

        // Check AML (Anti-Money Laundering) screening
        Object amlScreenedObj = complianceData.get("amlScreened");
        boolean amlScreened = amlScreenedObj != null && Boolean.parseBoolean(amlScreenedObj.toString());
        if (!amlScreened) {
            log.warn("Compliance check failed: AML screening not completed");
            return false;
        }

        // Check regulatory reporting threshold (configurable, default $10K)
        Object amountObj = complianceData.get("amount");
        if (amountObj != null) {
            try {
                double amount = ((Number) amountObj).doubleValue();
                double reportingThreshold = Double.parseDouble(System.getenv().getOrDefault("REPORTING_THRESHOLD", "10000"));
                if (amount >= reportingThreshold) {
                    Object reportedObj = complianceData.get("regulatoryReported");
                    boolean reported = reportedObj != null && Boolean.parseBoolean(reportedObj.toString());
                    if (!reported) {
                        log.warn("Compliance check failed: amount {} requires regulatory reporting", amount);
                        return false;
                    }
                }
            } catch (ClassCastException e) {
                log.warn("Compliance check failed: invalid amount format");
                return false;
            }
        }

        // Check for sanctioned entities
        Object sanctionedObj = complianceData.get("sanctioned");
        boolean sanctioned = sanctionedObj != null && Boolean.parseBoolean(sanctionedObj.toString());
        if (sanctioned) {
            log.warn("Compliance check failed: counterparty is sanctioned");
            return false;
        }

        // Check jurisdiction compliance
        Object jurisdictionObj = complianceData.get("jurisdiction");
        if (jurisdictionObj == null || jurisdictionObj.toString().isBlank()) {
            log.warn("Compliance check failed: missing jurisdiction");
            return false;
        }

        return true;
    }

    private String calculateRiskLevel(Map<String, Object> riskData) {
        // Finance-specific risk level calculation
        // Basic risk assessment logic - can be extended with OPA policy evaluation

        if (riskData == null || riskData.isEmpty()) {
            return "UNKNOWN";
        }

        double riskScore = calculateRiskScore(riskData);

        // Risk level thresholds (configurable)
        double highThreshold = Double.parseDouble(System.getenv().getOrDefault("RISK_HIGH_THRESHOLD", "0.7"));
        double mediumThreshold = Double.parseDouble(System.getenv().getOrDefault("RISK_MEDIUM_THRESHOLD", "0.4"));

        if (riskScore >= highThreshold) {
            return "HIGH";
        } else if (riskScore >= mediumThreshold) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private double calculateRiskScore(Map<String, Object> riskData) {
        // Finance-specific risk score calculation
        // Basic risk scoring logic - can be extended with OPA policy evaluation

        if (riskData == null || riskData.isEmpty()) {
            return 0.5; // Default medium risk
        }

        double riskScore = 0.0;

        // Amount-based risk (larger amounts = higher risk)
        Object amountObj = riskData.get("amount");
        if (amountObj != null) {
            try {
                double amount = ((Number) amountObj).doubleValue();
                double maxAmount = Double.parseDouble(System.getenv().getOrDefault("MAX_TRADE_AMOUNT", "10000000"));
                riskScore += (amount / maxAmount) * 0.3; // Up to 30% risk from amount
            } catch (ClassCastException e) {
                log.warn("Risk calculation failed: invalid amount format");
            }
        }

        // Counterparty risk
        Object counterpartyRiskObj = riskData.get("counterpartyRisk");
        if (counterpartyRiskObj != null) {
            try {
                double counterpartyRisk = ((Number) counterpartyRiskObj).doubleValue();
                riskScore += counterpartyRisk * 0.25; // Up to 25% risk from counterparty
            } catch (ClassCastException e) {
                log.warn("Risk calculation failed: invalid counterparty risk format");
            }
        }

        // Market risk
        Object marketRiskObj = riskData.get("marketRisk");
        if (marketRiskObj != null) {
            try {
                double marketRisk = ((Number) marketRiskObj).doubleValue();
                riskScore += marketRisk * 0.25; // Up to 25% risk from market
            } catch (ClassCastException e) {
                log.warn("Risk calculation failed: invalid market risk format");
            }
        }

        // Volatility risk
        Object volatilityObj = riskData.get("volatility");
        if (volatilityObj != null) {
            try {
                double volatility = ((Number) volatilityObj).doubleValue();
                riskScore += (volatility / 100.0) * 0.2; // Up to 20% risk from volatility
            } catch (ClassCastException e) {
                log.warn("Risk calculation failed: invalid volatility format");
            }
        }

        // Ensure score is within valid range
        return Math.max(0.0, Math.min(1.0, riskScore));
    }

    // ==================== Result Types ====================

    /**
     * Result of trade validation.
     */
    public record TradeValidationResult(boolean valid, String reason) {}

    /**
     * Result of compliance checking.
     */
    public record ComplianceResult(boolean compliant, String reason) {}

    /**
     * Result of risk assessment.
     */
    public record RiskAssessmentResult(String riskLevel, double riskScore) {}
}
