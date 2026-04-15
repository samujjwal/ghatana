package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.plugins.lineage.LineagePlugin;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Publish, discover, and subscribe to Data Cloud data products
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class DataProductHandler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String DATA_PRODUCTS_COLLECTION = "dc_data_products";
    private static final String SUBSCRIPTIONS_COLLECTION = "dc_data_product_subscriptions";
    private static final int SCHEMA_SAMPLE_LIMIT = 50;

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final ObjectMapper objectMapper;
    private final LineagePlugin lineagePlugin;

    public DataProductHandler(DataCloudClient client,
                              HttpHandlerSupport http,
                              ObjectMapper objectMapper,
                              LineagePlugin lineagePlugin) {
        this.client = Objects.requireNonNull(client, "client");
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.lineagePlugin = lineagePlugin;
    }

    public Promise<HttpResponse> handlePublishDataProduct(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String requestId = http.resolveCorrelationId(request);

        return request.loadBody().then(body -> {
            Map<String, Object> payload = parseBody(body.getString(StandardCharsets.UTF_8));
            String collection = stringValue(payload.get("collection"));
            String name = stringValue(payload.get("name"));
            if (collection == null || collection.isBlank()) {
                return Promise.of(http.errorResponse(400, "'collection' is required"));
            }
            if (name == null || name.isBlank()) {
                return Promise.of(http.errorResponse(400, "'name' is required"));
            }

            return client.query(tenantId, collection, DataCloudClient.Query.limit(SCHEMA_SAMPLE_LIMIT))
                .then(samples -> buildLineageSummary(tenantId, collection)
                    .then(lineage -> {
                        ProductQualitySnapshot qualitySnapshot = computeQualitySnapshot(samples);
                        Map<String, Object> descriptor = buildDescriptor(payload, collection, name, tenantId, samples, lineage, qualitySnapshot);
                        return client.save(tenantId, DATA_PRODUCTS_COLLECTION, descriptor)
                            .map(saved -> http.jsonResponse(Map.of(
                                "productId", saved.id(),
                                "collection", collection,
                                "name", name,
                                "descriptor", descriptor,
                                "requestId", requestId,
                                "publishedAt", Instant.now().toString()
                            ), requestId));
                    }));
        });
    }

    public Promise<HttpResponse> handleListDataProducts(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String requestId = http.resolveCorrelationId(request);
        int limit = HttpHandlerSupport.parseIntParam(request.getQueryParameter("limit"), 100);

        return client.query(tenantId, DATA_PRODUCTS_COLLECTION, DataCloudClient.Query.limit(limit))
            .then(products -> Promises.toList(products.stream()
                .map(product -> enrichProductForRead(tenantId, product))
                .toList()))
            .map(items -> http.jsonResponse(Map.of(
                "items", items,
                "count", items.size(),
                "requestId", requestId,
                "timestamp", Instant.now().toString()
            ), requestId));
    }

    public Promise<HttpResponse> handleSubscribe(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String requestId = http.resolveCorrelationId(request);
        String productId = request.getPathParameter("productId");

        return request.loadBody().then(body -> {
            Map<String, Object> payload = parseBody(body.getString(StandardCharsets.UTF_8));
            String consumerId = Optional.ofNullable(stringValue(payload.get("consumerId")))
                .filter(value -> !value.isBlank())
                .orElse(tenantId);

            return client.findById(tenantId, DATA_PRODUCTS_COLLECTION, productId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.of(http.errorResponse(404, "Data product not found: " + productId));
                    }

                    Map<String, Object> data = opt.get().data();
                    if (!isConsumerAllowed(data, consumerId)) {
                        return Promise.of(http.errorResponse(403, "Consumer is not allowed to subscribe to this data product"));
                    }

                    Map<String, Object> subscription = new LinkedHashMap<>();
                    subscription.put("id", UUID.randomUUID().toString());
                    subscription.put("productId", productId);
                    subscription.put("consumerId", consumerId);
                    subscription.put("consumerTenantId", tenantId);
                    subscription.put("status", "ACTIVE");
                    subscription.put("subscribedAt", Instant.now().toString());

                    return client.save(tenantId, SUBSCRIPTIONS_COLLECTION, subscription)
                        .map(saved -> http.jsonResponse(Map.of(
                            "subscriptionId", saved.id(),
                            "productId", productId,
                            "consumerId", consumerId,
                            "status", "ACTIVE",
                            "requestId", requestId
                        ), requestId));
                });
        });
    }

    private Promise<Map<String, Object>> buildLineageSummary(String tenantId, String collection) {
        if (lineagePlugin == null) {
            return Promise.of(Map.of(
                "upstream", List.of(),
                "downstream", List.of(),
                "impactLevel", "UNKNOWN"
            ));
        }

        return lineagePlugin.getUpstreamLineage(tenantId, collection)
            .then(upstream -> lineagePlugin.getDownstreamLineage(tenantId, collection)
                .then(downstream -> lineagePlugin.analyzeImpact(tenantId, collection)
                    .map(impact -> Map.of(
                        "upstream", upstream.stream().sorted().toList(),
                        "downstream", downstream.stream().sorted().toList(),
                        "impactLevel", impact.getImpactLevel()
                    ))));
    }

    private Promise<Map<String, Object>> enrichProductForRead(String tenantId, DataCloudClient.Entity product) {
        Map<String, Object> data = new LinkedHashMap<>(product.data());
        String collection = stringValue(data.get("collection"));
        if (collection == null || collection.isBlank()) {
            return Promise.of(data);
        }

        return client.query(tenantId, collection, DataCloudClient.Query.limit(SCHEMA_SAMPLE_LIMIT))
            .map(samples -> {
                ProductQualitySnapshot qualitySnapshot = computeQualitySnapshot(samples);
                data.put("quality", qualitySnapshot.toMap());
                data.put("qualityStatus", evaluateSlaStatus(mapValue(data.get("sla")), qualitySnapshot));
                return data;
            });
    }

    private Map<String, Object> buildDescriptor(Map<String, Object> payload,
                                                String collection,
                                                String name,
                                                String tenantId,
                                                List<DataCloudClient.Entity> samples,
                                                Map<String, Object> lineage,
                                                ProductQualitySnapshot qualitySnapshot) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("id", Optional.ofNullable(stringValue(payload.get("productId"))).filter(value -> !value.isBlank()).orElse(UUID.randomUUID().toString()));
        descriptor.put("tenantId", tenantId);
        descriptor.put("name", name);
        descriptor.put("collection", collection);
        descriptor.put("description", Optional.ofNullable(stringValue(payload.get("description"))).orElse(""));
        descriptor.put("publishedAt", Instant.now().toString());
        descriptor.put("schema", inferSchema(samples));
        descriptor.put("governance", mapValue(payload.get("governance")));
        descriptor.put("access", normalizeAccess(payload.get("access")));
        descriptor.put("sla", normalizeSla(payload.get("sla")));
        descriptor.put("quality", qualitySnapshot.toMap());
        descriptor.put("qualityStatus", evaluateSlaStatus(normalizeSla(payload.get("sla")), qualitySnapshot));
        descriptor.put("lineage", lineage);
        return descriptor;
    }

    private Map<String, Object> inferSchema(List<DataCloudClient.Entity> samples) {
        Map<String, Set<String>> fieldTypes = new LinkedHashMap<>();
        for (DataCloudClient.Entity entity : samples) {
            for (Map.Entry<String, Object> entry : entity.data().entrySet()) {
                fieldTypes.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>())
                    .add(inferFieldType(entry.getValue()));
            }
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        fieldTypes.forEach((field, types) -> fields.add(Map.of(
            "name", field,
            "types", types.stream().sorted().toList()
        )));
        return Map.of("fields", fields, "sampleCount", samples.size());
    }

    private ProductQualitySnapshot computeQualitySnapshot(List<DataCloudClient.Entity> entities) {
        if (entities.isEmpty()) {
            return new ProductQualitySnapshot(1.0, 0L, 0);
        }

        long totalFields = 0L;
        long nonNullFields = 0L;
        long newestAgeSeconds = Long.MAX_VALUE;
        for (DataCloudClient.Entity entity : entities) {
            totalFields += entity.data().size();
            nonNullFields += entity.data().values().stream().filter(Objects::nonNull).count();
            Instant updatedAt = entity.updatedAt() != null ? entity.updatedAt() : entity.createdAt();
            newestAgeSeconds = Math.min(newestAgeSeconds, Math.max(0L, Duration.between(updatedAt, Instant.now()).getSeconds()));
        }

        double completeness = totalFields == 0L ? 1.0 : (double) nonNullFields / (double) totalFields;
        return new ProductQualitySnapshot(completeness, newestAgeSeconds == Long.MAX_VALUE ? 0L : newestAgeSeconds, entities.size());
    }

    private String evaluateSlaStatus(Map<String, Object> sla, ProductQualitySnapshot qualitySnapshot) {
        if (sla.isEmpty()) {
            return "UNSPECIFIED";
        }
        double completenessTarget = numericValue(sla.get("completenessTarget"), 0.0d);
        long freshnessTargetSeconds = (long) numericValue(sla.get("freshnessSeconds"), Long.MAX_VALUE);
        boolean completenessOk = qualitySnapshot.completeness() >= completenessTarget;
        boolean freshnessOk = qualitySnapshot.freshnessLagSeconds() <= freshnessTargetSeconds;
        return completenessOk && freshnessOk ? "HEALTHY" : "AT_RISK";
    }

    private boolean isConsumerAllowed(Map<String, Object> product, String consumerId) {
        Map<String, Object> access = mapValue(product.get("access"));
        Object allowed = access.get("allowedSubscribers");
        if (!(allowed instanceof List<?> allowedSubscribers) || allowedSubscribers.isEmpty()) {
            return true;
        }
        return allowedSubscribers.stream().map(String::valueOf).anyMatch(consumerId::equals);
    }

    private Map<String, Object> normalizeAccess(Object rawValue) {
        Map<String, Object> access = new LinkedHashMap<>(mapValue(rawValue));
        access.putIfAbsent("visibility", "PRIVATE");
        Object allowed = access.get("allowedSubscribers");
        if (!(allowed instanceof List<?>)) {
            access.put("allowedSubscribers", List.of());
        }
        return access;
    }

    private Map<String, Object> normalizeSla(Object rawValue) {
        Map<String, Object> sla = new LinkedHashMap<>(mapValue(rawValue));
        sla.putIfAbsent("freshnessSeconds", 3600);
        sla.putIfAbsent("completenessTarget", 0.95d);
        sla.putIfAbsent("accuracyTarget", 0.99d);
        return sla;
    }

    private String inferFieldType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        return "string";
    }

    private Map<String, Object> parseBody(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private double numericValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String rawValue) {
            try {
                return Double.parseDouble(rawValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private record ProductQualitySnapshot(double completeness, long freshnessLagSeconds, int sampleSize) {
        private Map<String, Object> toMap() {
            return Map.of(
                "completeness", completeness,
                "freshnessLagSeconds", freshnessLagSeconds,
                "sampleSize", sampleSize,
                "measuredAt", Instant.now().toString()
            );
        }
    }
}