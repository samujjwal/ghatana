package com.ghatana.datacloud.client;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-Cloud Client API for embedded library usage.
 * 
 * <p>
 * Provides a unified interface for accessing Data-Cloud functionality
 * in embedded mode (in-process, no HTTP). Supports all core operations:
 * entities, events, search, and analytics.
 * </p>
 * 
 * <p>
 * <b>Deployment Modes:</b>
 * </p>
 * <ul>
 * <li>EMBEDDED - In-process, library mode (this API)</li>
 * <li>STANDALONE - Single node with HTTP API</li>
 * <li>DISTRIBUTED - Multi-node cluster</li>
 * </ul>
 * 
 * <p>
 * <b>Usage Example:</b>
 * </p>
 * 
 * <pre>{@code
 * // Create embedded client
 * DataCloudClient client = DataCloudClientFactory.embedded(config);
 * 
 * // Entity operations
 * Entity entity = Entity.builder()
 *         .tenantId("tenant-123")
 *         .collectionName("products")
 *         .data(Map.of("name", "Widget", "price", 99.99))
 *         .build();
 * 
 * Entity created = client.createEntity("tenant-123", "products",
 *         entity.getData()).get();
 * 
 * // Query operations
 * QuerySpec query = QuerySpec.builder()
 *         .filter("price > 50")
 *         .limit(10)
 *         .build();
 * 
 * List<Entity> results = client.queryEntities("tenant-123", "products", query)
 *         .get();
 * 
 * // Event operations
 * Event event = Event.builder()
 *         .tenantId("tenant-123")
 *         .eventTypeName("purchase")
 *         .payload(Map.of("productId", "prod-123", "amount", 99.99))
 *         .build();
 * 
 * client.appendEvent("tenant-123", "purchases", event).get();
 * 
 * // Search operations
 * SearchResults results = client.search("tenant-123",
 *         SearchQuery.builder()
 *                 .query("Widget")
 *                 .limit(20)
 *                 .build())
 *         .get();
 * }</pre>
 * 
 * @doc.type interface
 * @doc.purpose Unified client API for embedded Data-Cloud usage
 * @doc.layer core
 * @doc.pattern Client, Facade
 */
public interface DataCloudClient {

    // ============ Entity Operations ============

    /**
     * Creates a new entity.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param data           entity data
     * @return promise of created entity
     */
    Promise<EntityInterface> createEntity(String tenantId, String collectionName, Map<String, Object> data);

    /**
     * Gets an entity by ID.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param entityId       entity ID
     * @return promise of optional entity
     */
    Promise<Optional<EntityInterface>> getEntity(String tenantId, String collectionName, UUID entityId);

    /**
     * Updates an entity.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param entityId       entity ID
     * @param data           updated data
     * @return promise of updated entity
     */
    Promise<EntityInterface> updateEntity(String tenantId, String collectionName, UUID entityId,
            Map<String, Object> data);

    /**
     * Deletes an entity.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param entityId       entity ID
     * @return promise of void
     */
    Promise<Void> deleteEntity(String tenantId, String collectionName, UUID entityId);

    /**
     * Queries entities.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param query          query specification
     * @return promise of entity list
     */
    Promise<List<EntityInterface>> queryEntities(String tenantId, String collectionName, QuerySpecInterface query);

    /**
     * Counts entities matching query.
     * 
     * @param tenantId         tenant identifier
     * @param collectionName   collection name
     * @param filterExpression optional filter expression
     * @return promise of count
     */
    Promise<Long> countEntities(String tenantId, String collectionName, String filterExpression);

    /**
     * Bulk creates entities.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param entities       list of entities
     * @return promise of created entities
     */
    Promise<List<EntityInterface>> bulkCreateEntities(String tenantId, String collectionName,
            List<EntityInterface> entities);

    /**
     * Bulk deletes entities.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param entityIds      list of entity IDs
     * @return promise of deleted count
     */
    Promise<Long> bulkDeleteEntities(String tenantId, String collectionName, List<UUID> entityIds);

    // ============ Event Operations ============

    /**
     * Appends an event to a stream.
     * 
     * @param tenantId   tenant identifier
     * @param streamName stream name
     * @param event      event to append
     * @return promise of event offset
     */
    Promise<Long> appendEvent(String tenantId, String streamName, DataRecord event);

    /**
     * Reads events from a stream.
     * 
     * @param tenantId   tenant identifier
     * @param streamName stream name
     * @param fromOffset starting offset
     * @param limit      maximum number of events
     * @return promise of event list
     */
    Promise<List<DataRecord>> readEvents(String tenantId, String streamName,
            long fromOffset, int limit);

    /**
     * Reads events by time range.
     * 
     * @param tenantId   tenant identifier
     * @param streamName stream name
     * @param startTime  start time (ISO-8601)
     * @param endTime    end time (ISO-8601)
     * @return promise of event list
     */
    Promise<List<DataRecord>> readEventsByTimeRange(String tenantId, String streamName,
            String startTime, String endTime);

    /**
     * Gets the latest offset in a stream.
     * 
     * @param tenantId   tenant identifier
     * @param streamName stream name
     * @return promise of latest offset
     */
    Promise<Long> getLatestOffset(String tenantId, String streamName);

    /**
     * Counts events in a stream.
     * 
     * @param tenantId   tenant identifier
     * @param streamName stream name
     * @return promise of event count
     */
    Promise<Long> countEvents(String tenantId, String streamName);

    // ============ Search Operations ============

    /**
     * Searches entities.
     * 
     * @param tenantId tenant identifier
     * @param query    search query
     * @return promise of search results
     */
    Promise<SearchResults> search(String tenantId, SearchQuery query);

    /**
     * Performs full-text search.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param queryText      search text
     * @param limit          result limit
     * @return promise of search results
     */
    Promise<SearchResults> fullTextSearch(String tenantId, String collectionName,
            String queryText, int limit);

    /**
     * Gets search facets.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param fieldName      field to facet on
     * @return promise of facet values
     */
    Promise<Map<String, Long>> getFacets(String tenantId, String collectionName, String fieldName);

    // ============ Analytics Operations ============

    /**
     * Gets quality metrics for a collection.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @return promise of quality metrics
     */
    Promise<QualityMetrics> getQualityMetrics(String tenantId, String collectionName);

    /**
     * Gets cost analysis.
     * 
     * @param tenantId tenant identifier
     * @param daysBack number of days to analyze
     * @return promise of cost analysis
     */
    Promise<CostAnalysis> getCostAnalysis(String tenantId, int daysBack);

    /**
     * Gets data lineage.
     * 
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @return promise of lineage graph
     */
    Promise<LineageGraph> getLineage(String tenantId, String collectionName);

    // ============ AI Operations ============

    /**
     * Processes a record through AI aspects.
     * 
     * @param tenantId tenant identifier
     * @param record   data record
     * @return promise of processing result
     */
    Promise<AIProcessingResult> processWithAI(String tenantId, DataRecord record);

    /**
     * Gets AI model info.
     * 
     * @param tenantId  tenant identifier
     * @param modelName model name
     * @return promise of model info
     */
    Promise<AIModelInfo> getAIModelInfo(String tenantId, String modelName);

    /**
     * Gets feature values for entity.
     * 
     * @param tenantId     tenant identifier
     * @param entityId     entity ID
     * @param featureNames list of feature names
     * @return promise of feature values
     */
    Promise<Map<String, Double>> getFeatures(String tenantId, String entityId,
            List<String> featureNames);

    // ============ Health & Status ============

    /**
     * Health check.
     * 
     * @return promise of health status
     */
    Promise<HealthStatus> healthCheck();

    /**
     * Gets system metrics.
     * 
     * @return promise of metrics
     */
    Promise<SystemMetrics> getMetrics();

    /**
     * Closes the client and releases resources.
     */
    void close();

    // ============ Data Types ============

    /**
     * Search query specification.
     */
    interface SearchQuery {
        String getQuery();

        String getCollectionName();

        int getLimit();

        int getOffset();

        Map<String, String> getFilters();

        static Builder builder() {
            return new SearchQueryBuilder();
        }

        interface Builder {
            Builder query(String query);

            Builder collectionName(String name);

            Builder limit(int limit);

            Builder offset(int offset);

            Builder filter(String key, String value);

            SearchQuery build();
        }
    }

    /**
     * Search results.
     */
    interface SearchResults {
        List<EntityInterface> getHits();

        long getTotalHits();

        Map<String, Map<String, Long>> getFacets();

        long getExecutionTimeMs();
    }

    /**
     * Quality metrics.
     */
    interface QualityMetrics {
        double getCompletenessScore();

        double getAccuracyScore();

        double getConsistencyScore();

        double getTimelinessScore();

        long getRecordCount();
    }

    /**
     * Cost analysis.
     */
    interface CostAnalysis {
        double getTotalCostUSD();

        Map<String, Double> getCostByTier();

        double getCostPerGB();

        long getStorageGB();
    }

    /**
     * Lineage graph.
     */
    interface LineageGraph {
        List<String> getUpstream();

        List<String> getDownstream();

        Map<String, String> getDependencies();
    }

    /**
     * AI processing result.
     */
    interface AIProcessingResult {
        DataRecord getRecord();

        List<AIAspectResult> getAspectResults();

        long getProcessingTimeMs();
    }

    /**
     * AI aspect result.
     */
    interface AIAspectResult {
        String getAspectName();

        String getAspectType();

        Map<String, Object> getOutput();

        double getConfidence();
    }

    /**
     * AI model info.
     */
    interface AIModelInfo {
        String getName();

        String getVersion();

        String getFramework();

        String getDeploymentStatus();

        long getLastUpdatedMs();
    }

    /**
     * Health status.
     */
    interface HealthStatus {
        boolean isHealthy();

        Map<String, ComponentStatus> getComponents();

        String getMessage();
    }

    /**
     * Component status.
     */
    interface ComponentStatus {
        String getName();

        boolean isHealthy();

        String getStatus();
    }

    /**
     * System metrics.
     */
    interface SystemMetrics {
        long getRequestCount();

        double getAverageLatencyMs();

        double getErrorRate();

        Map<String, Long> getMetricsByOperation();
    }
}
