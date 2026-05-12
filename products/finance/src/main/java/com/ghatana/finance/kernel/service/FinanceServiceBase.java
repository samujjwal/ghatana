package com.ghatana.finance.kernel.service;

import com.ghatana.finance.service.FinanceTraceContext;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.database.ProductDataServiceBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Finance-specific data service base class.
 *
 * <p>Extends the platform ProductDataServiceBase to provide Finance-specific
 * metadata enrichment and owner scope inference strategies.</p>
 *
 * @doc.type class
 * @doc.purpose Finance data service base class — extends platform base with Finance-specific strategies
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public abstract class FinanceServiceBase extends ProductDataServiceBase {

    protected FinanceServiceBase(KernelContext context) {
        super(context);
    }

    protected FinanceServiceBase(KernelContext context, Executor executor) {
        super(context, executor, new FinanceMetadataEnrichmentStrategy(), new FinanceOwnerScopeStrategy());
    }

    /**
     * Finance-specific metadata enrichment strategy.
     * Adds Finance-specific trace context metadata with underscore naming convention.
     */
    private static class FinanceMetadataEnrichmentStrategy implements MetadataEnrichmentStrategy {
        @Override
        public void enrich(Map<String, String> metadata, String action, String recordId, String entityType) {
            // Add Finance-specific trace context with underscore naming
            String normalizedEntityType = normalizeToken(entityType, "record");
            String normalizedAction = normalizeToken(action, "write");
            String operation = "finance_" + normalizedEntityType + "_" + normalizedAction;
            
            String correlationId = Objects.toString(
                metadata.getOrDefault("correlationId", FinanceTraceContext.newCorrelationId()),
                FinanceTraceContext.newCorrelationId()
            );
            
            Map<String, String> traceMetadata = new java.util.HashMap<>();
            Map<String, Object> rawMetadata = FinanceTraceContext.metadata(correlationId, operation, 
                metadata.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Objects.toString(entry.getValue(), "")
                    )));
            rawMetadata.forEach((key, value) -> traceMetadata.put(key, Objects.toString(value, "")));
            
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
                .toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Finance-specific owner scope strategy.
     * Prioritizes finance-specific owner fields with underscore naming.
     */
    private static class FinanceOwnerScopeStrategy implements OwnerScopeStrategy {
        @Override
        public String inferOwnerScope(Map<String, String> metadata, String entityType, String recordId) {
            return firstPresent(metadata, "accountId", "traderId", "clientId", "debitAccount", "creditAccount")
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
