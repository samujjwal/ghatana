/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config.interpolation;

/**
 * Exception thrown when variable resolution fails.
 *
 * @doc.type class
 * @doc.purpose Exception for variable resolution failures
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class VariableResolutionException extends RuntimeException {

    /**
     * Creates a new variable resolution exception.
     *
     * @param message the error message
     */
    public VariableResolutionException(String message) {
        super(message);
    }

    /**
     * Creates a new variable resolution exception with cause.
     *
     * @param message the error message
     * @param cause   the cause
     */
    public VariableResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
