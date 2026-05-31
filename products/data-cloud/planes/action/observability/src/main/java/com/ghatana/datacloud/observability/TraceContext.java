/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.observability;

import java.util.Optional;
import java.util.UUID;

/**
 * Canonical trace context for Data Cloud + AEP + agents + media (P9).
 *
 * <p>Provides unified trace identifiers across all planes and components
 * to enable end-to-end observability and correlation.
 *
 * @doc.type record
 * @doc.purpose Canonical trace context for cross-plane observability
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TraceContext(
        String requestId,
        String tenantId,
        String principalId,
        String operationId,
        Long eventOffset,
        String pipelineExecutionId,
        String patternInstanceId,
        String agentInvocationId,
        String mediaArtifactId,
        String mediaJobId,
        String correlationId,
        String traceId
) {
    public TraceContext {
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = traceId;
        }
    }

    /**
     * Creates a new trace context with minimal required fields.
     */
    public static TraceContext create(String tenantId, String principalId) {
        String traceId = UUID.randomUUID().toString();
        return new TraceContext(
            UUID.randomUUID().toString(), // requestId
            tenantId,
            principalId,
            UUID.randomUUID().toString(), // operationId
            null, // eventOffset
            null, // pipelineExecutionId
            null, // patternInstanceId
            null, // agentInvocationId
            null, // mediaArtifactId
            null, // mediaJobId
            traceId, // correlationId
            traceId  // traceId
        );
    }

    /**
     * Creates a trace context from an existing trace ID.
     */
    public static TraceContext fromTraceId(String traceId, String tenantId, String principalId) {
        return new TraceContext(
            UUID.randomUUID().toString(), // requestId
            tenantId,
            principalId,
            UUID.randomUUID().toString(), // operationId
            null, // eventOffset
            null, // pipelineExecutionId
            null, // patternInstanceId
            null, // agentInvocationId
            null, // mediaArtifactId
            null, // mediaJobId
            traceId, // correlationId
            traceId  // traceId
        );
    }

    /**
     * Returns a builder for creating trace contexts.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns true if this trace context has a pipeline execution ID.
     */
    public boolean hasPipelineExecution() {
        return pipelineExecutionId != null && !pipelineExecutionId.isBlank();
    }

    /**
     * Returns true if this trace context has a pattern instance ID.
     */
    public boolean hasPatternInstance() {
        return patternInstanceId != null && !patternInstanceId.isBlank();
    }

    /**
     * Returns true if this trace context has an agent invocation ID.
     */
    public boolean hasAgentInvocation() {
        return agentInvocationId != null && !agentInvocationId.isBlank();
    }

    /**
     * Returns true if this trace context has a media artifact ID.
     */
    public boolean hasMediaArtifact() {
        return mediaArtifactId != null && !mediaArtifactId.isBlank();
    }

    /**
     * Returns true if this trace context has a media job ID.
     */
    public boolean hasMediaJob() {
        return mediaJobId != null && !mediaJobId.isBlank();
    }

    /**
     * Builder for TraceContext.
     */
    public static class Builder {
        private String requestId;
        private String tenantId;
        private String principalId;
        private String operationId;
        private Long eventOffset;
        private String pipelineExecutionId;
        private String patternInstanceId;
        private String agentInvocationId;
        private String mediaArtifactId;
        private String mediaJobId;
        private String correlationId;
        private String traceId;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder operationId(String operationId) {
            this.operationId = operationId;
            return this;
        }

        public Builder eventOffset(Long eventOffset) {
            this.eventOffset = eventOffset;
            return this;
        }

        public Builder pipelineExecutionId(String pipelineExecutionId) {
            this.pipelineExecutionId = pipelineExecutionId;
            return this;
        }

        public Builder patternInstanceId(String patternInstanceId) {
            this.patternInstanceId = patternInstanceId;
            return this;
        }

        public Builder agentInvocationId(String agentInvocationId) {
            this.agentInvocationId = agentInvocationId;
            return this;
        }

        public Builder mediaArtifactId(String mediaArtifactId) {
            this.mediaArtifactId = mediaArtifactId;
            return this;
        }

        public Builder mediaJobId(String mediaJobId) {
            this.mediaJobId = mediaJobId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public TraceContext build() {
            return new TraceContext(
                requestId,
                tenantId,
                principalId,
                operationId,
                eventOffset,
                pipelineExecutionId,
                patternInstanceId,
                agentInvocationId,
                mediaArtifactId,
                mediaJobId,
                correlationId,
                traceId
            );
        }
    }
}
