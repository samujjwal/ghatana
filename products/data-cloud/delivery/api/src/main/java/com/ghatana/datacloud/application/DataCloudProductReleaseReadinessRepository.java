package com.ghatana.datacloud.application;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Data Cloud entity-store backed repository for product release readiness records.
 *
 * @doc.type class
 * @doc.purpose Persist product release readiness as tenant-scoped Data Cloud records
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DataCloudProductReleaseReadinessRepository
        implements ProductReleaseReadinessService.ProductReleaseReadinessRepository {

    public static final String COLLECTION = "product_release_readiness";

    private final DataCloudClient client;

    public DataCloudProductReleaseReadinessRepository(DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public Promise<ProductReleaseReadinessService.ProductReleaseReadiness> upsert(
            ProductReleaseReadinessService.ProductReleaseReadiness readiness) {
        Map<String, Object> data = toData(readiness);
        String id = readiness.id() == null || readiness.id().isBlank()
            ? stableId(readiness.productId(), readiness.productVersion(), readiness.releaseTarget())
            : readiness.id();
        data.put("id", id);
        return client.save(readiness.tenantId(), COLLECTION, data).map(this::fromEntity);
    }

    @Override
    public Promise<Optional<ProductReleaseReadinessService.ProductReleaseReadiness>> findByProductVersionAndTarget(
            String productId,
            String productVersion,
            String releaseTarget,
            String tenantId) {
        return client.query(
                tenantId,
                COLLECTION,
                DataCloudClient.Query.builder()
                    .filters(List.of(
                        DataCloudClient.Filter.eq("productId", productId),
                        DataCloudClient.Filter.eq("productVersion", productVersion),
                        DataCloudClient.Filter.eq("releaseTarget", releaseTarget)
                    ))
                    .limit(1)
                    .build())
            .map(records -> records.stream().findFirst().map(this::fromEntity));
    }

    @Override
    public Promise<List<ProductReleaseReadinessService.ProductReleaseReadiness>> findByProductId(
            String productId,
            String tenantId) {
        return client.query(
                tenantId,
                COLLECTION,
                DataCloudClient.Query.builder()
                    .filter(DataCloudClient.Filter.eq("productId", productId))
                    .limit(500)
                    .build())
            .map(records -> records.stream().map(this::fromEntity).toList());
    }

    @Override
    public Promise<List<ProductReleaseReadinessService.ProductReleaseReadiness>> findByTenant(String tenantId) {
        return client.query(tenantId, COLLECTION, DataCloudClient.Query.limit(500))
            .map(records -> records.stream().map(this::fromEntity).toList());
    }

    @Override
    public Promise<Void> deleteById(String id, String tenantId) {
        return client.delete(tenantId, COLLECTION, id);
    }

    private ProductReleaseReadinessService.ProductReleaseReadiness fromEntity(DataCloudClient.Entity entity) {
        Map<String, Object> data = entity.data();
        return ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .id(entity.id())
            .productId(string(data.get("productId")))
            .productVersion(string(data.get("productVersion")))
            .releaseTarget(string(data.get("releaseTarget")))
            .releaseVerdict(string(data.get("releaseVerdict")))
            .averageScore(doubleOrNull(data.get("averageScore")))
            .releaseTargetScore(doubleOrNull(data.get("releaseTargetScore")))
            .generatedAt(instant(data.get("generatedAt"), entity.createdAt()))
            .evidence(map(data.get("evidence")))
            .blockingGaps(listOfMaps(data.get("blockingGaps")))
            .belowTargetDimensions(listOfMaps(data.get("belowTargetDimensions")))
            .tenantId(string(data.get("tenantId")))
            .commitSha(string(data.get("commitSha")))
            .evidenceEnvironment(string(data.get("evidenceEnvironment")))
            .createdAt(entity.createdAt())
            .updatedAt(entity.updatedAt())
            .build();
    }

    private static Map<String, Object> toData(ProductReleaseReadinessService.ProductReleaseReadiness readiness) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", readiness.productId());
        data.put("productVersion", readiness.productVersion());
        data.put("releaseTarget", readiness.releaseTarget());
        data.put("releaseVerdict", readiness.releaseVerdict());
        data.put("averageScore", readiness.averageScore());
        data.put("releaseTargetScore", readiness.releaseTargetScore());
        data.put("generatedAt", readiness.generatedAt().toString());
        data.put("evidence", readiness.evidence());
        data.put("blockingGaps", readiness.blockingGaps());
        data.put("belowTargetDimensions", readiness.belowTargetDimensions());
        data.put("tenantId", readiness.tenantId());
        data.put("commitSha", readiness.commitSha());
        data.put("evidenceEnvironment", readiness.evidenceEnvironment());
        data.put("createdAt", readiness.createdAt().toString());
        data.put("updatedAt", Instant.now().toString());
        return data;
    }

    private static String stableId(String productId, String productVersion, String releaseTarget) {
        return (productId + ":" + productVersion + ":" + releaseTarget)
            .replaceAll("[^A-Za-z0-9_.:-]", "-");
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Double doubleOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.valueOf(String.valueOf(value));
    }

    private static Instant instant(Object value, Instant fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Instant.parse(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        return value instanceof List<?> source ? (List<Map<String, Object>>) source : List.of();
    }
}
