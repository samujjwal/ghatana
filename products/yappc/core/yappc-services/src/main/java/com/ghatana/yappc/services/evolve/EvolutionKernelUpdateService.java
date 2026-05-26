package com.ghatana.yappc.services.evolve;

import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @doc.type interface
 * @doc.purpose Produces Kernel-compatible update requests for Kernel-governed Evolve proposals
 * @doc.layer service
 * @doc.pattern Service Port
 */
public interface EvolutionKernelUpdateService {

    Promise<EvolutionKernelUpdate> prepareUpdate(@NotNull EvolutionKernelUpdateRequest request);

    static EvolutionKernelUpdateService unavailable() {
        return request -> Promise.of(EvolutionKernelUpdate.notApplicable("Kernel update service is not wired"));
    }

    record EvolutionKernelUpdateRequest(
            @NotNull String tenantId,
            @NotNull String projectId,
            @NotNull EvolutionPlan plan,
            @NotNull EvolutionImpactAnalysis impactAnalysis
    ) {
    }

    record EvolutionKernelUpdate(
            @NotNull String status,
            String productUnitIntentRef,
            Map<String, Object> productUnitIntent,
            Map<String, Object> metadata
    ) {
        public static EvolutionKernelUpdate notApplicable(String reason) {
            return new EvolutionKernelUpdate(
                    "NOT_APPLICABLE",
                    null,
                    Map.of(),
                    Map.of("reason", reason));
        }

        public static EvolutionKernelUpdate blocked(String reason) {
            return new EvolutionKernelUpdate(
                    "BLOCKED",
                    null,
                    Map.of(),
                    Map.of("reason", reason));
        }
    }
}
