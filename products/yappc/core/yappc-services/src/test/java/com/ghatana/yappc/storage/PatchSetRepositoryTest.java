package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.artifact.compileback.PatchSetService;
import com.ghatana.yappc.services.patch.PatchReviewService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * @doc.type test
 * @doc.purpose Unit tests for PatchSetRepository covering patch set persistence,
 *              review bundle storage, and rollback metadata.
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PatchSetRepository Tests")
class PatchSetRepositoryTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private ObjectMapper objectMapper;
    private Executor executor;
    private PatchSetRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        executor = Runnable::run;
        repository = new PatchSetRepository(dataSource, objectMapper, executor);

        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet);
    }

    @Test
    @DisplayName("Should save patch set with patches")
    void shouldSavePatchSet() throws Exception {
        PatchSetService.PatchStats stats = new PatchSetService.PatchStats(
            2, 1, 1, 0, 0
        );
        PatchSetService.TextPatch patch = new PatchSetService.TextPatch(
            "patch-1", "src/App.tsx", "diff content", List.of(),
            true, "op-1", "react-emitter", "base-checksum", "target-checksum",
            PatchSetService.PatchValidationStatus.VALIDATED
        );
        PatchSetService.PatchSet patchSet = new PatchSetService.PatchSet(
            "patchset-123", "tenant-1", "workspace-1", "project-1",
            "plan-123", "snapshot-123", PatchSetService.PatchSetStatus.PENDING,
            List.of(patch), List.of(), List.of(),
            stats, Instant.now(), "system", null, null
        );

        when(preparedStatement.executeUpdate()).thenReturn(1);

        PatchSetService.PatchSet saved = runPromise(() -> repository.savePatchSet(patchSet));

        assertThat(saved).isNotNull();
        assertThat(saved.patchSetId()).isEqualTo("patchset-123");
    }

    @Test
    @DisplayName("Should find patch set by ID")
    void shouldFindPatchSetById() throws Exception {
        Instant now = Instant.now();
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getString("patch_set_id")).thenReturn("patchset-123");
        when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
        when(resultSet.getString("workspace_id")).thenReturn("workspace-1");
        when(resultSet.getString("project_id")).thenReturn("project-1");
        when(resultSet.getString("plan_id")).thenReturn("plan-123");
        when(resultSet.getString("snapshot_id")).thenReturn("snapshot-123");
        when(resultSet.getString("status")).thenReturn("PENDING");
        when(resultSet.getString("preserved_residuals_json")).thenReturn("[]");
        when(resultSet.getString("review_required_patches_json")).thenReturn("[]");
        when(resultSet.getString("stats_json")).thenReturn("{\"totalPatches\":2,\"autoApplicable\":1,\"requiresReview\":1,\"conflicted\":0,\"preservedResiduals\":0}");
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
        when(resultSet.getString("created_by")).thenReturn("system");

        Optional<PatchSetService.PatchSet> found = runPromise(() -> repository.findPatchSetById("patchset-123"));

        assertThat(found).isPresent();
        assertThat(found.get().patchSetId()).isEqualTo("patchset-123");
    }

    @Test
    @DisplayName("Should list patch sets by scope")
    void shouldListPatchSetsByScope() throws Exception {
        Instant now = Instant.now();
        lenient().when(resultSet.next()).thenReturn(true).thenReturn(false);
        lenient().when(resultSet.getString("patch_set_id")).thenReturn("patchset-1");
        lenient().when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
        lenient().when(resultSet.getString("workspace_id")).thenReturn("workspace-1");
        lenient().when(resultSet.getString("project_id")).thenReturn("project-1");
        lenient().when(resultSet.getString("status")).thenReturn("PENDING");
        lenient().when(resultSet.getString("preserved_residuals_json")).thenReturn("[]");
        lenient().when(resultSet.getString("review_required_patches_json")).thenReturn("[]");
        lenient().when(resultSet.getString("stats_json")).thenReturn("{\"totalPatches\":0,\"autoApplicable\":0,\"requiresReview\":0,\"conflicted\":0,\"preservedResiduals\":0}");
        lenient().when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(now));

        List<PatchSetService.PatchSet> patchSets = runPromise(() -> repository.listPatchSetsByScope(
            "tenant-1", "workspace-1", "project-1", 10
        ));

        assertThat(patchSets).hasSize(1);
    }

    @Test
    @DisplayName("Should update patch set status")
    void shouldUpdatePatchSetStatus() throws Exception {
        when(preparedStatement.executeUpdate()).thenReturn(1);

        runPromise(() -> repository.updatePatchSetStatus(
            "patchset-123", PatchSetService.PatchSetStatus.APPROVED
        ));
    }

    @Test
    @DisplayName("Should mark patch set as applied")
    void shouldMarkPatchSetApplied() throws Exception {
        when(preparedStatement.executeUpdate()).thenReturn(1);

        runPromise(() -> repository.markPatchSetApplied("patchset-123", "user-1"));
    }

    @Test
    @DisplayName("Should save and find review bundle")
    void shouldSaveAndFindReviewBundle() throws Exception {
        Instant now = Instant.now();
        PatchReviewService.ReviewBundle bundle = new PatchReviewService.ReviewBundle(
            "bundle-123", "tenant-1", "project-1", "snapshot-123", "version-123",
            "patchset-123", "PENDING", null, null, now, java.util.Map.of()
        );

        lenient().when(preparedStatement.executeUpdate()).thenReturn(1);
        lenient().when(resultSet.next()).thenReturn(true);
        lenient().when(resultSet.getString("bundle_id")).thenReturn("bundle-123");
        lenient().when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
        lenient().when(resultSet.getString("project_id")).thenReturn("project-1");
        lenient().when(resultSet.getString("snapshot_id")).thenReturn("snapshot-123");
        lenient().when(resultSet.getString("version_id")).thenReturn("version-123");
        lenient().when(resultSet.getString("patch_set_id")).thenReturn("patchset-123");
        lenient().when(resultSet.getString("status")).thenReturn("PENDING");
        lenient().when(resultSet.getString("metadata_json")).thenReturn("{}");
        lenient().when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(now));

        // Save
        runPromise(() -> repository.saveReviewBundle(bundle));

        // Find
        Optional<PatchReviewService.ReviewBundle> found = runPromise(() -> repository.findReviewBundleById("bundle-123"));

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("bundle-123");
    }

    @Test
    @DisplayName("Should list review bundles by project")
    void shouldListReviewBundlesByProject() throws Exception {
        Instant now = Instant.now();
        lenient().when(resultSet.next()).thenReturn(true).thenReturn(false);
        lenient().when(resultSet.getString("bundle_id")).thenReturn("bundle-1");
        lenient().when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
        lenient().when(resultSet.getString("project_id")).thenReturn("project-1");
        lenient().when(resultSet.getString("snapshot_id")).thenReturn("snapshot-1");
        lenient().when(resultSet.getString("version_id")).thenReturn("version-1");
        lenient().when(resultSet.getString("patch_set_id")).thenReturn("patchset-1");
        lenient().when(resultSet.getString("status")).thenReturn("APPROVED");
        lenient().when(resultSet.getString("metadata_json")).thenReturn("{}");
        lenient().when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(now));

        List<PatchReviewService.ReviewBundle> bundles = runPromise(() -> repository.listReviewBundlesByProject(
            "tenant-1", "project-1"
        ));

        assertThat(bundles).hasSize(1);
        assertThat(bundles.get(0).status()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Should save rollback metadata")
    void shouldSaveRollbackMetadata() throws Exception {
        Instant now = Instant.now();
        PatchSetService.RollbackResult rollback = new PatchSetService.RollbackResult(
            "patchset-123", true, "original-123", "rollback-123",
            "user-1", now, "Rollback reason", null
        );

        when(preparedStatement.executeUpdate()).thenReturn(1);

        runPromise(() -> repository.saveRollbackMetadata(rollback));
    }
}
