package com.ghatana.yappc.services.patch;

import com.ghatana.yappc.storage.PatchSetRepository;
import com.ghatana.yappc.services.artifact.compileback.PatchSetService;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @doc.type class
 * @doc.purpose Patch review lifecycle for diff review approve reject apply and rollback flows
 * @doc.layer service
 * @doc.pattern Service
 */
public final class PatchReviewService {

    private final Map<String, ReviewBundle> reviewBundles = new ConcurrentHashMap<>();
    private final PatchSetRepository repository;

    public PatchReviewService() {
        this(null);
    }

    public PatchReviewService(PatchSetRepository repository) {
        this.repository = repository;
    }

    public Promise<ReviewBundle> createReviewBundle(CreateReviewRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (repository != null) {
            return repository.findPatchSetById(request.patchSetId())
                .then(patchSet -> patchSet
                    .map(value -> createPersistentReviewBundle(request, value))
                    .orElseGet(() -> Promise.ofException(new IllegalArgumentException(
                        "Unknown patch set " + request.patchSetId()))));
        }
        return Promise.of(createLocalReviewBundle(request, request.metadata() == null ? Map.of() : request.metadata()));
    }

    private Promise<ReviewBundle> createPersistentReviewBundle(
        CreateReviewRequest request,
        PatchSetService.PatchSet patchSet
    ) {
        ensurePatchSetScope(request, patchSet);
        ReviewBundle bundle = createLocalReviewBundle(request, buildDiffReviewMetadata(request, patchSet));
        reviewBundles.put(bundle.id(), bundle);
        return repository.saveReviewBundle(bundle)
            .then($ -> repository.updatePatchSetStatus(
                patchSet.patchSetId(),
                patchSet.requiresReview()
                    ? PatchSetService.PatchSetStatus.REVIEW_REQUIRED
                    : PatchSetService.PatchSetStatus.PENDING))
            .map($ -> bundle);
    }

    private ReviewBundle createLocalReviewBundle(CreateReviewRequest request, Map<String, Object> metadata) {
        ReviewBundle bundle = new ReviewBundle(
            UUID.randomUUID().toString(),
            request.tenantId(),
            request.projectId(),
            request.snapshotId(),
            request.versionId(),
            request.patchSetId(),
            "PENDING",
            null,
            null,
            Instant.now(),
            Map.copyOf(metadata)
        );
        reviewBundles.put(bundle.id(), bundle);
        return bundle;
    }

    public Promise<ReviewBundle> approve(String bundleId, String reviewer) {
        return transition(bundleId, "APPROVED", reviewer);
    }

    public Promise<ReviewBundle> reject(String bundleId, String reviewer) {
        return transition(bundleId, "REJECTED", reviewer);
    }

    public Promise<ReviewBundle> apply(String bundleId) {
        return transition(bundleId, "APPLIED", null)
            .then(bundle -> repository == null
                ? Promise.of(bundle)
                : repository.markPatchSetApplied(bundle.patchSetId(), actorOrSystem(bundle.reviewedBy()))
                    .map($ -> bundle));
    }

    public Promise<ReviewBundle> rollback(String bundleId) {
        return transition(bundleId, "ROLLED_BACK", null)
            .then(bundle -> repository == null
                ? Promise.of(bundle)
                : repository.updatePatchSetStatus(
                        bundle.patchSetId(),
                        PatchSetService.PatchSetStatus.ROLLED_BACK)
                    .map($ -> bundle));
    }

    public Promise<List<ReviewBundle>> listByProject(String tenantId, String projectId) {
        if (repository != null) {
            return repository.listReviewBundlesByProject(tenantId, projectId);
        }
        return Promise.of(reviewBundles.values().stream()
            .filter(bundle -> bundle.tenantId().equals(tenantId) && bundle.projectId().equals(projectId))
            .toList());
    }

    private Promise<ReviewBundle> transition(String bundleId, String status, String reviewer) {
        return getBundle(bundleId)
            .then(bundle -> {
                ReviewBundle updated = new ReviewBundle(
                    bundle.id(),
                    bundle.tenantId(),
                    bundle.projectId(),
                    bundle.snapshotId(),
                    bundle.versionId(),
                    bundle.patchSetId(),
                    status,
                    reviewer != null ? reviewer : bundle.reviewedBy(),
                    Instant.now(),
                    bundle.createdAt(),
                    bundle.metadata()
                );
                reviewBundles.put(bundleId, updated);
                if (repository != null) {
                    return repository.saveReviewBundle(updated)
                        .then($ -> updatePatchSetForReviewDecision(updated))
                        .map($ -> updated);
                }
                return Promise.of(updated);
            });
    }

    private Promise<Void> updatePatchSetForReviewDecision(ReviewBundle bundle) {
        PatchSetService.PatchSetStatus status = switch (bundle.status()) {
            case "APPROVED" -> PatchSetService.PatchSetStatus.APPROVED;
            case "REJECTED" -> PatchSetService.PatchSetStatus.REJECTED;
            default -> null;
        };
        if (status == null) {
            return Promise.complete();
        }
        return repository.updatePatchSetStatus(bundle.patchSetId(), status);
    }

    private Promise<ReviewBundle> getBundle(String bundleId) {
        ReviewBundle bundle = reviewBundles.get(bundleId);
        if (bundle != null) {
            return Promise.of(bundle);
        }
        if (repository != null) {
            return repository.findReviewBundleById(bundleId)
                .then(found -> found
                    .map(Promise::of)
                    .orElseGet(() -> Promise.ofException(new IllegalArgumentException("Unknown review bundle " + bundleId))));
        }
        return Promise.ofException(new IllegalArgumentException("Unknown review bundle " + bundleId));
    }

    private static void ensurePatchSetScope(CreateReviewRequest request, PatchSetService.PatchSet patchSet) {
        if (!request.tenantId().equals(patchSet.tenantId()) || !request.projectId().equals(patchSet.projectId())) {
            throw new SecurityException("Patch set scope does not match review request scope");
        }
    }

    private static Map<String, Object> buildDiffReviewMetadata(
        CreateReviewRequest request,
        PatchSetService.PatchSet patchSet
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        List<Map<String, Object>> files = new ArrayList<>();
        int totalAdded = 0;
        int totalRemoved = 0;
        for (PatchSetService.TextPatch patch : patchSet.patches()) {
            int added = countDiffLines(patch.diff(), '+');
            int removed = countDiffLines(patch.diff(), '-');
            totalAdded += added;
            totalRemoved += removed;
            files.add(Map.of(
                "patchId", patch.patchId(),
                "relativePath", patch.relativePath(),
                "linesAdded", added,
                "linesRemoved", removed,
                "validationStatus", patch.validationStatus().name(),
                "requiresReview", patch.validationStatus() == PatchSetService.PatchValidationStatus.REVIEW_REQUIRED));
        }
        metadata.put("diffReview", Map.of(
            "patchSetId", patchSet.patchSetId(),
            "planId", patchSet.planId(),
            "snapshotId", patchSet.snapshotId(),
            "status", patchSet.status().name(),
            "fileCount", files.size(),
            "linesAdded", totalAdded,
            "linesRemoved", totalRemoved,
            "reviewRequiredPatches", patchSet.reviewRequiredPatches(),
            "files", files));
        return metadata;
    }

    private static int countDiffLines(String diff, char marker) {
        if (diff == null || diff.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String line : diff.split("\\R")) {
            if (line.length() > 1
                    && line.charAt(0) == marker
                    && !line.startsWith("+++")
                    && !line.startsWith("---")) {
                count++;
            }
        }
        return count;
    }

    private static String actorOrSystem(String actor) {
        return actor == null || actor.isBlank() ? "system" : actor;
    }

    public record CreateReviewRequest(
        String tenantId,
        String projectId,
        String snapshotId,
        String versionId,
        String patchSetId,
        Map<String, Object> metadata
    ) {}

    public record ReviewBundle(
        String id,
        String tenantId,
        String projectId,
        String snapshotId,
        String versionId,
        String patchSetId,
        String status,
        String reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        Map<String, Object> metadata
    ) {}
}
