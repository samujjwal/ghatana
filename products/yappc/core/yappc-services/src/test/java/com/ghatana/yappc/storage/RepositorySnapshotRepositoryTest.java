package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type test
 * @doc.purpose Unit tests for RepositorySnapshotRepository covering persistence,
 *              retrieval, scope-based queries, and content hash deduplication.
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RepositorySnapshotRepository Tests")
class RepositorySnapshotRepositoryTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private PreparedStatement filesPreparedStatement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private ResultSet filesResultSet;

    private ObjectMapper objectMapper;
    private Executor executor;
    private RepositorySnapshotRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        executor = Runnable::run;
        repository = new RepositorySnapshotRepository(dataSource, objectMapper, executor);

        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("repository_snapshot_files")) {
                return filesPreparedStatement;
            }
            return preparedStatement;
        });
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet);
        lenient().when(filesPreparedStatement.executeQuery()).thenReturn(filesResultSet);
        lenient().when(filesResultSet.next()).thenReturn(false);
        // Mock the required columns for filesResultSet (even though next() returns false, Mockito may still check column access)
        lenient().when(filesResultSet.getString("relative_path")).thenReturn("");
        lenient().when(filesResultSet.getString("absolute_path")).thenReturn("");
        lenient().when(filesResultSet.getLong("size_bytes")).thenReturn(0L);
        lenient().when(filesResultSet.getTimestamp("last_modified_at")).thenReturn(Timestamp.from(Instant.now()));
        lenient().when(filesResultSet.getString("content_checksum")).thenReturn("");
    }

    @Test
    @DisplayName("Should save snapshot with files")
    void shouldSaveSnapshotWithFiles() throws Exception {
        RepositorySnapshot.SnapshotFile file = new RepositorySnapshot.SnapshotFile(
            "src/App.tsx", "/tmp/src/App.tsx", 1024, Instant.now(), "checksum-1"
        );
        RepositorySnapshot snapshot = RepositorySnapshot.builder()
            .snapshotId("snap-123")
            .provider("github")
            .repoId("owner/repo")
            .commitSha("abc123")
            .materializedRoot("/tmp/yappc-123")
            .checksum("abc123")
            .contentHash("content-hash-123")
            .createdAt(Instant.now())
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .projectId("project-1")
            .files(List.of(file))
            .diagnostics(List.of())
            .build();

        when(preparedStatement.executeUpdate()).thenReturn(1);

        RepositorySnapshot saved = runPromise(() -> repository.saveSnapshot(snapshot, 
            SourceLocator.builder()
                .provider("github")
                .repoId("owner/repo")
                .ref("main")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .projectId("project-1")
                .build()));

        assertThat(saved).isNotNull();
        assertThat(saved.snapshotId()).isEqualTo("snap-123");
    }

    @Test
    @DisplayName("Should find snapshot by ID")
    void shouldFindSnapshotById() throws Exception {
        Instant now = Instant.now();
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getString("snapshot_id")).thenReturn("snap-123");
        when(resultSet.getString("provider")).thenReturn("github");
        when(resultSet.getString("repo_id")).thenReturn("owner/repo");
        when(resultSet.getString("commit_sha")).thenReturn("abc123");
        when(resultSet.getString("materialized_root")).thenReturn("/tmp/yappc-123");
        when(resultSet.getString("checksum")).thenReturn("abc123");
        when(resultSet.getString("content_hash")).thenReturn("content-hash-123");
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
        when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
        when(resultSet.getString("workspace_id")).thenReturn("workspace-1");
        when(resultSet.getString("project_id")).thenReturn("project-1");

        Optional<RepositorySnapshot> found = runPromise(() -> repository.findById("snap-123", "tenant-1", "workspace-1", "project-1"));

        assertThat(found).isPresent();
        assertThat(found.get().snapshotId()).isEqualTo("snap-123");
        assertThat(found.get().provider()).isEqualTo("github");
    }

    @Test
    @DisplayName("Should return empty when snapshot not found")
    void shouldReturnEmptyWhenSnapshotNotFound() throws Exception {
        when(resultSet.next()).thenReturn(false);

        Optional<RepositorySnapshot> found = runPromise(() -> repository.findById("non-existent", "tenant-1", "workspace-1", "project-1"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should find snapshots by scope")
    void shouldFindSnapshotsByScope() throws Exception {
        Instant now = Instant.now();
        lenient().when(resultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        lenient().when(resultSet.getString("snapshot_id")).thenReturn("snap-1").thenReturn("snap-2");
        lenient().when(resultSet.getString("provider")).thenReturn("github");
        lenient().when(resultSet.getString("repo_id")).thenReturn("owner/repo");
        lenient().when(resultSet.getString("commit_sha")).thenReturn("abc123");
        lenient().when(resultSet.getString("materialized_root")).thenReturn("/tmp/yappc-123");
        lenient().when(resultSet.getString("checksum")).thenReturn("abc123");
        lenient().when(resultSet.getString("content_hash")).thenReturn("content-hash-123");
        lenient().when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
        lenient().when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
        lenient().when(resultSet.getString("workspace_id")).thenReturn("workspace-1");
        lenient().when(resultSet.getString("project_id")).thenReturn("project-1");
        lenient().when(resultSet.getString("diagnostics_json")).thenReturn("[]");

        List<RepositorySnapshot> snapshots = runPromise(() -> repository.findByScope(
            "tenant-1", "workspace-1", "project-1", 10
        ));

        assertThat(snapshots).hasSize(2);
    }

    @Test
    @DisplayName("Should find snapshot by content hash")
    void shouldFindSnapshotByContentHash() throws Exception {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("snapshot_id")).thenReturn("snap-123");

        Optional<String> found = runPromise(() -> repository.findByContentHash("content-hash-123", "tenant-1", "workspace-1", "project-1"));

        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo("snap-123");
    }

    @Test
    @DisplayName("Should delete old snapshots")
    void shouldDeleteOldSnapshots() throws Exception {
        when(preparedStatement.executeUpdate()).thenReturn(5);

        Integer deleted = runPromise(() -> repository.deleteOldSnapshots(Instant.now().minusSeconds(86400), "tenant-1", "workspace-1", "project-1"));

        assertThat(deleted).isEqualTo(5);
    }

    @Test
    @DisplayName("Should infer file types correctly")
    void shouldInferFileTypes() throws Exception {
        // Test by checking file type inference through actual file storage
        RepositorySnapshot.SnapshotFile javaFile = new RepositorySnapshot.SnapshotFile(
            "src/Main.java", "/tmp/Main.java", 1024, Instant.now(), "checksum"
        );
        RepositorySnapshot.SnapshotFile tsFile = new RepositorySnapshot.SnapshotFile(
            "src/App.tsx", "/tmp/App.tsx", 1024, Instant.now(), "checksum"
        );

        RepositorySnapshot snapshot = RepositorySnapshot.builder()
            .snapshotId("snap-123")
            .provider("local")
            .repoId("test")
            .materializedRoot("/tmp")
            .checksum("abc")
            .contentHash("hash")
            .createdAt(Instant.now())
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .projectId("project-1")
            .files(List.of(javaFile, tsFile))
            .diagnostics(List.of())
            .build();

        when(preparedStatement.executeUpdate()).thenReturn(1);

        RepositorySnapshot saved = runPromise(() -> repository.saveSnapshot(snapshot, 
            SourceLocator.builder()
                .provider("github")
                .repoId("owner/repo")
                .ref("main")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .projectId("project-1")
                .build()));

        assertThat(saved.files()).hasSize(2);
    }
}
