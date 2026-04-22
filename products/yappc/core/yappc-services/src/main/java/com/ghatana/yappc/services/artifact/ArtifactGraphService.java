package com.ghatana.yappc.services.artifact;

import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisResult;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphMergeRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphResponse;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * @doc.type interface
 * @doc.purpose Service facade for artifact graph ingestion, analysis, merge, and query operations
 * @doc.layer service
 * @doc.pattern Facade
 */
public interface ArtifactGraphService {

    /**
     * Ingest artifact nodes and edges into the graph store.
     */
    Promise<ArtifactGraphResponse> ingestGraph(ArtifactGraphIngestRequest request);

    /**
     * Run graph analysis algorithms (centrality, cycles, communities, topological sort).
     */
    Promise<List<ArtifactGraphAnalysisResult>> analyzeGraph(ArtifactGraphAnalysisRequest request);

    /**
     * Perform three-way semantic merge of artifact models.
     */
    Promise<ArtifactGraphResponse> mergeModels(ArtifactGraphMergeRequest request);

    /**
     * Query the artifact graph with pattern-based traversal.
     */
    Promise<Map<String, Object>> queryGraph(String productId, String tenantId, String queryType, List<String> seedNodeIds);

    /**
     * Analyze residual islands (unextractable blocks flagged by the TS scanner).
     */
    Promise<ArtifactGraphResponse> analyzeResidual(String productId, String tenantId, List<Map<String, Object>> residualIslands);
}
