package com.ghatana.pipeline.runtime.adapter;

import com.ghatana.pipeline.registry.connector.ConnectorOperator;
import io.activej.promise.Promise;

/**
 * Runtime representation of a pipeline stage with operators and connectors.
 *
 * <p>
 * <b>Purpose</b><br>
 * Encapsulates a stage in the runtime execution pipeline. Holds operators,
 * connectors, and wiring to next stage. Represents the materialized form of
 * PipelineStageSpec.
 *
 * <p>
 * <b>Responsibilities</b><br>
 * - Hold stage metadata (name, type, tenant) - Reference instantiated operators
 * and connectors - Link to next stage in execution order - Manage stage
 * lifecycle (initialize, execute, close)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * RuntimeStage stage = RuntimeStage.builder()
 *     .name("filtering")
 *     .type("STREAM")
 *     .connectors(connectorOperators)
 *     .build();
 * }</pre>
 *
 * @see EventPipeline
 * @doc.type class
 * @doc.purpose Runtime representation of pipeline stage
 * @doc.layer core
 * @doc.pattern Value Object
 */
public class RuntimeStage {

    private final String name;
    private final String type;
    private final String tenantId;
    private final java.util.List<ConnectorOperator> connectors;
    private RuntimeStage nextStage;

    public RuntimeStage(
            String name,
            String type,
            String tenantId,
            java.util.List<ConnectorOperator> connectors) {
        this.name = name;
        this.type = type;
        this.tenantId = tenantId;
        this.connectors = connectors != null ? connectors : java.util.Collections.emptyList();
    }

    /**
     * Gets stage name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets stage type (STREAM, PATTERN, CONNECTOR).
     */
    public String getType() {
        return type;
    }

    /**
     * Gets tenant ID.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets list of connectors in this stage.
     */
    public java.util.List<ConnectorOperator> getConnectors() {
        return connectors;
    }

    /**
     * Gets next stage in execution order.
     */
    public RuntimeStage getNextStage() {
        return nextStage;
    }

    /**
     * Sets next stage in execution order.
     */
    public void setNextStage(RuntimeStage next) {
        this.nextStage = next;
    }

    /**
     * Builder for RuntimeStage.
     */
    public static RuntimeStageBuilder builder() {
        return new RuntimeStageBuilder();
    }

    /**
     * Builder implementation.
     */
    public static class RuntimeStageBuilder {

        private String name;
        private String type;
        private String tenantId;
        private java.util.List<ConnectorOperator> connectors;

        public RuntimeStageBuilder name(String name) {
            this.name = name;
            return this;
        }

        public RuntimeStageBuilder type(String type) {
            this.type = type;
            return this;
        }

        public RuntimeStageBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public RuntimeStageBuilder connectors(java.util.List<ConnectorOperator> connectors) {
            this.connectors = connectors;
            return this;
        }

        public RuntimeStage build() {
            return new RuntimeStage(name, type, tenantId, connectors);
        }
    }

    @Override
    public String toString() {
        return "RuntimeStage{"
                + "name='" + name + '\''
                + ", type='" + type + '\''
                + ", tenantId='" + tenantId + '\''
                + ", connectorCount=" + connectors.size()
                + '}';
    }
}
