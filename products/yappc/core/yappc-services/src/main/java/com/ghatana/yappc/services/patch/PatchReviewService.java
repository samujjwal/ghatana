package com.ghatana.yappc.services.patch;

import com.ghatana.yappc.storage.PatchSetRepository;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @doc.type class
 * @doc.purpose In-memory patch review lifecycle for validate review approve apply and rollback flows
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
            request.metadata() == null ? Map.of() : Map.copyOf(request.metadata())
        );
        reviewBundles.put(bundle.id(), bundle);
        if (repository != null) {
            return repository.saveReviewBundle(bundle).map($ -> bundle);
        }
        return Promise.of(bundle);
    }

    public Promise<ReviewBundle> approve(String bundleId, String reviewer) {
        return transition(bundleId, "APPROVED", reviewer);
    }

    public Promise<ReviewBundle> reject(String bundleId, String reviewer) {
        return transition(bundleId, "REJECTED", reviewer);
    }

    public Promise<ReviewBundle> apply(String bundleId) {
        return transition(bundleId, "APPLIED", null);
    }

    public Promise<ReviewBundle> rollback(String bundleId) {
        return transition(bundleId, "ROLLED_BACK", null);
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
                    return repository.saveReviewBundle(updated).map($ -> updated);
                }
                return Promise.of(updated);
            });
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
