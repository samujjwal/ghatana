package com.ghatana.yappc.services.artifact;

import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisResult;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphMergeRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphQueryResponse;
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
    Promise<ArtifactGraphResponse> ingestGraph(ArtifactRequestScope scope, ArtifactGraphIngestRequest request);

    /**
     * Run graph analysis algorithms (centrality, cycles, communities, topological sort).
     */
    Promise<List<ArtifactGraphAnalysisResult>> analyzeGraph(ArtifactRequestScope scope, ArtifactGraphAnalysisRequest request);

    /**
     * Perform three-way semantic merge of artifact models.
     */
    Promise<ArtifactGraphResponse> mergeModels(ArtifactRequestScope scope, ArtifactGraphMergeRequest request);

    /**
     * Query the artifact graph with pattern-based traversal.
     * P1-13: Added cursor-based pagination support for large graph queries.
     * P3-1: Returns typed response with items, nextCursor, totalEstimate, and scope metadata.
     * P0: Accepts ArtifactRequestScope for proper tenant/workspace/project isolation.
     */
    Promise<ArtifactGraphQueryResponse> queryGraph(ArtifactRequestScope scope, String queryType, List<String> seedNodeIds, String cursor, int pageSize, String snapshotId, Boolean includeUnresolvedEdges);

    /**
     * Analyze residual islands (unextractable blocks flagged by the TS scanner).
     */
    Promise<ArtifactGraphResponse> analyzeResidual(String projectId, String tenantId, List<Map<String, Object>> residualIslands);
}
