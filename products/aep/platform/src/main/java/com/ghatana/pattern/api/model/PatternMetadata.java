package com.ghatana.pattern.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents lightweight metadata about a pattern for querying and listing purposes.
 * 
 * <p>PatternMetadata is a lightweight projection of PatternSpecification containing
 * only queryable fields. Used for:
 * <ul>
 *   <li>List/search operations without loading full specifications</li>
 *   <li>Pattern catalog views showing status, timestamps, labels</li>
 *   <li>Quick status checks (isActive, isCompiled, isActivated)</li>
 * </ul>
 * 
 * @doc.pattern Value Object Pattern (immutable metadata), Projection Pattern (lightweight view)
 * @doc.compiler-phase Pattern Metadata (query projection for pattern catalog)
 * @doc.threading Thread-safe after construction (immutable value object)
 * @doc.performance O(1) for field access; O(1) for status checks
 * @doc.memory O(1) for fixed fields + O(l+e) where l=labels, e=event types
 * @doc.immutability Immutable after build(); use builder for modifications
 * @doc.serialization JSON serializable via Jackson annotations
 * @doc.apiNote Use for listing patterns without fetching full specifications
 * @doc.limitation No operator tree or runtime config; fetch PatternSpecification/DetectionPlan for full details
 * 
 * <h2>Metadata Fields</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Field</th>
 *     <th>Type</th>
 *     <th>Purpose</th>
 *   </tr>
 *   <tr>
 *     <td>id</td>
 *     <td>UUID</td>
 *     <td>Unique pattern identifier</td>
 *   </tr>
 *   <tr>
 *     <td>tenantId</td>
 *     <td>String</td>
 *     <td>Multi-tenant isolation key</td>
 *   </tr>
 *   <tr>
 *     <td>name</td>
 *     <td>String</td>
 *     <td>Human-readable pattern name</td>
 *   </tr>
 *   <tr>
 *     <td>version</td>
 *     <td>int</td>
 *     <td>Pattern version number</td>
 *   </tr>
 *   <tr>
 *     <td>status</td>
 *     <td>PatternStatus</td>
 *     <td>Current lifecycle/recommendation status</td>
 *   </tr>
 *   <tr>
 *     <td>labels</td>
 *     <td>List&lt;String&gt;</td>
 *     <td>Searchable tags/categories</td>
 *   </tr>
 *   <tr>
 *     <td>priority</td>
 *     <td>int</td>
 *     <td>Execution priority (higher = earlier)</td>
 *   </tr>
 *   <tr>
 *     <td>eventTypes</td>
 *     <td>List&lt;String&gt;</td>
 *     <td>Event types referenced by pattern</td>
 *   </tr>
 *   <tr>
 *     <td>createdAt</td>
 *     <td>Instant</td>
 *     <td>Pattern creation timestamp</td>
 *   </tr>
 *   <tr>
 *     <td>compiledAt</td>
 *     <td>Instant</td>
 *     <td>Last compilation timestamp (null if not compiled)</td>
 *   </tr>
 *   <tr>
 *     <td>activatedAt</td>
 *     <td>Instant</td>
 *     <td>Activation timestamp (null if never activated)</td>
 *   </tr>
 * </table>
 */
public class PatternMetadata {
    
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
    
    @JsonProperty("status")
    private PatternStatus status;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
    
    @JsonProperty("updatedAt")
    private Instant updatedAt;
    
    @JsonProperty("activatedAt")
    private Instant activatedAt;
    
    @JsonProperty("compiledAt")
    private Instant compiledAt;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("eventTypes")
    private List<String> eventTypes;

    // Default constructor for JSON deserialization
    public PatternMetadata() {}
    
    // Builder pattern constructor
    public PatternMetadata(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.labels = builder.labels;
        this.priority = builder.priority;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.activatedAt = builder.activatedAt;
        this.compiledAt = builder.compiledAt;
        this.metadata = builder.metadata;
        this.eventTypes = builder.eventTypes;
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public int getVersion() { return version; }
    public String getDescription() { return description; }
    public List<String> getLabels() { return labels; }
    public int getPriority() { return priority; }
    public PatternStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getCompiledAt() { return compiledAt; }
    public Map<String, Object> getMetadata() { return metadata; }
    public List<String> getEventTypes() { return eventTypes; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setName(String name) { this.name = name; }
    public void setVersion(int version) { this.version = version; }
    public void setDescription(String description) { this.description = description; }
    public void setLabels(List<String> labels) { this.labels = labels; }
    public void setPriority(int priority) { this.priority = priority; }
    public void setStatus(PatternStatus status) { this.status = status; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
    public void setCompiledAt(Instant compiledAt) { this.compiledAt = compiledAt; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public void setEventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; }

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID id;
        private String tenantId;
        private String name;
        private int version;
        private String description;
        private List<String> labels;
        private int priority;
        private PatternStatus status;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant activatedAt;
        private Instant compiledAt;
        private Map<String, Object> metadata;
        private List<String> eventTypes;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder version(int version) { this.version = version; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder labels(List<String> labels) { this.labels = labels; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder status(PatternStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder activatedAt(Instant activatedAt) { this.activatedAt = activatedAt; return this; }
        public Builder compiledAt(Instant compiledAt) { this.compiledAt = compiledAt; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder eventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; return this; }

        public PatternMetadata build() {
            return new PatternMetadata(this);
        }
    }
    
    /**
     * Check if the pattern is currently active.
     * 
     * @return true if the pattern is active
     */
    public boolean isActive() {
        return status == PatternStatus.ACTIVE;
    }
    
    /**
     * Check if the pattern has been compiled.
     * 
     * @return true if the pattern has been compiled
     */
    public boolean isCompiled() {
        return compiledAt != null;
    }
    
    /**
     * Check if the pattern has been activated.
     * 
     * @return true if the pattern has been activated
     */
    public boolean isActivated() {
        return activatedAt != null;
    }
    
    @Override
    public String toString() {
        return "PatternMetadata{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", version=" + version +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}

