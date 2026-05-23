package com.ghatana.yappc.kernel;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed ProductUnitIntent handoff contract exposed by YAPPC to Kernel consumers.
 *
 * @param schemaVersion contract schema version
 * @param candidateId YAPPC candidate identifier
 * @param productUnitId intended Kernel ProductUnit identifier
 * @param source source adapter name
 * @param registryProvider public Kernel registry provider name
 * @param surfaces requested product surfaces
 * @param capabilities requested product capabilities
 * @param metadata additional governed handoff metadata
 * @param createdAt creation timestamp
 *
 * @doc.type record
 * @doc.purpose Typed ProductUnitIntent handoff payload for bounded YAPPC-to-Kernel integration
 * @doc.layer adapter
 * @doc.pattern Contract
 */
public record ProductUnitIntentContract(
        String schemaVersion,
        String candidateId,
        String productUnitId,
        String source,
        String registryProvider,
        List<String> surfaces,
        List<String> capabilities,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public ProductUnitIntentContract {
        requireNonBlank(schemaVersion, "schemaVersion");
        requireNonBlank(candidateId, "candidateId");
        requireNonBlank(productUnitId, "productUnitId");
        requireNonBlank(source, "source");
        requireNonBlank(registryProvider, "registryProvider");
        surfaces = surfaces == null ? List.of() : List.copyOf(surfaces);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    /**
     * Converts the typed contract to a map for compatibility with older plugin call sites.
     */
    public Map<String, Object> toMap() {
        return Map.of(
                "schemaVersion", schemaVersion,
                "kind", "product-unit-intent",
                "candidateId", candidateId,
                "productUnitId", productUnitId,
                "source", source,
                "registryProvider", registryProvider,
                "surfaces", surfaces,
                "capabilities", capabilities,
                "metadata", metadata,
                "createdAt", createdAt.toString());
    }

    /**
     * Builds a typed contract from a request map while keeping Kernel registry mutation out of YAPPC.
     */
    public static ProductUnitIntentContract fromRequest(String candidateId, Map<String, Object> request, String source) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        String productUnitId = stringValue(safeRequest.get("productUnitId"), candidateId);
        String registryProvider = stringValue(safeRequest.get("registryProvider"), "kernel-product-registry");
        return new ProductUnitIntentContract(
                "1.0.0",
                Objects.requireNonNull(candidateId, "candidateId cannot be null"),
                productUnitId,
                source,
                registryProvider,
                stringList(safeRequest.get("surfaces")),
                stringList(safeRequest.get("capabilities")),
                safeRequest,
                Instant.now());
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (Object entry : iterable) {
            if (entry instanceof String text && !text.isBlank()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }
}
