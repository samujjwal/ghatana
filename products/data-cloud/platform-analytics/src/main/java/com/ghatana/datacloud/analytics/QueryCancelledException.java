/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

/**
 * Exception thrown when a query is cancelled by the user.
 *
 * @doc.type exception
 * @doc.purpose Indicates query was cancelled
 * @doc.layer core
 */
public class QueryCancelledException extends RuntimeException {

    public QueryCancelledException(String message) {
        super(message);
    }

    public QueryCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
