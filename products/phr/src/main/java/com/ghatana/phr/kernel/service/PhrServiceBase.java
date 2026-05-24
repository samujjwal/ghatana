package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.platform.database.MutationMetadataEnricher;
import com.ghatana.platform.database.ProductDataServiceBase;

import java.util.HashMap;
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

    private static final MutationMetadataEnricher METADATA_ENRICHER = MutationMetadataEnricher.builder("phr")
        .correlationIdFactory(PhrTraceContext::newCorrelationId)
        .traceMetadataProvider(PhrTraceContext::metadata)
        .build();
    private final KernelContext kernelContext;

    protected PhrServiceBase(KernelContext context) {
        super(context, java.util.concurrent.ForkJoinPool.commonPool(), METADATA_ENRICHER, ownerScopeStrategy());
        this.kernelContext = context;
    }

    protected PhrServiceBase(KernelContext context, Executor executor) {
        super(context, executor, METADATA_ENRICHER, ownerScopeStrategy());
        this.kernelContext = context;
    }

    protected Map<String, String> mutationMetadata(Map<String, String> baseMetadata, String principalId) {
        HashMap<String, String> enriched = new HashMap<>(baseMetadata == null ? Map.of() : baseMetadata);
        enriched.put("tenantId", currentTenantId());
        enriched.put("principalId", currentPrincipalId(principalId));
        return Map.copyOf(enriched);
    }

    protected String currentTenantId() {
        Optional<String> tenantId = Optional.ofNullable(kernelContext.getTenantContext())
            .map(KernelTenantContext::getTenantId)
            .filter(value -> !value.isBlank());
        String resolved = tenantId.orElseGet(() -> {
            if (isProductionEnvironment()) {
                throw new IllegalStateException("tenantId is required in KernelTenantContext for PHR mutations");
            }
            return "default";
        });
        return PhrInputSanitizationUtils.requireSafeIdentifier(resolved, "tenantId");
    }

    protected String currentPrincipalId(String fallbackPrincipalId) {
        Optional<String> principalId = Optional.ofNullable(kernelContext.getTenantContext())
            .map(KernelTenantContext::getSecurityContext)
            .map(KernelTenantContext.SecurityContext::getUserId)
            .filter(value -> !value.isBlank());
        String resolved = principalId.orElse(fallbackPrincipalId == null || fallbackPrincipalId.isBlank()
            ? "system"
            : fallbackPrincipalId);
        return PhrInputSanitizationUtils.requireSafeIdentifier(resolved, "principalId");
    }

    private boolean isProductionEnvironment() {
        String environment = kernelContext.getEnvironment();
        return environment != null
            && ("prod".equalsIgnoreCase(environment) || "production".equalsIgnoreCase(environment));
    }

    private static OwnerScopeStrategy ownerScopeStrategy() {
        return MutationMetadataEnricher.ownerScopeStrategy("patientId", "subjectId", "encounterId", "providerId");
    }
}
