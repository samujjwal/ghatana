package com.ghatana.datacloud.plugins.lineage;

import com.ghatana.platform.plugin.HealthStatus;
import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Lineage Plugin for Data-Cloud.
 * 
 * <p>Tracks data provenance and lineage across collections and transformations.
 * Enables impact analysis and data governance.</p>
 * 
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Data lineage tracking (upstream/downstream)</li>
 *   <li>Transformation recording</li>
 *   <li>Impact analysis</li>
 *   <li>Lineage visualization</li>
 *   <li>Data governance integration</li>
 * </ul>
 * 
 * @doc.type plugin
 * @doc.purpose Data lineage and provenance tracking
 * @doc.layer enterprise
 * @doc.pattern Plugin, Observer
 */
public class LineagePlugin implements Plugin {
    private static final Logger logger = LoggerFactory.getLogger(LineagePlugin.class);
    
    private static final PluginMetadata METADATA = PluginMetadata.builder()
        .id("lineage-plugin")
        .name("Data Lineage Plugin")
        .version("1.0.0")
        .vendor("Ghatana")
        .description("Data lineage and provenance tracking")
        .type(PluginType.PROCESSING)
        .capabilities(Set.of("lineage-tracking", "impact-analysis", "data-provenance"))
        .build();
    
    private final Map<String, LineageRecord> lineageStore;
    private final Map<String, Set<String>> upstreamDependencies;
    private final Map<String, Set<String>> downstreamDependencies;
    private volatile PluginState state = PluginState.UNLOADED;
    private volatile boolean running = false;
    
    public LineagePlugin() {
        this.lineageStore = new ConcurrentHashMap<>();
        this.upstreamDependencies = new ConcurrentHashMap<>();
        this.downstreamDependencies = new ConcurrentHashMap<>();
    }
    
    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }
    
    @Override
    public @NotNull PluginState getState() {
        return state;
    }
    
    @Override
    public @NotNull Promise<Void> initialize(@NotNull PluginContext context) {
        logger.info("Initializing LineagePlugin");
        state = PluginState.INITIALIZED;
        return Promise.complete();
    }
    
    @Override
    public @NotNull Promise<Void> start() {
        state = PluginState.STARTED;
        running = true;
        logger.info("LineagePlugin started");
        return Promise.complete();
    }
    
    @Override
    public @NotNull Promise<Void> stop() {
        state = PluginState.STOPPED;
        running = false;
        logger.info("LineagePlugin stopped");
        return Promise.complete();
    }
    
    @Override
    public @NotNull Promise<Void> shutdown() {
        state = PluginState.STOPPED;
        running = false;
        logger.info("LineagePlugin shutdown");
        // Clean up resources
        lineageStore.clear();
        upstreamDependencies.clear();
        downstreamDependencies.clear();
        return Promise.complete();
    }
    
    @Override
    public @NotNull Promise<HealthStatus> healthCheck() {
        return Promise.of(
            running 
                ? HealthStatus.ok("Lineage plugin is running")
                : HealthStatus.error("Lineage plugin is not running")
        );
    }
    
    /**
     * Records a data transformation.
     * 
     * @param tenantId tenant identifier
     * @param sourceCollection source collection
     * @param targetCollection target collection
     * @param transformationType type of transformation
     * @param metadata transformation metadata
     * @return promise of void
     */
    public Promise<Void> recordTransformation(String tenantId, String sourceCollection,
                                              String targetCollection, String transformationType,
                                              Map<String, Object> metadata) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Plugin not running"));
        }
        
        String sourceKey = buildKey(tenantId, sourceCollection);
        String targetKey = buildKey(tenantId, targetCollection);
        
        // Record lineage
        LineageRecord record = LineageRecord.builder()
            .id(UUID.randomUUID().toString())
            .tenantId(tenantId)
            .sourceCollection(sourceCollection)
            .targetCollection(targetCollection)
            .transformationType(transformationType)
            .metadata(metadata)
            .timestamp(Instant.now())
            .build();
        
        lineageStore.put(record.getId(), record);
        
        // Update dependencies
        upstreamDependencies.computeIfAbsent(targetKey, k -> ConcurrentHashMap.newKeySet())
            .add(sourceKey);
        downstreamDependencies.computeIfAbsent(sourceKey, k -> ConcurrentHashMap.newKeySet())
            .add(targetKey);
        
        logger.debug("Recorded transformation: {} -> {}", sourceCollection, targetCollection);
        
        return Promise.complete();
    }
    
    /**
     * Gets upstream lineage for a collection.
     * 
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return promise of upstream collections
     */
    public Promise<Set<String>> getUpstreamLineage(String tenantId, String collectionName) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Plugin not running"));
        }
        
        String key = buildKey(tenantId, collectionName);
        Set<String> upstream = upstreamDependencies.getOrDefault(key, Set.of());
        
        return Promise.of(new HashSet<>(upstream));
    }
    
    /**
     * Gets downstream lineage for a collection.
     * 
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return promise of downstream collections
     */
    public Promise<Set<String>> getDownstreamLineage(String tenantId, String collectionName) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Plugin not running"));
        }
        
        String key = buildKey(tenantId, collectionName);
        Set<String> downstream = downstreamDependencies.getOrDefault(key, Set.of());
        
        return Promise.of(new HashSet<>(downstream));
    }
    
    /**
     * Analyzes impact of changes to a collection.
     * 
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return promise of impact analysis
     */
    public Promise<ImpactAnalysis> analyzeImpact(String tenantId, String collectionName) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Plugin not running"));
        }
        
        return getDownstreamLineage(tenantId, collectionName)
            .map(downstream -> {
                ImpactAnalysis analysis = ImpactAnalysis.builder()
                    .collection(collectionName)
                    .affectedCollections(downstream)
                    .impactLevel(calculateImpactLevel(downstream.size()))
                    .timestamp(Instant.now())
                    .build();
                
                logger.debug("Impact analysis: {} affects {} collections",
                    collectionName, downstream.size());
                
                return analysis;
            });
    }
    
    /**
     * Gets complete lineage graph.
     * 
     * @param tenantId tenant identifier
     * @return promise of lineage graph
     */
    public Promise<LineageGraph> getLineageGraph(String tenantId) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Plugin not running"));
        }
        
        Map<String, Set<String>> upstreamFiltered = new HashMap<>();
        Map<String, Set<String>> downstreamFiltered = new HashMap<>();
        
        // Filter by tenant
        upstreamDependencies.forEach((key, deps) -> {
            if (key.startsWith(tenantId + ":")) {
                upstreamFiltered.put(key.substring(tenantId.length() + 1), new HashSet<>(deps));
            }
        });
        
        downstreamDependencies.forEach((key, deps) -> {
            if (key.startsWith(tenantId + ":")) {
                downstreamFiltered.put(key.substring(tenantId.length() + 1), new HashSet<>(deps));
            }
        });
        
        LineageGraph graph = LineageGraph.builder()
            .tenantId(tenantId)
            .upstream(upstreamFiltered)
            .downstream(downstreamFiltered)
            .timestamp(Instant.now())
            .build();
        
        return Promise.of(graph);
    }
    
    /**
     * Calculates impact level based on affected collection count.
     * 
     * @param affectedCount number of affected collections
     * @return impact level
     */
    private String calculateImpactLevel(int affectedCount) {
        if (affectedCount == 0) {
            return "NONE";
        } else if (affectedCount <= 3) {
            return "LOW";
        } else if (affectedCount <= 10) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }
    
    /**
     * Builds cache key.
     * 
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return cache key
     */
    private String buildKey(String tenantId, String collectionName) {
        return tenantId + ":" + collectionName;
    }
    
    /**
     * Lineage record.
     */
    public static class LineageRecord {
        private final String id;
        private final String tenantId;
        private final String sourceCollection;
        private final String targetCollection;
        private final String transformationType;
        private final Map<String, Object> metadata;
        private final Instant timestamp;
        
        private LineageRecord(Builder builder) {
            this.id = builder.id;
            this.tenantId = builder.tenantId;
            this.sourceCollection = builder.sourceCollection;
            this.targetCollection = builder.targetCollection;
            this.transformationType = builder.transformationType;
            this.metadata = builder.metadata;
            this.timestamp = builder.timestamp;
        }
        
        public String getId() { return id; }
        public String getTenantId() { return tenantId; }
        public String getSourceCollection() { return sourceCollection; }
        public String getTargetCollection() { return targetCollection; }
        public String getTransformationType() { return transformationType; }
        public Map<String, Object> getMetadata() { return metadata; }
        public Instant getTimestamp() { return timestamp; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String id;
            private String tenantId;
            private String sourceCollection;
            private String targetCollection;
            private String transformationType;
            private Map<String, Object> metadata;
            private Instant timestamp;
            
            public Builder id(String id) { this.id = id; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder sourceCollection(String source) { this.sourceCollection = source; return this; }
            public Builder targetCollection(String target) { this.targetCollection = target; return this; }
            public Builder transformationType(String type) { this.transformationType = type; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
            public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
            
            public LineageRecord build() {
                return new LineageRecord(this);
            }
        }
    }
    
    /**
     * Impact analysis result.
     */
    public static class ImpactAnalysis {
        private final String collection;
        private final Set<String> affectedCollections;
        private final String impactLevel;
        private final Instant timestamp;
        
        private ImpactAnalysis(Builder builder) {
            this.collection = builder.collection;
            this.affectedCollections = builder.affectedCollections;
            this.impactLevel = builder.impactLevel;
            this.timestamp = builder.timestamp;
        }
        
        public String getCollection() { return collection; }
        public Set<String> getAffectedCollections() { return affectedCollections; }
        public String getImpactLevel() { return impactLevel; }
        public Instant getTimestamp() { return timestamp; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String collection;
            private Set<String> affectedCollections;
            private String impactLevel;
            private Instant timestamp;
            
            public Builder collection(String collection) { this.collection = collection; return this; }
            public Builder affectedCollections(Set<String> affected) { this.affectedCollections = affected; return this; }
            public Builder impactLevel(String level) { this.impactLevel = level; return this; }
            public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
            
            public ImpactAnalysis build() {
                return new ImpactAnalysis(this);
            }
        }
    }
    
    /**
     * Lineage graph.
     */
    public static class LineageGraph {
        private final String tenantId;
        private final Map<String, Set<String>> upstream;
        private final Map<String, Set<String>> downstream;
        private final Instant timestamp;
        
        private LineageGraph(Builder builder) {
            this.tenantId = builder.tenantId;
            this.upstream = builder.upstream;
            this.downstream = builder.downstream;
            this.timestamp = builder.timestamp;
        }
        
        public String getTenantId() { return tenantId; }
        public Map<String, Set<String>> getUpstream() { return upstream; }
        public Map<String, Set<String>> getDownstream() { return downstream; }
        public Instant getTimestamp() { return timestamp; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String tenantId;
            private Map<String, Set<String>> upstream;
            private Map<String, Set<String>> downstream;
            private Instant timestamp;
            
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder upstream(Map<String, Set<String>> upstream) { this.upstream = upstream; return this; }
            public Builder downstream(Map<String, Set<String>> downstream) { this.downstream = downstream; return this; }
            public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
            
            public LineageGraph build() {
                return new LineageGraph(this);
            }
        }
    }
}
