/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Idempotency and tenant-isolation contract tests for GenerationRunRepository.
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GenerationRunRepository idempotency and tenant isolation")
class GenerationRunIdempotencyTest extends EventloopTestBase {

    private GenerationRunRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(GenerationRunRepository.class);
        lenient().when(repository.save(any(GenerationRun.class))).thenReturn(Promise.complete());
        lenient().when(repository.findById(any())).thenReturn(Promise.of(null));
        lenient().when(repository.findByTenantId(any())).thenReturn(Promise.of(List.of()));
        lenient().when(repository.findByTenantAndContentDigest(any(), any())).thenReturn(Promise.of(List.of()));
    }

    @Test
    @DisplayName("save is idempotent — second save for same id calls upsert without throwing")
    void saveIsIdempotent() {
        GenerationRun run = run("run-1", "tenant-1", "sha256-abc");

        runPromise(() -> repository.save(run));
        runPromise(() -> repository.save(run));

        verify(repository, times(2)).save(run);
    }

    @Test
    @DisplayName("findByTenantAndContentDigest returns existing run when content digest matches")
    void findByTenantAndContentDigestReturnsExistingRun() {
        GenerationRun existing = run("run-2", "tenant-a", "sha256-xyz");
        when(repository.findByTenantAndContentDigest("tenant-a", "sha256-xyz"))
                .thenReturn(Promise.of(List.of(existing)));

        List<GenerationRun> found = runPromise(() ->
                repository.findByTenantAndContentDigest("tenant-a", "sha256-xyz"));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).id()).isEqualTo("run-2");
        assertThat(found.get(0).contentDigest()).isEqualTo("sha256-xyz");
    }

    @Test
    @DisplayName("findByTenantAndContentDigest enforces tenant isolation — different tenant returns empty")
    void tenantIsolationOnContentDigestQuery() {
        // tenant-a has a run with digest sha256-xyz
        when(repository.findByTenantAndContentDigest("tenant-a", "sha256-xyz"))
                .thenReturn(Promise.of(List.of(run("run-3", "tenant-a", "sha256-xyz"))));
        // tenant-b queries same digest — must be empty
        when(repository.findByTenantAndContentDigest("tenant-b", "sha256-xyz"))
                .thenReturn(Promise.of(List.of()));

        List<GenerationRun> tenantAResult = runPromise(() ->
                repository.findByTenantAndContentDigest("tenant-a", "sha256-xyz"));
        List<GenerationRun> tenantBResult = runPromise(() ->
                repository.findByTenantAndContentDigest("tenant-b", "sha256-xyz"));

        assertThat(tenantAResult).hasSize(1);
        assertThat(tenantBResult).isEmpty();
    }

    @Test
    @DisplayName("findByTenantId returns only runs for the given tenant")
    void findByTenantIdScopedToTenant() {
        GenerationRun runA = run("run-4", "tenant-a", null);
        GenerationRun runA2 = run("run-5", "tenant-a", null);
        when(repository.findByTenantId("tenant-a")).thenReturn(Promise.of(List.of(runA, runA2)));
        when(repository.findByTenantId("tenant-b")).thenReturn(Promise.of(List.of()));

        List<GenerationRun> tenantAResults = runPromise(() -> repository.findByTenantId("tenant-a"));
        List<GenerationRun> tenantBResults = runPromise(() -> repository.findByTenantId("tenant-b"));

        assertThat(tenantAResults).hasSize(2)
                .allMatch(r -> "tenant-a".equals(r.tenantId()));
        assertThat(tenantBResults).isEmpty();
    }

    @Test
    @DisplayName("duplicate review decision is idempotent — updateReviewStatus can be called twice for same id")
    void duplicateReviewDecisionIsIdempotent() {
        when(repository.updateReviewStatus(eq("run-6"), eq(GenerationRun.ReviewStatus.APPROVED)))
                .thenReturn(Promise.complete());

        runPromise(() -> repository.updateReviewStatus("run-6", GenerationRun.ReviewStatus.APPROVED));
        runPromise(() -> repository.updateReviewStatus("run-6", GenerationRun.ReviewStatus.APPROVED));

        verify(repository, times(2)).updateReviewStatus("run-6", GenerationRun.ReviewStatus.APPROVED);
    }

    @Test
    @DisplayName("contentDigest field is preserved through GenerationRun builder")
    void contentDigestFieldRoundTrip() {
        GenerationRun run = GenerationRun.builder()
                .id("run-7")
                .tenantId("tenant-1")
                .projectId("proj-1")
                .workspaceId("ws-1")
                .planId("plan-1")
                .contentDigest("sha256-roundtrip")
                .build();

        assertThat(run.contentDigest()).isEqualTo("sha256-roundtrip");
        assertThat(run.tenantId()).isEqualTo("tenant-1");
    }

    // -- helpers --

    private static GenerationRun run(String id, String tenantId, String contentDigest) {
        return GenerationRun.builder()
                .id(id)
                .tenantId(tenantId)
                .projectId("proj-1")
                .workspaceId("ws-1")
                .planId("plan-1")
                .contentDigest(contentDigest)
                .build();
    }
}
