/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.platform.core.exception.GovernancePolicyException;

/**
 * Thrown when an AI model is used without governance approval.
 *
 * @doc.type class
 * @doc.purpose Exception for unapproved AI model usage
 * @doc.layer product
 * @doc.pattern Exception
 */
public class ModelNotApprovedException extends GovernancePolicyException {

    public ModelNotApprovedException(String message) {
        super(message);
    }

    public ModelNotApprovedException(String message, Throwable cause) {
        super(message, cause);
    }
}
