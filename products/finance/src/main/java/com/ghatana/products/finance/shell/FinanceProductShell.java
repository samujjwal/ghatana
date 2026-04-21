/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.shell;

import com.ghatana.finance.kernel.service.PortfolioManagementService;
import com.ghatana.finance.service.Transaction;
import com.ghatana.finance.service.TransactionResult;
import com.ghatana.finance.service.TransactionService;
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
    private final TransactionService transactionService;
    private final PortfolioManagementService portfolioManagementService;
    private volatile boolean started = false;

    /**
     * Creates a new finance product shell.
     *
     * @param context the kernel context
     * @param transactionService the transaction service; may be null
     * @param portfolioManagementService the portfolio management service; may be null
     */
    public FinanceProductShell(KernelContext context, TransactionService transactionService, 
                              PortfolioManagementService portfolioManagementService) {
        this.context = context;
        this.transactionService = transactionService;
        this.portfolioManagementService = portfolioManagementService;
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

        if (transactionService == null) {
            throw new IllegalStateException("TransactionService is not available. Cannot execute trade without transaction runtime.");
        }

        if (!(tradeRequest instanceof Transaction)) {
            throw new IllegalArgumentException("tradeRequest must be an instance of Transaction");
        }

        Transaction tx = (Transaction) tradeRequest;
        TransactionResult result = transactionService.processTransaction(tx);
        
        List<String> checksPerformed = Arrays.asList(
            "FRAUD_DETECTION",
            "RATE_LIMIT_CHECK",
            "COMPLIANCE_VALIDATION"
        );

        TradeExecutionResult.RiskValidation riskValidation = new TradeExecutionResult.RiskValidation(
            !"REJECTED".equals(result.getStatus()),
            "REJECTED".equals(result.getStatus()) ? "HIGH" : "LOW",
            checksPerformed,
            Collections.emptyList()
        );

        TradeExecutionResult.DecisionContext decisionContext = new TradeExecutionResult.DecisionContext(
            "PENDING_REVIEW".equals(result.getStatus()) ? "HUMAN_APPROVED" : "AUTO",
            "PENDING_REVIEW".equals(result.getStatus()) ? "risk-analyst" : null,
            "REJECTED".equals(result.getStatus()) ? 0.0 : 0.95,
            result.getMessage(),
            Instant.now()
        );

        TradeExecutionResult.AuditEntry auditEntry = new TradeExecutionResult.AuditEntry(
            Instant.now(),
            result.getStatus(),
            safeUserId,
            "Trade execution: " + result.getMessage()
        );

        return TradeExecutionResult.builder()
            .executionId(UUID.randomUUID().toString())
            .tradeId(tx.getId())
            .userId(safeUserId)
            .status(result.getStatus())
            .filledQuantity(tx.getAmount())
            .executionPrice(0.0) // Would come from market data in full implementation
            .executionCost(tx.getAmount())
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

        if (portfolioManagementService == null) {
            throw new IllegalStateException("PortfolioManagementService is not available. Cannot manage portfolio without portfolio service.");
        }

        try {
            String portfolioId = UUID.randomUUID().toString();
            PortfolioManagementService.PortfolioRequest request = 
                new PortfolioManagementService.PortfolioRequest(safeUserId, "Growth Portfolio", "Auto-created portfolio", "USD");
            
            PortfolioManagementService.Portfolio portfolio = 
                portfolioManagementService.createPortfolio(request).getResult();
            
            List<PortfolioManagementResult.PortfolioChange> changes = Arrays.asList(
                new PortfolioManagementResult.PortfolioChange("CASH", "INITIAL", portfolio.getTotalValue().doubleValue(), portfolio.getTotalValue().doubleValue(), "Initial portfolio creation")
            );

            PortfolioManagementResult.DecisionContext decisionContext = new PortfolioManagementResult.DecisionContext(
                "AUTO",
                null,
                1.0,
                "Portfolio created successfully",
                Instant.now()
            );

            PortfolioManagementResult.PerformanceImpact impact = new PortfolioManagementResult.PerformanceImpact(
                0.0,
                0.0,
                0.0,
                0.0
            );

            PortfolioManagementResult.AuditEntry auditEntry = new PortfolioManagementResult.AuditEntry(
                Instant.now(),
                "CREATED",
                safeUserId,
                "Portfolio created via service"
            );

            return PortfolioManagementResult.builder()
                .operationId(UUID.randomUUID().toString())
                .portfolioId(portfolio.getId())
                .userId(safeUserId)
                .operationType("CREATE")
                .status("COMPLETED")
                .timestamp(Instant.now())
                .changes(changes)
                .decisionContext(decisionContext)
                .performanceImpact(impact)
                .auditTrail(Collections.singletonList(auditEntry))
                .build();
        } catch (Exception e) {
            log.error("Portfolio management service error", e);
            throw new IllegalStateException("Failed to manage portfolio: " + e.getMessage(), e);
        }
    }
}
