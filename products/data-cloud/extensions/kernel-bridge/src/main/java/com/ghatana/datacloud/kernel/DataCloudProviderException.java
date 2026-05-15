/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.kernel;

/**
 * Typed failure for Data Cloud-backed kernel provider operations.
 *
 * @doc.type class
 * @doc.purpose Surface provider failures without silent bootstrap fallback
 * @doc.layer adapter
 * @doc.pattern Exception
 */
public final class DataCloudProviderException extends RuntimeException {

    private final String providerName;
    private final String operation;

    public DataCloudProviderException(String providerName, String operation, String message) {
        super(message);
        this.providerName = providerName;
        this.operation = operation;
    }

    public DataCloudProviderException(String providerName, String operation, Throwable cause) {
        super("Data Cloud provider " + providerName + " failed during " + operation, cause);
        this.providerName = providerName;
        this.operation = operation;
    }

    public String providerName() {
        return providerName;
    }

    public String operation() {
        return operation;
    }
}
