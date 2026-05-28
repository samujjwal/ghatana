package com.ghatana.yappc.services.evolve;

import com.ghatana.yappc.kernel.ProductUnitIntentExporter;
import com.ghatana.yappc.services.kernel.KernelProductUnitHandoffService;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Builds validated Kernel ProductUnitIntent change requests for Evolve proposals
 * @doc.layer service
 * @doc.pattern Adapter
 */
public final class KernelProductUnitEvolutionUpdateService implements EvolutionKernelUpdateService {

    private final KernelProductUnitHandoffService handoffService;

    public KernelProductUnitEvolutionUpdateService(@NotNull KernelProductUnitHandoffService handoffService) {
        this.handoffService = Objects.requireNonNull(handoffService, "handoffService must not be null");
    }

    @Override
    public Promise<EvolutionKernelUpdate> prepareUpdate(@NotNull EvolutionKernelUpdateRequest request) {
        Map<String, String> planMetadata = request.plan().metadata() == null ? Map.of() : request.plan().metadata();
        if (!Boolean.parseBoolean(planMetadata.getOrDefault("kernelGoverned", "false"))) {
            return Promise.of(EvolutionKernelUpdate.notApplicable("Evolution plan is not marked kernelGoverned"));
        }

        String workspaceId = planMetadata.get("workspaceId");
        String projectName = planMetadata.get("projectName");
        List<String> surfaces = splitCsv(planMetadata.get("surfaces"));
        String sourceProvider = planMetadata.get("sourceProvider");
        if (isBlank(workspaceId) || isBlank(projectName) || surfaces.isEmpty() || isBlank(sourceProvider)) {
            return Promise.of(EvolutionKernelUpdate.blocked(
                "Kernel-governed evolution requires workspaceId, projectName, surfaces, and sourceProvider metadata"));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("changeRequestType", "evolution-update");
        metadata.put("evolutionPlanId", request.plan().id());
        metadata.put("sourceInsightsRef", request.plan().insightsRef());
        metadata.put("impactAnalysis", request.impactAnalysis().toMetadata());
        metadata.put("kernelGoverned", true);

        try {
            KernelProductUnitHandoffService.HandoffResult result = handoffService.generate(
                    new KernelProductUnitHandoffService.HandoffRequest(
                            request.tenantId(),
                            workspaceId,
                            request.projectId(),
                            projectName,
                            surfaces,
                            defaultIfBlank(planMetadata.get("runtimeProvider"), "ghatana-file-registry"),
                            sourceProvider,
                            defaultIfBlank(planMetadata.get("lifecycleProfile"), "standard-web-api-product"),
                            "evolve",
                            metadata,
                            planMetadata.get("correlationId")));
            return Promise.of(new EvolutionKernelUpdate(
                    "GENERATED",
                    result.intentId(),
                    result.productUnitIntent(),
                    Map.of(
                            "status", "GENERATED",
                            "productUnitIntentRef", result.intentId(),
                            "valid", result.valid(),
                            "sourcePhase", "evolve",
                            "changeRequestType", "evolution-update")));
        } catch (ProductUnitIntentExporter.ExportException exception) {
            return Promise.of(EvolutionKernelUpdate.blocked(exception.getMessage()));
        }
    }

    private static List<String> splitCsv(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
