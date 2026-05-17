package com.ghatana.yappc.services.compiler;

import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphResponse;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.EdgeResolutionRecordDto;
import com.ghatana.yappc.domain.artifact.ResidualIslandDto;
import com.ghatana.yappc.domain.artifact.SemanticModelDto;
import com.ghatana.yappc.domain.artifact.UnresolvedGraphEdgeDto;
import com.ghatana.yappc.services.artifact.ArtifactGraphService;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
import com.ghatana.yappc.services.source.RepositoryInventoryScanner;
import com.ghatana.yappc.services.source.SourceProvider;
import com.ghatana.yappc.services.source.SourceProviderRegistry;
import com.ghatana.yappc.storage.RepositorySnapshotRepository;
import com.ghatana.yappc.storage.SemanticModelRepository;
import io.activej.promise.Promise;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Java orchestrator for source provider to snapshot to canonical inventory to extraction to graph ingest
 * @doc.layer service
 * @doc.pattern Orchestrator
 * 
 * P1: Persists snapshot before extraction, runs canonical Java inventory, routes TS files to TS worker,
 * Java files to Java extractor, and persists semantic model.
 * P3: Integrates SemanticModelRepository for semantic model persistence.
 */
public final class ArtifactCompileJobService {

    private final SourceProviderRegistry sourceProviderRegistry;
    private final TsExtractorWorker tsExtractorWorker;
    private final ArtifactGraphService artifactGraphService;
    private final RepositorySnapshotRepository snapshotRepository;
    private final SemanticModelRepository semanticModelRepository;
    private final RepositoryInventoryScanner inventoryScanner;
    private final JavaArtifactExtractor javaArtifactExtractor;

    public ArtifactCompileJobService(
        SourceProviderRegistry sourceProviderRegistry,
        TsExtractorWorker tsExtractorWorker,
        ArtifactGraphService artifactGraphService,
        RepositorySnapshotRepository snapshotRepository,
        SemanticModelRepository semanticModelRepository,
        RepositoryInventoryScanner inventoryScanner,
        JavaArtifactExtractor javaArtifactExtractor
    ) {
        this.sourceProviderRegistry = Objects.requireNonNull(sourceProviderRegistry, "sourceProviderRegistry must not be null");
        this.tsExtractorWorker = Objects.requireNonNull(tsExtractorWorker, "tsExtractorWorker must not be null");
        this.artifactGraphService = Objects.requireNonNull(artifactGraphService, "artifactGraphService must not be null");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository must not be null");
        this.semanticModelRepository = Objects.requireNonNull(semanticModelRepository, "semanticModelRepository must not be null");
        this.inventoryScanner = Objects.requireNonNull(inventoryScanner, "inventoryScanner must not be null");
        this.javaArtifactExtractor = Objects.requireNonNull(javaArtifactExtractor, "javaArtifactExtractor must not be null");
    }

    /**
     * P1: Compile job orchestration with snapshot persistence, canonical inventory, and language-specific routing.
     * P3: Integrates semantic model persistence.
     * 
     * Flow:
     * 1. Resolve snapshot from source provider
     * 2. Persist snapshot with source locator ref
     * 3. Run canonical Java inventory
     * 4. Route TS files to TS worker, Java files to Java extractor
     * 5. Merge extraction results
     * 6. Persist semantic models
     * 7. Ingest artifact graph
     */
    public Promise<CompileJobResult> compile(CompileJobRequest request) {
        String versionId = UUID.randomUUID().toString();
        SourceProvider.ScopeContext scopeContext = new SourceProvider.ScopeContext(
            request.tenantId(),
            request.workspaceId(),
            request.projectId(),
            request.principalId()
        );

        return sourceProviderRegistry.resolve(request.sourceLocator(), scopeContext)
            .then(snapshot -> {
                // P1: Persist snapshot before extraction
                return snapshotRepository.saveSnapshot(snapshot, request.sourceLocator())
                    .then(persistedSnapshot -> {
                        // P1: Run canonical inventory on snapshot
                        final RepositoryInventoryScanner.InventoryResult inventoryResult;
                        try {
                            inventoryResult = inventoryScanner.scanRepository(
                                java.nio.file.Paths.get(persistedSnapshot.materializedRoot())
                            );
                        } catch (IOException e) {
                            return Promise.ofException(new IllegalStateException(
                                "Failed to scan repository inventory for snapshot " + persistedSnapshot.snapshotId(), e));
                        }

                        Map<String, RepositorySnapshot.SnapshotFile> filesByRelativePath = new HashMap<>();
                        for (RepositorySnapshot.SnapshotFile file : persistedSnapshot.files()) {
                            filesByRelativePath.put(file.relativePath(), file);
                        }
                        
                        // P1: Filter files by type and route to appropriate extractors
                        List<RepositorySnapshot.SnapshotFile> tsFiles = new ArrayList<>();
                        List<RepositorySnapshot.SnapshotFile> javaFiles = new ArrayList<>();

                        for (var inventoryFile : inventoryResult.files()) {
                            RepositorySnapshot.SnapshotFile file = filesByRelativePath.get(inventoryFile.relativePath());
                            if (file == null) {
                                continue;
                            }
                            String relativePath = file.relativePath().toLowerCase();
                            if (relativePath.endsWith(".ts") || relativePath.endsWith(".tsx") ||
                                relativePath.endsWith(".js") || relativePath.endsWith(".jsx")) {
                                tsFiles.add(file);
                            } else if (relativePath.endsWith(".java")) {
                                javaFiles.add(file);
                            }
                        }
                        
                        // P1: Route TS files to TS worker
                        Promise<ExtractionResult> tsExtraction = tsFiles.isEmpty()
                            ? Promise.of(new ExtractionResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()))
                            : tsExtractorWorker.extract(persistedSnapshot, tsFiles);
                        
                        // P1: Route Java files to Java extractor
                        Promise<ExtractionResult> javaExtraction = javaFiles.isEmpty()
                            ? Promise.of(new ExtractionResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()))
                            : javaArtifactExtractor.extract(persistedSnapshot, javaFiles, scopeContext);
                        
                        // P1: Merge extraction results and ingest graph first to avoid orphaned semantic rows on ingest failure
                        return tsExtraction.then(tsResult ->
                            javaExtraction.then(javaResult -> {
                                ExtractionResult merged = mergeExtractionResults(tsResult, javaResult);
                                return ingestGraph(request, persistedSnapshot, versionId, merged, inventoryResult)
                                    .then(result -> semanticModelRepository.saveModels(merged.semanticModels())
                                        .map(count -> result));
                            })
                        );
                    });
            });
    }

    /**
     * P1: Merge extraction results from multiple language extractors.
     */
    private ExtractionResult mergeExtractionResults(ExtractionResult tsResult, ExtractionResult javaResult) {
        List<ArtifactNodeDto> mergedNodes = new ArrayList<>(tsResult.nodes());
        mergedNodes.addAll(javaResult.nodes());
        
        List<ArtifactEdgeDto> mergedEdges = new ArrayList<>(tsResult.edges());
        mergedEdges.addAll(javaResult.edges());
        
        List<UnresolvedGraphEdgeDto> mergedUnresolved = new ArrayList<>(tsResult.unresolvedEdges());
        mergedUnresolved.addAll(javaResult.unresolvedEdges());
        
        List<EdgeResolutionRecordDto> mergedResolutionRecords = new ArrayList<>(tsResult.edgeResolutionRecords());
        mergedResolutionRecords.addAll(javaResult.edgeResolutionRecords());
        
        List<ResidualIslandDto> mergedResiduals = new ArrayList<>(tsResult.residualIslands());
        mergedResiduals.addAll(javaResult.residualIslands());

        List<SemanticModelDto> mergedSemanticModels = new ArrayList<>(tsResult.semanticModels());
        mergedSemanticModels.addAll(javaResult.semanticModels());
        
        return new ExtractionResult(
            mergedNodes,
            mergedEdges,
            mergedUnresolved,
            mergedResolutionRecords,
            mergedResiduals,
            mergedSemanticModels
        );
    }

    private Promise<CompileJobResult> ingestGraph(
        CompileJobRequest request,
        RepositorySnapshot snapshot,
        String versionId,
        ExtractionResult extraction,
        RepositoryInventoryScanner.InventoryResult inventoryResult
    ) {
        ArtifactGraphIngestRequest ingestRequest = new ArtifactGraphIngestRequest(
            request.projectId(),
            request.tenantId(),
            extraction.nodes(),
            extraction.edges(),
            snapshot.snapshotId(),
            snapshot.snapshotId(),
            versionId,
            snapshot.checksum(),
            extraction.unresolvedEdges(),
            extraction.edgeResolutionRecords(),
            extraction.residualIslands()
        );

        ArtifactRequestScope scope = new ArtifactRequestScope(request.projectId(), request.tenantId(), request.workspaceId());
        return artifactGraphService.ingestGraph(scope, ingestRequest)
            .map(response -> new CompileJobResult(
                request.jobId(),
                response.success(),
                snapshot.snapshotId(),
                versionId,
                extraction.nodes().size(),
                extraction.edges().size(),
                inventoryResult.totalFiles(),
                inventoryResult.skipped().size(),
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
        int inventoryFileCount,
        int skippedFileCount,
        Instant completedAt,
        ArtifactGraphResponse graphResponse
    ) {}

    public record ExtractionResult(
        List<ArtifactNodeDto> nodes,
        List<ArtifactEdgeDto> edges,
        List<UnresolvedGraphEdgeDto> unresolvedEdges,
        List<EdgeResolutionRecordDto> edgeResolutionRecords,
        List<ResidualIslandDto> residualIslands,
        List<SemanticModelDto> semanticModels
    ) {
        public ExtractionResult {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
            unresolvedEdges = unresolvedEdges == null ? List.of() : List.copyOf(unresolvedEdges);
            edgeResolutionRecords = edgeResolutionRecords == null ? List.of() : List.copyOf(edgeResolutionRecords);
            residualIslands = residualIslands == null ? List.of() : List.copyOf(residualIslands);
            semanticModels = semanticModels == null ? List.of() : List.copyOf(semanticModels);
        }
    }

    /**
     * P1: TS extractor worker interface for TypeScript/JavaScript files.
     * Takes snapshot and filtered TS files, returns extraction result.
     */
    @FunctionalInterface
    public interface TsExtractorWorker {
        Promise<ExtractionResult> extract(RepositorySnapshot snapshot, List<RepositorySnapshot.SnapshotFile> tsFiles);
    }

    /**
     * P1: Java artifact extractor interface for Java files.
     * 
     * P1-7: This interface will be implemented by JavaArtifactExtractor with source locations,
     * symbol refs, unresolved refs, confidence, and provenance.
     */
    @FunctionalInterface
    public interface JavaArtifactExtractor {
        Promise<ExtractionResult> extract(
            RepositorySnapshot snapshot,
            List<RepositorySnapshot.SnapshotFile> javaFiles,
            SourceProvider.ScopeContext scope
        );
    }
}
