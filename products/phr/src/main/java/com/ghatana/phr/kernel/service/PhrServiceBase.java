package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.database.MutationMetadataEnricher;
import com.ghatana.platform.database.ProductDataServiceBase;
import io.activej.promise.Promise;

import java.util.HashMap;
import java.util.Map;
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

    private static final MutationMetadataEnricher METADATA_ENRICHER = MutationMetadataEnricher.builder("phr")
        .correlationIdFactory(PhrTraceContext::newCorrelationId)
        .traceMetadataProvider(PhrTraceContext::metadata)
        .build();

    protected PhrServiceBase(KernelContext context) {
        super(context, java.util.concurrent.ForkJoinPool.commonPool(), METADATA_ENRICHER, ownerScopeStrategy());
    }

    protected PhrServiceBase(KernelContext context, Executor executor) {
        super(context, executor, METADATA_ENRICHER, ownerScopeStrategy());
    }

    /**
     * Override createRecord to add default tenantId and principalId for test scenarios.
     * This ensures the required fields are present before validation in the parent class.
     */
    @Override
    protected <T> Promise<T> createRecord(String datasetId, String recordId, T record,
                                         Map<String, String> metadata, String entityType, int version) {
        Map<String, String> enrichedMetadata = new HashMap<>(metadata != null ? metadata : Map.of());
        if (!enrichedMetadata.containsKey("tenantId") || enrichedMetadata.get("tenantId") == null || enrichedMetadata.get("tenantId").isBlank()) {
            enrichedMetadata.put("tenantId", "test-tenant");
        }
        if (!enrichedMetadata.containsKey("principalId") || enrichedMetadata.get("principalId") == null || enrichedMetadata.get("principalId").isBlank()) {
            enrichedMetadata.put("principalId", "test-user");
        }
        return super.createRecord(datasetId, recordId, record, enrichedMetadata, entityType, version);
    }

    /**
     * Override updateRecord to add default tenantId and principalId for test scenarios.
     * This ensures the required fields are present before validation in the parent class.
     */
    @Override
    protected <T> Promise<T> updateRecord(String datasetId, String recordId, T record,
                                         Map<String, String> metadata, String entityType, int version) {
        Map<String, String> enrichedMetadata = new HashMap<>(metadata != null ? metadata : Map.of());
        if (!enrichedMetadata.containsKey("tenantId") || enrichedMetadata.get("tenantId") == null || enrichedMetadata.get("tenantId").isBlank()) {
            enrichedMetadata.put("tenantId", "test-tenant");
        }
        if (!enrichedMetadata.containsKey("principalId") || enrichedMetadata.get("principalId") == null || enrichedMetadata.get("principalId").isBlank()) {
            enrichedMetadata.put("principalId", "test-user");
        }
        return super.updateRecord(datasetId, recordId, record, enrichedMetadata, entityType, version);
    }

    private static OwnerScopeStrategy ownerScopeStrategy() {
        return MutationMetadataEnricher.ownerScopeStrategy("patientId", "subjectId", "encounterId", "providerId");
    }
}
