package com.ghatana.core.operator;

import java.util.Objects;

/**
 * Unique identifier for operators in the Unified Operator Model.
 *
 * <p><b>Purpose</b><br>
 * Provides type-safe, structured identification for all operators (Stream, Pattern, Learning)
 * to enable operator discovery, versioning, cataloging, and deployment tracking.
 *
 * <p><b>Architecture Role</b><br>
 * Part of the Unified Operator Model (Decision 1, WORLD_CLASS_DESIGN_MASTER.md Section III).
 * Every {@link UnifiedOperator} instance has exactly one immutable OperatorId. This enables:
 * <ul>
 *   <li>Catalog queries by namespace, type, name, or version</li>
 *   <li>Version-based operator deployment and rollback</li>
 *   <li>Operator-as-agent serialization to EventCloud</li>
 *   <li>Git-like versioning for operator pipelines</li>
 *   <li>Tenant-scoped operator isolation (via namespace)</li>
 * </ul>
 *
 * <p><b>ID Format</b><br>
 * <pre>{namespace}:{type}:{name}:{version}</pre>
 * <ul>
 *   <li><b>namespace</b>: Operator ownership/tenant (e.g., "ghatana", "tenant-123")</li>
 *   <li><b>type</b>: Operator classification ("stream", "pattern", "learning")</li>
 *   <li><b>name</b>: Operator name ("filter", "seq", "apriori")</li>
 *   <li><b>version</b>: Semantic version ("1.0.0", "2.1.3")</li>
 * </ul>
 *
 * <p><b>Usage Examples</b>
 *
 * <p><b>Example 1: Create operator ID from components</b>
 * <pre>{@code
 * // Platform operator (ghatana namespace)
 * OperatorId streamFilter = OperatorId.of("ghatana", "stream", "filter", "1.0.0");
 * System.out.println(streamFilter); // ghatana:stream:filter:1.0.0
 * 
 * // Tenant-specific operator
 * OperatorId tenantPattern = OperatorId.of("tenant-acme", "pattern", "fraud-seq", "2.1.0");
 * }</pre>
 *
 * <p><b>Example 2: Parse operator ID from string</b>
 * <pre>{@code
 * // Parse from EventCloud event metadata
 * String operatorIdString = event.getMetadata().get("operatorId");
 * OperatorId id = OperatorId.parse(operatorIdString);
 * 
 * // Access components
 * String namespace = id.getNamespace(); // "ghatana"
 * String type = id.getType();           // "stream"
 * String name = id.getName();           // "filter"
 * String version = id.getVersion();     // "1.0.0"
 * }</pre>
 *
 * <p><b>Example 3: Catalog queries</b>
 * <pre>{@code
 * // Query catalog by operator ID
 * OperatorId targetId = OperatorId.parse("ghatana:stream:filter:1.0.0");
 * Optional<UnifiedOperator> operator = catalog.get(targetId);
 * 
 * // Query by ID components
 * List<UnifiedOperator> streamOps = catalog.findByType("stream");
 * List<UnifiedOperator> ghatanaOps = catalog.findByNamespace("ghatana");
 * }</pre>
 *
 * <p><b>Example 4: Version comparison</b>
 * <pre>{@code
 * OperatorId v1 = OperatorId.parse("ghatana:stream:filter:1.0.0");
 * OperatorId v2 = OperatorId.parse("ghatana:stream:filter:2.0.0");
 * 
 * // Extract version for semantic comparison
 * String version1 = v1.getVersion(); // "1.0.0"
 * String version2 = v2.getVersion(); // "2.0.0"
 * // Use semantic version library for proper comparison
 * }</pre>
 *
 * <p><b>Example 5: EventCloud serialization</b>
 * <pre>{@code
 * // Serialize operator to EventCloud event
 * Event operatorEvent = Event.builder()
 *     .type("operator.deployed")
 *     .addMetadata("operatorId", operatorId.toString())
 *     .addMetadata("namespace", operatorId.getNamespace())
 *     .addMetadata("operatorType", operatorId.getType())
 *     .build();
 * 
 * // Deserialize from event
 * String idString = operatorEvent.getMetadata().get("operatorId");
 * OperatorId id = OperatorId.parse(idString);
 * }</pre>
 *
 * <p><b>Example 6: Multi-tenant isolation</b>
 * <pre>{@code
 * // Tenant-specific operators
 * OperatorId tenant1Op = OperatorId.of("tenant-123", "pattern", "fraud-seq", "1.0.0");
 * OperatorId tenant2Op = OperatorId.of("tenant-456", "pattern", "fraud-seq", "1.0.0");
 * 
 * // Same operator name but isolated by namespace
 * assert !tenant1Op.equals(tenant2Op);
 * assert tenant1Op.getName().equals(tenant2Op.getName());
 * }</pre>
 *
 * <p><b>Best Practices</b>
 * <ul>
 *   <li>Use semantic versioning (MAJOR.MINOR.PATCH) for version field</li>
 *   <li>Keep namespace lowercase and DNS-friendly (alphanumeric + hyphens)</li>
 *   <li>Use type classification (stream/pattern/learning) consistently</li>
 *   <li>Avoid special characters in name (alphanumeric + hyphens only)</li>
 *   <li>Increment version for any operator logic change</li>
 *   <li>Parse IDs from untrusted sources with try-catch</li>
 *   <li>Use namespace for tenant isolation (tenant-{tenantId} pattern)</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b>
 * <ul>
 *   <li>❌ DON'T mutate components after creation (immutable)</li>
 *   <li>❌ DON'T use spaces or special characters in components</li>
 *   <li>❌ DON'T parse IDs without validating format</li>
 *   <li>❌ DON'T mix version schemes (stick to semantic versioning)</li>
 *   <li>❌ DON'T hardcode operator IDs (use constants or configuration)</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>
 * <ul>
 *   <li>Construction: O(1) time, minimal heap allocation (4 strings + 1 formatted)</li>
 *   <li>Parsing: O(n) where n = string length (4-way split operation)</li>
 *   <li>toString(): O(1) cached in fullId field</li>
 *   <li>Equality: O(1) delegated to String.equals()</li>
 *   <li>Memory: ~100-200 bytes per instance (5 strings + object overhead)</li>
 * </ul>
 *
 * <p><b>Integration Points</b>
 * <ul>
 *   <li>{@link UnifiedOperator#getId()} - Every operator returns its OperatorId</li>
 *   <li>{@link OperatorCatalog#get(OperatorId)} - Catalog queries by ID</li>
 *   <li>EventCloud - Serialized in operator.deployed events</li>
 *   <li>PipelineBuilder - Reference operators by ID in YAML/JSON</li>
 *   <li>OperatorRegistry - Index operators by namespace, type, name, version</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable and thread-safe. All fields are final and defensive copies are made during
 * construction. Safe for concurrent access from multiple threads without synchronization.
 *
 * @see UnifiedOperator
 * @see OperatorCatalog
 * @see OperatorType
 * 
 * @doc.type class
 * @doc.purpose Unique identifier for operators in Unified Operator Model
 * @doc.layer core
 * @doc.pattern Value Object
 * 
 * @author Ghatana Platform Team
 * @version 2.0.0
 * @since 2025-10-25
 */
public final class OperatorId {
    
    private final String namespace;
    private final String type;
    private final String name;
    private final String version;
    private final String fullId;

    private OperatorId(String namespace, String type, String name, String version) {
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.fullId = String.format("%s:%s:%s:%s", namespace, type, name, version);
    }

    /**
     * Create operator ID from components.
     * 
     * @param namespace operator namespace (e.g., "ghatana")
     * @param type operator type (e.g., "stream", "pattern", "learning")
     * @param name operator name (e.g., "filter", "seq", "apriori")
     * @param version operator version (e.g., "1.0.0")
     * @return operator ID
     */
    public static OperatorId of(String namespace, String type, String name, String version) {
        return new OperatorId(namespace, type, name, version);
    }

    /**
     * Parse operator ID from string.
     * 
     * @param id operator ID string (format: namespace:type:name:version)
     * @return operator ID
     * @throws IllegalArgumentException if format is invalid
     */
    public static OperatorId parse(String id) {
        Objects.requireNonNull(id, "id cannot be null");
        String[] parts = id.split(":", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                "Invalid operator ID format. Expected 'namespace:type:name:version', got: " + id
            );
        }
        return new OperatorId(parts[0], parts[1], parts[2], parts[3]);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return fullId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OperatorId)) return false;
        OperatorId that = (OperatorId) o;
        return fullId.equals(that.fullId);
    }

    @Override
    public int hashCode() {
        return fullId.hashCode();
    }
}
