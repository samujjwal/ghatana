package com.ghatana.yappc.services.compiler;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.services.artifact.ArtifactPatchService;
import com.ghatana.yappc.services.artifact.ChangePlanService;
import com.ghatana.yappc.services.artifact.PatchSetService;
import com.ghatana.yappc.services.artifact.PatchReviewService;
import com.ghatana.yappc.storage.PatchSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @doc.type test
 * @doc.pround-trip tests for ArtifactPatchService covering change plan creation,
 *              patch set generation, review bundle creation, and rollback.
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArtifactPatchService Round-Trip Tests")
class ArtifactPatchServiceRoundTripTest extends EventloopTestBase {

    @Mock
    private ChangePlanService changePlanService;

    @Mock
    private PatchSetService patchSetService;

    @Mock
    private PatchReviewService patchReviewService;

    @Mock
    private PatchSetRepository patchSetRepository;

    @Mock
    private ArtifactPatchService.TsPatchWorker tsPatchWorker;

    private ArtifactPatchService service;

    @BeforeEach
    void setUp() {
        service = new ArtifactPatchService(changePlanService, patchSetService, patchReviewService, patchSetRepository, tsPatchWorker);

        // Mock service responses
        when(changePlanService.saveChangePlan(any()))
            .thenAnswer(invocation -> io.activej.promise.Promise.of(invocation.getArgument(0)));
        when(patchSetService.savePatchSet(any()))
            .thenAnswer(invocation -> io.activej.promise.Promise.of(invocation.getArgument(0)));
        when(patchReviewService.saveReviewBundle(any()))
            .thenAnswer(invocation -> io.activej.promise.Promise.of(invocation.getArgument(0)));
    }

    @Test
    @DisplayName("Should execute full patch workflow round-trip")
    void shouldExecuteFullPatchWorkflowRoundTrip() {
        // Create change plan
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node-1", "component", "Button", "/src/Button.tsx", null,
            Map.of(), List.of("ui"), "tenant-1", "project-1", null,
            "extractor-1", "1.0", 0.9, "test", List.of(), List.of(), null, null
        );

        ArtifactPatchService.ChangePlan changePlan = new ArtifactPatchService.ChangePlan(
            "plan-1",
            List.of(new ArtifactPatchService.ChangeOp("op-1", "rename-component", "node-1", "Button", "NewButton", 0.9, null)),
            "tenant-1",
            "workspace-1",
            "project-1",
            "user-1"
        );

        ArtifactPatchService.PatchWorkflowRequest request = new ArtifactPatchService.PatchWorkflowRequest(
            changePlan,
            List.of(node),
            Map.of(),
            "tenant-1",
            "workspace-1",
            "project-1",
            "user-1"
        );

        // Execute workflow
        ArtifactPatchService.PatchWorkflowResult result = runPromise(() -> service.executePatchWorkflow(request));

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.changePlanId()).isEqualTo("plan-1");
        assertThat(result.patchSetId()).isNotNull();
        assertThat(result.reviewBundleId()).isNotNull();
    }

    @Test
    @DisplayName("Should validate change plan before execution")
    void shouldValidateChangePlanBeforeExecution() {
        ArtifactPatchService.ChangePlan changePlan = new ArtifactPatchService.ChangePlan(
            "plan-1",
            List.of(new ArtifactPatchService.ChangeOp("op-1", "rename-component", "node-1", "Button", "NewButton", 0.9, null)),
            "tenant-1",
            "workspace-1",
            "project-1",
            "user-1"
        );

        ArtifactPatchService.ValidationResult result = runPromise(() -> service.validateChangePlan(changePlan));

        assertThat(result).isNotNull();
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("Should detect residual overlaps in change plan")
    void shouldDetectResidualOverlaps() {
        ArtifactPatchService.ChangePlan changePlan = new ArtifactPatchService.ChangePlan(
            "plan-1",
            List.of(new ArtifactPatchService.ChangeOp("op-1", "rename-component", "residual-1", "Button", "NewButton", 0.9, null)),
            "tenant-1",
            "workspace-1",
            "project-1",
            "user-1"
        );

        // Mock residual islands
        when(patchSetService.findResidualIslandsBySnapshotId(anyString()))
            .thenReturn(io.activej.promise.Promise.of(List.of(
                new com.ghatana.yappc.domain.artifact.ResidualIslandDto(
                    "residual-1", "unknown", "Summary", "source", "span",
                    "checksum", "ref", "reason", 0.5, true, 0.5, Map.of(), 1,
                    "tenant-1", "project-1", "workspace-1", "snapshot-1"
                )
            )));

        ArtifactPatchService.ValidationResult result = runPromise(() -> service.validateChangePlan(changePlan));

        assertThat(result).isNotNull();
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.code().equals("RESIDUAL_OVERLAP"));
    }
}
