package com.ghatana.yappc.services.evolve;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Data Cloud-backed evolution proposal repository.
 *
 * @doc.type class
 * @doc.purpose Persist Evolve phase proposals to Data Cloud
 * @doc.layer service
 * @doc.pattern Repository Adapter
 */
public final class DataCloudEvolutionPlanRepository implements EvolutionPlanRepository {

    private static final String COLLECTION = "yappc_evolution_proposals";

    private final DataCloudClient dataCloudClient;

    /**
     * Creates the Data Cloud adapter.
     *
     * @param dataCloudClient Data Cloud client
     */
    public DataCloudEvolutionPlanRepository(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = dataCloudClient;
    }

    @Override
    public Promise<Void> save(@NotNull EvolutionProposal proposal) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", proposal.proposalId());
        document.put("proposalId", proposal.proposalId());
        document.put("tenantId", proposal.tenantId());
        document.put("projectId", proposal.projectId());
        document.put("insightsRef", proposal.insights().id());
        document.put("planId", proposal.plan().id());
        document.put("taskCount", proposal.plan().tasks().size());
        document.put("newIntentRef", proposal.plan().newIntentRef());
        document.put("productUnitIntentRef", proposal.productUnitIntentRef());
        document.put("approvalState", proposal.approvalState());
        document.put("hasConstraints", proposal.constraints() != null);
        document.put("provenance", proposal.provenance());
        document.put("sourceInsightsRef", proposal.metadata().getOrDefault("sourceInsightsRef", proposal.insights().id()));
        document.put("sourceObservationRef", proposal.metadata().getOrDefault("sourceObservationRef", proposal.insights().observationRef()));
        document.put("sourceLearningEvidenceIds", proposal.metadata().getOrDefault("sourceLearningEvidenceIds", java.util.List.of()));
        document.put("impactAnalysis", proposal.metadata().getOrDefault("impactAnalysis", Map.of()));
        document.put("kernelUpdateRequest", proposal.metadata().getOrDefault("kernelUpdateRequest", Map.of()));
        document.put("kernelProductUnitIntent", proposal.metadata().getOrDefault("kernelProductUnitIntent", Map.of()));
        document.put("metadata", proposal.metadata());
        document.put("createdAt", proposal.createdAt().toString());

        return dataCloudClient.save(proposal.tenantId(), COLLECTION, document).toVoid();
    }

    @Override
    public Promise<Optional<EvolutionProposalState>> findProposalState(
            @NotNull String tenantId,
            @NotNull String proposalId
    ) {
        return dataCloudClient.findById(tenantId, COLLECTION, proposalId)
                .map(entityOpt -> entityOpt.map(entity -> toState(entity.data(), proposalId, tenantId)));
    }

    @Override
    public Promise<Void> transitionApprovalState(
            @NotNull String tenantId,
            @NotNull String proposalId,
            @NotNull String approvalState,
            @NotNull String decidedBy,
            @Nullable String decisionReason,
            @NotNull Map<String, Object> transitionMetadata
    ) {
        return dataCloudClient.findById(tenantId, COLLECTION, proposalId)
                .then(entityOpt -> {
                    if (entityOpt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Evolution proposal not found: " + proposalId));
                    }

                    Map<String, Object> document = new LinkedHashMap<>(entityOpt.get().data());
                    document.put("id", proposalId);
                    document.put("proposalId", proposalId);
                    document.put("tenantId", tenantId);
                    document.put("approvalState", approvalState);
                    document.put("decidedBy", decidedBy);
                    document.put("decidedAt", Instant.now().toString());
                    if (decisionReason != null && !decisionReason.isBlank()) {
                        document.put("decisionReason", decisionReason);
                    }
                    document.put("approvalTransition", transitionMetadata);

                    return dataCloudClient.save(tenantId, COLLECTION, document).toVoid();
                });
    }

    private EvolutionProposalState toState(
            Map<String, Object> document,
            String proposalId,
            String tenantId
    ) {
        String projectId = asString(document.get("projectId"), "project-unavailable");
        String approvalState = asString(document.get("approvalState"), "PENDING_APPROVAL");
        String intentRef = asString(document.get("productUnitIntentRef"), "intent-unavailable");
        Instant createdAt = parseInstant(asString(document.get("createdAt"), null));
        Map<String, Object> metadata = asMap(document.get("metadata"));

        return new EvolutionProposalState(
                proposalId,
                tenantId,
                projectId,
                approvalState,
                intentRef,
                metadata,
                createdAt
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String asString(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private static Instant parseInstant(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }
}
