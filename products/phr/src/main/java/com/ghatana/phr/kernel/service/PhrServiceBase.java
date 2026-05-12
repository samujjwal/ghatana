package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.phr.kernel.service.PhrTraceContext;
import com.ghatana.platform.database.ProductDataServiceBase;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PHR-specific data service base class.
 *
 * <p>Extends the platform ProductDataServiceBase to provide PHR-specific
 * metadata enrichment and owner scope inference strategies.</p>
 *
 * @doc.type class
 * @doc.purpose PHR data service base class — extends platform base with PHR-specific strategies
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public abstract class PhrServiceBase extends ProductDataServiceBase {

    protected PhrServiceBase(KernelContext context) {
        super(context);
    }

    protected PhrServiceBase(KernelContext context, Executor executor) {
        super(context, executor, new PhrMetadataEnrichmentStrategy(), new PhrOwnerScopeStrategy());
    }

    /**
     * PHR-specific metadata enrichment strategy.
     * Adds PHR-specific trace context metadata.
     */
    private static class PhrMetadataEnrichmentStrategy implements MetadataEnrichmentStrategy {
        @Override
        public void enrich(Map<String, String> metadata, String action, String recordId, String entityType) {
            // Add PHR-specific trace context
            String normalizedEntityType = normalizeToken(entityType, "record");
            String normalizedAction = normalizeToken(action, "write");
            String operation = "phr_" + normalizedEntityType + "_" + normalizedAction;
            
            String correlationId = metadata.getOrDefault("correlationId", PhrTraceContext.newCorrelationId(operation));
            Map<String, String> traceMetadata = PhrTraceContext.metadata(correlationId, operation, metadata);
            
            metadata.putAll(traceMetadata);
        }

        private String normalizeToken(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * PHR-specific owner scope strategy.
     * Prioritizes healthcare-specific owner fields.
     */
    private static class PhrOwnerScopeStrategy implements OwnerScopeStrategy {
        @Override
        public String inferOwnerScope(Map<String, String> metadata, String entityType, String recordId) {
            return firstPresent(metadata, "patientId", "subjectId", "encounterId", "providerId")
                .map(value -> entityType + ":" + value)
                .orElse(entityType + ":" + recordId);
        }

        private Optional<String> firstPresent(Map<String, String> metadata, String... keys) {
            for (String key : keys) {
                String value = metadata.get(key);
                if (value != null && !value.isBlank()) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }
}
