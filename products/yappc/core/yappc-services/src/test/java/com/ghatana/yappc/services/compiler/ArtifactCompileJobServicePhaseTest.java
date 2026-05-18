package com.ghatana.yappc.services.compiler;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.artifact.ArtifactGraphResponse;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
import com.ghatana.yappc.services.artifact.ArtifactGraphService;
import com.ghatana.yappc.services.source.RepositoryInventoryScanner;
import com.ghatana.yappc.services.source.SourceProvider;
import com.ghatana.yappc.services.source.SourceProviderRegistry;
import com.ghatana.yappc.storage.RepositorySnapshotRepository;
import com.ghatana.yappc.storage.SemanticModelRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

/**
 * @doc.type test
 * @doc.purpose Verifies compile phase behavior for graph and semantic persistence boundaries
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArtifactCompileJobService Phase Tests")
class ArtifactCompileJobServicePhaseTest extends EventloopTestBase {

    @Mock
    private SourceProviderRegistry sourceProviderRegistry;

    @Mock
    private ArtifactCompileJobService.TsExtractorWorker tsExtractorWorker;

    @Mock
    private ArtifactCompileJobService.JavaArtifactExtractor javaArtifactExtractor;

    @Mock
    private ArtifactGraphService artifactGraphService;

    @Mock
    private RepositorySnapshotRepository snapshotRepository;

    @Mock
    private SemanticModelRepository semanticModelRepository;

    @Mock
    private RepositoryInventoryScanner inventoryScanner;

    private ArtifactCompileJobService service;

    @BeforeEach
    void setUp() {
        service = new ArtifactCompileJobService(
            sourceProviderRegistry,
            tsExtractorWorker,
            artifactGraphService,
            snapshotRepository,
            semanticModelRepository,
            inventoryScanner,
            javaArtifactExtractor
        );
    }

    @Test
    @DisplayName("returns FAILED_PARTIAL when semantic model persistence fails after graph ingest")
    void returnsFailedPartialWhenSemanticPersistenceFails() {
        RepositorySnapshot snapshot = testSnapshot();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(Map.of(
                "skipped", List.of(),
                "packageBoundaries", List.of(),
                "fileCounts", Map.of(),
                "totalFiles", 0,
                "totalBytes", 0L
            ))));
        when(artifactGraphService.ingestGraph(any(), any()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(true, "ingestGraph", Map.of("saved", true), "ok")));
        when(artifactGraphService.rollbackIngest(any(), anyString()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(true, "rollback", Map.of("snapshotId", "snap-1", "tombstoned", true), "rollback-ok")));
        when(semanticModelRepository.saveModels(anyList()))
            .thenReturn(Promise.ofException(new IllegalStateException("semantic-db-unavailable")));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isFalse();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.FAILED_PARTIAL);
        assertThat(result.graphResponse()).isNotNull();
        assertThat(result.graphResponse().operation()).isEqualTo("rollback");
        assertThat(result.graphResponse().result()).isNotEmpty();
        assertThat(result.graphResponse().result()).containsEntry("snapshotId", "snap-1");
        assertThat(result.graphResponse().result()).containsEntry("tombstoned", true);
        assertThat(result.graphResponse().message()).contains("rollback-ok");
        assertThat(result.errorMessage())
            .contains("SEMANTIC_MODEL_PERSISTED")
            .contains("semantic-db-unavailable");
    }

    @Test
    @DisplayName("returns FAILED_PARTIAL with rollback response even when rollback finds no active rows")
    void returnsFailedPartialWhenRollbackFindsNoActiveRows() {
        RepositorySnapshot snapshot = testSnapshot();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(Map.of(
                "skipped", List.of(),
                "packageBoundaries", List.of(),
                "fileCounts", Map.of(),
                "totalFiles", 0,
                "totalBytes", 0L
            ))));
        when(artifactGraphService.ingestGraph(any(), any()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(true, "ingestGraph", Map.of("saved", true), "ok")));
        when(artifactGraphService.rollbackIngest(any(), anyString()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(false, "rollback", Map.of("snapshotId", "snap-1", "tombstoned", false), "rollback-no-active-rows")));
        when(semanticModelRepository.saveModels(anyList()))
            .thenReturn(Promise.ofException(new IllegalStateException("semantic-db-unavailable")));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isFalse();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.FAILED_PARTIAL);
        assertThat(result.graphResponse()).isNotNull();
        assertThat(result.graphResponse().operation()).isEqualTo("rollback");
        assertThat(result.graphResponse().success()).isFalse();
        assertThat(result.graphResponse().result()).isNotEmpty();
        assertThat(result.graphResponse().result()).containsEntry("snapshotId", "snap-1");
        assertThat(result.graphResponse().result()).containsEntry("tombstoned", false);
        assertThat(result.graphResponse().message()).contains("rollback-no-active-rows");
        assertThat(result.errorMessage())
            .contains("SEMANTIC_MODEL_PERSISTED")
            .contains("semantic-db-unavailable");
    }

    @Test
    @DisplayName("returns FAILED_PARTIAL with ingest graph response when rollback compensation fails")
    void returnsFailedPartialWhenRollbackCompensationFails() {
        RepositorySnapshot snapshot = testSnapshot();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(Map.of(
                "skipped", List.of(),
                "packageBoundaries", List.of(),
                "fileCounts", Map.of(),
                "totalFiles", 0,
                "totalBytes", 0L
            ))));
        when(artifactGraphService.ingestGraph(any(), any()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(true, "ingestGraph", Map.of("saved", true), "ok")));
        when(artifactGraphService.rollbackIngest(any(), anyString()))
            .thenReturn(Promise.ofException(new IllegalStateException("rollback-failed")));
        when(semanticModelRepository.saveModels(anyList()))
            .thenReturn(Promise.ofException(new IllegalStateException("semantic-db-unavailable")));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isFalse();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.FAILED_PARTIAL);
        assertThat(result.graphResponse()).isNotNull();
        assertThat(result.graphResponse().operation()).isEqualTo("ingestGraph");
        assertThat(result.graphResponse().result()).isNotEmpty();
        assertThat(result.graphResponse().result()).containsEntry("saved", true);
        assertThat(result.graphResponse().message()).contains("ok");
        assertThat(result.errorMessage())
            .contains("SEMANTIC_MODEL_PERSISTED")
            .contains("semantic-db-unavailable")
            .contains("compensation rollback failed");
    }

    @Test
    @DisplayName("returns FAILED_PARTIAL with ingest graph response when rollback payload is malformed")
    void returnsFailedPartialWhenRollbackPayloadMalformed() {
        RepositorySnapshot snapshot = testSnapshot();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(Map.of(
                "skipped", List.of(),
                "packageBoundaries", List.of(),
                "fileCounts", Map.of(),
                "totalFiles", 0,
                "totalBytes", 0L
            ))));
        when(artifactGraphService.ingestGraph(any(), any()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(true, "ingestGraph", Map.of("saved", true), "ok")));
        when(artifactGraphService.rollbackIngest(any(), anyString()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(true, "rollback", Map.of(), "rollback-malformed")));
        when(semanticModelRepository.saveModels(anyList()))
            .thenReturn(Promise.ofException(new IllegalStateException("semantic-db-unavailable")));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isFalse();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.FAILED_PARTIAL);
        assertThat(result.graphResponse()).isNotNull();
        assertThat(result.graphResponse().operation()).isEqualTo("ingestGraph");
        assertThat(result.graphResponse().result()).containsEntry("saved", true);
        assertThat(result.errorMessage())
            .contains("SEMANTIC_MODEL_PERSISTED")
            .contains("semantic-db-unavailable")
            .contains("malformed rollback response payload");
    }

    @Test
    @DisplayName("returns FAILED_PARTIAL with ingest graph response when rollback payload snapshotId mismatches")
    void returnsFailedPartialWhenRollbackPayloadSnapshotIdMismatches() {
        RepositorySnapshot snapshot = testSnapshot();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(Map.of(
                "skipped", List.of(),
                "packageBoundaries", List.of(),
                "fileCounts", Map.of(),
                "totalFiles", 0,
                "totalBytes", 0L
            ))));
        when(artifactGraphService.ingestGraph(any(), any()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(true, "ingestGraph", Map.of("saved", true), "ok")));
        when(artifactGraphService.rollbackIngest(any(), anyString()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(
                true,
                "rollback",
                Map.of("snapshotId", "snap-mismatch", "tombstoned", true),
                "rollback-ok"
            )));
        when(semanticModelRepository.saveModels(anyList()))
            .thenReturn(Promise.ofException(new IllegalStateException("semantic-db-unavailable")));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isFalse();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.FAILED_PARTIAL);
        assertThat(result.graphResponse()).isNotNull();
        assertThat(result.graphResponse().operation()).isEqualTo("ingestGraph");
        assertThat(result.graphResponse().result()).containsEntry("saved", true);
        assertThat(result.errorMessage())
            .contains("SEMANTIC_MODEL_PERSISTED")
            .contains("semantic-db-unavailable")
            .contains("malformed rollback response payload");
    }

    @Test
    @DisplayName("returns FAILED_PARTIAL with ingest graph response when rollback payload operation is invalid")
    void returnsFailedPartialWhenRollbackPayloadOperationInvalid() {
        RepositorySnapshot snapshot = testSnapshot();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(Map.of(
                "skipped", List.of(),
                "packageBoundaries", List.of(),
                "fileCounts", Map.of(),
                "totalFiles", 0,
                "totalBytes", 0L
            ))));
        when(artifactGraphService.ingestGraph(any(), any()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(true, "ingestGraph", Map.of("saved", true), "ok")));
        when(artifactGraphService.rollbackIngest(any(), anyString()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(
                true,
                "unexpected-operation",
                Map.of("snapshotId", "snap-1", "tombstoned", true),
                "rollback-invalid-operation"
            )));
        when(semanticModelRepository.saveModels(anyList()))
            .thenReturn(Promise.ofException(new IllegalStateException("semantic-db-unavailable")));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isFalse();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.FAILED_PARTIAL);
        assertThat(result.graphResponse()).isNotNull();
        assertThat(result.graphResponse().operation()).isEqualTo("ingestGraph");
        assertThat(result.graphResponse().result()).containsEntry("saved", true);
        assertThat(result.errorMessage())
            .contains("SEMANTIC_MODEL_PERSISTED")
            .contains("semantic-db-unavailable")
            .contains("malformed rollback response payload");
    }

    @Test
    @DisplayName("returns FAILED_PARTIAL with ingest graph response when rollback payload misses tombstoned key")
    void returnsFailedPartialWhenRollbackPayloadMissingTombstonedKey() {
        RepositorySnapshot snapshot = testSnapshot();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(Map.of(
                "skipped", List.of(),
                "packageBoundaries", List.of(),
                "fileCounts", Map.of(),
                "totalFiles", 0,
                "totalBytes", 0L
            ))));
        when(artifactGraphService.ingestGraph(any(), any()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(true, "ingestGraph", Map.of("saved", true), "ok")));
        when(artifactGraphService.rollbackIngest(any(), anyString()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(
                true,
                "rollback",
                Map.of("snapshotId", "snap-1"),
                "rollback-missing-tombstoned"
            )));
        when(semanticModelRepository.saveModels(anyList()))
            .thenReturn(Promise.ofException(new IllegalStateException("semantic-db-unavailable")));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isFalse();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.FAILED_PARTIAL);
        assertThat(result.graphResponse()).isNotNull();
        assertThat(result.graphResponse().operation()).isEqualTo("ingestGraph");
        assertThat(result.graphResponse().result()).containsEntry("saved", true);
        assertThat(result.errorMessage())
            .contains("SEMANTIC_MODEL_PERSISTED")
            .contains("semantic-db-unavailable")
            .contains("malformed rollback response payload");
    }

    @Test
    @DisplayName("returns COMPLETE when graph ingest and semantic model persistence both succeed")
    void returnsCompleteWhenGraphAndSemanticPersistenceSucceed() {
        RepositorySnapshot snapshot = testSnapshot();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(Map.of(
                "skipped", List.of(),
                "packageBoundaries", List.of(),
                "fileCounts", Map.of(),
                "totalFiles", 0,
                "totalBytes", 0L
            ))));
        when(artifactGraphService.ingestGraph(any(), any()))
            .thenReturn(Promise.of(new ArtifactGraphResponse(true, "ingestGraph", Map.of("saved", true), "ok")));
        when(semanticModelRepository.saveModels(anyList()))
            .thenReturn(Promise.of(0));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isTrue();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.COMPLETE);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("returns FAILED_PARTIAL when graph ingest fails")
    void returnsFailedPartialWhenGraphIngestFails() {
        RepositorySnapshot snapshot = testSnapshot();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(Map.of(
                "skipped", List.of(),
                "packageBoundaries", List.of(),
                "fileCounts", Map.of(),
                "totalFiles", 0,
                "totalBytes", 0L
            ))));
        when(artifactGraphService.ingestGraph(any(), any()))
            .thenReturn(Promise.ofException(new IllegalStateException("graph-ingest-failed")));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isFalse();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.FAILED_PARTIAL);
        assertThat(result.graphResponse()).isNull();
        assertThat(result.errorMessage())
            .contains("GRAPH_INGESTED")
            .contains("graph-ingest-failed");
    }

    @Test
    @DisplayName("returns FAILED_PARTIAL when extraction fails before graph ingest")
    void returnsFailedPartialWhenExtractionFails() throws Exception {
        RepositorySnapshot snapshot = testSnapshotWithTsFile();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class), anyMap()))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        when(inventoryScanner.scanRepository(any(java.nio.file.Path.class)))
            .thenReturn(new RepositoryInventoryScanner.InventoryResult(
                List.of(new RepositoryInventoryScanner.InventoryEntry(
                    "src/App.tsx",
                    RepositoryInventoryScanner.FileType.SOURCE,
                    128,
                    "file-checksum"
                )),
                List.of(),
                Map.of(RepositoryInventoryScanner.FileType.SOURCE, 1),
                1,
                128L,
                List.of()
            ));
        when(tsExtractorWorker.extract(any(RepositorySnapshot.class), anyList()))
            .thenThrow(new IllegalStateException("ts-extraction-failed"));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isFalse();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.FAILED_PARTIAL);
        assertThat(result.graphResponse()).isNull();
        assertThat(result.errorMessage())
            .contains("EXTRACTION_COMPLETE")
            .contains("ts-extraction-failed");
    }

    @Test
    @DisplayName("returns FAILED_PARTIAL when Java extractor invocation fails before graph ingest")
    void returnsFailedPartialWhenJavaExtractorInvocationFails() throws Exception {
        RepositorySnapshot snapshot = testSnapshotWithJavaFile();
        ArtifactCompileJobService.CompileJobRequest request = testRequest();

        when(sourceProviderRegistry.resolve(any(SourceLocator.class), any(SourceProvider.ScopeContext.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class)))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.saveSnapshot(any(RepositorySnapshot.class), any(SourceLocator.class), anyMap()))
            .thenReturn(Promise.of(snapshot));
        when(snapshotRepository.findInventoryMetadata(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        when(inventoryScanner.scanRepository(any(java.nio.file.Path.class)))
            .thenReturn(new RepositoryInventoryScanner.InventoryResult(
                List.of(new RepositoryInventoryScanner.InventoryEntry(
                    "src/Main.java",
                    RepositoryInventoryScanner.FileType.SOURCE,
                    256,
                    "java-file-checksum"
                )),
                List.of(),
                Map.of(RepositoryInventoryScanner.FileType.SOURCE, 1),
                1,
                256L,
                List.of()
            ));
        when(javaArtifactExtractor.extract(any(RepositorySnapshot.class), anyList(), any(SourceProvider.ScopeContext.class)))
            .thenThrow(new IllegalStateException("java-extraction-failed"));

        ArtifactCompileJobService.CompileJobResult result = runPromise(() -> service.compile(request));

        assertThat(result.success()).isFalse();
        assertThat(result.phase()).isEqualTo(ArtifactCompileJobService.CompilePhase.FAILED_PARTIAL);
        assertThat(result.graphResponse()).isNull();
        assertThat(result.errorMessage())
            .contains("EXTRACTION_COMPLETE")
            .contains("java-extraction-failed");
    }

    private ArtifactCompileJobService.CompileJobRequest testRequest() {
        return new ArtifactCompileJobService.CompileJobRequest(
            "job-1",
            "tenant-1",
            "workspace-1",
            "project-1",
            "user-1",
            SourceLocator.builder()
                .provider("github")
                .repoId("org/repo")
                .ref("main")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .projectId("project-1")
                .build()
        );
    }

    private RepositorySnapshot testSnapshot() {
        return RepositorySnapshot.builder()
            .snapshotId("snap-1")
            .provider("github")
            .repoId("org/repo")
            .commitSha("abc123")
            .materializedRoot("/tmp/yappc-snapshot")
            .files(List.of())
            .checksum("checksum-1")
            .contentHash("content-hash-1")
            .diagnostics(List.of())
            .createdAt(Instant.parse("2026-05-17T00:00:00Z"))
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .projectId("project-1")
            .build();
    }

    private RepositorySnapshot testSnapshotWithTsFile() {
        return RepositorySnapshot.builder()
            .snapshotId("snap-1")
            .provider("github")
            .repoId("org/repo")
            .commitSha("abc123")
            .materializedRoot("/tmp/yappc-snapshot")
            .files(List.of(new RepositorySnapshot.SnapshotFile(
                "src/App.tsx",
                "/tmp/yappc-snapshot/src/App.tsx",
                128,
                Instant.parse("2026-05-17T00:00:00Z"),
                "file-checksum"
            )))
            .checksum("checksum-1")
            .contentHash("content-hash-1")
            .diagnostics(List.of())
            .createdAt(Instant.parse("2026-05-17T00:00:00Z"))
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .projectId("project-1")
            .build();
    }

    private RepositorySnapshot testSnapshotWithJavaFile() {
        return RepositorySnapshot.builder()
            .snapshotId("snap-1")
            .provider("github")
            .repoId("org/repo")
            .commitSha("abc123")
            .materializedRoot("/tmp/yappc-snapshot")
            .files(List.of(new RepositorySnapshot.SnapshotFile(
                "src/Main.java",
                "/tmp/yappc-snapshot/src/Main.java",
                256,
                Instant.parse("2026-05-17T00:00:00Z"),
                "java-file-checksum"
            )))
            .checksum("checksum-1")
            .contentHash("content-hash-1")
            .diagnostics(List.of())
            .createdAt(Instant.parse("2026-05-17T00:00:00Z"))
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .projectId("project-1")
            .build();
    }
}
