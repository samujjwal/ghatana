package com.ghatana.datacloud.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageBackendType;
import com.ghatana.datacloud.entity.storage.StorageProfile;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Ceph RADOS Gateway (RGW) object storage connector.
 *
 * <p>Stores entities as JSON objects in Ceph using the S3-compatible API exposed by
 * the RADOS Gateway (RGW). Each entity is stored at:
 * <pre>{@code s3://{bucket}/{tenantId}/{collectionId}/{entityId}.json}</pre>
 *
 * <p>Uses AWS SDK v2 with {@code pathStyleAccessEnabled=true} which is required by Ceph RGW.
 * All blocking S3 calls are wrapped with {@code Promise.ofBlocking} to avoid blocking
 * the ActiveJ event loop.
 *
 * @doc.type class
 * @doc.purpose Ceph RGW StorageConnector for BLOB-tier entity storage using S3-compatible API
 * @doc.layer product
 * @doc.pattern Adapter, StorageConnector
 */
public class CephObjectStorageConnector implements StorageConnector {

    private static final Logger log = LoggerFactory.getLogger(CephObjectStorageConnector.class);
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final S3Client s3;
    private final String bucket;
    private final Executor executor;
    private final Counter createCounter;
    private final Counter createErrorCounter;
    private final Counter readCounter;
    private final Counter deleteCounter;
    private final Timer createTimer;
    private final Timer readTimer;

    /**
     * Create connector with a explicit MeterRegistry and virtual-thread executor.
     */
    public CephObjectStorageConnector(CephObjectStorageConfig config, MeterRegistry meterRegistry) {
        this(config, meterRegistry, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Create connector with defaults — uses a {@link SimpleMeterRegistry} suitable for testing.
     */
    public CephObjectStorageConnector(CephObjectStorageConfig config) {
        this(config, new SimpleMeterRegistry());
    }

    /**
     * Full constructor.
     */
    public CephObjectStorageConnector(CephObjectStorageConfig config,
                                       MeterRegistry meterRegistry,
                                       Executor executor) {
        this.bucket   = config.bucket();
        this.executor = executor;
        this.s3 = S3Client.builder()
                .endpointOverride(config.endpointUri())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKey(), config.secretKey())))
                .region(Region.of(config.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)   // Ceph RGW requires path-style
                        .build())
                .build();
        ensureBucketExists();

        this.createCounter      = meterRegistry.counter("ceph.entity.create.total");
        this.createErrorCounter = meterRegistry.counter("ceph.entity.create.error.total");
        this.readCounter        = meterRegistry.counter("ceph.entity.read.total");
        this.deleteCounter      = meterRegistry.counter("ceph.entity.delete.total");
        this.createTimer        = meterRegistry.timer("ceph.entity.create.duration");
        this.readTimer          = meterRegistry.timer("ceph.entity.read.duration");
    }

    // ==================== StorageConnector Implementation ====================

    @Override
    public Promise<Entity> create(Entity entity) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            try {
                String key  = objectKey(entity.getTenantId(),
                                        entity.getCollectionName(),
                                        entity.getId().toString());
                byte[] body = MAPPER.writeValueAsBytes(entity.getData());
                s3.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType(CONTENT_TYPE_JSON)
                                .contentLength((long) body.length)
                                .build(),
                        RequestBody.fromBytes(body));
                createCounter.increment();
                log.debug("Ceph: created object {}", key);
                return entity;
            } catch (Exception e) {
                createErrorCounter.increment();
                log.error("Ceph: create failed for entity {}", entity.getId(), e);
                throw new RuntimeException("Ceph create failed", e);
            } finally {
                sample.stop(createTimer);
            }
        });
    }

    @Override
    public Promise<Optional<Entity>> read(UUID collectionId, String tenantId, UUID entityId) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            try {
                // collectionId is stored as namespace; do a prefix-scan to find collectionName
                String prefix = tenantId + "/";
                // Use entityId to locate the object — scan listing for this entityId
                ListObjectsV2Response listing = s3.listObjectsV2(
                        ListObjectsV2Request.builder()
                                .bucket(bucket)
                                .prefix(prefix)
                                .build());

                Optional<S3Object> match = listing.contents().stream()
                        .filter(o -> o.key().endsWith("/" + entityId + ".json"))
                        .findFirst();

                if (match.isEmpty()) {
                    readCounter.increment();
                    return Optional.empty();
                }
                readCounter.increment();
                return Optional.of(fetchEntity(match.get().key()));
            } catch (NoSuchKeyException e) {
                return Optional.empty();
            } finally {
                sample.stop(readTimer);
            }
        });
    }

    @Override
    public Promise<Entity> update(Entity entity) {
        // Overwrite object in-place (S3 PUT is idempotent)
        return create(entity);
    }

    @Override
    public Promise<Void> delete(UUID collectionId, String tenantId, UUID entityId) {
        return Promise.ofBlocking(executor, () -> {
            // We need to find the exact key — use listing to resolve collectionName
            String prefix = tenantId + "/";
            ListObjectsV2Response listing = s3.listObjectsV2(
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .build());
            listing.contents().stream()
                    .filter(o -> o.key().endsWith("/" + entityId + ".json"))
                    .findFirst()
                    .ifPresent(o -> {
                        s3.deleteObject(DeleteObjectRequest.builder()
                                .bucket(bucket).key(o.key()).build());
                        deleteCounter.increment();
                        log.debug("Ceph: deleted object {}", o.key());
                    });
            return null;
        });
    }

    @Override
    public Promise<QueryResult> query(UUID collectionId, String tenantId, QuerySpec spec) {
        return Promise.ofBlocking(executor, () -> {
            String prefix = tenantId + "/" + collectionId + "/";
            List<Entity> results = new ArrayList<>();
            String continuationToken = null;
            do {
                ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .maxKeys(1_000);
                if (continuationToken != null) req.continuationToken(continuationToken);
                ListObjectsV2Response page = s3.listObjectsV2(req.build());
                for (S3Object obj : page.contents()) {
                    results.add(fetchEntity(obj.key()));
                }
                continuationToken = page.isTruncated() ? page.nextContinuationToken() : null;
            } while (continuationToken != null);

            // Apply spec offset/limit (Ceph has no server-side filter for arbitrary JSON)
            int total  = results.size();
            int from   = Math.min(spec.getOffset(), total);
            int to     = Math.min(from + spec.getLimit(), total);
            List<Entity> page = results.subList(from, to);
            return new QueryResult(page, total, spec.getLimit(), spec.getOffset(), 0L);
        });
    }

    @Override
    public Promise<List<Entity>> scan(UUID collectionId, String tenantId,
                                       String filterExpression, int limit, int offset) {
        return Promise.ofBlocking(executor, () -> {
            String prefix = tenantId + "/" + collectionId + "/";
            List<Entity> results = new ArrayList<>();
            ListObjectsV2Response page = s3.listObjectsV2(
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .maxKeys(offset + limit)
                            .build());
            for (int i = offset; i < Math.min(offset + limit, page.contents().size()); i++) {
                results.add(fetchEntity(page.contents().get(i).key()));
            }
            return results;
        });
    }

    @Override
    public Promise<Long> count(UUID collectionId, String tenantId, String filterExpression) {
        return Promise.ofBlocking(executor, () -> {
            String prefix = tenantId + "/" + collectionId + "/";
            long count = 0;
            String continuationToken = null;
            do {
                ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                        .bucket(bucket).prefix(prefix).maxKeys(1_000);
                if (continuationToken != null) req.continuationToken(continuationToken);
                ListObjectsV2Response resp = s3.listObjectsV2(req.build());
                count += resp.keyCount();
                continuationToken = resp.isTruncated() ? resp.nextContinuationToken() : null;
            } while (continuationToken != null);
            return count;
        });
    }

    @Override
    public Promise<List<Entity>> bulkCreate(UUID collectionId, String tenantId, List<Entity> entities) {
        return Promise.ofBlocking(executor, () -> {
            List<Entity> created = new ArrayList<>(entities.size());
            for (Entity e : entities) {
                String key  = objectKey(e.getTenantId(), e.getCollectionName(), e.getId().toString());
                byte[] body = MAPPER.writeValueAsBytes(e.getData());
                s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(key)
                        .contentType(CONTENT_TYPE_JSON).contentLength((long) body.length).build(),
                        RequestBody.fromBytes(body));
                created.add(e);
            }
            return created;
        });
    }

    @Override
    public Promise<List<Entity>> bulkUpdate(UUID collectionId, String tenantId, List<Entity> entities) {
        return bulkCreate(collectionId, tenantId, entities);
    }

    @Override
    public Promise<Long> bulkDelete(UUID collectionId, String tenantId, List<UUID> entityIds) {
        return Promise.ofBlocking(executor, () -> {
            Set<String> ids = new HashSet<>();
            for (UUID id : entityIds) ids.add(id.toString());
            String prefix = tenantId + "/" + collectionId + "/";
            ListObjectsV2Response listing = s3.listObjectsV2(
                    ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build());
            long deleted = 0;
            for (S3Object obj : listing.contents()) {
                String entityIdFromKey = obj.key().substring(obj.key().lastIndexOf('/') + 1)
                        .replace(".json", "");
                if (ids.contains(entityIdFromKey)) {
                    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(obj.key()).build());
                    deleted++;
                }
            }
            return deleted;
        });
    }

    @Override
    public Promise<Long> truncate(UUID collectionId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String prefix = tenantId + "/" + collectionId + "/";
            long deleted = 0;
            String continuationToken = null;
            do {
                ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                        .bucket(bucket).prefix(prefix).maxKeys(1_000);
                if (continuationToken != null) req.continuationToken(continuationToken);
                ListObjectsV2Response resp = s3.listObjectsV2(req.build());
                for (S3Object obj : resp.contents()) {
                    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(obj.key()).build());
                    deleted++;
                }
                continuationToken = resp.isTruncated() ? resp.nextContinuationToken() : null;
            } while (continuationToken != null);
            return deleted;
        });
    }

    @Override
    public ConnectorMetadata getMetadata() {
        return ConnectorMetadata.builder()
                .backendType(StorageBackendType.BLOB)
                .latencyClass(StorageProfile.LatencyClass.BULK)
                .supportsTransactions(false)
                .supportsTimeSeries(false)
                .supportsFullText(false)
                .supportsSchemaless(true)
                .maxBatchSize(10_000)
                .build();
    }

    @Override
    public Promise<Void> healthCheck() {
        return Promise.ofBlocking(executor, () -> {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return null;
        });
    }

    // ==================== Internal Helpers ====================

    private static String objectKey(String tenantId, String collectionName, String entityId) {
        return tenantId + "/" + collectionName + "/" + entityId + ".json";
    }

    @SuppressWarnings("unchecked")
    private Entity fetchEntity(String key) throws IOException {
        byte[] bytes = s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        Map<String, Object> data = MAPPER.readValue(bytes, Map.class);
        // Reconstruct entity from path and data
        String[] parts = key.split("/");
        String tenantId        = parts.length > 0 ? parts[0] : "";
        String collectionName  = parts.length > 1 ? parts[1] : "";
        String entityIdStr     = parts.length > 2 ? parts[2].replace(".json", "") : UUID.randomUUID().toString();
        return Entity.builder()
                .tenantId(tenantId)
                .collectionName(collectionName)
                .data(data)
                .build();
    }

    private void ensureBucketExists() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Ceph: created bucket '{}'", bucket);
        }
    }
}
