package com.ghatana.core.domain.pipeline;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Pipeline stage specification.
 * 
 * <p>Defines a single stage within a pipeline including its configuration,
 * connectors, and execution parameters.
 * 
 * @doc.type class
 * @doc.purpose Pipeline stage specification
 * @doc.layer core
 */
public class PipelineStageSpec {
    private final String id;
    private final String name;
    private final String stageType;
    private final String description;
    private final List<String> connectorIds;
    private final StageConfiguration configuration;
    private final boolean enabled;
    
    /**
     * Creates a new pipeline stage specification.
     * 
     * @param id Stage identifier
     * @param name Stage name
     * @param stageType Stage type
     * @param description Stage description
     * @param connectorIds List of connector IDs
     * @param configuration Stage configuration
     * @param enabled Whether stage is enabled
     */
    public PipelineStageSpec(String id, String name, String stageType, String description,
                            List<String> connectorIds, StageConfiguration configuration, boolean enabled) {
        this.id = Objects.requireNonNull(id, "Stage ID cannot be null");
        this.name = Objects.requireNonNull(name, "Stage name cannot be null");
        this.stageType = Objects.requireNonNull(stageType, "Stage type cannot be null");
        this.description = description;
        this.connectorIds = connectorIds != null ? new ArrayList<>(connectorIds) : new ArrayList<>();
        this.configuration = configuration;
        this.enabled = enabled;
    }
    
    /**
     * Gets the stage identifier.
     * 
     * @return stage ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Gets the stage name.
     * 
     * @return stage name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the stage type.
     * 
     * @return stage type
     */
    public String getStageType() {
        return stageType;
    }
    
    /**
     * Gets the stage description.
     * 
     * @return stage description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets the connector IDs.
     * 
     * @return list of connector IDs
     */
    public List<String> getConnectorIds() {
        return new ArrayList<>(connectorIds);
    }
    
    /**
     * Gets the stage configuration.
     * 
     * @return stage configuration
     */
    public StageConfiguration getConfiguration() {
        return configuration;
    }
    
    /**
     * Checks if the stage is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Adds a connector ID to the stage.
     * 
     * @param connectorId connector ID to add
     */
    public void addConnectorId(String connectorId) {
        Objects.requireNonNull(connectorId, "Connector ID cannot be null");
        connectorIds.add(connectorId);
    }
    
    /**
     * Removes a connector ID from the stage.
     * 
     * @param connectorId connector ID to remove
     * @return true if connector ID was removed
     */
    public boolean removeConnectorId(String connectorId) {
        return connectorIds.remove(connectorId);
    }
    
    /**
     * Gets the number of connectors in the stage.
     * 
     * @return connector count
     */
    public int getConnectorCount() {
        return connectorIds.size();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineStageSpec that = (PipelineStageSpec) o;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("PipelineStageSpec{id='%s', name='%s', type='%s', connectors=%d, enabled=%s}",
            id, name, stageType, connectorIds.size(), enabled);
    }
    
    /**
     * Stage configuration.
     */
    public static class StageConfiguration {
        private final int parallelism;
        private final long timeoutMs;
        private final String executionStrategy;
        private final boolean faultTolerant;
        
        public StageConfiguration(int parallelism, long timeoutMs, String executionStrategy, boolean faultTolerant) {
            this.parallelism = parallelism;
            this.timeoutMs = timeoutMs;
            this.executionStrategy = executionStrategy;
            this.faultTolerant = faultTolerant;
        }
        
        public int getParallelism() { return parallelism; }
        public long getTimeoutMs() { return timeoutMs; }
        public String getExecutionStrategy() { return executionStrategy; }
        public boolean isFaultTolerant() { return faultTolerant; }
    }
}
