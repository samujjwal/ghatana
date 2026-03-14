package com.ghatana.datacloud.infrastructure.storage;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageBackendType;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.datacloud.entity.storage.StorageProfile;
import com.ghatana.datacloud.infrastructure.storage.EntityDocumentMapper;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.CountResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * OpenSearch full-text search StorageConnector.
 *
 * <p>Stores entities as OpenSearch documents indexed in a per-tenant index
 * named {@code datacloud-{tenantId}}. The collection name is stored as a
 * metadata field ({@code _dc_collection_name}) so cross-collection queries are
 * handled via field filtering.
 *
 * <p>Uses the Apache 2.0 licensed {@code org.opensearch.client:opensearch-java}
 * typed client (2.x). All blocking REST calls are wrapped with
 * {@link io.activej.promise.Promise#ofBlocking} to keep the ActiveJ event loop
 * non-blocking.
 *
 * <p>Capabilities:
 * <ul>
 *   <li>Full-text search via {@code query_string} over filter expressions</li>
 *   <li>Schemaless JSON entity storage (dynamic mapping)</li>
 *   <li>Pagination via from/size</li>
 *   <li>Bulk create/update/delete</li>
 *   <li>Health check via cluster health API</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose OpenSearch StorageConnector for SEARCH-tier entity storage
 * @doc.layer product
 * @doc.pattern Adapter, StorageConnector
 */
public class OpenSearchConnector implements StorageConnector {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchConnector.class);

    // Metadata field names — canonical definitions live in EntityDocumentMapper
    private static final String FIELD_TENANT_ID       = EntityDocumentMapper.FIELD_TENANT_ID;
    private static final String FIELD_COLLECTION_NAME = EntityDocumentMapper.FIELD_COLLECTION_NAME;
    private static final String FIELD_ENTITY_ID       = EntityDocumentMapper.FIELD_ENTITY_ID;

    private final OpenSearchClient client;
    private final RestClient restClient;
    private final Executor executor;

    // Track indices already confirmed to exist so we skip repeated exists() calls
    private final Set<String> confirmedIndices = ConcurrentHashMap.newKeySet();

    private final Counter indexCounter;
    private final Counter indexErrorCounter;
    private final Counter readCounter;
    private final Counter deleteCounter;
    private final Timer   indexTimer;
    private final Timer   searchTimer;

    /**
     * Convenience constructor with a {@link SimpleMeterRegistry} — suitable for
     * testing and standalone usage.
     */
    public OpenSearchConnector(OpenSearchConfig config) {
        this(config, new SimpleMeterRegistry());
    }

    /**
     * Constructor with MeterRegistry; uses a virtual-thread-per-task executor.
     */
    public OpenSearchConnector(OpenSearchConfig config, MeterRegistry meterRegistry) {
        this(config, meterRegistry, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Full constructor for production use with custom executor.
     */
    public OpenSearchConnector(OpenSearchConfig config, MeterRegistry meterRegistry, Executor executor) {
        this.executor = executor;
        this.restClient = buildRestClient(config);
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.client = new OpenSearchClient(transport);

        this.indexCounter      = meterRegistry.counter("opensearch.entity.index.total");
        this.indexErrorCounter = meterRegistry.counter("opensearch.entity.index.error.total");
        this.readCounter       = meterRegistry.counter("opensearch.entity.read.total");
        this.deleteCounter     = meterRegistry.counter("opensearch.entity.delete.total");
        this.indexTimer        = meterRegistry.timer("opensearch.entity.index.duration");
        this.searchTimer       = meterRegistry.timer("opensearch.entity.search.duration");
    }

    // =========================================================================
    //  StorageConnector implementation
    // =========================================================================

    @Override
    public Promise<Entity> create(Entity entity) {
        // Assign an ID if the caller didn't supply one (mirrors JPA @PrePersist)
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            try {
                String index = tenantIndex(entity.getTenantId());
                ensureIndex(index);
                Map<String, Object> source = docFrom(entity);
                client.index(IndexRequest.of(req -> req
                        .index(index)
                        .id(entity.getId().toString())
                        .document(source)));
                indexCounter.increment();
                log.debug("OpenSearch: indexed entity {} in {}", entity.getId(), index);
                return entity;
            } catch (IOException e) {
                indexErrorCounter.increment();
                log.error("OpenSearch: index failed for entity {}", entity.getId(), e);
                throw new RuntimeException("OpenSearch index failed", e);
            } finally {
                sample.stop(indexTimer);
            }
        });
    }

    @Override
    public Promise<Optional<Entity>> read(UUID collectionId, String tenantId, UUID entityId) {
        return Promise.ofBlocking(executor, () -> {
            readCounter.increment();
            String index = tenantIndex(tenantId);
            try {
                @SuppressWarnings("unchecked")
                GetResponse<Map> resp = client.get(
                        GetRequest.of(req -> req.index(index).id(entityId.toString())),
                        (Class<Map>) (Class<?>) Map.class);
                if (!resp.found() || resp.source() == null) return Optional.empty();
                return Optional.of(entityFrom(resp.source()));
            } catch (Exception e) {
                log.warn("OpenSearch: read failed for entity {}: {}", entityId, e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public Promise<Entity> update(Entity entity) {
        // OpenSearch IndexRequest performs upsert semantics by document ID
        return create(entity);
    }

    @Override
    public Promise<Void> delete(UUID collectionId, String tenantId, UUID entityId) {
        return Promise.ofBlocking(executor, () -> {
            String index = tenantIndex(tenantId);
            client.delete(DeleteRequest.of(req -> req.index(index).id(entityId.toString())));
            deleteCounter.increment();
            return null;
        });
    }

    @Override
    public Promise<QueryResult> query(UUID collectionId, String tenantId, QuerySpec spec) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            try {
                String index = tenantIndex(tenantId);
                int limit  = spec.getLimit()  > 0 ? spec.getLimit()  : 1_000;
                int offset = spec.getOffset() > 0 ? spec.getOffset() : 0;
                Query query = buildQuery(tenantId, spec);

                @SuppressWarnings("unchecked")
                SearchResponse<Map> resp = client.search(
                        SearchRequest.of(req -> req
                                .index(index)
                                .from(offset)
                                .size(limit)
                                .query(query)),
                        (Class<Map>) (Class<?>) Map.class);

                List<Entity> entities = new ArrayList<>();
                for (Hit<Map> hit : resp.hits().hits()) {
                    if (hit.source() != null) entities.add(entityFrom(hit.source()));
                }
                long total = resp.hits().total() != null ? resp.hits().total().value() : entities.size();
                long duration = sample.stop(searchTimer);
                return new QueryResult(entities, total, limit, offset, duration);
            } catch (IOException e) {
                log.error("OpenSearch: query failed for tenant {}", tenantId, e);
                return QueryResult.empty();
            }
        });
    }

    @Override
    public Promise<List<Entity>> scan(UUID collectionId, String tenantId,
                                      String filterExpression, int limit, int offset) {
        return Promise.ofBlocking(executor, () -> {
            int effectiveLimit  = limit  > 0 ? limit  : 1_000;
            int effectiveOffset = offset > 0 ? offset : 0;
            String index = tenantIndex(tenantId);
            Query matchTenant  = Query.of(q -> q.term(t -> t.field(FIELD_TENANT_ID).value(FieldValue.of(tenantId))));

            @SuppressWarnings("unchecked")
            SearchResponse<Map> resp = client.search(
                    SearchRequest.of(req -> req
                            .index(index)
                            .from(effectiveOffset)
                            .size(effectiveLimit)
                            .query(matchTenant)),
                    (Class<Map>) (Class<?>) Map.class);

            List<Entity> results = new ArrayList<>();
            for (Hit<Map> hit : resp.hits().hits()) {
                if (hit.source() != null) results.add(entityFrom(hit.source()));
            }
            return results;
        });
    }

    @Override
    public Promise<Long> count(UUID collectionId, String tenantId, String filterExpression) {
        return Promise.ofBlocking(executor, () -> {
            String index = tenantIndex(tenantId);
            Query tenantFilter = Query.of(q -> q.term(t -> t.field(FIELD_TENANT_ID).value(FieldValue.of(tenantId))));
            CountResponse resp = client.count(
                    CountRequest.of(req -> req.index(index).query(tenantFilter)));
            return resp.count();
        });
    }

    @Override
    public Promise<List<Entity>> bulkCreate(UUID collectionId, String tenantId, List<Entity> entities) {
        return Promise.ofBlocking(executor, () -> {
            if (entities.isEmpty()) return entities;
            String index = tenantIndex(tenantId);
            ensureIndex(index);

            List<BulkOperation> ops = new ArrayList<>();
            for (Entity e : entities) {
                if (e.getId() == null) e.setId(UUID.randomUUID());
                Map<String, Object> source = docFrom(e);
                final String docId = e.getId().toString();
                ops.add(BulkOperation.of(op -> op.index(idx -> idx
                        .index(index)
                        .id(docId)
                        .document(source))));
            }
            BulkResponse resp = client.bulk(BulkRequest.of(req -> req.operations(ops)));
            if (resp.errors()) {
                log.warn("OpenSearch: bulkCreate had partial failures for tenant {}", tenantId);
            }
            indexCounter.increment(entities.size());
            return entities;
        });
    }

    @Override
    public Promise<List<Entity>> bulkUpdate(UUID collectionId, String tenantId, List<Entity> entities) {
        return bulkCreate(collectionId, tenantId, entities);
    }

    @Override
    public Promise<Long> bulkDelete(UUID collectionId, String tenantId, List<UUID> entityIds) {
        return Promise.ofBlocking(executor, () -> {
            if (entityIds.isEmpty()) return 0L;
            String index = tenantIndex(tenantId);
            List<BulkOperation> ops = new ArrayList<>();
            for (UUID id : entityIds) {
                final String docId = id.toString();
                ops.add(BulkOperation.of(op -> op.delete(d -> d.index(index).id(docId))));
            }
            client.bulk(BulkRequest.of(req -> req.operations(ops)));
            deleteCounter.increment(entityIds.size());
            return (long) entityIds.size();
        });
    }

    @Override
    public Promise<Long> truncate(UUID collectionId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            // Count then delete-all using a match-all query via scroll is heavy;
            // For simplicity, delete the tenant index and recreate it.
            String index = tenantIndex(tenantId);
            long counted = 0L;
            try {
                CountResponse cResp = client.count(
                        CountRequest.of(req -> req.index(index)
                                .query(Query.of(q -> q.matchAll(m -> m)))));
                counted = cResp.count();
                client.indices().delete(d -> d.index(index));
                confirmedIndices.remove(index);
                log.info("OpenSearch: truncated tenant index '{}' ({} docs)", index, counted);
            } catch (Exception e) {
                log.error("OpenSearch: truncate failed for tenant {}", tenantId, e);
            }
            return counted;
        });
    }

    @Override
    public ConnectorMetadata getMetadata() {
        return ConnectorMetadata.builder()
                .backendType(StorageBackendType.SEARCH)
                .latencyClass(StorageProfile.LatencyClass.FAST)
                .supportsTransactions(false)
                .supportsTimeSeries(false)
                .supportsFullText(true)
                .supportsSchemaless(true)
                .maxBatchSize(10_000)
                .build();
    }

    @Override
    public Promise<Void> healthCheck() {
        return Promise.ofBlocking(executor, () -> {
            client.cluster().health(req -> req);
            return null;
        });
    }

    // =========================================================================
    //  Internal helpers
    // =========================================================================

    /** One index per tenant — simplifies ID-based lookups and deletes. */
    private static String tenantIndex(String tenantId) {
        return ("datacloud-" + tenantId).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    /**
     * Build the OpenSearch document source map from an Entity.
     * Delegates to {@link EntityDocumentMapper#toDocument} so that metadata
     * field injection is consistent across all storage connectors.
     */
    private static Map<String, Object> docFrom(Entity entity) {
        return EntityDocumentMapper.toDocument(entity);
    }

    /**
     * Reconstruct an Entity from an OpenSearch source map.
     * Delegates to {@link EntityDocumentMapper#fromDocument} for consistent
     * metadata-stripping and null-safe UUID parsing across all connectors.
     */
    private static Entity entityFrom(Map<?, ?> source) {
        return EntityDocumentMapper.fromDocument(source);
    }

    /**
     * Build OpenSearch query from QuerySpec.
     * Uses {@code query_string} when a filter expression is present,
     * falling back to {@code match_all} for unfiltered scans.
     */
    private static Query buildQuery(String tenantId, QuerySpec spec) {
        Query tenantFilter = Query.of(q -> q.term(t -> t.field(FIELD_TENANT_ID).value(FieldValue.of(tenantId))));

        if (!spec.hasFilters()) {
            return tenantFilter;
        }
        String filterExpr = spec.getFilter().orElse("");
        Query userFilter = Query.of(q -> q.queryString(qs -> qs.query(filterExpr)));
        return Query.of(q -> q.bool(b -> b.must(tenantFilter).must(userFilter)));
    }

    /**
     * Ensure that the tenant index exists. Creates it with sane defaults if
     * it doesn't. Results are cached in {@link #confirmedIndices} to avoid
     * repeated exists() round-trips.
     */
    private void ensureIndex(String index) throws IOException {
        if (confirmedIndices.contains(index)) return;
        boolean exists = client.indices().exists(req -> req.index(index)).value();
        if (!exists) {
            // Use 0 replicas so the index is GREEN on single-node clusters (1 replica
            // can't be assigned to the same node and would leave the index YELLOW).
            // Map _dc_* metadata fields as keyword so term queries work without the
            // .keyword sub-field suffix — avoids dynamic text-tokenization issues.
            client.indices().create(CreateIndexRequest.of(req -> req
                    .index(index)
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0"))
                    .mappings(m -> m
                            .properties(FIELD_TENANT_ID,       p -> p.keyword(k -> k))
                            .properties(FIELD_COLLECTION_NAME, p -> p.keyword(k -> k))
                            .properties(FIELD_ENTITY_ID,       p -> p.keyword(k -> k)))));
            log.info("OpenSearch: created index '{}'", index);
        }
        confirmedIndices.add(index);
    }

    /** Build the low-level RestClient with optional Basic-Auth support. */
    private static RestClient buildRestClient(OpenSearchConfig config) {
        var builder = RestClient.builder(new HttpHost(config.host(), config.port(), config.scheme()));
        if (config.username() != null && !config.username().isBlank()) {
            BasicCredentialsProvider creds = new BasicCredentialsProvider();
            creds.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(config.username(), config.password()));
            builder.setHttpClientConfigCallback(cb -> cb.setDefaultCredentialsProvider(creds));
        }
        return builder.build();
    }

    /** Close the underlying REST client on shutdown. */
    public void close() {
        try {
            restClient.close();
        } catch (IOException e) {
            log.warn("OpenSearch: error closing RestClient", e);
        }
    }
}
