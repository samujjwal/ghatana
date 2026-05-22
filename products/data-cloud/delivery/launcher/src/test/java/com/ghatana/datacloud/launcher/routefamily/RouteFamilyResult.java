/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.routefamily;

/**
 * Result of route-family atomic workflow execution.
 *
 * @doc.type class
 * @doc.purpose Result of route-family atomic workflow execution
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class RouteFamilyResult {

    private final String resourceId;
    private final Status status;
    private final boolean rollbackExecuted;
    private final int retryCount;
    private final boolean auditGenerated;
    private final String auditId;
    private final boolean outboxUsed;
    private final String outboxId;
    private final String version;
    private final String previousVersion;
    private final String sourceEnvironment;
    private final String targetEnvironment;
    private final String errorMessage;

    public enum Status {
        SUCCESS,
        ROLLED_BACK,
        BLOCKED,
        IDEMPOTENT,
        ERROR
    }

    private RouteFamilyResult(Builder builder) {
        this.resourceId = builder.resourceId;
        this.status = builder.status;
        this.rollbackExecuted = builder.rollbackExecuted;
        this.retryCount = builder.retryCount;
        this.auditGenerated = builder.auditGenerated;
        this.auditId = builder.auditId;
        this.outboxUsed = builder.outboxUsed;
        this.outboxId = builder.outboxId;
        this.version = builder.version;
        this.previousVersion = builder.previousVersion;
        this.sourceEnvironment = builder.sourceEnvironment;
        this.targetEnvironment = builder.targetEnvironment;
        this.errorMessage = builder.errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RouteFamilyResult success(String resourceId) {
        return builder()
            .resourceId(resourceId)
            .status(Status.SUCCESS)
            .build();
    }

    public static RouteFamilyResult rolledBack() {
        return builder()
            .status(Status.ROLLED_BACK)
            .rollbackExecuted(true)
            .build();
    }

    public static RouteFamilyResult blocked(String errorMessage) {
        return builder()
            .status(Status.BLOCKED)
            .errorMessage(errorMessage)
            .build();
    }

    public static RouteFamilyResult idempotent(String resourceId) {
        return builder()
            .resourceId(resourceId)
            .status(Status.IDEMPOTENT)
            .build();
    }

    public String getResourceId() {
        return resourceId;
    }

    public Status getStatus() {
        return status;
    }

    public boolean getRollbackExecuted() {
        return rollbackExecuted;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public boolean getAuditGenerated() {
        return auditGenerated;
    }

    public String getAuditId() {
        return auditId;
    }

    public boolean getOutboxUsed() {
        return outboxUsed;
    }

    public String getOutboxId() {
        return outboxId;
    }

    public String getVersion() {
        return version;
    }

    public String getPreviousVersion() {
        return previousVersion;
    }

    public String getSourceEnvironment() {
        return sourceEnvironment;
    }

    public String getTargetEnvironment() {
        return targetEnvironment;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public RouteFamilyResult withRollbackExecuted(boolean rollbackExecuted) {
        return new Builder()
            .resourceId(this.resourceId)
            .status(this.status)
            .rollbackExecuted(rollbackExecuted)
            .retryCount(this.retryCount)
            .auditGenerated(this.auditGenerated)
            .auditId(this.auditId)
            .outboxUsed(this.outboxUsed)
            .outboxId(this.outboxId)
            .version(this.version)
            .previousVersion(this.previousVersion)
            .sourceEnvironment(this.sourceEnvironment)
            .targetEnvironment(this.targetEnvironment)
            .errorMessage(this.errorMessage)
            .build();
    }

    public RouteFamilyResult withAuditGenerated(boolean auditGenerated) {
        return new Builder()
            .resourceId(this.resourceId)
            .status(this.status)
            .rollbackExecuted(this.rollbackExecuted)
            .retryCount(this.retryCount)
            .auditGenerated(auditGenerated)
            .auditId(this.auditId)
            .outboxUsed(this.outboxUsed)
            .outboxId(this.outboxId)
            .version(this.version)
            .previousVersion(this.previousVersion)
            .sourceEnvironment(this.sourceEnvironment)
            .targetEnvironment(this.targetEnvironment)
            .errorMessage(this.errorMessage)
            .build();
    }

    public RouteFamilyResult withAuditId(String auditId) {
        return new Builder()
            .resourceId(this.resourceId)
            .status(this.status)
            .rollbackExecuted(this.rollbackExecuted)
            .retryCount(this.retryCount)
            .auditGenerated(this.auditGenerated)
            .auditId(auditId)
            .outboxUsed(this.outboxUsed)
            .outboxId(this.outboxId)
            .version(this.version)
            .previousVersion(this.previousVersion)
            .sourceEnvironment(this.sourceEnvironment)
            .targetEnvironment(this.targetEnvironment)
            .errorMessage(this.errorMessage)
            .build();
    }

    public RouteFamilyResult withOutboxUsed(boolean outboxUsed) {
        return new Builder()
            .resourceId(this.resourceId)
            .status(this.status)
            .rollbackExecuted(this.rollbackExecuted)
            .retryCount(this.retryCount)
            .auditGenerated(this.auditGenerated)
            .auditId(this.auditId)
            .outboxUsed(outboxUsed)
            .outboxId(this.outboxId)
            .version(this.version)
            .previousVersion(this.previousVersion)
            .sourceEnvironment(this.sourceEnvironment)
            .targetEnvironment(this.targetEnvironment)
            .errorMessage(this.errorMessage)
            .build();
    }

    public RouteFamilyResult withOutboxId(String outboxId) {
        return new Builder()
            .resourceId(this.resourceId)
            .status(this.status)
            .rollbackExecuted(this.rollbackExecuted)
            .retryCount(this.retryCount)
            .auditGenerated(this.auditGenerated)
            .auditId(this.auditId)
            .outboxUsed(this.outboxUsed)
            .outboxId(outboxId)
            .version(this.version)
            .previousVersion(this.previousVersion)
            .sourceEnvironment(this.sourceEnvironment)
            .targetEnvironment(this.targetEnvironment)
            .errorMessage(this.errorMessage)
            .build();
    }

    public RouteFamilyResult withVersion(String version) {
        return new Builder()
            .resourceId(this.resourceId)
            .status(this.status)
            .rollbackExecuted(this.rollbackExecuted)
            .retryCount(this.retryCount)
            .auditGenerated(this.auditGenerated)
            .auditId(this.auditId)
            .outboxUsed(this.outboxUsed)
            .outboxId(this.outboxId)
            .version(version)
            .previousVersion(this.previousVersion)
            .sourceEnvironment(this.sourceEnvironment)
            .targetEnvironment(this.targetEnvironment)
            .errorMessage(this.errorMessage)
            .build();
    }

    public RouteFamilyResult withPreviousVersion(String previousVersion) {
        return new Builder()
            .resourceId(this.resourceId)
            .status(this.status)
            .rollbackExecuted(this.rollbackExecuted)
            .retryCount(this.retryCount)
            .auditGenerated(this.auditGenerated)
            .auditId(this.auditId)
            .outboxUsed(this.outboxUsed)
            .outboxId(this.outboxId)
            .version(this.version)
            .previousVersion(previousVersion)
            .sourceEnvironment(this.sourceEnvironment)
            .targetEnvironment(this.targetEnvironment)
            .errorMessage(this.errorMessage)
            .build();
    }

    public RouteFamilyResult withSourceEnvironment(String sourceEnvironment) {
        return new Builder()
            .resourceId(this.resourceId)
            .status(this.status)
            .rollbackExecuted(this.rollbackExecuted)
            .retryCount(this.retryCount)
            .auditGenerated(this.auditGenerated)
            .auditId(this.auditId)
            .outboxUsed(this.outboxUsed)
            .outboxId(this.outboxId)
            .version(this.version)
            .previousVersion(this.previousVersion)
            .sourceEnvironment(sourceEnvironment)
            .targetEnvironment(this.targetEnvironment)
            .errorMessage(this.errorMessage)
            .build();
    }

    public RouteFamilyResult withTargetEnvironment(String targetEnvironment) {
        return new Builder()
            .resourceId(this.resourceId)
            .status(this.status)
            .rollbackExecuted(this.rollbackExecuted)
            .retryCount(this.retryCount)
            .auditGenerated(this.auditGenerated)
            .auditId(this.auditId)
            .outboxUsed(this.outboxUsed)
            .outboxId(this.outboxId)
            .version(this.version)
            .previousVersion(this.previousVersion)
            .sourceEnvironment(this.sourceEnvironment)
            .targetEnvironment(targetEnvironment)
            .errorMessage(this.errorMessage)
            .build();
    }

    public static class Builder {
        private String resourceId;
        private Status status;
        private boolean rollbackExecuted;
        private int retryCount;
        private boolean auditGenerated;
        private String auditId;
        private boolean outboxUsed;
        private String outboxId;
        private String version;
        private String previousVersion;
        private String sourceEnvironment;
        private String targetEnvironment;
        private String errorMessage;

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder rollbackExecuted(boolean rollbackExecuted) {
            this.rollbackExecuted = rollbackExecuted;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder auditGenerated(boolean auditGenerated) {
            this.auditGenerated = auditGenerated;
            return this;
        }

        public Builder auditId(String auditId) {
            this.auditId = auditId;
            return this;
        }

        public Builder outboxUsed(boolean outboxUsed) {
            this.outboxUsed = outboxUsed;
            return this;
        }

        public Builder outboxId(String outboxId) {
            this.outboxId = outboxId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder previousVersion(String previousVersion) {
            this.previousVersion = previousVersion;
            return this;
        }

        public Builder sourceEnvironment(String sourceEnvironment) {
            this.sourceEnvironment = sourceEnvironment;
            return this;
        }

        public Builder targetEnvironment(String targetEnvironment) {
            this.targetEnvironment = targetEnvironment;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public RouteFamilyResult build() {
            return new RouteFamilyResult(this);
        }
    }
}
