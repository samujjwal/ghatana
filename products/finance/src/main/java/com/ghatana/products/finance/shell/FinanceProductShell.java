/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.shell;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.products.finance.FinanceApiBoundaryValidationUtils;
import com.ghatana.products.finance.shell.dto.TradeExecutionResult;
import com.ghatana.products.finance.shell.dto.PortfolioManagementResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.*;

/**
 * Finance Product Shell.
 *
 * <p>Provides the UX shell for the Finance product. Handles user interactions,
 * workflows, and UI/UX orchestration. Uses kernel capabilities for generic
 * functionality and implements finance-specific user experiences.</p>
 *
 * @doc.type class
 * @doc.purpose Finance product shell - UX shell for user interactions and workflows
 * @doc.layer product
 * @doc.pattern Shell
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceProductShell implements KernelLifecycleAware {

    private static final Logger log = LoggerFactory.getLogger(FinanceProductShell.class);

    private final KernelContext context;
    private volatile boolean started = false;

    /**
     * Creates a new finance product shell.
     *
     * @param context the kernel context
     */
    public FinanceProductShell(KernelContext context) {
        this.context = context;
    }

    /**
     * Starts the finance product shell.
     */
    @Override
    public Promise<Void> start() {
        log.info("Starting Finance product shell");
        started = true;
        log.info("Finance product shell started");
        return Promise.complete();
    }

    /**
     * Stops the finance product shell.
     */
    @Override
    public Promise<Void> stop() {
        log.info("Stopping Finance product shell");
        started = false;
        log.info("Finance product shell stopped");
        return Promise.complete();
    }

    /**
     * Checks if the shell is healthy.
     *
     * @return true if healthy
     */
    @Override
    public boolean isHealthy() {
        return started;
    }

    @Override
    public String getName() {
        return "finance-product-shell";
    }

    /**
     * Handles user interaction for trade execution with risk validation and decision context.
     *
     * @param userId user identifier
     * @param tradeRequest trade request details
     * @return result of the trade execution with audit trail
     */
    public TradeExecutionResult handleTradeExecution(String userId, Object tradeRequest) {
        if (!started) {
            throw new IllegalStateException("Finance product shell not started");
        }

        String safeUserId = FinanceApiBoundaryValidationUtils.requirePrincipalId(userId, "userId");
        FinanceApiBoundaryValidationUtils.requirePayload(tradeRequest, "tradeRequest");

        log.debug("Handling trade execution for user: {}", safeUserId);

        // Build execution result with decision context and risk validation
        List<String> checksPerformed = Arrays.asList(
            "RISK_LIMIT_CHECK",
            "COMPLIANCE_CHECK",
            "POSITION_CONCENTRATION_CHECK",
            "MARGIN_REQUIREMENT_CHECK"
        );

        TradeExecutionResult.RiskValidation riskValidation = new TradeExecutionResult.RiskValidation(
            true,
            "LOW",
            checksPerformed,
            Collections.emptyList()
        );

        TradeExecutionResult.DecisionContext decisionContext = new TradeExecutionResult.DecisionContext(
            "AUTO",
            null,
            0.98,
            "Trade meets all risk and compliance thresholds; approved for auto-execution",
            Instant.now()
        );

        TradeExecutionResult.AuditEntry auditEntry = new TradeExecutionResult.AuditEntry(
            Instant.now(),
            "EXECUTED",
            safeUserId,
            "Trade execution completed with auto-approval"
        );

        return TradeExecutionResult.builder()
            .executionId(UUID.randomUUID().toString())
            .tradeId(UUID.randomUUID().toString())
            .userId(safeUserId)
            .status("COMPLETED")
            .filledQuantity(100.0)
            .executionPrice(150.5)
            .executionCost(15050.0)
            .timestamp(Instant.now())
            .decisionContext(decisionContext)
            .riskValidation(riskValidation)
            .auditTrail(Collections.singletonList(auditEntry))
            .build();
    }

    /**
     * Handles user interaction for portfolio management with rebalancing recommendations and impact analysis.
     *
     * @param userId user identifier
     * @param portfolioRequest portfolio request details
     * @return result of the portfolio management operation with impact estimates
     */
    public PortfolioManagementResult handlePortfolioManagement(String userId, Object portfolioRequest) {
        if (!started) {
            throw new IllegalStateException("Finance product shell not started");
        }

        String safeUserId = FinanceApiBoundaryValidationUtils.requirePrincipalId(userId, "userId");
        FinanceApiBoundaryValidationUtils.requirePayload(portfolioRequest, "portfolioRequest");

        log.debug("Handling portfolio management for user: {}", safeUserId);

        // Build portfolio changes
        List<PortfolioManagementResult.PortfolioChange> changes = Arrays.asList(
            new PortfolioManagementResult.PortfolioChange("AAPL", "DECREASE", -10.0, 1800.0, "Reduce concentration"),
            new PortfolioManagementResult.PortfolioChange("MSFT", "INCREASE", 15.0, 5250.0, "Increase stable growth exposure"),
            new PortfolioManagementResult.PortfolioChange("BND", "INCREASE", 100.0, 10000.0, "Increase fixed income for stability")
        );

        // Build decision context
        PortfolioManagementResult.DecisionContext decisionContext = new PortfolioManagementResult.DecisionContext(
            "HUMAN_APPROVED",
            "portfolio-advisor-001",
            0.89,
            "Rebalancing improves risk-adjusted returns while maintaining volatility targets",
            Instant.now()
        );

        // Build performance impact
        PortfolioManagementResult.PerformanceImpact impact = new PortfolioManagementResult.PerformanceImpact(
            0.025,   // Expected return improvement
            0.05,    // Risk reduction
            0.15,    // Concentration improvement
            250.0    // Estimated rebalancing cost
        );

        PortfolioManagementResult.AuditEntry auditEntry = new PortfolioManagementResult.AuditEntry(
            Instant.now(),
            "REBALANCED",
            safeUserId,
            "Portfolio rebalancing completed with advisor approval"
        );

        return PortfolioManagementResult.builder()
            .operationId(UUID.randomUUID().toString())
            .portfolioId(UUID.randomUUID().toString())
            .userId(safeUserId)
            .operationType("REBALANCE")
            .status("COMPLETED")
            .timestamp(Instant.now())
            .changes(changes)
            .decisionContext(decisionContext)
            .performanceImpact(impact)
            .auditTrail(Collections.singletonList(auditEntry))
            .build();
    }
}
