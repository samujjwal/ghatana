package com.ghatana.pipeline.runtime.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runtime materialization of an executable pipeline.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents the fully constructed, executable form of a PipelineSpec. Contains
 * all runtime stages wired in execution order, ready for deployment and
 * execution.
 *
 * <p>
 * <b>Responsibilities</b><br>
 * - Hold pipeline metadata (id, name, tenant) - Reference ordered list of
 * runtime stages - Track pipeline state (created, deployed, running, stopped) -
 * Provide access to stages for execution
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * EventPipeline pipeline = EventPipeline.builder()
 *     .id("pipeline-1")
 *     .name("Transaction Pipeline")
 *     .tenantId("acme-corp")
 *     .stages(stages)
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Lifecycle</b><br>
 * - Created: Constructed from PipelineSpec via adapter - Deployed: Registered
 * with deployment handler - Running: Processing events through stages -
 * Stopped: No longer processing events
 *
 * @see RuntimeStage
 * @doc.type class
 * @doc.purpose Runtime executable pipeline representation
 * @doc.layer core
 * @doc.pattern Value Object
 */
public class EventPipeline {

    private final String id;
    private final String name;
    private final String tenantId;
    private final List<RuntimeStage> stages;
    private PipelineState state;

    /**
     * Pipeline execution state enum.
     */
    public enum PipelineState {
        CREATED,
        DEPLOYED,
        RUNNING,
        STOPPED,
        ERROR
    }

    public EventPipeline(
            String id,
            String name,
            String tenantId,
            List<RuntimeStage> stages) {
        this.id = id;
        this.name = name;
        this.tenantId = tenantId;
        this.stages = stages != null ? new ArrayList<>(stages) : new ArrayList<>();
        this.state = PipelineState.CREATED;
    }

    /**
     * Gets pipeline ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets pipeline name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets tenant ID.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets list of runtime stages in execution order.
     */
    public List<RuntimeStage> getStages() {
        return Collections.unmodifiableList(stages);
    }

    /**
     * Gets first stage in pipeline (source/ingress).
     */
    public RuntimeStage getFirstStage() {
        return stages.isEmpty() ? null : stages.get(0);
    }

    /**
     * Gets last stage in pipeline (sink/egress).
     */
    public RuntimeStage getLastStage() {
        return stages.isEmpty() ? null : stages.get(stages.size() - 1);
    }

    /**
     * Gets current pipeline state.
     */
    public PipelineState getState() {
        return state;
    }

    /**
     * Sets pipeline state.
     */
    public void setState(PipelineState newState) {
        this.state = newState;
    }

    /**
     * Gets stage count.
     */
    public int getStageCount() {
        return stages.size();
    }

    /**
     * Builder for EventPipeline.
     */
    public static EventPipelineBuilder builder() {
        return new EventPipelineBuilder();
    }

    /**
     * Builder implementation.
     */
    public static class EventPipelineBuilder {

        private String id;
        private String name;
        private String tenantId;
        private List<RuntimeStage> stages;

        public EventPipelineBuilder id(String id) {
            this.id = id;
            return this;
        }

        public EventPipelineBuilder name(String name) {
            this.name = name;
            return this;
        }

        public EventPipelineBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public EventPipelineBuilder stages(List<RuntimeStage> stages) {
            this.stages = stages;
            return this;
        }

        public EventPipeline build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Pipeline ID is required");
            }
            if (tenantId == null || tenantId.isEmpty()) {
                throw new IllegalArgumentException("Tenant ID is required");
            }
            if (stages == null || stages.isEmpty()) {
                throw new IllegalArgumentException("Pipeline must have at least one stage");
            }
            return new EventPipeline(id, name, tenantId, stages);
        }
    }

    @Override
    public String toString() {
        return "EventPipeline{"
                + "id='" + id + '\''
                + ", name='" + name + '\''
                + ", tenantId='" + tenantId + '\''
                + ", stageCount=" + stages.size()
                + ", state=" + state
                + '}';
    }
}
