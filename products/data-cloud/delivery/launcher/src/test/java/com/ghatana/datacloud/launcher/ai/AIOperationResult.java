/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.ai;

import java.time.Instant;

/**
 * Result of an AI operation with governance information.
 *
 * @doc.type class
 * @doc.purpose Result of an AI operation with governance information
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class AIOperationResult {

    private final String model;
    private final Status status;
    private final boolean modelAvailable;
    private final boolean fallbackUsed;
    private final String modelUsed;
    private final String redactedPrompt;
    private final String provenanceId;
    private final Provenance provenance;
    private final boolean costEnforced;
    private final double qualityScore;
    private final boolean approvalRequired;
    private final String approvalId;
    private final boolean audited;
    private final String auditId;
    private final boolean safetyCheckPassed;
    private final String response;

    public enum Status {
        SUCCESS,
        SUCCESS_WITH_FALLBACK,
        ERROR,
        BLOCKED
    }

    private AIOperationResult(Builder builder) {
        this.model = builder.model;
        this.status = builder.status;
        this.modelAvailable = builder.modelAvailable;
        this.fallbackUsed = builder.fallbackUsed;
        this.modelUsed = builder.modelUsed;
        this.redactedPrompt = builder.redactedPrompt;
        this.provenanceId = builder.provenanceId;
        this.provenance = builder.provenance;
        this.costEnforced = builder.costEnforced;
        this.qualityScore = builder.qualityScore;
        this.approvalRequired = builder.approvalRequired;
        this.approvalId = builder.approvalId;
        this.audited = builder.audited;
        this.auditId = builder.auditId;
        this.safetyCheckPassed = builder.safetyCheckPassed;
        this.response = builder.response;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AIOperationResult success(String model) {
        return builder()
            .model(model)
            .status(Status.SUCCESS)
            .build();
    }

    public String getModel() {
        return model;
    }

    public Status getStatus() {
        return status;
    }

    public boolean getModelAvailable() {
        return modelAvailable;
    }

    public boolean getFallbackUsed() {
        return fallbackUsed;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public String getRedactedPrompt() {
        return redactedPrompt;
    }

    public String getProvenanceId() {
        return provenanceId;
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public boolean getCostEnforced() {
        return costEnforced;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public boolean getApprovalRequired() {
        return approvalRequired;
    }

    public String getApprovalId() {
        return approvalId;
    }

    public boolean getAudited() {
        return audited;
    }

    public String getAuditId() {
        return auditId;
    }

    public boolean getSafetyCheckPassed() {
        return safetyCheckPassed;
    }

    public String getResponse() {
        return response;
    }

    public AIOperationResult withModelAvailable(boolean modelAvailable) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withFallbackUsed(boolean fallbackUsed) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withModelUsed(String modelUsed) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withRedactedPrompt(String redactedPrompt) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withProvenanceId(String provenanceId) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withProvenance(Provenance provenance) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withCostEnforced(boolean costEnforced) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withQualityScore(double qualityScore) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withApprovalRequired(boolean approvalRequired) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withApprovalId(String approvalId) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withAudited(boolean audited) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withAuditId(String auditId) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withSafetyCheckPassed(boolean safetyCheckPassed) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(safetyCheckPassed)
            .response(this.response)
            .build();
    }

    public AIOperationResult withResponse(String response) {
        return new Builder()
            .model(this.model)
            .status(this.status)
            .modelAvailable(this.modelAvailable)
            .fallbackUsed(this.fallbackUsed)
            .modelUsed(this.modelUsed)
            .redactedPrompt(this.redactedPrompt)
            .provenanceId(this.provenanceId)
            .provenance(this.provenance)
            .costEnforced(this.costEnforced)
            .qualityScore(this.qualityScore)
            .approvalRequired(this.approvalRequired)
            .approvalId(this.approvalId)
            .audited(this.audited)
            .auditId(this.auditId)
            .safetyCheckPassed(this.safetyCheckPassed)
            .response(response)
            .build();
    }

    public static class Builder {
        private String model;
        private Status status;
        private boolean modelAvailable;
        private boolean fallbackUsed;
        private String modelUsed;
        private String redactedPrompt;
        private String provenanceId;
        private Provenance provenance;
        private boolean costEnforced;
        private double qualityScore;
        private boolean approvalRequired;
        private String approvalId;
        private boolean audited;
        private String auditId;
        private boolean safetyCheckPassed;
        private String response;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder modelAvailable(boolean modelAvailable) {
            this.modelAvailable = modelAvailable;
            return this;
        }

        public Builder fallbackUsed(boolean fallbackUsed) {
            this.fallbackUsed = fallbackUsed;
            return this;
        }

        public Builder modelUsed(String modelUsed) {
            this.modelUsed = modelUsed;
            return this;
        }

        public Builder redactedPrompt(String redactedPrompt) {
            this.redactedPrompt = redactedPrompt;
            return this;
        }

        public Builder provenanceId(String provenanceId) {
            this.provenanceId = provenanceId;
            return this;
        }

        public Builder provenance(Provenance provenance) {
            this.provenance = provenance;
            return this;
        }

        public Builder costEnforced(boolean costEnforced) {
            this.costEnforced = costEnforced;
            return this;
        }

        public Builder qualityScore(double qualityScore) {
            this.qualityScore = qualityScore;
            return this;
        }

        public Builder approvalRequired(boolean approvalRequired) {
            this.approvalRequired = approvalRequired;
            return this;
        }

        public Builder approvalId(String approvalId) {
            this.approvalId = approvalId;
            return this;
        }

        public Builder audited(boolean audited) {
            this.audited = audited;
            return this;
        }

        public Builder auditId(String auditId) {
            this.auditId = auditId;
            return this;
        }

        public Builder safetyCheckPassed(boolean safetyCheckPassed) {
            this.safetyCheckPassed = safetyCheckPassed;
            return this;
        }

        public Builder response(String response) {
            this.response = response;
            return this;
        }

        public AIOperationResult build() {
            return new AIOperationResult(this);
        }
    }
}
