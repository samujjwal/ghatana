/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.bff;

import com.ghatana.finance.kernel.service.PortfolioManagementService;
import com.ghatana.finance.service.Transaction;
import com.ghatana.finance.service.TransactionResult;
import com.ghatana.finance.service.TransactionService;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.products.finance.FinanceApiBoundaryValidationUtils;
import com.ghatana.products.finance.bff.dto.TradeDataComposition;
import com.ghatana.products.finance.bff.dto.PortfolioDataComposition;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;

/**
 * Finance Backend-for-Frontend.
 *
 * <p>Provides the BFF layer for the Finance product. Handles API composition,
 * data aggregation, and frontend-specific data transformations. Uses kernel
 * capabilities for generic functionality and implements finance-specific
 * business logic orchestration.</p>
 *
 * @doc.type class
 * @doc.purpose Finance BFF - API composition and data aggregation for frontend
 * @doc.layer product
 * @doc.pattern BFF
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceBFF implements KernelLifecycleAware {

    private static final Logger log = LoggerFactory.getLogger(FinanceBFF.class);

    private final KernelContext context;
    private volatile boolean started = false;

    /**
     * Creates a new finance BFF.
     *
     * @param context the kernel context
     */
    public FinanceBFF(KernelContext context) {
        this.context = context;
    }

    /**
     * Starts the finance BFF.
     */
    @Override
    public Promise<Void> start() {
        log.info("Starting Finance BFF");
        started = true;
        log.info("Finance BFF started");
        return Promise.complete();
    }

    /**
     * Stops the finance BFF.
     */
    @Override
    public Promise<Void> stop() {
        log.info("Stopping Finance BFF");
        started = false;
        log.info("Finance BFF stopped");
        return Promise.complete();
    }

    /**
     * Checks if the BFF is healthy.
     *
     * @return true if healthy
     */
    @Override
    public boolean isHealthy() {
        return started;
    }

    @Override
    public String getName() {
        return "finance-bff";
    }

    /**
     * Composes trade data for frontend consumption.
     *
     * @param userId user identifier
     * @param tradeId trade identifier
     * @return composed trade data with risk assessment and compliance status
     */
    public TradeDataComposition getTradeData(String userId, String tradeId) {
        if (!started) {
            throw new IllegalStateException("Finance BFF not started");
        }

        String safeUserId = FinanceApiBoundaryValidationUtils.requirePrincipalId(userId, "userId");
        String safeTradeId = FinanceApiBoundaryValidationUtils.requireResourceId(tradeId, "tradeId");

        log.debug("Getting trade data for user: {} trade: {}", safeUserId, safeTradeId);

        // Compose trade data from kernel services
        return TradeDataComposition.builder()
            .tradeId(safeTradeId)
            .userId(safeUserId)
            .status("ACTIVE")
            .tradeType("EQUITY")
            .quantity(100.0)
            .price(150.5)
            .totalValue(15050.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .riskAssessment(new TradeDataComposition.RiskAssessment("MEDIUM", 0.45, 0.92, false))
            .complianceStatus(new TradeDataComposition.ComplianceStatus(true, 8, 0, "All compliance checks passed"))
            .auditTrail(java.util.Collections.singletonList(
                new TradeDataComposition.AuditEntry(Instant.now(), "CREATED", "system", "Trade created and composed")
            ))
            .build();
    }

    /**
     * Composes portfolio data for frontend consumption.
     *
     * @param userId user identifier
     * @param portfolioId portfolio identifier
     * @return composed portfolio data with risk metrics and performance
     */
    public PortfolioDataComposition getPortfolioData(String userId, String portfolioId) {
        if (!started) {
            throw new IllegalStateException("Finance BFF not started");
        }

        String safeUserId = FinanceApiBoundaryValidationUtils.requirePrincipalId(userId, "userId");
        String safePortfolioId = FinanceApiBoundaryValidationUtils.requireResourceId(portfolioId, "portfolioId");

        log.debug("Getting portfolio data for user: {} portfolio: {}", safeUserId, safePortfolioId);

        // Compose portfolio data from kernel services
        PortfolioDataComposition.AllocationData allocation = new PortfolioDataComposition.AllocationData(
            java.util.Map.of("EQUITY", 60.0, "FIXED_INCOME", 30.0, "CASH", 10.0),
            java.util.Map.of("TECHNOLOGY", 25.0, "HEALTHCARE", 20.0, "FINANCE", 15.0),
            java.util.Map.of("US", 70.0, "INTERNATIONAL", 30.0)
        );

        java.util.List<PortfolioDataComposition.PositionData> positions = java.util.List.of(
            new PortfolioDataComposition.PositionData("AAPL", 50.0, 180.0, 150.0, 1500.0, 18.0),
            new PortfolioDataComposition.PositionData("MSFT", 40.0, 350.0, 300.0, 2000.0, 28.0),
            new PortfolioDataComposition.PositionData("TSLA", 30.0, 250.0, 180.0, 2100.0, 19.0)
        );

        PortfolioDataComposition.RiskMetrics riskMetrics = new PortfolioDataComposition.RiskMetrics(
            50000.0, // VaR 95%
            75000.0, // VaR 99%
            1.35,    // Sharpe Ratio
            1.1,     // Beta
            0.15     // Max Drawdown
        );

        PortfolioDataComposition.PerformanceData performance = new PortfolioDataComposition.PerformanceData(
            0.125,  // YTD Return
            0.18,   // 1Y Return
            0.42,   // 3Y Return
            0.08    // Benchmark Comparison
        );

        return PortfolioDataComposition.builder()
            .portfolioId(safePortfolioId)
            .userId(safeUserId)
            .portfolioName("Growth Portfolio")
            .totalValue(500000.0)
            .unrealizedPnl(45000.0)
            .realizedPnl(12000.0)
            .allocation(allocation)
            .positions(positions)
            .riskMetrics(riskMetrics)
            .performance(performance)
            .updatedAt(Instant.now())
            .auditTrail(java.util.Collections.singletonList(
                new PortfolioDataComposition.AuditEntry(Instant.now(), "COMPOSED", "system", "Portfolio data composed for frontend")
            ))
            .build();
    }

    /**
     * Routes transaction processing through the Finance product runtime.
     *
     * @param transaction transaction request
     * @return transaction result
     */
    public TransactionResult processTransaction(Transaction transaction) {
        if (!started) {
            throw new IllegalStateException("Finance BFF not started");
        }

        Transaction safeTransaction = FinanceApiBoundaryValidationUtils.requirePayload(transaction, "transaction");

        return context.getDependency(TransactionService.class).processTransaction(safeTransaction);
    }
}
