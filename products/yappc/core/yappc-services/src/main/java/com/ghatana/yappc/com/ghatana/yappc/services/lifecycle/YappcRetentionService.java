/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle — Data Retention Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TTL-based data retention service for YAPPC lifecycle artifacts.
 *
 * <p>Artifacts are tagged with a {@code created_at} metadata stamp when stored. The
 * retention service inspects that stamp and purges any artifact whose age exceeds the
 * configured maximum age.
 *
 * <p><b>Lifecycle</b></p>
 * <ol>
 *   <li>Store an artifact with its creation timestamp in metadata via
 *       {@link YappcArtifactRepository#storeMetadata}.</li>
 *   <li>Periodically call {@link #purgeExpiredArtifacts(String, PhaseType, Duration)}
 *       to remove artifacts older than {@code maxAge}.</li>
 * </ol>
 *
 * <p>All operations are fully async (ActiveJ {@link Promise}-based) and must not block
 * the event loop.
 *
 * @doc.type class
 * @doc.purpose TTL-based artifact retention and cleanup for YAPPC lifecycle data
 * @doc.layer product
 * @doc.pattern Service
 */
public class YappcRetentionService {

    private static final Logger log = LoggerFactory.getLogger(YappcRetentionService.class);

    /** Metadata key used to store artifact creation time (ISO-8601 instant). */
    public static final String CREATED_AT_KEY = "created_at";

    /** Metadata key used to signal that an artifact is scheduled for deletion. */
    public static final String EXPIRES_AT_KEY = "expires_at";

    private final YappcArtifactRepository repository;

    /**
     * Constructs the retention service.
     *
     * @param repository artifact repository to operate on
     */
    public YappcRetentionService(YappcArtifactRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * Tags an artifact version with its creation timestamp.
     *
     * <p>This should be called immediately after storing a new artifact version so the
     * retention service can track its age later.
     *
     * @param projectId project the artifact belongs to
     * @param phase     lifecycle phase that produced the artifact
     * @param version   artifact version identifier returned by the store
     * @return promise that completes when the metadata is written
     */
    public Promise<Void> tagCreatedAt(String projectId, PhaseType phase, String version) {
        String createdAt = Instant.now().toString();
        return repository.storeMetadata(projectId, phase, version,
                Map.of(CREATED_AT_KEY, createdAt))
                .whenComplete((v, ex) -> {
                    if (ex == null) {
                        log.debug("YappcRetentionService: tagged artifact {}/{}/{} created_at={}",
                                projectId, phase, version, createdAt);
                    } else {
                        log.warn("YappcRetentionService: failed to tag created_at for {}/{}/{}",
                                projectId, phase, version, ex);
                    }
                });
    }

    /**
     * Purges all artifact versions for the given project and phase whose age exceeds
     * {@code maxAge}.
     *
     * <p>Only versions with a valid {@code created_at} metadata stamp are candidates for
     * purge; versions without the stamp are left untouched (safe default).
     *
     * @param projectId project to purge artifacts for
     * @param phase     lifecycle phase to target
     * @param maxAge    maximum allowed age; versions older than this are deleted
     * @return promise of the number of artifact versions deleted
     */
    public Promise<Integer> purgeExpiredArtifacts(String projectId, PhaseType phase, Duration maxAge) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(phase,     "phase must not be null");
        Objects.requireNonNull(maxAge,    "maxAge must not be null");

        log.info("YappcRetentionService.purgeExpiredArtifacts: project={} phase={} maxAge={}",
                projectId, phase, maxAge);

        Instant cutoff = Instant.now().minus(maxAge);

        return repository.listVersions(projectId, phase)
                .then(versions -> purgeVersionsBefore(projectId, phase, versions, cutoff));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Promise<Integer> purgeVersionsBefore(
            String projectId, PhaseType phase, List<String> versions, Instant cutoff) {

        if (versions.isEmpty()) {
            log.debug("YappcRetentionService: no versions to inspect for project={} phase={}", projectId, phase);
            return Promise.of(0);
        }

        List<Promise<Boolean>> deletePromises = new ArrayList<>();

        for (String version : versions) {
            deletePromises.add(
                    repository.getMetadata(projectId, phase, version)
                            .then(meta -> maybePurge(projectId, phase, version, meta, cutoff))
            );
        }

        return Promises.toList(deletePromises)
                .map(results -> (int) results.stream().filter(Boolean.TRUE::equals).count());
    }

    private Promise<Boolean> maybePurge(
            String projectId, PhaseType phase, String version,
            Map<String, String> meta, Instant cutoff) {

        String createdAtStr = meta.get(CREATED_AT_KEY);
        if (createdAtStr == null || createdAtStr.isBlank()) {
            log.debug("YappcRetentionService: skipping version {} (no {} stamp)", version, CREATED_AT_KEY);
            return Promise.of(false);
        }

        Instant createdAt;
        try {
            createdAt = Instant.parse(createdAtStr);
        } catch (Exception e) {
            log.warn("YappcRetentionService: unparseable {} '{}' for version {} — skipping",
                    CREATED_AT_KEY, createdAtStr, version);
            return Promise.of(false);
        }

        if (createdAt.isBefore(cutoff)) {
            log.info("YappcRetentionService: purging expired artifact project={} phase={} version={} age={}",
                    projectId, phase, version, Duration.between(createdAt, Instant.now()));
            return repository.deleteArtifactVersion(projectId, phase, version)
                    .map(unused -> true);
        }

        return Promise.of(false);
    }
}
