package com.ghatana.digitalmarketing.domain.attribution;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Map;

/**
 * Domain entity representing an attribution model (DMOS-P3-005).
 *
 * @doc.type class
 * @doc.purpose Represents a multi-touch attribution model
 * @doc.layer domain
 */
public final class AttributionModel {

    private final String modelId;
    private final DmTenantId tenantId;
    private final DmWorkspaceId workspaceId;
    private final String modelName;
    private final String modelType;
    private final Map<String, Double> touchpointWeights;
    private final Double confidenceIntervalLower;
    private final Double confidenceIntervalUpper;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AttributionModel(Builder builder) {
        this.modelId = builder.modelId;
        this.tenantId = builder.tenantId;
        this.workspaceId = builder.workspaceId;
        this.modelName = builder.modelName;
        this.modelType = builder.modelType;
        this.touchpointWeights = builder.touchpointWeights;
        this.confidenceIntervalLower = builder.confidenceIntervalLower;
        this.confidenceIntervalUpper = builder.confidenceIntervalUpper;
        this.active = builder.active;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public String getModelId() {
        return modelId;
    }

    public DmTenantId getTenantId() {
        return tenantId;
    }

    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelType() {
        return modelType;
    }

    public Map<String, Double> getTouchpointWeights() {
        return touchpointWeights;
    }

    public Double getConfidenceIntervalLower() {
        return confidenceIntervalLower;
    }

    public Double getConfidenceIntervalUpper() {
        return confidenceIntervalUpper;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String modelId;
        private DmTenantId tenantId;
        private DmWorkspaceId workspaceId;
        private String modelName;
        private String modelType;
        private Map<String, Double> touchpointWeights;
        private Double confidenceIntervalLower;
        private Double confidenceIntervalUpper;
        private boolean active = true;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder tenantId(DmTenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelType(String modelType) {
            this.modelType = modelType;
            return this;
        }

        public Builder touchpointWeights(Map<String, Double> touchpointWeights) {
            this.touchpointWeights = touchpointWeights;
            return this;
        }

        public Builder confidenceIntervalLower(Double confidenceIntervalLower) {
            this.confidenceIntervalLower = confidenceIntervalLower;
            return this;
        }

        public Builder confidenceIntervalUpper(Double confidenceIntervalUpper) {
            this.confidenceIntervalUpper = confidenceIntervalUpper;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public AttributionModel build() {
            return new AttributionModel(this);
        }
    }
}
