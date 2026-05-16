package com.ghatana.yappc.services.artifact.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.RepositorySnapshot.SnapshotDiagnostic;
import com.ghatana.yappc.domain.source.RepositorySnapshot.SnapshotFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Tests for TypeScriptExtractorWorkerClient - Java client to invoke TS worker
 * @doc.layer test
 * @doc.pattern UnitTest
 *
 * P1: Tests for Java client to invoke TS worker with timeout, diagnostics, and output contract.
 */
@DisplayName("TypeScriptExtractorWorkerClient Tests")
class TypeScriptExtractorWorkerClientTest {

    private ObjectMapper objectMapper;
    private TypeScriptExtractorWorkerClient client;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should create client with required dependencies")
    void shouldCreateClientWithRequiredDependencies() {
        client = new TypeScriptExtractorWorkerClient(
            objectMapper,
            Executors.newSingleThreadExecutor(),
            "/path/to/worker.js"
        );

        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("Should create client with custom timeout")
    void shouldCreateClientWithCustomTimeout() {
        client = new TypeScriptExtractorWorkerClient(
            objectMapper,
            Executors.newSingleThreadExecutor(),
            "/path/to/worker.js",
            600 // 10 minutes
        );

        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("Should reject null objectMapper")
    void shouldRejectNullObjectMapper() {
        assertThatThrownBy(() -> new TypeScriptExtractorWorkerClient(
            null,
            Executors.newSingleThreadExecutor(),
            "/path/to/worker.js"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("objectMapper must not be null");
    }

    @Test
    @DisplayName("Should reject null executor")
    void shouldRejectNullExecutor() {
        assertThatThrownBy(() -> new TypeScriptExtractorWorkerClient(
            objectMapper,
            null,
            "/path/to/worker.js"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("executor must not be null");
    }

    @Test
    @DisplayName("Should reject null worker script path")
    void shouldRejectNullWorkerScriptPath() {
        assertThatThrownBy(() -> new TypeScriptExtractorWorkerClient(
            objectMapper,
            Executors.newSingleThreadExecutor(),
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("workerScriptPath must not be null");
    }

    @Test
    @DisplayName("Should build valid request DTO")
    void shouldBuildValidRequestDto() {
        RepositorySnapshot snapshot = createTestSnapshot();

        var request = new TypeScriptExtractorWorkerClient.ExtractorWorkerRequest(
            snapshot.snapshotId(),
            snapshot.provider(),
            snapshot.repoId(),
            snapshot.materializedRoot(),
            snapshot.files().stream()
                .map(f -> new TypeScriptExtractorWorkerClient.FileMetadata(
                    f.relativePath(), f.absolutePath(), f.sizeBytes()
                ))
                .toList()
        );

        assertThat(request.snapshotId()).isEqualTo("snapshot-1");
        assertThat(request.provider()).isEqualTo("github");
        assertThat(request.repoId()).isEqualTo("owner/repo");
        assertThat(request.materializedRoot()).isEqualTo("/tmp/repo");
        assertThat(request.files()).hasSize(2);
    }

    @Test
    @DisplayName("Should build result with proper immutability")
    void shouldBuildResultWithProperImmutability() {
        List<TypeScriptExtractorWorkerClient.WorkerDiagnostic> diagnostics = List.of(
            new TypeScriptExtractorWorkerClient.WorkerDiagnostic(
                "INFO", "E001", "Test message", "test.ts", 1, 0
            )
        );

        var result = new TypeScriptExtractorWorkerClient.ExtractorWorkerResult(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("residual-1"),
            diagnostics
        );

        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
        assertThat(result.residualIslandIds()).containsExactly("residual-1");
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should detect errors in diagnostics")
    void shouldDetectErrorsInDiagnostics() {
        List<TypeScriptExtractorWorkerClient.WorkerDiagnostic> diagnostics = List.of(
            new TypeScriptExtractorWorkerClient.WorkerDiagnostic(
                "ERROR", "E001", "Error message", "test.ts", 1, 0
            ),
            new TypeScriptExtractorWorkerClient.WorkerDiagnostic(
                "WARNING", "W001", "Warning message", "test.ts", 2, 0
            )
        );

        var result = new TypeScriptExtractorWorkerClient.ExtractorWorkerResult(
            List.of(), List.of(), List.of(), List.of(), List.of(), diagnostics
        );

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getWarnings()).hasSize(1);
    }

    @Test
    @DisplayName("Should handle empty result")
    void shouldHandleEmptyResult() {
        var result = new TypeScriptExtractorWorkerClient.ExtractorWorkerResult(
            null, null, null, null, null, null
        );

        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
        assertThat(result.residualIslandIds()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should create worker exception with details")
    void shouldCreateWorkerExceptionWithDetails() {
        List<String> diagnostics = List.of("Error 1", "Error 2");
        var exception = new TypeScriptExtractorWorkerClient.ExtractorWorkerException(
            "Worker failed", 1, diagnostics
        );

        assertThat(exception.getMessage()).isEqualTo("Worker failed");
        assertThat(exception.exitCode()).isEqualTo(1);
        assertThat(exception.diagnostics()).containsExactly("Error 1", "Error 2");
    }

    @Test
    @DisplayName("Should serialize and deserialize request")
    void shouldSerializeAndDeserializeRequest() throws IOException {
        var request = new TypeScriptExtractorWorkerClient.ExtractorWorkerRequest(
            "snapshot-1",
            "github",
            "owner/repo",
            "/tmp/repo",
            List.of(
                new TypeScriptExtractorWorkerClient.FileMetadata("src/index.ts", "/tmp/repo/src/index.ts", 1000)
            )
        );

        String json = objectMapper.writeValueAsString(request);
        var parsed = objectMapper.readValue(json, TypeScriptExtractorWorkerClient.ExtractorWorkerRequest.class);

        assertThat(parsed.snapshotId()).isEqualTo(request.snapshotId());
        assertThat(parsed.files()).hasSize(1);
    }

    private RepositorySnapshot createTestSnapshot() {
        return RepositorySnapshot.builder()
            .snapshotId("snapshot-1")
            .provider("github")
            .repoId("owner/repo")
            .commitSha("abc123")
            .materializedRoot("/tmp/repo")
            .files(List.of(
                new SnapshotFile("src/index.ts", "/tmp/repo/src/index.ts", 1000, Instant.now(), "checksum1"),
                new SnapshotFile("src/utils.ts", "/tmp/repo/src/utils.ts", 500, Instant.now(), "checksum2")
            ))
            .checksum("snapshot-checksum")
            .diagnostics(List.of())
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .projectId("project-1")
            .build();
    }
}
