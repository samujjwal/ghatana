/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.resilience;

/**
 * Result of a dependency operation with resilience information.
 *
 * @doc.type class
 * @doc.purpose Result of a dependency operation with resilience information
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class DependencyOperationResult {

    private final String operationName;
    private final Status status;
    private final boolean fallbackUsed;
    private final boolean backpressureApplied;
    private final int retryCount;
    private final String errorMessage;
    private final Boolean decision;
    private final String response;

    public enum Status {
        SUCCESS,
        SUCCESS_WITH_FALLBACK,
        DEGRADED,
        ERROR,
        CIRCUIT_OPEN,
        BACKPRESSURE
    }

    private DependencyOperationResult(Builder builder) {
        this.operationName = builder.operationName;
        this.status = builder.status;
        this.fallbackUsed = builder.fallbackUsed;
        this.backpressureApplied = builder.backpressureApplied;
        this.retryCount = builder.retryCount;
        this.errorMessage = builder.errorMessage;
        this.decision = builder.decision;
        this.response = builder.response;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DependencyOperationResult success(String operationName) {
        return builder()
            .operationName(operationName)
            .status(Status.SUCCESS)
            .build();
    }

    public String getOperationName() {
        return operationName;
    }

    public Status getStatus() {
        return status;
    }

    public boolean getFallbackUsed() {
        return fallbackUsed;
    }

    public boolean getBackpressureApplied() {
        return backpressureApplied;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Boolean getDecision() {
        return decision;
    }

    public String getResponse() {
        return response;
    }

    public DependencyOperationResult withRetryCount(int retryCount) {
        return new Builder()
            .operationName(this.operationName)
            .status(this.status)
            .fallbackUsed(this.fallbackUsed)
            .backpressureApplied(this.backpressureApplied)
            .retryCount(retryCount)
            .errorMessage(this.errorMessage)
            .decision(this.decision)
            .response(this.response)
            .build();
    }

    public DependencyOperationResult withDecision(Boolean decision) {
        return new Builder()
            .operationName(this.operationName)
            .status(this.status)
            .fallbackUsed(this.fallbackUsed)
            .backpressureApplied(this.backpressureApplied)
            .retryCount(this.retryCount)
            .errorMessage(this.errorMessage)
            .decision(decision)
            .response(this.response)
            .build();
    }

    public DependencyOperationResult withResponse(String response) {
        return new Builder()
            .operationName(this.operationName)
            .status(this.status)
            .fallbackUsed(this.fallbackUsed)
            .backpressureApplied(this.backpressureApplied)
            .retryCount(this.retryCount)
            .errorMessage(this.errorMessage)
            .decision(this.decision)
            .response(response)
            .build();
    }

    public static class Builder {
        private String operationName;
        private Status status;
        private boolean fallbackUsed;
        private boolean backpressureApplied;
        private int retryCount;
        private String errorMessage;
        private Boolean decision;
        private String response;

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder fallbackUsed(boolean fallbackUsed) {
            this.fallbackUsed = fallbackUsed;
            return this;
        }

        public Builder backpressureApplied(boolean backpressureApplied) {
            this.backpressureApplied = backpressureApplied;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder decision(Boolean decision) {
            this.decision = decision;
            return this;
        }

        public Builder response(String response) {
            this.response = response;
            return this;
        }

        public DependencyOperationResult build() {
            return new DependencyOperationResult(this);
        }
    }
}
