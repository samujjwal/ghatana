/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
@DisplayName("YappcRetentionService Tests [GH-90000]")
class YappcRetentionServiceTest extends EventloopTestBase {

    private InMemoryArtifactStore store;
    private YappcArtifactRepository repository;
    private YappcRetentionService retentionService;

    @BeforeEach
    void setUp() { // GH-90000
        store = new InMemoryArtifactStore(); // GH-90000
        repository = new YappcArtifactRepository(store); // GH-90000
        retentionService = new YappcRetentionService(repository); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Tag creation timestamp
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("tagCreatedAt — metadata stamping [GH-90000]")
    class TagCreatedAt {

        @Test
        @DisplayName("stores ISO-8601 created_at stamp in artifact metadata [GH-90000]")
        void storesCreatedAtStamp() { // GH-90000
            // GIVEN
            String version = runPromise(() -> repository.storeArtifact("proj-1", PhaseType.INTENT, "data".getBytes())); // GH-90000

            // WHEN
            runPromise(() -> retentionService.tagCreatedAt("proj-1", PhaseType.INTENT, version)); // GH-90000

            // THEN — metadata must contain a parseable ISO-8601 instant
            Map<String, String> meta = runPromise(() -> repository.getMetadata("proj-1", PhaseType.INTENT, version)); // GH-90000
            assertThat(meta).containsKey(YappcRetentionService.CREATED_AT_KEY); // GH-90000
            Instant stamp = Instant.parse(meta.get(YappcRetentionService.CREATED_AT_KEY)); // GH-90000
            assertThat(stamp).isBefore(Instant.now().plusSeconds(5)); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. purgeExpiredArtifacts — main retention logic
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("purgeExpiredArtifacts — TTL enforcement [GH-90000]")
    class PurgeExpiredArtifacts {

        @Test
        @DisplayName("returns 0 when no versions exist for the given project/phase [GH-90000]")
        void returnsZeroForEmptyRepo() { // GH-90000
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts( // GH-90000
                    "no-such-project", PhaseType.INTENT, Duration.ofDays(30))); // GH-90000

            assertThat(purged).isZero(); // GH-90000
        }

        @Test
        @DisplayName("does not purge versions that have no created_at stamp [GH-90000]")
        void skipsVersionsWithoutStamp() { // GH-90000
            // GIVEN — artifact stored without a created_at tag
            runPromise(() -> repository.storeArtifact("proj-1", PhaseType.INTENT, "data".getBytes())); // GH-90000

            // WHEN — purge with maxAge of 0 (everything is stale) // GH-90000
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts( // GH-90000
                    "proj-1", PhaseType.INTENT, Duration.ZERO));

            // THEN — nothing purged because there is no stamp to compare
            assertThat(purged).isZero(); // GH-90000
        }

        @Test
        @DisplayName("does not purge versions younger than maxAge [GH-90000]")
        void sparesFreshVersions() { // GH-90000
            // GIVEN — fresh artifact tagged just now
            String version = runPromise(() -> repository.storeArtifact("proj-2", PhaseType.SHAPE, "payload".getBytes())); // GH-90000
            runPromise(() -> retentionService.tagCreatedAt("proj-2", PhaseType.SHAPE, version)); // GH-90000

            // WHEN — purge with 7-day max age (fresh artifact is < 1 second old) // GH-90000
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts( // GH-90000
                    "proj-2", PhaseType.SHAPE, Duration.ofDays(7))); // GH-90000

            // THEN — version must survive
            assertThat(purged).isZero(); // GH-90000
        }

        @Test
        @DisplayName("purges versions older than maxAge and returns count [GH-90000]")
        void purgesStaleVersions() { // GH-90000
            // GIVEN — two artifacts with an artificially old created_at timestamp
            String v1 = runPromise(() -> repository.storeArtifact("proj-3", PhaseType.VALIDATE, "old1".getBytes())); // GH-90000
            String v2 = runPromise(() -> repository.storeArtifact("proj-3", PhaseType.VALIDATE, "old2".getBytes())); // GH-90000

            // Stamp both versions as 90 days old
            Instant ninetyDaysAgo = Instant.now().minus(Duration.ofDays(90)); // GH-90000
                runPromise(() -> repository.storeMetadata("proj-3", PhaseType.VALIDATE, v1, // GH-90000
                    Map.of(YappcRetentionService.CREATED_AT_KEY, ninetyDaysAgo.toString()))); // GH-90000
                runPromise(() -> repository.storeMetadata("proj-3", PhaseType.VALIDATE, v2, // GH-90000
                    Map.of(YappcRetentionService.CREATED_AT_KEY, ninetyDaysAgo.toString()))); // GH-90000

            // WHEN — purge with 30-day max age
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts( // GH-90000
                    "proj-3", PhaseType.VALIDATE, Duration.ofDays(30))); // GH-90000

            // THEN — both stale versions are purged
            assertThat(purged).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("purges only stale versions, spares fresh ones in the same project/phase [GH-90000]")
        void purgesOnlyStaleMixed() { // GH-90000
            // GIVEN — one fresh and one stale version
            String fresh = runPromise(() -> repository.storeArtifact("proj-4", PhaseType.GENERATE, "new".getBytes())); // GH-90000
            String stale = runPromise(() -> repository.storeArtifact("proj-4", PhaseType.GENERATE, "old".getBytes())); // GH-90000

            runPromise(() -> retentionService.tagCreatedAt("proj-4", PhaseType.GENERATE, fresh)); // GH-90000

            Instant sixtyDaysAgo = Instant.now().minus(Duration.ofDays(60)); // GH-90000
            runPromise(() -> repository.storeMetadata("proj-4", PhaseType.GENERATE, stale, // GH-90000
                    Map.of(YappcRetentionService.CREATED_AT_KEY, sixtyDaysAgo.toString()))); // GH-90000

            // WHEN — purge with 30-day max age
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts( // GH-90000
                    "proj-4", PhaseType.GENERATE, Duration.ofDays(30))); // GH-90000

            // THEN — only the stale version is purged
            assertThat(purged).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("skips versions with a malformed created_at value [GH-90000]")
        void skipsMalformedTimestamp() { // GH-90000
            // GIVEN — artifact with a broken timestamp
            String version = runPromise(() -> repository.storeArtifact("proj-5", PhaseType.RUN, "data".getBytes())); // GH-90000
            runPromise(() -> repository.storeMetadata("proj-5", PhaseType.RUN, version, // GH-90000
                    Map.of(YappcRetentionService.CREATED_AT_KEY, "not-a-date"))); // GH-90000

            // WHEN
            int purged = runPromise(() -> retentionService.purgeExpiredArtifacts( // GH-90000
                    "proj-5", PhaseType.RUN, Duration.ZERO));

            // THEN — malformed stamp is skipped gracefully
            assertThat(purged).isZero(); // GH-90000
        }
    }
}
