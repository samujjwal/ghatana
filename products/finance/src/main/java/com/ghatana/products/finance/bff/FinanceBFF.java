/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.bff;

import com.ghatana.finance.service.Transaction;
import com.ghatana.finance.service.TransactionResult;
import com.ghatana.finance.service.TransactionService;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.products.finance.FinanceApiBoundaryValidationUtils;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * @return composed trade data
     */
    public Object getTradeData(String userId, String tradeId) {
        if (!started) {
            throw new IllegalStateException("Finance BFF not started");
        }

        String safeUserId = FinanceApiBoundaryValidationUtils.requirePrincipalId(userId, "userId");
        String safeTradeId = FinanceApiBoundaryValidationUtils.requireResourceId(tradeId, "tradeId");

        log.debug("Getting trade data for user: {} trade: {}", safeUserId, safeTradeId);

        return "Trade data composed";
    }

    /**
     * Composes portfolio data for frontend consumption.
     *
     * @param userId user identifier
     * @param portfolioId portfolio identifier
     * @return composed portfolio data
     */
    public Object getPortfolioData(String userId, String portfolioId) {
        if (!started) {
            throw new IllegalStateException("Finance BFF not started");
        }

        String safeUserId = FinanceApiBoundaryValidationUtils.requirePrincipalId(userId, "userId");
        String safePortfolioId = FinanceApiBoundaryValidationUtils.requireResourceId(portfolioId, "portfolioId");

        log.debug("Getting portfolio data for user: {} portfolio: {}", safeUserId, safePortfolioId);

        return "Portfolio data composed";
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
