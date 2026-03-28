/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.ledger.service;

import com.ghatana.platform.core.exception.ServiceException;

/**
 * Thrown when a journal's debit totals do not equal its credit totals per currency.
 *
 * <p>Balance enforcement is performed synchronously before any persistence call
 * to fail fast without incurring any I/O cost.
 *
 * @doc.type class
 * @doc.purpose Exception for unbalanced double-entry journals
 * @doc.layer finance
 * @doc.pattern Exception
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class UnbalancedJournalException extends ServiceException {

    public UnbalancedJournalException(String message) {
        super(message);
    }
}
