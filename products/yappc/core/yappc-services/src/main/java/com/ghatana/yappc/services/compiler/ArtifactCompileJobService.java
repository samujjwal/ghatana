package com.ghatana.yappc.services.compiler;

import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphResponse;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.services.artifact.ArtifactGraphService;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import com.ghatana.yappc.services.source.RepositorySnapshot;
import com.ghatana.yappc.services.source.SourceLocator;
import com.ghatana.yappc.services.source.SourceProvider;
import com.ghatana.yappc.services.source.SourceProviderRegistry;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Java orchestrator for source provider to snapshot to extraction to graph ingest
 * @doc.layer service
 * @doc.pattern Orchestrator
 */
public final class ArtifactCompileJobService {

    private final SourceProviderRegistry sourceProviderRegistry;
    private final TsExtractorWorker tsExtractorWorker;
    private final ArtifactGraphService artifactGraphService;

    public ArtifactCompileJobService(
        SourceProviderRegistry sourceProviderRegistry,
        TsExtractorWorker tsExtractorWorker,
        ArtifactGraphService artifactGraphService
    ) {
        this.sourceProviderRegistry = Objects.requireNonNull(sourceProviderRegistry, "sourceProviderRegistry must not be null");
        this.tsExtractorWorker = Objects.requireNonNull(tsExtractorWorker, "tsExtractorWorker must not be null");
        this.artifactGraphService = Objects.requireNonNull(artifactGraphService, "artifactGraphService must not be null");
    }

    public Promise<CompileJobResult> compile(CompileJobRequest request) {
        String versionId = UUID.randomUUID().toString();
        SourceProvider.ScopeContext scopeContext = new SourceProvider.ScopeContext(
            request.tenantId(),
            request.workspaceId(),
            request.projectId(),
            request.principalId()
        );

        return sourceProviderRegistry.resolve(request.sourceLocator(), scopeContext)
            .then(snapshot -> tsExtractorWorker.extract(snapshot)
                .then(extraction -> ingestGraph(request, snapshot, versionId, extraction))
            );
    }

    private Promise<CompileJobResult> ingestGraph(
        CompileJobRequest request,
        RepositorySnapshot snapshot,
        String versionId,
        ExtractionResult extraction
    ) {
        ArtifactGraphIngestRequest ingestRequest = new ArtifactGraphIngestRequest(
            request.projectId(),
            request.tenantId(),
            extraction.nodes(),
            extraction.edges(),
            snapshot.snapshotId(),
            snapshot.snapshotId(),
            versionId,
            snapshot.contentChecksum(),
            extraction.unresolvedEdges(),
            extraction.edgeResolutionRecords(),
            extraction.residualIslandIds()
        );

        ArtifactRequestScope scope = new ArtifactRequestScope(request.projectId(), request.tenantId());
        return artifactGraphService.ingestGraph(scope, ingestRequest)
            .map(response -> new CompileJobResult(
                request.jobId(),
                response.success(),
                snapshot.snapshotId(),
                versionId,
                extraction.nodes().size(),
                extraction.edges().size(),
                Instant.now(),
                response
            ));
    }

    public record CompileJobRequest(
        String jobId,
        String tenantId,
        String workspaceId,
        String projectId,
        String principalId,
        SourceLocator sourceLocator
    ) {}

    public record CompileJobResult(
        String jobId,
        boolean success,
        String snapshotId,
        String versionId,
        int nodeCount,
        int edgeCount,
        Instant completedAt,
        ArtifactGraphResponse graphResponse
    ) {}

    public record ExtractionResult(
        List<ArtifactNodeDto> nodes,
        List<ArtifactEdgeDto> edges,
        List<Map<String, Object>> unresolvedEdges,
        List<Map<String, Object>> edgeResolutionRecords,
        List<String> residualIslandIds
    ) {
        public ExtractionResult {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
            unresolvedEdges = unresolvedEdges == null ? List.of() : List.copyOf(unresolvedEdges);
            edgeResolutionRecords = edgeResolutionRecords == null ? List.of() : List.copyOf(edgeResolutionRecords);
            residualIslandIds = residualIslandIds == null ? List.of() : List.copyOf(residualIslandIds);
        }
    }

    @FunctionalInterface
    public interface TsExtractorWorker {
        Promise<ExtractionResult> extract(RepositorySnapshot snapshot);
    }
}
