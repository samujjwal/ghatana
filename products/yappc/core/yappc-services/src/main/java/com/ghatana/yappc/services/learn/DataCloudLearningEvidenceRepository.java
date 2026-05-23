package com.ghatana.yappc.services.learn;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data Cloud-backed learning evidence repository.
 *
 * @doc.type class
 * @doc.purpose Persist Learn phase evidence to Data Cloud
 * @doc.layer service
 * @doc.pattern Repository Adapter
 */
public final class DataCloudLearningEvidenceRepository implements LearningEvidenceRepository {

    private static final String COLLECTION = "yappc_learning_evidence";

    private final DataCloudClient dataCloudClient;

    /**
     * Creates the Data Cloud adapter.
     *
     * @param dataCloudClient Data Cloud client
     */
    public DataCloudLearningEvidenceRepository(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = dataCloudClient;
    }

    @Override
    public Promise<Void> save(@NotNull LearningEvidence evidence) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", evidence.evidenceId());
        document.put("evidenceId", evidence.evidenceId());
        document.put("tenantId", evidence.tenantId());
        document.put("projectId", evidence.projectId());
        document.put("runId", evidence.runId());
        document.put("observationRef", evidence.observation().id());
        document.put("insightsRef", evidence.insights().id());
        document.put("patternCount", evidence.insights().patterns().size());
        document.put("anomalyCount", evidence.insights().anomalies().size());
        document.put("recommendationCount", evidence.insights().recommendations().size());
        document.put("provenance", evidence.provenance());
        document.put("metadata", evidence.metadata());
        document.put("createdAt", evidence.createdAt().toString());

        return dataCloudClient.save(evidence.tenantId(), COLLECTION, document).toVoid();
    }
}
