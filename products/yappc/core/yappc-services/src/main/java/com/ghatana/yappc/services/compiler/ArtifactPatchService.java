package com.ghatana.yappc.services.compiler;

import com.ghatana.yappc.domain.artifact.ResidualIslandDto;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import com.ghatana.yappc.services.artifact.compileback.ChangePlanService;
import com.ghatana.yappc.services.artifact.compileback.PatchSetService;
import com.ghatana.yappc.services.patch.PatchReviewService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose Java-governed patch workflow orchestrator with TS patch worker invocation and residual overlap checks
 * @doc.layer service
 * @doc.pattern Orchestrator
 * 
 * P1: Orchestrates patch workflow with:
 * - Java-governed change plan validation
 * - TS patch worker invocation for TS/React emitters
 * - Residual overlap checks to prevent patch conflicts
 * - Scope enforcement for tenant/workspace/project
 */
public final class ArtifactPatchService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactPatchService.class);

    private final ChangePlanService changePlanService;
    private final PatchSetService patchSetService;
    private final PatchReviewService patchReviewService;
    private final TsPatchWorker tsPatchWorker;
    private final Executor executor;

    public ArtifactPatchService(
        ChangePlanService changePlanService,
        PatchSetService patchSetService,
        PatchReviewService patchReviewService,
        TsPatchWorker tsPatchWorker,
        Executor executor
    ) {
        this.changePlanService = changePlanService;
        this.patchSetService = patchSetService;
        this.patchReviewService = patchReviewService;
        this.tsPatchWorker = tsPatchWorker;
        this.executor = executor;
    }

    /**
     * P1: Execute full patch workflow with Java governance and TS patch worker invocation.
     * 
     * Workflow:
     * 1. Create change plan
     * 2. Validate change plan with Java governance
     * 3. Generate patch set
     * 4. Check residual overlaps
     * 5. Invoke TS patch worker for TS/React files
     * 6. Create review bundle
     */
    public Promise<PatchWorkflowResult> executePatchWorkflow(
        PatchWorkflowRequest request,
        ArtifactRequestScope scope
    ) {
        // Step 1: Create change plan
        return changePlanService.createChangePlan(scope, request.baseModelId(), request.targetModelId())
            .then(plan -> {
                    log.info("Created change plan {} for base={} target={}", plan.planId(), request.baseModelId(), request.targetModelId());
                    
                    // Step 2: Validate change plan with Java governance
                    return changePlanService.validateChangePlan(plan.planId(), scope.tenantId())
                        .then(validation -> {
                            if (!validation.valid()) {
                                return Promise.of(new PatchWorkflowResult(
                                    plan.planId(),
                                    null,
                                    null,
                                    null,
                                    false,
                                    "Validation failed: " + validation.errors(),
                                    Instant.now()
                                ));
                            }
                            
                            // Step 3: Generate patch set
                            return patchSetService.generatePatchSet(scope, plan.planId())
                                .then(patchSet -> {
                                    log.info("Generated patch set {} with {} patches", patchSet.patchSetId(), patchSet.patches().size());
                                    
                                    // Step 4: Check residual overlaps
                                    return checkResidualOverlaps(patchSet, request.residualIslands(), scope)
                                        .then(overlapCheck -> {
                                            if (overlapCheck.hasOverlaps()) {
                                                return Promise.of(new PatchWorkflowResult(
                                                    plan.planId(),
                                                    patchSet.patchSetId(),
                                                    null,
                                                    null,
                                                    false,
                                                    "Residual overlap detected: " + overlapCheck.overlapDetails(),
                                                    Instant.now()
                                                ));
                                            }
                                            
                                            // Step 5: Invoke TS patch worker for TS/React files
                                            return invokeTsPatchWorker(patchSet, request, scope)
                                                .then(tsPatchResult -> {
                                                    log.info("TS patch worker completed: {}", tsPatchResult.status());
                                                    
                                                    // Step 6: Create review bundle
                                                    var createRequest = new PatchReviewService.CreateReviewRequest(
                                                        scope.tenantId(),
                                                        scope.projectId(),
                                                        request.snapshotId(),
                                                        request.versionId(),
                                                        patchSet.patchSetId(),
                                                        tsPatchResult.patchMetadata()
                                                    );
                                                    
                                                    return patchReviewService.createReviewBundle(createRequest)
                                                        .map(bundle -> new PatchWorkflowResult(
                                                            plan.planId(),
                                                            patchSet.patchSetId(),
                                                            bundle.id(),
                                                            tsPatchResult.status(),
                                                            true,
                                                            null,
                                                            Instant.now()
                                                        ));
                                                });
                                        });
                                });
                        });
                });
    }

    /**
     * P1: Check for residual overlaps between patch set and existing residual islands.
     */
    private Promise<OverlapCheckResult> checkResidualOverlaps(
        PatchSetService.PatchSet patchSet,
        List<ResidualIslandDto> residualIslands,
        ArtifactRequestScope scope
    ) {
        return Promise.ofBlocking(executor, () -> {
            List<String> overlapDetails = new ArrayList<>();
            
            for (var patch : patchSet.patches()) {
                String filePath = patch.relativePath();
                
                // Check if this file has residual islands
                for (ResidualIslandDto residual : residualIslands) {
                    // P1: Simple overlap check - in production would use more sophisticated range analysis
                    if (residual.originalSource().contains(filePath)) {
                        overlapDetails.add(String.format(
                            "File %s has residual island %s (type=%s)",
                            filePath, residual.id(), residual.islandType()
                        ));
                    }
                }
            }
            
            return new OverlapCheckResult(
                !overlapDetails.isEmpty(),
                String.join("; ", overlapDetails)
            );
        });
    }

    /**
     * P1: Invoke TS patch worker for TypeScript/React files in the patch set.
     */
    private Promise<TsPatchResult> invokeTsPatchWorker(
        PatchSetService.PatchSet patchSet,
        PatchWorkflowRequest request,
        ArtifactRequestScope scope
    ) {
        // Filter TS/React files from patch set
        List<PatchSetService.TextPatch> tsPatches = new ArrayList<>();
        for (var patch : patchSet.patches()) {
            String filePath = patch.relativePath().toLowerCase();
            if (filePath.endsWith(".ts") || filePath.endsWith(".tsx") ||
                filePath.endsWith(".js") || filePath.endsWith(".jsx")) {
                tsPatches.add(patch);
            }
        }
        
        if (tsPatches.isEmpty()) {
            return Promise.of(new TsPatchResult("no-ts-files", Map.of()));
        }
        
        // Invoke TS patch worker
        return tsPatchWorker.applyPatches(tsPatches, request.snapshotId(), scope);
    }

    public record PatchWorkflowRequest(
        String baseModelId,
        String targetModelId,
        String snapshotId,
        String versionId,
        List<ResidualIslandDto> residualIslands
    ) {}

    public record PatchWorkflowResult(
        String planId,
        String patchSetId,
        String reviewBundleId,
        String tsPatchStatus,
        boolean success,
        String error,
        Instant completedAt
    ) {}

    public record OverlapCheckResult(
        boolean hasOverlaps,
        String overlapDetails
    ) {}

    public record TsPatchResult(
        String status,
        Map<String, Object> patchMetadata
    ) {}

    /**
     * P1: TS patch worker interface for TypeScript/React patch application.
     * Implemented by the patch-coordinator.ts worker library.
     */
    @FunctionalInterface
    public interface TsPatchWorker {
        Promise<TsPatchResult> applyPatches(
            List<PatchSetService.TextPatch> patches,
            String snapshotId,
            ArtifactRequestScope scope
        );
    }
}
