package com.ghatana.finance.kernel.service;

import com.ghatana.finance.service.FinanceTraceContext;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.database.MutationMetadataEnricher;
import com.ghatana.platform.database.ProductDataServiceBase;

import java.util.Map;
import java.util.Objects;
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

    private static final MutationMetadataEnricher METADATA_ENRICHER = MutationMetadataEnricher.builder("finance")
        .keyStyle(MutationMetadataEnricher.MetadataKeyStyle.SNAKE_CASE)
        .correlationIdFactory(operation -> FinanceTraceContext.newCorrelationId())
        .traceMetadataProvider(FinanceServiceBase::traceMetadata)
        .build();

    protected FinanceServiceBase(KernelContext context) {
        super(context, java.util.concurrent.ForkJoinPool.commonPool(), METADATA_ENRICHER, ownerScopeStrategy());
    }

    protected FinanceServiceBase(KernelContext context, Executor executor) {
        super(context, executor, METADATA_ENRICHER, ownerScopeStrategy());
    }

    private static Map<String, ?> traceMetadata(
            String correlationId,
            String operation,
            Map<String, String> metadata) {
        return FinanceTraceContext.metadata(correlationId, operation,
            metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> Objects.toString(entry.getValue(), "")
                )));
    }

    private static OwnerScopeStrategy ownerScopeStrategy() {
        return MutationMetadataEnricher.ownerScopeStrategy(
            "accountId",
            "traderId",
            "clientId",
            "debitAccount",
            "creditAccount"
        );
    }
}
