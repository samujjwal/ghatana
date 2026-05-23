package com.ghatana.yappc.services.evolve;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data Cloud-backed lifecycle execution handoff adapter.
 *
 * @doc.type class
 * @doc.purpose Persist approved evolve proposal handoffs for execution workers
 * @doc.layer service
 * @doc.pattern Adapter
 */
public final class DataCloudEvolutionExecutionHandoffService implements EvolutionExecutionHandoffService {

    private static final String COLLECTION = "yappc_evolution_execution_handoffs";

    private final DataCloudClient dataCloudClient;

    public DataCloudEvolutionExecutionHandoffService(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = dataCloudClient;
    }

    @Override
    public Promise<EvolutionExecutionHandoff> handoff(@NotNull EvolutionExecutionRequest request) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", request.handoffId());
        document.put("handoffId", request.handoffId());
        document.put("proposalId", request.proposalId());
        document.put("tenantId", request.tenantId());
        document.put("projectId", request.projectId());
        document.put("productUnitIntentRef", request.productUnitIntentRef());
        document.put("requestedBy", request.requestedBy());
        document.put("phases", request.phases());
        document.put("requestedAt", request.requestedAt().toString());
        document.put("status", "QUEUED");
        document.put("metadata", request.metadata());

        Instant acceptedAt = Instant.now();
        return dataCloudClient.save(request.tenantId(), COLLECTION, document)
                .map(ignored -> new EvolutionExecutionHandoff(
                        request.handoffId(),
                        "QUEUED",
                        acceptedAt,
                        Map.of(
                                "collection", COLLECTION,
                                "tenantId", request.tenantId(),
                                "projectId", request.projectId(),
                                "proposalId", request.proposalId()
                        )
                ));
    }
}