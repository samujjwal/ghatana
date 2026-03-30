/*
 * Copyright (c) 2026 Ghatana Technologies
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.storage.InMemoryArtifactStore;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit / integration tests for {@link YappcRetentionService}.
 *
 * <p>Uses {@link InMemoryArtifactStore} as the backing store so no external dependencies
 * are required; all tests run in the ActiveJ event loop via {@link EventloopTestBase}.
 *
 * @doc.type class
 * @doc.purpose Tests for YappcRetentionService TTL-based artifact cleanup
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YappcRetentionService Tests")
class YappcRetentionServiceTest extends EventloopTestBase {

    private InMemoryArtifactStore store;
    private YappcArtifactRepository repository;
    private YappcRetentionService retentionService;

    @BeforeEach
    void setUp() {
        store = new InMemoryArtifactStore();
        repository = new YappcArtifactRepository(store);
        retentionService = new YappcRetentionService(repository);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Tag creation timestamp
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("tagCreatedAt — metadata stamping")
    class TagCreatedAt {

        @Test
        @DisplayName("stores ISO-8601 created_at stamp in artifact metadata")
        void storesCreatedAtStamp() {
            // GIVEN
            String version = runPromise(() -> repository.storeArtifact("proj-1", PhaseType.INTENT, "data".getBytes()));

            // WHEN
            runPromise(() -> retentionService.tagCreatedAt("proj-1", PhaseType.INTENT, version));

            // THEN — metadata must contain a parseable ISO-8601 instant
            Map<String, String> meta = runPromise(() -> repository.getMetadata("proj-1", PhaseType.INTENT, version));
            assertThat(meta).containsKey(YappcRetentionService.CREATED_AT_KEY);
            Instant stamp = Instant.parse(meta.get(YappcRetentionService.CREATED_AT_KEY));
            assertThat(stamp).isBefore(Instant.now().plusSeconds(5));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. purgeExpiredArtifacts — main retention logic
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("purgeExpiredArtifacts — TTL enforcement")
    class PurgeExpiredArtifacts {

        @Test
        @DisplayName("returns 0 when no versions exist for the given project/phase")
        void returnsZeroForEmptyRepo() {
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts(
                    "no-such-project", PhaseType.INTENT, Duration.ofDays(30)));

            assertThat(purged).isZero();
        }

        @Test
        @DisplayName("does not purge versions that have no created_at stamp")
        void skipsVersionsWithoutStamp() {
            // GIVEN — artifact stored without a created_at tag
            runPromise(() -> repository.storeArtifact("proj-1", PhaseType.INTENT, "data".getBytes()));

            // WHEN — purge with maxAge of 0 (everything is stale)
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts(
                    "proj-1", PhaseType.INTENT, Duration.ZERO));

            // THEN — nothing purged because there is no stamp to compare
            assertThat(purged).isZero();
        }

        @Test
        @DisplayName("does not purge versions younger than maxAge")
        void sparesFreshVersions() {
            // GIVEN — fresh artifact tagged just now
            String version = runPromise(() -> repository.storeArtifact("proj-2", PhaseType.CONTEXT, "payload".getBytes()));
            runPromise(() -> retentionService.tagCreatedAt("proj-2", PhaseType.CONTEXT, version));

            // WHEN — purge with 7-day max age (fresh artifact is < 1 second old)
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts(
                    "proj-2", PhaseType.CONTEXT, Duration.ofDays(7)));

            // THEN — version must survive
            assertThat(purged).isZero();
        }

        @Test
        @DisplayName("purges versions older than maxAge and returns count")
        void purgesStaleVersions() {
            // GIVEN — two artifacts with an artificially old created_at timestamp
            String v1 = runPromise(() -> repository.storeArtifact("proj-3", PhaseType.DESIGN, "old1".getBytes()));
            String v2 = runPromise(() -> repository.storeArtifact("proj-3", PhaseType.DESIGN, "old2".getBytes()));

            // Stamp both versions as 90 days old
            Instant ninetyDaysAgo = Instant.now().minus(Duration.ofDays(90));
            runPromise(() -> repository.storeMetadata("proj-3", PhaseType.DESIGN, v1,
                    Map.of(YappcRetentionService.CREATED_AT_KEY, ninetyDaysAgo.toString())));
            runPromise(() -> repository.storeMetadata("proj-3", PhaseType.DESIGN, v2,
                    Map.of(YappcRetentionService.CREATED_AT_KEY, ninetyDaysAgo.toString())));

            // WHEN — purge with 30-day max age
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts(
                    "proj-3", PhaseType.DESIGN, Duration.ofDays(30)));

            // THEN — both stale versions are purged
            assertThat(purged).isEqualTo(2);
        }

        @Test
        @DisplayName("purges only stale versions, spares fresh ones in the same project/phase")
        void purgesOnlyStaleMixed() {
            // GIVEN — one fresh and one stale version
            String fresh = runPromise(() -> repository.storeArtifact("proj-4", PhaseType.IMPLEMENTATION, "new".getBytes()));
            String stale = runPromise(() -> repository.storeArtifact("proj-4", PhaseType.IMPLEMENTATION, "old".getBytes()));

            runPromise(() -> retentionService.tagCreatedAt("proj-4", PhaseType.IMPLEMENTATION, fresh));

            Instant sixtyDaysAgo = Instant.now().minus(Duration.ofDays(60));
            runPromise(() -> repository.storeMetadata("proj-4", PhaseType.IMPLEMENTATION, stale,
                    Map.of(YappcRetentionService.CREATED_AT_KEY, sixtyDaysAgo.toString())));

            // WHEN — purge with 30-day max age
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts(
                    "proj-4", PhaseType.IMPLEMENTATION, Duration.ofDays(30)));

            // THEN — only the stale version is purged
            assertThat(purged).isEqualTo(1);
        }

        @Test
        @DisplayName("skips versions with a malformed created_at value")
        void skipsMalformedTimestamp() {
            // GIVEN — artifact with a broken timestamp
            String version = runPromise(() -> repository.storeArtifact("proj-5", PhaseType.TESTING, "data".getBytes()));
            runPromise(() -> repository.storeMetadata("proj-5", PhaseType.TESTING, version,
                    Map.of(YappcRetentionService.CREATED_AT_KEY, "not-a-date")));

            // WHEN
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts(
                    "proj-5", PhaseType.TESTING, Duration.ZERO));

            // THEN — malformed stamp is skipped gracefully
            assertThat(purged).isZero();
        }
    }
}
