/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.RecordType;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageBackendType;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.datacloud.entity.storage.StorageProfile;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * S3-compatible object storage {@link StorageConnector} for BLOB-tier entity persistence.
 *
 * <p>Stores entities as JSON objects in AWS S3 or any S3-compatible backend (MinIO,
 * LocalStack, Ceph RGW with virtual-hosted style). Each entity is stored at:
 * <pre>{@code s3://{bucket}/{keyPrefix}{tenantId}/{collectionName}/{entityId}.json}</pre>
 *
 * <p>Unlike {@link CephObjectStorageConnector} which hardcodes Ceph path-style access,
 * this connector honours the {@code pathStyleAccess} flag in {@link BlobStorageConnectorConfig},
 * making it usable against both AWS S3 (virtual-hosted) and MinIO/LocalStack (path-style).
 *
 * <p>All blocking S3 SDK calls are wrapped in {@code Promise.ofBlocking} with a
 * dedicated virtual-thread executor so the ActiveJ event loop is never stalled.
 *
 * <p><b>Multi-tenancy</b>: tenant isolation is achieved through the object key prefix
 * {@code {keyPrefix}{tenantId}/}, so one S3 bucket may safely host multiple tenants.
 *
 * @doc.type class
 * @doc.purpose S3/MinIO StorageConnector for BLOB-tier entity CRUD using AWS SDK v2
 * @doc.layer product
 * @doc.pattern Adapter, StorageConnector
 */
public class BlobStorageConnector implements StorageConnector {

    private static final Logger log = LoggerFactory.getLogger(BlobStorageConnector.class);
    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final S3Client s3;
    private final S3Presigner presigner;
    private final BlobStorageConnectorConfig config;
    private final Executor executor;
    private final Duration presignedUrlExpiry;

    // Metrics
    private final Counter createOps;
    private final Counter createErrors;
    private final Counter readOps;
    private final Counter readMisses;
    private final Counter updateOps;
    private final Counter deleteOps;
    private final Counter deleteErrors;
    private final Counter bulkCreateOps;
    private final Timer createLatency;
    private final Timer readLatency;
    private final Timer queryLatency;

    /**
     * Create with an explicit {@link MeterRegistry}.
     * Uses a virtual-thread-per-task executor internally.
     *
     * @param config        connector configuration
     * @param meterRegistry registry for operational metrics
     */
    public BlobStorageConnector(BlobStorageConnectorConfig config, MeterRegistry meterRegistry) {
        this(config, meterRegistry, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Create with a {@link SimpleMeterRegistry} — suitable for testing and embedded use.
     *
     * @param config connector configuration
     */
    public BlobStorageConnector(BlobStorageConnectorConfig config) {
        this(config, new SimpleMeterRegistry());
    }

    /**
     * Full constructor for DI and testing with a custom executor.
     *
     * @param config        connector configuration
     * @param meterRegistry metrics registry
     * @param executor      executor for blocking S3 calls
     */
    public BlobStorageConnector(BlobStorageConnectorConfig config,
                                 MeterRegistry meterRegistry,
                                 Executor executor) {
        this.config          = Objects.requireNonNull(config, "config");
        this.executor        = Objects.requireNonNull(executor, "executor");
        this.presignedUrlExpiry = config.getPresignedUrlExpiry() != null
                ? config.getPresignedUrlExpiry() : Duration.ofHours(1);

        var clientBuilder = S3Client.builder()
                .region(Region.of(config.getRegion() != null ? config.getRegion() : "us-east-1"))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyleAccess())
                        .build());

        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(Region.of(config.getRegion() != null ? config.getRegion() : "us-east-1"));

        if (config.getEndpoint() != null) {
            clientBuilder.endpointOverride(config.getEndpoint());
            presignerBuilder.endpointOverride(config.getEndpoint());
        }

        if (config.getAccessKeyId() != null && !config.getAccessKeyId().isBlank()) {
            var creds = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey()));
            clientBuilder.credentialsProvider(creds);
            presignerBuilder.credentialsProvider(creds);
        } else {
            clientBuilder.credentialsProvider(DefaultCredentialsProvider.create());
            presignerBuilder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        this.s3 = clientBuilder.build();
        this.presigner = presignerBuilder.build();

        ensureBucketExists();

        // --- Metrics ---
        String[] tags = {"connector", "s3"};
        this.createOps     = meterRegistry.counter("blob.entity.create.total",         tags);
        this.createErrors  = meterRegistry.counter("blob.entity.create.error.total",   tags);
        this.readOps       = meterRegistry.counter("blob.entity.read.total",           tags);
        this.readMisses    = meterRegistry.counter("blob.entity.read.miss.total",      tags);
        this.updateOps     = meterRegistry.counter("blob.entity.update.total",         tags);
        this.deleteOps     = meterRegistry.counter("blob.entity.delete.total",         tags);
        this.deleteErrors  = meterRegistry.counter("blob.entity.delete.error.total",   tags);
        this.bulkCreateOps = meterRegistry.counter("blob.entity.bulk.create.total",    tags);
        this.createLatency = meterRegistry.timer("blob.entity.create.duration",        tags);
        this.readLatency   = meterRegistry.timer("blob.entity.read.duration",          tags);
        this.queryLatency  = meterRegistry.timer("blob.entity.query.duration",         tags);
    }

    // ==================== StorageConnector Implementation ====================

    @Override
    public Promise<Entity> create(Entity entity) {
        // Assign a new UUID if the caller did not supply one (mirrors JPA @PrePersist behaviour)
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            try {
                String key   = objectKey(entity.getTenantId(), entity.getCollectionName(), entity.getId().toString());
                byte[] bytes = serializeEntity(entity);
                putObject(key, bytes);
                createOps.increment();
                log.debug("BlobConnector: created key={}", key);
                return entity;
            } catch (Exception e) {
                createErrors.increment();
                log.error("BlobConnector: create failed entity={}", entity.getId(), e);
                throw new RuntimeException("S3 create failed: " + e.getMessage(), e);
            } finally {
                sample.stop(createLatency);
            }
        });
    }

    @Override
    public Promise<Optional<Entity>> read(UUID collectionId, String tenantId, UUID entityId) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            try {
                // Object key uses collection name as path segment. Since we only have collectionId here,
                // list under tenantId prefix and find the object whose key ends with this entityId.
                String prefix = keyPrefix() + tenantId + "/";
                Optional<Entity> found = listAndFind(prefix, entityId.toString());
                readOps.increment();
                if (found.isEmpty()) readMisses.increment();
                return found;
            } catch (NoSuchKeyException e) {
                readMisses.increment();
                return Optional.empty();
            } finally {
                sample.stop(readLatency);
            }
        });
    }

    /**
     * Read by explicit collection name (preferred — avoids listing).
     *
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param entityId       entity UUID
     * @return Promise of Optional entity
     */
    public Promise<Optional<Entity>> readByName(String tenantId, String collectionName, UUID entityId) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            try {
                String key = objectKey(tenantId, collectionName, entityId.toString());
                GetObjectRequest req = GetObjectRequest.builder()
                        .bucket(config.getBucketName()).key(key).build();
                byte[] bytes = s3.getObjectAsBytes(req).asByteArray();
                Entity entity = deserializeEntityFromKey(key, bytes);
                readOps.increment();
                return Optional.of(entity);
            } catch (NoSuchKeyException e) {
                readMisses.increment();
                return Optional.empty();
            } finally {
                sample.stop(readLatency);
            }
        });
    }

    @Override
    public Promise<Entity> update(Entity entity) {
        return Promise.ofBlocking(executor, () -> {
            String key   = objectKey(entity.getTenantId(), entity.getCollectionName(), entity.getId().toString());
            byte[] bytes = serializeEntity(entity);
            putObject(key, bytes);
            updateOps.increment();
            log.debug("BlobConnector: updated key={}", key);
            return entity;
        });
    }

    @Override
    public Promise<Void> delete(UUID collectionId, String tenantId, UUID entityId) {
        return Promise.ofBlocking(executor, () -> {
            // Resolve key via listing  (collectionId→name is unknown here)
            String prefix = keyPrefix() + tenantId + "/";
            listObjectsWithPrefix(prefix).stream()
                    .filter(o -> o.key().endsWith("/" + entityId + ".json"))
                    .findFirst()
                    .ifPresentOrElse(
                            o -> {
                                s3.deleteObject(DeleteObjectRequest.builder()
                                        .bucket(config.getBucketName()).key(o.key()).build());
                                deleteOps.increment();
                                log.debug("BlobConnector: deleted key={}", o.key());
                            },
                            () -> {
                                deleteErrors.increment();
                                log.warn("BlobConnector: delete miss entity={}", entityId);
                            });
            return null;
        });
    }

    /**
     * Delete by explicit collection name (preferred — avoids listing).
     *
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param entityId       entity UUID
     * @return Promise&lt;Void&gt; on success
     */
    public Promise<Void> deleteByName(String tenantId, String collectionName, UUID entityId) {
        return Promise.ofBlocking(executor, () -> {
            String key = objectKey(tenantId, collectionName, entityId.toString());
            try {
                s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(config.getBucketName()).key(key).build());
                deleteOps.increment();
                log.debug("BlobConnector: deleted key={}", key);
            } catch (SdkException e) {
                deleteErrors.increment();
                log.warn("BlobConnector: delete error key={}: {}", key, e.getMessage());
            }
            return null;
        });
    }

    @Override
    public Promise<QueryResult> query(UUID collectionId, String tenantId, QuerySpec spec) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            try {
                // Use collectionId as prefix segment (UUID string)
                String prefix = keyPrefix() + tenantId + "/" + collectionId + "/";
                List<Entity> all = loadAllEntities(prefix, tenantId, collectionId.toString());
                return paginateResults(all, spec.getOffset(), spec.getLimit());
            } finally {
                sample.stop(queryLatency);
            }
        });
    }

    @Override
    public Promise<List<Entity>> scan(UUID collectionId, String tenantId,
                                       String filterExpression, int limit, int offset) {
        return Promise.ofBlocking(executor, () -> {
            String prefix = keyPrefix() + tenantId + "/" + collectionId + "/";
            List<Entity> all = loadAllEntities(prefix, tenantId, collectionId.toString());
            int from = Math.min(offset, all.size());
            int to   = Math.min(offset + (limit > 0 ? limit : all.size()), all.size());
            return all.subList(from, to);
        });
    }

    /**
     * Scan by collection name — avoids building a synthetic UUID prefix.
     *
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param limit          maximum results (0 = all)
     * @param offset         starting offset
     * @return Promise of entities
     */
    public Promise<List<Entity>> scanByName(String tenantId, String collectionName, int limit, int offset) {
        return Promise.ofBlocking(executor, () -> {
            String prefix = objectKey(tenantId, collectionName, "");
            List<Entity> all = loadAllEntities(prefix, tenantId, collectionName);
            int from = Math.min(offset, all.size());
            int to   = Math.min(offset + (limit > 0 ? limit : all.size()), all.size());
            return all.subList(from, to);
        });
    }

    @Override
    public Promise<Long> count(UUID collectionId, String tenantId, String filterExpression) {
        return Promise.ofBlocking(executor, () -> {
            String prefix = keyPrefix() + tenantId + "/" + collectionId + "/";
            long count = 0;
            String token = null;
            do {
                ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                        .bucket(config.getBucketName()).prefix(prefix).maxKeys(1_000);
                if (token != null) req.continuationToken(token);
                ListObjectsV2Response resp = s3.listObjectsV2(req.build());
                count += resp.keyCount();
                token = resp.isTruncated() ? resp.nextContinuationToken() : null;
            } while (token != null);
            return count;
        });
    }

    @Override
    public Promise<List<Entity>> bulkCreate(UUID collectionId, String tenantId, List<Entity> entities) {
        return Promise.ofBlocking(executor, () -> {
            List<Entity> created = new ArrayList<>(entities.size());
            for (Entity e : entities) {
                if (e.getId() == null) e.setId(UUID.randomUUID());
                String key = objectKey(e.getTenantId(), e.getCollectionName(), e.getId().toString());
                putObject(key, serializeEntity(e));
                created.add(e);
            }
            bulkCreateOps.increment(entities.size());
            log.debug("BlobConnector: bulk-created {} entities", entities.size());
            return created;
        });
    }

    @Override
    public Promise<List<Entity>> bulkUpdate(UUID collectionId, String tenantId, List<Entity> entities) {
        // S3 PUT is idempotent — reuse bulkCreate
        return bulkCreate(collectionId, tenantId, entities);
    }

    @Override
    public Promise<Long> bulkDelete(UUID collectionId, String tenantId, List<UUID> entityIds) {
        return Promise.ofBlocking(executor, () -> {
            if (entityIds.isEmpty()) return 0L;
            List<ObjectIdentifier> toDelete = entityIds.stream()
                    .map(id -> ObjectIdentifier.builder()
                            .key(keyPrefix() + tenantId + "/" + collectionId + "/" + id + ".json")
                            .build())
                    .toList();
            DeleteObjectsRequest req = DeleteObjectsRequest.builder()
                    .bucket(config.getBucketName())
                    .delete(Delete.builder().objects(toDelete).quiet(true).build())
                    .build();
            DeleteObjectsResponse resp = s3.deleteObjects(req);
            long deleted = (long) entityIds.size() - resp.errors().size();
            deleteOps.increment(deleted);
            log.debug("BlobConnector: bulk-deleted {}/{} entities", deleted, entityIds.size());
            return deleted;
        });
    }

    @Override
    public Promise<Long> truncate(UUID collectionId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String prefix = keyPrefix() + tenantId + "/" + collectionId + "/";
            long deleted = 0;
            String token = null;
            do {
                ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                        .bucket(config.getBucketName()).prefix(prefix).maxKeys(1_000);
                if (token != null) req.continuationToken(token);
                ListObjectsV2Response page = s3.listObjectsV2(req.build());
                if (!page.contents().isEmpty()) {
                    List<ObjectIdentifier> keys = page.contents().stream()
                            .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                            .toList();
                    s3.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(config.getBucketName())
                            .delete(Delete.builder().objects(keys).quiet(true).build())
                            .build());
                    deleted += keys.size();
                }
                token = page.isTruncated() ? page.nextContinuationToken() : null;
            } while (token != null);
            log.info("BlobConnector: truncated collection={} tenant={} count={}", collectionId, tenantId, deleted);
            return deleted;
        });
    }

    @Override
    public Promise<Void> healthCheck() {
        return Promise.ofBlocking(executor, () -> {
            s3.headBucket(HeadBucketRequest.builder().bucket(config.getBucketName()).build());
            return null;
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
                .maxBatchSize(1_000)
                .build();
    }

    // ==================== Presigned URL Support ====================

    /**
     * Generates a pre-signed GET URL for direct client download of an entity.
     *
     * <p>The URL expires after the duration configured in
     * {@link BlobStorageConnectorConfig#getPresignedUrlExpiry()}.
     *
     * @param tenantId       tenant identifier
     * @param collectionName collection name
     * @param entityId       entity UUID
     * @return pre-signed URL valid for the configured duration
     */
    public URL presignedGetUrl(String tenantId, String collectionName, UUID entityId) {
        String key = objectKey(tenantId, collectionName, entityId.toString());
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(presignedUrlExpiry)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(config.getBucketName()).key(key).build())
                .build();
        return presigner.presignGetObject(req).url();
    }

    // ==================== Private Helpers ====================

    private String keyPrefix() {
        String p = config.getKeyPrefix();
        return (p == null || p.isBlank()) ? "" : (p.endsWith("/") ? p : p + "/");
    }

    private String objectKey(String tenantId, String collectionName, String entityId) {
        return keyPrefix() + tenantId + "/" + collectionName + "/" + entityId + (entityId.isEmpty() ? "" : ".json");
    }

    private void putObject(String key, byte[] bytes) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(config.getBucketName())
                        .key(key)
                        .contentType(CONTENT_TYPE_JSON)
                        .contentLength((long) bytes.length)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    private byte[] serializeEntity(Entity entity) throws IOException {
        // Store only the data map; entity identity is derived from the object key path.
        // This mirrors the CephObjectStorageConnector approach and avoids coupling to
        // the exact EntityRecord getter API (Boolean vs boolean, etc.).
        return MAPPER.writeValueAsBytes(entity.getData());
    }

    @SuppressWarnings("unchecked")
    private Entity deserializeEntityFromKey(String key, byte[] bytes) throws IOException {
        // Key format: {prefix}{tenant}/{collection}/{entityId}.json
        String strippedPrefix = key.startsWith(keyPrefix()) ? key.substring(keyPrefix().length()) : key;
        String[] parts       = strippedPrefix.split("/", 3);
        String tenant        = parts.length > 0 ? parts[0] : "unknown";
        String collection    = parts.length > 1 ? parts[1] : "unknown";
        String entityFile    = parts.length > 2 ? parts[2] : "unknown.json";
        String entityIdStr   = entityFile.replace(".json", "");

        UUID id;
        try {
            id = UUID.fromString(entityIdStr);
        } catch (IllegalArgumentException e) {
            id = UUID.nameUUIDFromBytes(entityIdStr.getBytes(StandardCharsets.UTF_8));
        }

        Map<String, Object> data = MAPPER.readValue(bytes, Map.class);
        return Entity.builder()
                .id(id)
                .tenantId(tenant)
                .collectionName(collection)
                .recordType(RecordType.ENTITY)
                .data(data)
                .build();
    }

    private Optional<Entity> listAndFind(String prefix, String entityIdStr) {
        for (S3Object obj : listObjectsWithPrefix(prefix)) {
            if (obj.key().endsWith("/" + entityIdStr + ".json")) {
                try {
                    byte[] bytes = s3.getObjectAsBytes(
                            GetObjectRequest.builder().bucket(config.getBucketName()).key(obj.key()).build()
                    ).asByteArray();
                    return Optional.of(deserializeEntityFromKey(obj.key(), bytes));
                } catch (Exception e) {
                    log.warn("BlobConnector: failed to deserialize key={}: {}", obj.key(), e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    private List<Entity> loadAllEntities(String prefix, String fallbackTenant, String fallbackCollection) {
        List<Entity> results = new ArrayList<>();
        for (S3Object obj : listObjectsWithPrefix(prefix)) {
            if (!obj.key().endsWith(".json")) continue;
            try {
                byte[] bytes = s3.getObjectAsBytes(
                        GetObjectRequest.builder().bucket(config.getBucketName()).key(obj.key()).build()
                ).asByteArray();
                results.add(deserializeEntityFromKey(obj.key(), bytes));
            } catch (Exception e) {
                log.warn("BlobConnector: skipping unreadable key={}: {}", obj.key(), e.getMessage());
            }
        }
        return results;
    }

    private List<S3Object> listObjectsWithPrefix(String prefix) {
        List<S3Object> objects = new ArrayList<>();
        String token = null;
        do {
            ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                    .bucket(config.getBucketName()).prefix(prefix).maxKeys(1_000);
            if (token != null) req.continuationToken(token);
            ListObjectsV2Response page = s3.listObjectsV2(req.build());
            objects.addAll(page.contents());
            token = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (token != null);
        return objects;
    }

    private QueryResult paginateResults(List<Entity> all, int offset, int limit) {
        int total  = all.size();
        int from   = Math.min(offset, total);
        int to     = Math.min(from + (limit > 0 ? limit : total), total);
        return new QueryResult(all.subList(from, to), total, limit, offset, 0L);
    }

    private void ensureBucketExists() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(config.getBucketName()).build());
        } catch (NoSuchBucketException e) {
            log.info("BlobConnector: creating bucket '{}'", config.getBucketName());
            s3.createBucket(CreateBucketRequest.builder().bucket(config.getBucketName()).build());
            s3.waiter().waitUntilBucketExists(
                    HeadBucketRequest.builder().bucket(config.getBucketName()).build());
            log.info("BlobConnector: bucket '{}' ready", config.getBucketName());
        } catch (SdkException e) {
            log.warn("BlobConnector: could not verify bucket '{}': {}", config.getBucketName(), e.getMessage());
        }
    }
}
