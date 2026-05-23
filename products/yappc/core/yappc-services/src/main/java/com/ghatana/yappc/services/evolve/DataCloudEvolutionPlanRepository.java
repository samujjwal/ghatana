package com.ghatana.yappc.services.evolve;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

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
        document.put("metadata", proposal.metadata());
        document.put("createdAt", proposal.createdAt().toString());

        return dataCloudClient.save(proposal.tenantId(), COLLECTION, document).toVoid();
    }
}
