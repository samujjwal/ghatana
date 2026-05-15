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

    /**
     * Failure reason codes for Data Cloud provider operations.
     */
    public enum ReasonCode {
        /** Provider not available or not configured */
        PROVIDER_UNAVAILABLE,
        /** Authentication failed */
        AUTHENTICATION_FAILED,
        /** Authorization failed - insufficient permissions */
        AUTHORIZATION_FAILED,
        /** Network connectivity issue */
        NETWORK_ERROR,
        /** Request timeout */
        TIMEOUT,
        /** Rate limit exceeded */
        RATE_LIMIT_EXCEEDED,
        /** Invalid request payload */
        INVALID_REQUEST,
        /** Data Cloud service unavailable */
        SERVICE_UNAVAILABLE,
        /** Internal Data Cloud error */
        INTERNAL_ERROR,
        /** Tenant context missing or invalid */
        TENANT_CONTEXT_INVALID,
        /** Workspace context missing or invalid */
        WORKSPACE_CONTEXT_INVALID,
        /** Project context missing or invalid */
        PROJECT_CONTEXT_INVALID,
        /** Record not found */
        RECORD_NOT_FOUND,
        /** Record already exists */
        RECORD_ALREADY_EXISTS,
        /** Serialization/deserialization error */
        SERIALIZATION_ERROR,
        /** Unknown or unexpected error */
        UNKNOWN_ERROR
    }

    private final String providerName;
    private final String operation;
    private final ReasonCode reasonCode;

    public DataCloudProviderException(String providerName, String operation, String message) {
        super(message);
        this.providerName = providerName;
        this.operation = operation;
        this.reasonCode = ReasonCode.UNKNOWN_ERROR;
    }

    public DataCloudProviderException(String providerName, String operation, String message, ReasonCode reasonCode) {
        super(message);
        this.providerName = providerName;
        this.operation = operation;
        this.reasonCode = reasonCode != null ? reasonCode : ReasonCode.UNKNOWN_ERROR;
    }

    public DataCloudProviderException(String providerName, String operation, Throwable cause) {
        super("Data Cloud provider " + providerName + " failed during " + operation, cause);
        this.providerName = providerName;
        this.operation = operation;
        this.reasonCode = ReasonCode.UNKNOWN_ERROR;
    }

    public DataCloudProviderException(String providerName, String operation, Throwable cause, ReasonCode reasonCode) {
        super("Data Cloud provider " + providerName + " failed during " + operation, cause);
        this.providerName = providerName;
        this.operation = operation;
        this.reasonCode = reasonCode != null ? reasonCode : ReasonCode.UNKNOWN_ERROR;
    }

    public String providerName() {
        return providerName;
    }

    public String operation() {
        return operation;
    }

    public ReasonCode reasonCode() {
        return reasonCode;
    }
}
