package com.ghatana.pattern.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.aep.domain.pattern.SelectionMode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a pattern specification that defines the structure and behavior
 * of an event pattern to be detected.
 * 
 * <p>PatternSpecification is the declarative input to the pattern compiler. It describes
 * WHAT pattern to detect (not HOW to execute it). The compiler transforms this specification
 * into an executable DetectionPlan.
 * 
 * @doc.pattern Value Object Pattern (immutable specification), Builder Pattern (construction)
 * @doc.compiler-phase PatternSpecification (input to compilation pipeline)
 * @doc.threading Thread-safe after construction (immutable specification)
 * @doc.performance O(1) for field access; O(n) for operator tree traversal
 * @doc.memory O(n) for operator tree where n=operator count
 * @doc.immutability Immutable after build(); use builder for modifications
 * @doc.serialization JSON serializable via Jackson; suitable for storage/API
 * @doc.apiNote Create via builder pattern; validate before compilation
 * @doc.versioning Version field enables pattern evolution and backward compatibility
 * 
 * <h2>Pattern Specification Fields</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Field</th>
 *     <th>Required</th>
 *     <th>Purpose</th>
 *   </tr>
 *   <tr>
 *     <td>id</td>
 *     <td>Yes</td>
 *     <td>Unique pattern identifier (UUID)</td>
 *   </tr>
 *   <tr>
 *     <td>tenantId</td>
 *     <td>Yes</td>
 *     <td>Multi-tenant isolation key</td>
 *   </tr>
 *   <tr>
 *     <td>name</td>
 *     <td>Yes</td>
 *     <td>Human-readable pattern name</td>
 *   </tr>
 *   <tr>
 *     <td>version</td>
 *     <td>Yes</td>
 *     <td>Pattern version for evolution tracking</td>
 *   </tr>
 *   <tr>
 *     <td>operator</td>
 *     <td>Yes</td>
 *     <td>Root operator specification (tree structure)</td>
 *   </tr>
 *   <tr>
 *     <td>eventTypes</td>
 *     <td>Yes</td>
 *     <td>List of event types referenced in pattern</td>
 *   </tr>
 *   <tr>
 *     <td>window</td>
 *     <td>No</td>
 *     <td>Time window for pattern matching (optional)</td>
 *   </tr>
 *   <tr>
 *     <td>activation</td>
 *     <td>No</td>
 *     <td>Enable/disable pattern execution (default: true)</td>
 *   </tr>
 *   <tr>
 *     <td>priority</td>
 *     <td>No</td>
 *     <td>Execution priority (higher = earlier execution)</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Design Reference:</b>
 * This specification format implements the pattern model from WORLD_CLASS_DESIGN_MASTER.md.
 * See .github/copilot-instructions.md "Contracts-first" for schema evolution patterns.
 */
public class PatternSpecification {
    
    @JsonProperty("id")
    private UUID id;
    
    @JsonProperty("tenantId")
    private String tenantId;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("version")
    private int version;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("labels")
    private List<String> labels;
    
    @JsonProperty("priority")
    private int priority;
    
    @JsonProperty("activation")
    private boolean activation;
    
    @JsonProperty("status")
    private PatternStatus status;
    
    @JsonProperty("selection")
    private SelectionMode selection;
    
    @JsonProperty("window")
    private PatternWindowSpec window;
    
    @JsonProperty("operator")
    private OperatorSpec operator;

    @JsonProperty("eventTypes")
    private List<String> eventTypes;

    @JsonProperty("whereClause")
    private String whereClause;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
    
    @JsonProperty("updatedAt")
    private Instant updatedAt;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    // Default constructor for JSON deserialization
    public PatternSpecification() {}
    
    // Builder pattern constructor
    public PatternSpecification(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.labels = builder.labels;
        this.priority = builder.priority;
        this.activation = builder.activation;
        this.status = builder.status;
        this.selection = builder.selection;
        this.window = builder.window;
        this.operator = builder.operator;
        this.eventTypes = builder.eventTypes;
        this.whereClause = builder.whereClause;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.metadata = builder.metadata;
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public int getVersion() { return version; }
    public String getDescription() { return description; }
    public List<String> getLabels() { return labels; }
    public int getPriority() { return priority; }
    public boolean isActivation() { return activation; }
    public PatternStatus getStatus() { return status; }
    public SelectionMode getSelection() { return selection; }
    public PatternWindowSpec getWindow() { return window; }
    public OperatorSpec getOperator() { return operator; }
    public List<String> getEventTypes() { return eventTypes; }
    public String getWhereClause() { return whereClause; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setName(String name) { this.name = name; }
    public void setVersion(int version) { this.version = version; }
    public void setDescription(String description) { this.description = description; }
    public void setLabels(List<String> labels) { this.labels = labels; }
    public void setPriority(int priority) { this.priority = priority; }
    public void setActivation(boolean activation) { this.activation = activation; }
    public void setStatus(PatternStatus status) { this.status = status; }
    public void setSelection(SelectionMode selection) { this.selection = selection; }
    public void setWindow(PatternWindowSpec window) { this.window = window; }
    public void setOperator(OperatorSpec operator) { this.operator = operator; }
    public void setEventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; }
    public void setWhereClause(String whereClause) { this.whereClause = whereClause; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID id;
        private String tenantId;
        private String name;
        private int version = 1;
        private String description;
        private List<String> labels;
        private int priority = 0;
        private boolean activation = false;
        private PatternStatus status = PatternStatus.DRAFT;
        private SelectionMode selection = SelectionMode.ALL;
        private PatternWindowSpec window;
        private OperatorSpec operator;
        private List<String> eventTypes;
        private String whereClause;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private Map<String, Object> metadata;
        
        public Builder id(UUID id) { this.id = id; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder version(int version) { this.version = version; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder labels(List<String> labels) { this.labels = labels; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder activation(boolean activation) { this.activation = activation; return this; }
        public Builder status(PatternStatus status) { this.status = status; return this; }
        public Builder selection(SelectionMode selection) { this.selection = selection; return this; }
        public Builder window(PatternWindowSpec window) { this.window = window; return this; }
        public Builder operator(OperatorSpec operator) { this.operator = operator; return this; }
        public Builder eventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; return this; }
        public Builder whereClause(String whereClause) { this.whereClause = whereClause; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        
        public PatternSpecification build() {
            return new PatternSpecification(this);
        }
    }
    
    @Override
    public String toString() {
        return "PatternSpecification{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", version=" + version +
                ", status=" + status +
                ", selection=" + selection +
                '}';
    }
}

