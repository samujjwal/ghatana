/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

/**
 * Exception type for error handling
 *
 * @doc.type class
 * @doc.purpose Exception type for error handling
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class ModelNotApprovedException extends RuntimeException {
    
    public ModelNotApprovedException(String message) {
        super(message);
    }
    
    public ModelNotApprovedException(String message, Throwable cause) {
        super(message, cause);
    }
}
