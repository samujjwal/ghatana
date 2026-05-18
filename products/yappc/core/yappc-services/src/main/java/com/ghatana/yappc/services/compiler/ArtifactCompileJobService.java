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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * P1: Adds compile phase state tracking and partial failure handling for atomic graph+semantic persistence.
 */
public final class ArtifactCompileJobService {

    /**
     * P1: Compile phase states for tracking workflow progress and handling partial failures.
     */
    public enum CompilePhase {
        SNAPSHOT_PERSISTED,
        INVENTORY_SCANNED,
        EXTRACTION_COMPLETE,
        GRAPH_INGESTED,
        SEMANTIC_MODEL_PERSISTED,
        COMPLETE,
        FAILED_PARTIAL
    }

    private final SourceProviderRegistry sourceProviderRegistry;
    private final TsExtractorWorker tsExtractorWorker;
    private final ArtifactGraphService artifactGraphService;
    private final RepositorySnapshotRepository snapshotRepository;
    private final SemanticModelRepository semanticModelRepository;
    private final RepositoryInventoryScanner inventoryScanner;
    private final JavaArtifactExtractor javaArtifactExtractor;
    private static final Logger log = LoggerFactory.getLogger(ArtifactCompileJobService.class);

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
     * P1: Compile job orchestration with snapshot persistence, canonical inventory, language-specific routing,
     * and compile phase state tracking for atomic graph+semantic persistence.
     * P3: Integrates semantic model persistence.
     * 
     * Flow:
     * 1. Resolve snapshot from source provider
     * 2. Persist snapshot with source locator ref
     * 3. Run canonical Java inventory
     * 4. Route TS files to TS worker, Java files to Java extractor
     * 5. Merge extraction results
     * 6. Ingest artifact graph (phase: GRAPH_INGESTED)
     * 7. Persist semantic models (phase: SEMANTIC_MODEL_PERSISTED)
     * 8. Mark complete (phase: COMPLETE)
     * 
     * P1: If semantic model persistence fails after graph ingest, mark as FAILED_PARTIAL
     * to allow repair/retry without inconsistent state.
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
                        final CompilePhase[] phaseHolder = new CompilePhase[1];
                        phaseHolder[0] = CompilePhase.SNAPSHOT_PERSISTED;
                        
                        // P1: Check if persisted inventory exists, otherwise run canonical inventory
                        return snapshotRepository.findInventoryMetadata(
                            persistedSnapshot.snapshotId(),
                            request.tenantId(),
                            request.workspaceId(),
                            request.projectId()
                        ).then(existingMetadata -> {
                            final RepositoryInventoryScanner.InventoryResult[] inventoryResultHolder = new RepositoryInventoryScanner.InventoryResult[1];
                            if (existingMetadata.isPresent()) {
                                // P1: Use persisted inventory if available
                                try {
                                    inventoryResultHolder[0] = deserializeInventoryResult(existingMetadata.get());
                                    log.info("Using persisted inventory for snapshot {} with {} files", 
                                        persistedSnapshot.snapshotId(), inventoryResultHolder[0].totalFiles());
                                } catch (Exception e) {
                                    log.warn("Failed to deserialize persisted inventory, rescanning", e);
                                    inventoryResultHolder[0] = runInventoryScan(persistedSnapshot);
                                }
                            } else {
                                // P1: Run canonical inventory if not persisted
                                inventoryResultHolder[0] = runInventoryScan(persistedSnapshot);
                            }
                            phaseHolder[0] = CompilePhase.INVENTORY_SCANNED;

                            // P1: Store inventory result with snapshot if not already persisted
                            if (existingMetadata.isEmpty()) {
                                Map<String, Object> inventoryMetadata = serializeInventoryResult(inventoryResultHolder[0]);
                                return snapshotRepository.saveSnapshot(persistedSnapshot, request.sourceLocator(), inventoryMetadata)
                                    .map(updatedSnapshot -> inventoryResultHolder[0]);
                            } else {
                                return Promise.of(inventoryResultHolder[0]);
                            }
                        }).then(invResult -> {
                            Map<String, RepositorySnapshot.SnapshotFile> filesByRelativePath = new HashMap<>();
                            for (RepositorySnapshot.SnapshotFile file : persistedSnapshot.files()) {
                                filesByRelativePath.put(file.relativePath(), file);
                            }
                            
                            // P1: Filter files by type and route to appropriate extractors
                            List<RepositorySnapshot.SnapshotFile> tsFiles = new ArrayList<>();
                            List<RepositorySnapshot.SnapshotFile> javaFiles = new ArrayList<>();

                            for (var inventoryFile : invResult.files()) {
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
                            
                            // P1: Track result for error handling
                            final CompileJobResult[] finalResult = new CompileJobResult[1];
                            final int[] nodeCount = new int[1];
                            final int[] edgeCount = new int[1];

                            // P1: Route TS files to TS worker
                            final Promise<ExtractionResult> tsExtraction;
                            try {
                                tsExtraction = tsFiles.isEmpty()
                                    ? Promise.of(new ExtractionResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()))
                                    : tsExtractorWorker.extract(persistedSnapshot, tsFiles);
                            } catch (Exception e) {
                                log.error("TypeScript extractor invocation failed for snapshot {}: {}",
                                    persistedSnapshot.snapshotId(), e.getMessage(), e);
                                phaseHolder[0] = CompilePhase.FAILED_PARTIAL;
                                return Promise.of(createPartialFailureResult(
                                    request,
                                    persistedSnapshot.snapshotId(),
                                    versionId,
                                    0,
                                    0,
                                    invResult,
                                    null,
                                    "EXTRACTION_COMPLETE",
                                    e
                                ));
                            }

                            // P1: Route Java files to Java extractor
                            final Promise<ExtractionResult> javaExtraction;
                            try {
                                javaExtraction = javaFiles.isEmpty()
                                    ? Promise.of(new ExtractionResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()))
                                    : javaArtifactExtractor.extract(persistedSnapshot, javaFiles, scopeContext);
                            } catch (Exception e) {
                                log.error("Java extractor invocation failed for snapshot {}: {}",
                                    persistedSnapshot.snapshotId(), e.getMessage(), e);
                                phaseHolder[0] = CompilePhase.FAILED_PARTIAL;
                                return Promise.of(createPartialFailureResult(
                                    request,
                                    persistedSnapshot.snapshotId(),
                                    versionId,
                                    0,
                                    0,
                                    invResult,
                                    null,
                                    "EXTRACTION_COMPLETE",
                                    e
                                ));
                            }

                            phaseHolder[0] = CompilePhase.EXTRACTION_COMPLETE;
                            
                            // P1: Merge extraction results and ingest graph first to avoid orphaned semantic rows on ingest failure
                            return tsExtraction.then(tsResult ->
                                javaExtraction.then(javaResult -> {
                                    ExtractionResult merged = mergeExtractionResults(tsResult, javaResult);
                                    nodeCount[0] = merged.nodes().size();
                                    edgeCount[0] = merged.edges().size();
                                    return ingestGraph(request, persistedSnapshot, versionId, merged, invResult)
                                        .then(result -> {
                                            phaseHolder[0] = CompilePhase.GRAPH_INGESTED;
                                            finalResult[0] = result;
                                            // P1: Persist semantic models - if this fails, we still return the graph result
                                            // but mark as FAILED_PARTIAL to allow repair/retry
                                            return semanticModelRepository.saveModels(merged.semanticModels())
                                                .map(count -> {
                                                    phaseHolder[0] = CompilePhase.SEMANTIC_MODEL_PERSISTED;
                                                    return result;
                                                })
                                                .then(Promise::of, e -> {
                                                    log.error("Semantic model persistence failed after graph ingest for snapshot {}: {}", 
                                                        persistedSnapshot.snapshotId(), e.getMessage(), e);
                                                    phaseHolder[0] = CompilePhase.FAILED_PARTIAL;
                                                    ArtifactRequestScope rollbackScope = new ArtifactRequestScope(
                                                        request.projectId(),
                                                        request.tenantId(),
                                                        request.workspaceId()
                                                    );
                                                    return artifactGraphService.rollbackIngest(rollbackScope, persistedSnapshot.snapshotId())
                                                        .map(rollbackResponse -> {
                                                            if (!isValidRollbackResponse(rollbackResponse, persistedSnapshot.snapshotId())) {
                                                                return createPartialFailureResult(
                                                                    request,
                                                                    persistedSnapshot.snapshotId(),
                                                                    versionId,
                                                                    nodeCount[0],
                                                                    edgeCount[0],
                                                                    invResult,
                                                                    result.graphResponse(),
                                                                    "SEMANTIC_MODEL_PERSISTED",
                                                                    new IllegalStateException(
                                                                        "semantic persistence failed (" + e.getMessage()
                                                                            + "); malformed rollback response payload",
                                                                        e
                                                                    )
                                                                );
                                                            }
                                                            return createPartialFailureResult(
                                                                request,
                                                                persistedSnapshot.snapshotId(),
                                                                versionId,
                                                                nodeCount[0],
                                                                edgeCount[0],
                                                                invResult,
                                                                rollbackResponse,
                                                                "SEMANTIC_MODEL_PERSISTED",
                                                                e
                                                            );
                                                        })
                                                        .then(Promise::of, rollbackError -> Promise.of(createPartialFailureResult(
                                                            request,
                                                            persistedSnapshot.snapshotId(),
                                                            versionId,
                                                            nodeCount[0],
                                                            edgeCount[0],
                                                            invResult,
                                                            result.graphResponse(),
                                                            "SEMANTIC_MODEL_PERSISTED",
                                                            new IllegalStateException(
                                                                "semantic persistence failed (" + e.getMessage()
                                                                    + "); compensation rollback failed ("
                                                                    + rollbackError.getMessage() + ")",
                                                                e
                                                            )
                                                        )));
                                                });
                                        })
                                        .then(Promise::of, e -> {
                                            log.error("Graph ingest failed for snapshot {}: {}", 
                                                persistedSnapshot.snapshotId(), e.getMessage(), e);
                                            phaseHolder[0] = CompilePhase.FAILED_PARTIAL;
                                            return Promise.of(createPartialFailureResult(
                                                request,
                                                persistedSnapshot.snapshotId(),
                                                versionId,
                                                nodeCount[0],
                                                edgeCount[0],
                                                invResult,
                                                null,
                                                "GRAPH_INGESTED",
                                                e
                                            ));
                                        });
                                })
                                .then(Promise::of, e -> {
                                    log.error("Extraction failed for snapshot {}: {}", 
                                        persistedSnapshot.snapshotId(), e.getMessage(), e);
                                    phaseHolder[0] = CompilePhase.FAILED_PARTIAL;
                                    return Promise.of(createPartialFailureResult(
                                        request,
                                        persistedSnapshot.snapshotId(),
                                        versionId,
                                        nodeCount[0],
                                        edgeCount[0],
                                        invResult,
                                        finalResult[0] != null ? finalResult[0].graphResponse() : null,
                                        "EXTRACTION_COMPLETE",
                                        e
                                    ));
                                })
                            ).then(Promise::of, e -> {
                                log.error("TypeScript extraction failed for snapshot {}: {}",
                                    persistedSnapshot.snapshotId(), e.getMessage(), e);
                                phaseHolder[0] = CompilePhase.FAILED_PARTIAL;
                                return Promise.of(createPartialFailureResult(
                                    request,
                                    persistedSnapshot.snapshotId(),
                                    versionId,
                                    nodeCount[0],
                                    edgeCount[0],
                                    invResult,
                                    finalResult[0] != null ? finalResult[0].graphResponse() : null,
                                    "EXTRACTION_COMPLETE",
                                    e
                                ));
                            });
                        });
                    });
            });
    }

    private boolean isValidRollbackResponse(ArtifactGraphResponse rollbackResponse, String snapshotId) {
        if (rollbackResponse == null || rollbackResponse.result() == null || rollbackResponse.result().isEmpty()) {
            return false;
        }
        if (!"rollback".equals(rollbackResponse.operation())) {
            return false;
        }
        Object responseSnapshotId = rollbackResponse.result().get("snapshotId");
        if (!(responseSnapshotId instanceof String responseSnapshotIdStr) || !snapshotId.equals(responseSnapshotIdStr)) {
            return false;
        }
        return rollbackResponse.result().containsKey("tombstoned");
    }

    /**
     * P1: Run canonical inventory scan on snapshot materialized root.
     */
    private RepositoryInventoryScanner.InventoryResult runInventoryScan(RepositorySnapshot snapshot) {
        try {
            return inventoryScanner.scanRepository(
                java.nio.file.Paths.get(snapshot.materializedRoot())
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to scan repository inventory for snapshot " + snapshot.snapshotId(), e);
        }
    }

    /**
     * P1: Serialize inventory result to map for persistence.
     */
    private Map<String, Object> serializeInventoryResult(RepositoryInventoryScanner.InventoryResult result) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Serialize skipped entries with matched patterns
        List<Map<String, String>> skippedList = new ArrayList<>();
        for (var skipped : result.skipped()) {
            Map<String, String> skippedEntry = new HashMap<>();
            skippedEntry.put("relativePath", skipped.relativePath());
            skippedEntry.put("reason", skipped.reason().name());
            skippedEntry.put("matchedPattern", skipped.matchedPattern());
            skippedList.add(skippedEntry);
        }
        metadata.put("skipped", skippedList);
        
        // Serialize package boundaries
        metadata.put("packageBoundaries", result.packageBoundaries());
        
        // Serialize file counts
        Map<String, Integer> fileCountsMap = new HashMap<>();
        result.fileCounts().forEach((type, count) -> fileCountsMap.put(type.name(), count));
        metadata.put("fileCounts", fileCountsMap);
        
        // Serialize summary stats
        metadata.put("totalFiles", result.totalFiles());
        metadata.put("totalBytes", result.totalBytes());
        
        return metadata;
    }

    /**
     * P1: Deserialize inventory result from persisted map.
     */
    @SuppressWarnings("unchecked")
    private RepositoryInventoryScanner.InventoryResult deserializeInventoryResult(Map<String, Object> metadata) {
        // Deserialize skipped entries
        List<Map<String, String>> skippedList = (List<Map<String, String>>) metadata.getOrDefault("skipped", List.of());
        List<RepositoryInventoryScanner.SkippedEntry> skipped = new ArrayList<>();
        for (Map<String, String> entry : skippedList) {
            skipped.add(new RepositoryInventoryScanner.SkippedEntry(
                entry.get("relativePath"),
                RepositoryInventoryScanner.SkipReason.valueOf(entry.get("reason")),
                entry.get("matchedPattern")
            ));
        }
        
        // Deserialize package boundaries
        List<String> packageBoundaries = (List<String>) metadata.getOrDefault("packageBoundaries", List.of());
        
        // Deserialize file counts
        Map<String, Integer> fileCountsMap = (Map<String, Integer>) metadata.getOrDefault("fileCounts", Map.of());
        Map<RepositoryInventoryScanner.FileType, Integer> fileCounts = new HashMap<>();
        fileCountsMap.forEach((typeStr, count) -> {
            fileCounts.put(RepositoryInventoryScanner.FileType.valueOf(typeStr), count);
        });
        
        // Deserialize summary stats
        int totalFiles = ((Number) metadata.getOrDefault("totalFiles", 0)).intValue();
        long totalBytes = ((Number) metadata.getOrDefault("totalBytes", 0)).longValue();
        
        return new RepositoryInventoryScanner.InventoryResult(
            List.of(),  // Files are reconstructed from snapshot.files table
            skipped,
            fileCounts,
            totalFiles,
            totalBytes,
            packageBoundaries
        );
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

    private CompileJobResult createPartialFailureResult(
        CompileJobRequest request,
        String snapshotId,
        String versionId,
        int nodeCount,
        int edgeCount,
        RepositoryInventoryScanner.InventoryResult inventoryResult,
        ArtifactGraphResponse graphResponse,
        String failedStage,
        Exception error
    ) {
        String message = "Compilation failed during stage " + failedStage + ": " + error.getMessage();
        return new CompileJobResult(
            request.jobId(),
            false,
            snapshotId,
            versionId,
            nodeCount,
            edgeCount,
            inventoryResult.totalFiles(),
            inventoryResult.skipped().size(),
            Instant.now(),
            graphResponse,
            CompilePhase.FAILED_PARTIAL,
            message
        );
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
        ArtifactGraphResponse graphResponse,
        CompilePhase phase,
        String errorMessage
    ) {
        public CompileJobResult {
            phase = phase != null ? phase : CompilePhase.COMPLETE;
        }
        
        /**
         * P1: Backward-compatible constructor for existing code without phase/error fields.
         */
        public CompileJobResult(
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
        ) {
            this(jobId, success, snapshotId, versionId, nodeCount, edgeCount, 
                inventoryFileCount, skippedFileCount, completedAt, graphResponse, 
                success ? CompilePhase.COMPLETE : CompilePhase.FAILED_PARTIAL, null);
        }
    }

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
