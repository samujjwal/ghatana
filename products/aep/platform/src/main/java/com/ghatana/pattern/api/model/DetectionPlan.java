package com.ghatana.pattern.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a compiled detection plan that can be executed by a runtime engine.
 * 
 * <p>This is the final output of the pattern compilation process. A DetectionPlan
 * is an immutable, executable artifact containing:
 * <ul>
 *   <li><b>Operator DAG</b>: Graph structure defining detection logic</li>
 *   <li><b>Runtime Config</b>: Execution parameters (parallelism, checkpointing, etc.)</li>
 *   <li><b>Required Streams</b>: Event types and sources needed for execution</li>
 *   <li><b>Window Specs</b>: Time window configuration for pattern matching</li>
 *   <li><b>State Keys</b>: Keying strategy for partitioned state management</li>
 *   <li><b>Metadata</b>: Compilation version, timestamp, optimizer flags</li>
 * </ul>
 * 
 * @doc.pattern Immutable Value Object Pattern, Builder Pattern (construction)
 * @doc.compiler-phase DetectionPlan (final output after optimization)
 * @doc.threading Thread-safe (immutable after construction)
 * @doc.performance O(1) field access; O(n) for DAG traversal
 * @doc.memory O(n) for operator graph + O(m) for metadata where n=operators, m=config entries
 * @doc.immutability Immutable after build(); use builder for modifications
 * @doc.serialization JSON serializable via Jackson; suitable for storage/transmission
 * @doc.apiNote Deploy plans to runtime engine; cache compiled plans for reuse
 * @doc.limitation No incremental updates; recompile pattern for changes
 * 
 * <h2>DetectionPlan Lifecycle</h2>
 * <pre>
 * PatternSpec ──→ Compiler ──→ DetectionPlan ──→ Runtime Engine
 *    (Input)     (Transform)     (Executable)      (Execution)
 * </pre>
 * 
 * <p><b>Runtime Configuration</b>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Config Key</th>
 *     <th>Type</th>
 *     <th>Purpose</th>
 *   </tr>
 *   <tr>
 *     <td>parallelism</td>
 *     <td>Integer</td>
 *     <td>Number of parallel operator instances</td>
 *   </tr>
 *   <tr>
 *     <td>checkpointInterval</td>
 *     <td>Duration</td>
 *     <td>State checkpoint frequency</td>
 *   </tr>
 *   <tr>
 *     <td>maxOutOfOrderness</td>
 *     <td>Duration</td>
 *     <td>Late event tolerance window</td>
 *   </tr>
 *   <tr>
 *     <td>stateBackend</td>
 *     <td>String</td>
 *     <td>State storage implementation (redis, rocksdb, etc.)</td>
 *   </tr>
 * </table>
 */
public class DetectionPlan {
    
    @JsonProperty("patternId")
    private UUID patternId;
    
    @JsonProperty("operatorGraph")
    private OperatorDAG operatorGraph;

    @JsonProperty("eventTypes")
    private List<String> eventTypes;

    @JsonProperty("runtimeConfig")
    private Map<String, Object> runtimeConfig;
    
    @JsonProperty("requiredStreams")
    private List<String> requiredStreams;
    
    @JsonProperty("window")
    private PatternWindowSpec window;

    @JsonProperty("stateKeys")
    private Map<String, String> stateKeys;

    @JsonProperty("compiledAt")
    private Instant compiledAt;
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    // Default constructor for JSON deserialization
    public DetectionPlan() {}
    
    // Builder pattern constructor
    public DetectionPlan(Builder builder) {
        this.patternId = builder.patternId;
        this.operatorGraph = builder.operatorGraph;
        this.eventTypes = builder.eventTypes;
        this.runtimeConfig = builder.runtimeConfig;
        this.requiredStreams = builder.requiredStreams;
        this.window = builder.window;
        this.stateKeys = builder.stateKeys;
        this.compiledAt = builder.compiledAt;
        this.version = builder.version;
        this.metadata = builder.metadata;
    }
    
    // Getters
    public UUID getPatternId() { return patternId; }
    public OperatorDAG getOperatorGraph() { return operatorGraph; }
    public List<String> getEventTypes() { return eventTypes; }
    public Map<String, Object> getRuntimeConfig() { return runtimeConfig; }
    public List<String> getRequiredStreams() { return requiredStreams; }
    public PatternWindowSpec getWindow() { return window; }
    public Map<String, String> getStateKeys() { return stateKeys; }
    public Instant getCompiledAt() { return compiledAt; }
    public String getVersion() { return version; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    // Setters
    public void setPatternId(UUID patternId) { this.patternId = patternId; }
    public void setOperatorGraph(OperatorDAG operatorGraph) { this.operatorGraph = operatorGraph; }
    public void setEventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; }
    public void setRuntimeConfig(Map<String, Object> runtimeConfig) { this.runtimeConfig = runtimeConfig; }
    public void setRequiredStreams(List<String> requiredStreams) { this.requiredStreams = requiredStreams; }
    public void setWindow(PatternWindowSpec window) { this.window = window; }
    public void setStateKeys(Map<String, String> stateKeys) { this.stateKeys = stateKeys; }
    public void setCompiledAt(Instant compiledAt) { this.compiledAt = compiledAt; }
    public void setVersion(String version) { this.version = version; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID patternId;
        private OperatorDAG operatorGraph;
        private List<String> eventTypes;
        private Map<String, Object> runtimeConfig;
        private List<String> requiredStreams;
        private PatternWindowSpec window;
        private Map<String, String> stateKeys;
        private Instant compiledAt = Instant.now();
        private String version = "1.0";
        private Map<String, Object> metadata;
        
        public Builder patternId(UUID patternId) { this.patternId = patternId; return this; }
        public Builder operatorGraph(OperatorDAG operatorGraph) { this.operatorGraph = operatorGraph; return this; }
        public Builder eventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; return this; }
        public Builder runtimeConfig(Map<String, Object> runtimeConfig) { this.runtimeConfig = runtimeConfig; return this; }
        public Builder requiredStreams(List<String> requiredStreams) { this.requiredStreams = requiredStreams; return this; }
        public Builder window(PatternWindowSpec window) { this.window = window; return this; }
        public Builder stateKeys(Map<String, String> stateKeys) { this.stateKeys = stateKeys; return this; }
        public Builder compiledAt(Instant compiledAt) { this.compiledAt = compiledAt; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        
        public DetectionPlan build() {
            return new DetectionPlan(this);
        }
    }
    
    @Override
    public String toString() {
        return "DetectionPlan{" +
                "patternId=" + patternId +
                ", requiredStreams=" + requiredStreams +
                ", window=" + window +
                ", compiledAt=" + compiledAt +
                ", version='" + version + '\'' +
                '}';
    }
}

