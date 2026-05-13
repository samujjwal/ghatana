package com.ghatana.platform.database;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Kernel-owned mutation metadata enrichment primitive for product data services.
 *
 * @doc.type class
 * @doc.purpose Shared mutation metadata enrichment and owner-scope helpers for ProductDataServiceBase consumers
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public final class MutationMetadataEnricher implements ProductDataServiceBase.MetadataEnrichmentStrategy {

    public enum MetadataKeyStyle {
        CAMEL_CASE,
        SNAKE_CASE
    }

    @FunctionalInterface
    public interface TraceMetadataProvider {
        Map<String, ?> metadata(String correlationId, String operation, Map<String, String> existingMetadata);
    }

    private final String productPrefix;
    private final MetadataKeyStyle keyStyle;
    private final Function<String, String> correlationIdFactory;
    private final TraceMetadataProvider traceMetadataProvider;

    private MutationMetadataEnricher(Builder builder) {
        this.productPrefix = requireNonBlank(builder.productPrefix, "productPrefix");
        this.keyStyle = Objects.requireNonNull(builder.keyStyle, "keyStyle must not be null");
        this.correlationIdFactory = Objects.requireNonNull(
            builder.correlationIdFactory,
            "correlationIdFactory must not be null"
        );
        this.traceMetadataProvider = Objects.requireNonNull(
            builder.traceMetadataProvider,
            "traceMetadataProvider must not be null"
        );
    }

    public static Builder builder(String productPrefix) {
        return new Builder(productPrefix);
    }

    @Override
    public void enrich(Map<String, String> metadata, String action, String recordId, String entityType) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        String operation = operationName(productPrefix, entityType, action);
        String correlationId = firstNonBlank(metadata.get(correlationKey()), metadata.get("correlationId"))
            .orElseGet(() -> correlationIdFactory.apply(operation));

        Map<String, String> existing = Map.copyOf(metadata);
        Map<String, ?> traceMetadata = traceMetadataProvider.metadata(correlationId, operation, existing);
        traceMetadata.forEach((key, value) -> metadata.put(key, Objects.toString(value, "")));
    }

    public String operationName(String entityType, String action) {
        return operationName(productPrefix, entityType, action);
    }

    public ProductDataServiceBase.OwnerScopeStrategy ownerScopeStrategy(List<String> ownerKeys) {
        return ownerScopeStrategy(ownerKeys.toArray(String[]::new));
    }

    public static ProductDataServiceBase.OwnerScopeStrategy ownerScopeStrategy(String... ownerKeys) {
        String[] keys = Objects.requireNonNull(ownerKeys, "ownerKeys must not be null").clone();
        return (metadata, entityType, recordId) -> firstPresent(metadata, keys)
            .map(value -> entityType + ":" + value)
            .orElse(entityType + ":" + recordId);
    }

    public static String normalizeToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value
            .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
            .replaceAll("[^A-Za-z0-9]+", "-")
            .replaceAll("^-+|-+$", "")
            .toLowerCase(Locale.ROOT);
    }

    private String operationName(String prefix, String entityType, String action) {
        return prefix + "_" + normalizeToken(entityType, "record") + "_" + normalizeToken(action, "write");
    }

    private String correlationKey() {
        return switch (keyStyle) {
            case CAMEL_CASE -> "correlationId";
            case SNAKE_CASE -> "correlation_id";
        };
    }

    private static Optional<String> firstPresent(Map<String, String> metadata, String... keys) {
        for (String key : keys) {
            Optional<String> value = firstNonBlank(metadata.get(key));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public static final class Builder {
        private final String productPrefix;
        private MetadataKeyStyle keyStyle = MetadataKeyStyle.CAMEL_CASE;
        private Function<String, String> correlationIdFactory =
            operation -> operation + "-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        private TraceMetadataProvider traceMetadataProvider = (correlationId, operation, existingMetadata) -> {
            Map<String, String> trace = new HashMap<>();
            trace.put("correlationId", correlationId);
            trace.put("operation", operation);
            return trace;
        };

        private Builder(String productPrefix) {
            this.productPrefix = productPrefix;
        }

        public Builder keyStyle(MetadataKeyStyle keyStyle) {
            this.keyStyle = keyStyle;
            return this;
        }

        public Builder correlationIdFactory(Function<String, String> correlationIdFactory) {
            this.correlationIdFactory = correlationIdFactory;
            return this;
        }

        public Builder traceMetadataProvider(TraceMetadataProvider traceMetadataProvider) {
            this.traceMetadataProvider = traceMetadataProvider;
            return this;
        }

        public MutationMetadataEnricher build() {
            return new MutationMetadataEnricher(this);
        }
    }
}
