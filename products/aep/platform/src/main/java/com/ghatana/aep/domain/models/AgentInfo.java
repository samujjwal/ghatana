package com.ghatana.aep.domain.models.agent;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * {@code AgentInfo} encapsulates metadata about an autonomous agent in the system,
 * including identity, capabilities, status, and operational endpoints.
 *
 * <h2>Purpose</h2>
 * Provides agent discovery and management information for:
 * <ul>
 *   <li>Agent registry lookups</li>
 *   <li>Orchestration and routing decisions</li>
 *   <li>Health monitoring and status tracking</li>
 *   <li>Capability-based service selection</li>
 *   <li>Dynamic endpoint discovery</li>
 * </ul>
 *
 * <h2>Structure</h2>
 * Mutable agent metadata:
 * <ul>
 *   <li><b>id</b>: Unique agent identifier (e.g., "agent-planner-1")</li>
 *   <li><b>name</b>: Display name for UI/logging</li>
 *   <li><b>type</b>: Agent type classification (e.g., "PLANNER", "EXECUTOR", "ANALYZER")</li>
 *   <li><b>version</b>: Agent implementation version (e.g., "1.0.0")</li>
 *   <li><b>status</b>: Current operational status (ACTIVE, IDLE, DEGRADED, OFFLINE)</li>
 *   <li><b>description</b>: Human-readable purpose/responsibility</li>
 *   <li><b>endpoint</b>: Network address for communication (e.g., "http://agent:8080")</li>
 *   <li><b>capabilities</b>: Map of operation name → capability details</li>
 *   <li><b>metadata</b>: Flexible key-value pairs (team, owner, tags, etc.)</li>
 *   <li><b>lastSeen</b>: {@link Instant} of last heartbeat/interaction</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Created by</b>: Agent registration services, initialization</li>
 *   <li><b>Used by</b>: Agent registry, orchestrator, routing engine</li>
 *   <li><b>Stored in</b>: Agent registry cache, discovery service</li>
 *   <li><b>Updated by</b>: Health checks, capability updates, heartbeat</li>
 * </ul>
 *
 * <h2>Mutable Design Pattern</h2>
 * Note: AgentInfo is mutable (uses JavaBeans pattern) to support:
 * <ul>
 *   <li>Dynamic status updates (ACTIVE → DEGRADED → OFFLINE)</li>
 *   <li>Capability registration and updates</li>
 *   <li>Metadata tagging and enrichment</li>
 *   <li>Framework reflection-based deserialization</li>
 * </ul>
 * Despite mutability, ensure thread-safe updates through synchronization if shared.
 *
 * <h2>Defensive Copying</h2>
 * Collections (capabilities, metadata) are defensively copied on get/set
 * to prevent external mutation of internal state.
 *
 * <h2>Example: Agent Registration</h2>
 * {@code
 *   AgentInfo planner = new AgentInfo(
 *       "agent-planner-1",
 *       "Task Planner",
 *       "PLANNER",
 *       "1.0.0",
 *       "ACTIVE",
 *       "Plans execution tasks for event processing",
 *       "http://planner.internal:8080"
 *   );
 *   planner.addCapability("plan_tasks", Map.of(
 *       "throughput", 1000,
 *       "latency_ms", 50
 *   ));
 *   planner.addMetadata("team", "Platform");
 *   planner.setLastSeen(Instant.now());
 * }
 *
 * <h2>Typical Status Values</h2>
 * <ul>
 *   <li><b>ACTIVE</b>: Agent operational and accepting work</li>
 *   <li><b>IDLE</b>: Agent idle but ready</li>
 *   <li><b>DEGRADED</b>: Agent operational but with performance issues</li>
 *   <li><b>OFFLINE</b>: Agent not responding to heartbeats</li>
 * </ul>
 *
 * <h2>Typical Agent Types</h2>
 * <ul>
 *   <li><b>PLANNER</b>: Produces execution plans</li>
 *   <li><b>EXECUTOR</b>: Executes planned work</li>
 *   <li><b>ANALYZER</b>: Analyzes patterns and insights</li>
 *   <li><b>COORDINATOR</b>: Orchestrates multi-agent workflows</li>
 * </ul>
 *
 * <h2>Capability Registration Example</h2>
 * {@code
 * AgentInfo planner = new AgentInfo(
 *     "agent-planner-1",
 *     "Task Planner",
 *     "PLANNER",
 *     "1.2.0",
 *     "ACTIVE",
 *     "Produces high-level execution plans",
 *     "http://planner.internal:8080"
 * );
 * planner.addCapability("plan_tasks", Map.of(
 *     "throughput", "1000/sec",
 *     "latency_p99_ms", "50",
 *     "max_plan_depth", "10"
 * ));
 * planner.addCapability("optimize_workflow", Map.of(
 *     "enabled", "true"
 * ));
 * planner.addMetadata("team", "Platform-Engineering");
 * planner.addMetadata("owner", "platform-team@company.com");
 * planner.setLastSeen(Instant.now());
 * }
 *
 * <h2>Heartbeat & Status Tracking</h2>
 * Registry periodically updates lastSeen timestamp via setLastSeen() to detect stale agents:
 * {@code
 * // In heartbeat handler
 * agentInfo.setLastSeen(Instant.now());
 * 
 * // In health checker
 * Duration timeSinceLastSeen = Duration.between(agentInfo.getLastSeen(), Instant.now());
 * if (timeSinceLastSeen.toMinutes() > 5) {
 *     agentInfo.setStatus("OFFLINE");
 *     registry.updateAgent(agentInfo);
 * }
 * }
 *
 * <h2>Discovery & Routing Use Cases</h2>
 * <ul>
 *   <li><b>Find Analyzer</b>: registry.findByType("ANALYZER")</li>
 *   <li><b>Capability Check</b>: agentInfo.hasCapability("process_events")</li>
 *   <li><b>Route to Active</b>: agents.stream().filter(a → "ACTIVE".equals(a.getStatus()))</li>
 *   <li><b>Load Balance</b>: select agent with minimum activeWorkload metadata</li>
 * </ul>
 *
 * <h2>JSON Serialization</h2>
 * AgentInfo serializes to JSON for persistence and API responses:
 * {@code
 * {
 *   \"id\": \"agent-planner-1\",
 *   \"name\": \"Task Planner\",
 *   \"type\": \"PLANNER\",
 *   \"version\": \"1.2.0\",
 *   \"status\": \"ACTIVE\",
 *   \"description\": \"Produces high-level execution plans\",
 *   \"endpoint\": \"http://planner.internal:8080\",
 *   \"capabilities\": {
 *     \"plan_tasks\": { \"throughput\": \"1000/sec\", \"latency_p99_ms\": \"50\" }
 *   },
 *   \"metadata\": { \"team\": \"Platform-Engineering\" },
 *   \"lastSeen\": \"2025-11-06T15:30:00Z\"
 * }
 * }
 *
 * @see AgentCapabilities Related capability interface
 * @see AgentExecutionContext Related execution context type
 * @doc.type data-model
 * @doc.layer domain
 * @doc.purpose agent registry metadata and discovery information
 * @doc.pattern mutable-data-model service-registry-entry
 */
public class AgentInfo {
    private String id;
    private String name;
    private String type;
    private String version;
    private String status;
    private String description;
    private Map<String, Object> capabilities;
    private Map<String, String> metadata;
    private Instant lastSeen;
    private String endpoint;

    /**
     * Default constructor for frameworks that use reflection.
     */
    public AgentInfo() {
        this.capabilities = new HashMap<>();
        this.metadata = new HashMap<>();
    }
    
    /**
     * Creates a new AgentInfo with the specified details.
     *
     * @param id the unique identifier of the agent
     * @param name the display name of the agent
     * @param type the type of the agent
     * @param version the version of the agent
     * @param status the current status of the agent
     * @param description a description of the agent
     * @param endpoint the network endpoint of the agent
     */
    public AgentInfo(String id, String name, String type, String version, 
                    String status, String description, String endpoint) {
        this();
        this.id = id;
        this.name = name;
        this.type = type;
        this.version = version;
        this.status = status;
        this.description = description;
        this.endpoint = endpoint;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getCapabilities() {
        return new HashMap<>(capabilities);
    }

    public void setCapabilities(Map<String, Object> capabilities) {
        this.capabilities = new HashMap<>(capabilities);
    }

    public void addCapability(String key, Object value) {
        this.capabilities.put(key, value);
    }

    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = new HashMap<>(metadata);
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentInfo agentInfo = (AgentInfo) o;
        return Objects.equals(id, agentInfo.id) &&
               Objects.equals(name, agentInfo.name) &&
               Objects.equals(type, agentInfo.type) &&
               Objects.equals(version, agentInfo.version) &&
               Objects.equals(status, agentInfo.status) &&
               Objects.equals(description, agentInfo.description) &&
               Objects.equals(endpoint, agentInfo.endpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, version, status, description, endpoint);
    }

    @Override
    public String toString() {
        return "AgentInfo{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", type='" + type + '\'' +
               ", version='" + version + '\'' +
               ", status='" + status + '\'' +
               ", description='" + description + '\'' +
               ", endpoint='" + endpoint + '\'' +
               ", lastSeen=" + lastSeen +
               '}';
    }
}
