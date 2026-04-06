/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.platform.billing;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Coordinates cross-domain billing transaction execution with compensation.
 *
 * <p>This contract provides saga-style coordination for workflows that need to
 * post multiple ledger transactions while preserving consistency across product
 * boundaries. If a later posting fails, already-posted transactions must be
 * reversed in reverse order.
 *
 * @doc.type interface
 * @doc.purpose Cross-domain billing saga coordinator for multi-step posting workflows
 * @doc.layer platform
 * @doc.pattern Port
 */
public interface BillingTransactionCoordinator {

    /**
     * Executes a coordinated billing workflow.
     *
     * @param workflowId stable workflow identifier for audit/idempotency
     * @param transactions ordered transaction list to post
     * @return workflow result including posted and compensated transaction IDs
     */
    Promise<CoordinationResult> coordinate(String workflowId, List<BillingTransaction> transactions);

    /**
     * Result of a coordinated billing workflow.
     */
    record CoordinationResult(
        String workflowId,
        boolean succeeded,
        List<String> postedTransactionIds,
        List<String> compensatedTransactionIds,
        String failureReason
    ) {}
}
