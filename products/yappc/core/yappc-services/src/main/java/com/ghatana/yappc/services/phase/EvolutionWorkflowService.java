package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Evolve-phase workflow service that resolves durable proposal state.
 *
 * @doc.type class
 * @doc.purpose Resolves latest evolution proposal workflow state for phase packet rendering
 * @doc.layer service
 * @doc.pattern Service
 */
public final class EvolutionWorkflowService {

    private static final String COLLECTION = "yappc_evolution_proposals";

    private final DataCloudClient dataCloudClient;
    private final boolean available;

    public EvolutionWorkflowService(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = dataCloudClient;
        this.available = true;
    }

    public static EvolutionWorkflowService unavailable() {
        return new EvolutionWorkflowService();
    }

    private EvolutionWorkflowService() {
        this.dataCloudClient = null;
        this.available = false;
    }

    public Promise<EvolutionWorkflowState> resolveLatest(
            String tenantId,
            String workspaceId,
            String projectId,
            EvolutionWorkflowState fallback
    ) {
        if (!available) {
            return Promise.of(fallback);
        }
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filters(List.of(
                        DataCloudClient.Filter.eq("projectId", projectId)
                ))
                .sorts(List.of(DataCloudClient.Sort.desc("createdAt")))
                .limit(1)
                .build();
        return dataCloudClient.query(tenantId, COLLECTION, query)
                .map(entities -> {
                    if (entities.isEmpty()) {
                        return fallback;
                    }
                    Map<String, Object> data = entities.get(0).data();
                    return new EvolutionWorkflowState(
                            asString(data.get("proposalId"), asString(data.get("id"), "proposal-unavailable")),
                            asString(data.get("sourceObservationRef"), fallback.proposal()),
                            asString(data.get("impactAnalysis"), fallback.impactSummary()),
                            asString(data.get("planId"), fallback.diffSummary()),
                            fallback.validationRequirements(),
                            asString(data.get("approvalState"), fallback.approvalState()),
                            fallback.rollbackPath(),
                            fallback.rerunTarget()
                    );
                });
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
