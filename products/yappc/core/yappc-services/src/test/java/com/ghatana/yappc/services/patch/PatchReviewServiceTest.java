package com.ghatana.yappc.services.patch;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.artifact.compileback.PatchSetService;
import com.ghatana.yappc.storage.PatchSetRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies patch diff review lifecycle persistence and status transitions
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("PatchReviewService")
class PatchReviewServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("create review bundle includes diff metadata from patch set")
    void createReviewBundleIncludesDiffMetadataFromPatchSet() {
        PatchSetRepository repository = mock(PatchSetRepository.class);
        PatchSetService.PatchSet patchSet = patchSet(PatchSetService.PatchSetStatus.REVIEW_REQUIRED);
        when(repository.findPatchSetById("patchset-1")).thenReturn(Promise.of(Optional.of(patchSet)));
        when(repository.saveReviewBundle(org.mockito.ArgumentMatchers.any())).thenReturn(Promise.complete());
        when(repository.updatePatchSetStatus("patchset-1", PatchSetService.PatchSetStatus.REVIEW_REQUIRED))
                .thenReturn(Promise.complete());

        PatchReviewService service = new PatchReviewService(repository);

        PatchReviewService.ReviewBundle bundle = runPromise(() -> service.createReviewBundle(
                new PatchReviewService.CreateReviewRequest(
                        "tenant-1",
                        "project-1",
                        "snapshot-1",
                        "version-1",
                        "patchset-1",
                        Map.of("source", "evolve"))));

        assertThat(bundle.status()).isEqualTo("PENDING");
        assertThat(bundle.metadata()).containsKey("diffReview");
        @SuppressWarnings("unchecked")
        Map<String, Object> diffReview = (Map<String, Object>) bundle.metadata().get("diffReview");
        assertThat(diffReview).containsEntry("patchSetId", "patchset-1");
        assertThat(diffReview).containsEntry("fileCount", 1);
        assertThat(diffReview).containsEntry("linesAdded", 2);
        assertThat(diffReview).containsEntry("linesRemoved", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) diffReview.get("files");
        assertThat(files.get(0)).containsEntry("relativePath", "src/App.tsx");
        verify(repository).updatePatchSetStatus("patchset-1", PatchSetService.PatchSetStatus.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("approve reject and rollback update persisted patch set status")
    void approveRejectAndRollbackUpdatePersistedPatchSetStatus() {
        PatchSetRepository repository = mock(PatchSetRepository.class);
        PatchReviewService.ReviewBundle pending = new PatchReviewService.ReviewBundle(
                "bundle-1",
                "tenant-1",
                "project-1",
                "snapshot-1",
                "version-1",
                "patchset-1",
                "PENDING",
                null,
                null,
                Instant.parse("2026-05-26T22:45:00Z"),
                Map.of());
        when(repository.findReviewBundleById("bundle-1")).thenReturn(Promise.of(Optional.of(pending)));
        when(repository.saveReviewBundle(org.mockito.ArgumentMatchers.any())).thenReturn(Promise.complete());
        when(repository.updatePatchSetStatus("patchset-1", PatchSetService.PatchSetStatus.APPROVED))
                .thenReturn(Promise.complete());
        when(repository.updatePatchSetStatus("patchset-1", PatchSetService.PatchSetStatus.REJECTED))
                .thenReturn(Promise.complete());
        when(repository.updatePatchSetStatus("patchset-1", PatchSetService.PatchSetStatus.ROLLED_BACK))
                .thenReturn(Promise.complete());

        PatchReviewService service = new PatchReviewService(repository);

        PatchReviewService.ReviewBundle approved = runPromise(() -> service.approve("bundle-1", "reviewer-1"));
        PatchReviewService.ReviewBundle rejected = runPromise(() -> service.reject("bundle-1", "reviewer-2"));
        PatchReviewService.ReviewBundle rolledBack = runPromise(() -> service.rollback("bundle-1"));

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rolledBack.status()).isEqualTo("ROLLED_BACK");
        verify(repository).updatePatchSetStatus("patchset-1", PatchSetService.PatchSetStatus.APPROVED);
        verify(repository).updatePatchSetStatus("patchset-1", PatchSetService.PatchSetStatus.REJECTED);
        verify(repository).updatePatchSetStatus("patchset-1", PatchSetService.PatchSetStatus.ROLLED_BACK);
    }

    private static PatchSetService.PatchSet patchSet(PatchSetService.PatchSetStatus status) {
        return new PatchSetService.PatchSet(
                "patchset-1",
                "tenant-1",
                "workspace-1",
                "project-1",
                "plan-1",
                "snapshot-1",
                status,
                List.of(new PatchSetService.TextPatch(
                        "patch-1",
                        "src/App.tsx",
                        """
                        --- a/src/App.tsx
                        +++ b/src/App.tsx
                        -old line
                        +new line
                        +another new line
                        """,
                        List.of(),
                        true,
                        "change-1",
                        "evolve",
                        "base",
                        "target",
                        PatchSetService.PatchValidationStatus.REVIEW_REQUIRED)),
                List.of(),
                List.of("patch-1"),
                new PatchSetService.PatchStats(1, 0, 1, 0, 0),
                Instant.parse("2026-05-26T22:45:00Z"),
                "evolve",
                null,
                null);
    }
}
