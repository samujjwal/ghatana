package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.database.MutationMetadataEnricher;
import com.ghatana.platform.database.ProductDataServiceBase;

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

    private static OwnerScopeStrategy ownerScopeStrategy() {
        return MutationMetadataEnricher.ownerScopeStrategy("patientId", "subjectId", "encounterId", "providerId");
    }
}
