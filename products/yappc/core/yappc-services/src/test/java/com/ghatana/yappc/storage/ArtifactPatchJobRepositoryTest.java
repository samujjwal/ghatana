package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.patch.ArtifactPatchJobService;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * @doc.type test
 * @doc.purpose Unit tests for durable artifact patch job persistence.
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArtifactPatchJobRepository Tests")
class ArtifactPatchJobRepositoryTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private ArtifactPatchJobRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new ArtifactPatchJobRepository(dataSource, new ObjectMapper(), Runnable::run);
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet);
    }

    @Test
    @DisplayName("Should persist patch job lifecycle state")
    void shouldPersistPatchJob() throws Exception {
        Instant now = Instant.now();
        ArtifactPatchJobService.PatchJob job = new ArtifactPatchJobService.PatchJob(
            "job-1",
            "tenant-1",
            "workspace-1",
            "project-1",
            "plan-1",
            "snapshot-1",
            ArtifactPatchJobService.PatchJobStatus.RUNNING,
            25,
            "Generating patches",
            now,
            now,
            null,
            null,
            Map.of("source", "test")
        );

        when(preparedStatement.executeUpdate()).thenReturn(1);

        ArtifactPatchJobService.PatchJob saved = runPromise(() -> repository.save(job));

        assertThat(saved.jobId()).isEqualTo("job-1");
        assertThat(saved.status()).isEqualTo(ArtifactPatchJobService.PatchJobStatus.RUNNING);
    }

    @Test
    @DisplayName("Should find patch job by ID")
    void shouldFindPatchJobById() throws Exception {
        Instant now = Instant.now();
        when(resultSet.next()).thenReturn(true);
        stubJobRow(now);

        Optional<ArtifactPatchJobService.PatchJob> found = runPromise(() -> repository.findById("job-1"));

        assertThat(found).isPresent();
        assertThat(found.get().jobId()).isEqualTo("job-1");
        assertThat(found.get().patchSetId()).isEqualTo("patchset-1");
        assertThat(found.get().metadata()).containsEntry("source", "test");
    }

    @Test
    @DisplayName("Should list patch jobs by full scope")
    void shouldListPatchJobsByScope() throws Exception {
        Instant now = Instant.now();
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        stubJobRow(now);

        List<ArtifactPatchJobService.PatchJob> jobs = runPromise(() -> repository.listByScope(
            "tenant-1",
            "workspace-1",
            "project-1",
            25
        ));

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).tenantId()).isEqualTo("tenant-1");
        assertThat(jobs.get(0).workspaceId()).isEqualTo("workspace-1");
        assertThat(jobs.get(0).projectId()).isEqualTo("project-1");
    }

    private void stubJobRow(Instant now) throws Exception {
        lenient().when(resultSet.getString("job_id")).thenReturn("job-1");
        lenient().when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
        lenient().when(resultSet.getString("workspace_id")).thenReturn("workspace-1");
        lenient().when(resultSet.getString("project_id")).thenReturn("project-1");
        lenient().when(resultSet.getString("plan_id")).thenReturn("plan-1");
        lenient().when(resultSet.getString("snapshot_id")).thenReturn("snapshot-1");
        lenient().when(resultSet.getString("status")).thenReturn("COMPLETED");
        lenient().when(resultSet.getInt("progress_percent")).thenReturn(100);
        lenient().when(resultSet.getString("status_message")).thenReturn("Patch generation completed");
        lenient().when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
        lenient().when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(now));
        lenient().when(resultSet.getTimestamp("completed_at")).thenReturn(Timestamp.from(now));
        lenient().when(resultSet.getString("patch_set_id")).thenReturn("patchset-1");
        lenient().when(resultSet.getString("metadata_json")).thenReturn("{\"source\":\"test\"}");
    }
}
