/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.pipeline.registry.repository;

import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.pipeline.registry.model.PipelineVersionStatus;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InMemoryPipelineRepository}.
 *
 * @doc.type class
 * @doc.purpose Tests for in-memory pipeline CRUD and versioning operations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryPipelineRepository [GH-90000]")
class InMemoryPipelineRepositoryTest extends EventloopTestBase {

    private InMemoryPipelineRepository repository;

    private static final TenantId TENANT_A = TenantId.of("tenant-alpha [GH-90000]");
    private static final TenantId TENANT_B = TenantId.of("tenant-beta [GH-90000]");

    @BeforeEach
    void setUp() { // GH-90000
        repository = new InMemoryPipelineRepository(); // GH-90000
    }

    // =========================================================================
    // save / findById
    // =========================================================================

    @Nested
    @DisplayName("save and findById [GH-90000]")
    class SaveAndFind {

        @Test
        @DisplayName("saved pipeline can be retrieved by id and tenant [GH-90000]")
        void savedPipelineCanBeFound() { // GH-90000
            PipelineRegistration pipeline = buildPipeline("pipe-001", TENANT_A, "My Pipeline"); // GH-90000
            runPromise(() -> repository.save(pipeline)); // GH-90000

            Optional<PipelineRegistration> result = runPromise(() -> // GH-90000
                    repository.findById("pipe-001", TENANT_A.value())); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getName()).isEqualTo("My Pipeline [GH-90000]");
        }

        @Test
        @DisplayName("findById returns empty for wrong tenant [GH-90000]")
        void findByIdWrongTenantReturnsEmpty() { // GH-90000
            PipelineRegistration pipeline = buildPipeline("pipe-001", TENANT_A, "Tenant A Pipeline"); // GH-90000
            runPromise(() -> repository.save(pipeline)); // GH-90000

            Optional<PipelineRegistration> result = runPromise(() -> // GH-90000
                    repository.findById("pipe-001", TENANT_B.value())); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findById returns empty for unknown id [GH-90000]")
        void findByIdUnknownReturnsEmpty() { // GH-90000
            Optional<PipelineRegistration> result = runPromise(() -> // GH-90000
                    repository.findById("does-not-exist", TENANT_A.value())); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("save overwrites existing pipeline with same id [GH-90000]")
        void saveOverwritesExisting() { // GH-90000
            PipelineRegistration v1 = buildPipeline("pipe-001", TENANT_A, "Version 1"); // GH-90000
            runPromise(() -> repository.save(v1)); // GH-90000

            PipelineRegistration v2 = buildPipeline("pipe-001", TENANT_A, "Version 2"); // GH-90000
            runPromise(() -> repository.save(v2)); // GH-90000

            Optional<PipelineRegistration> result = runPromise(() -> // GH-90000
                    repository.findById("pipe-001", TENANT_A.value())); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getName()).isEqualTo("Version 2 [GH-90000]");
        }

        @Test
        @DisplayName("save returns the saved pipeline [GH-90000]")
        void saveReturnsSavedPipeline() { // GH-90000
            PipelineRegistration pipeline = buildPipeline("pipe-010", TENANT_A, "Returns Me"); // GH-90000

            PipelineRegistration returned = runPromise(() -> repository.save(pipeline)); // GH-90000

            assertThat(returned.getId()).isEqualTo("pipe-010 [GH-90000]");
        }
    }

    // =========================================================================
    // findByTenantId
    // =========================================================================

    @Nested
    @DisplayName("findByTenantId [GH-90000]")
    class FindByTenant {

        @Test
        @DisplayName("returns only pipelines owned by the given tenant [GH-90000]")
        void findsOnlyTenantPipelines() { // GH-90000
            runPromise(() -> repository.save(buildPipeline("a1", TENANT_A, "A one"))); // GH-90000
            runPromise(() -> repository.save(buildPipeline("a2", TENANT_A, "A two"))); // GH-90000
            runPromise(() -> repository.save(buildPipeline("b1", TENANT_B, "B one"))); // GH-90000

            List<PipelineRegistration> result = runPromise(() -> // GH-90000
                    repository.findByTenantId(TENANT_A.value())); // GH-90000

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result).extracting(PipelineRegistration::getId) // GH-90000
                    .containsExactlyInAnyOrder("a1", "a2"); // GH-90000
        }

        @Test
        @DisplayName("returns empty list for tenant with no pipelines [GH-90000]")
        void emptyListForUnknownTenant() { // GH-90000
            List<PipelineRegistration> result = runPromise(() -> // GH-90000
                    repository.findByTenantId("ghost-tenant [GH-90000]"));

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // countByTenantId
    // =========================================================================

    @Nested
    @DisplayName("countByTenantId [GH-90000]")
    class Count {

        @Test
        @DisplayName("returns correct count per tenant [GH-90000]")
        void correctCount() { // GH-90000
            runPromise(() -> repository.save(buildPipeline("p1", TENANT_A, "P1"))); // GH-90000
            runPromise(() -> repository.save(buildPipeline("p2", TENANT_A, "P2"))); // GH-90000
            runPromise(() -> repository.save(buildPipeline("p3", TENANT_B, "P3"))); // GH-90000

            long countA = runPromise(() -> repository.countByTenantId(TENANT_A.value())); // GH-90000
            long countB = runPromise(() -> repository.countByTenantId(TENANT_B.value())); // GH-90000

            assertThat(countA).isEqualTo(2); // GH-90000
            assertThat(countB).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("returns 0 for unknown tenant [GH-90000]")
        void zeroForUnknownTenant() { // GH-90000
            long count = runPromise(() -> repository.countByTenantId("nosuch [GH-90000]"));

            assertThat(count).isZero(); // GH-90000
        }
    }

    // =========================================================================
    // delete
    // =========================================================================

    @Nested
    @DisplayName("delete [GH-90000]")
    class Delete {

        @Test
        @DisplayName("deleted pipeline is no longer findable [GH-90000]")
        void deletedPipelineIsGone() { // GH-90000
            runPromise(() -> repository.save(buildPipeline("pipe-del", TENANT_A, "To Delete"))); // GH-90000

            runPromise(() -> repository.delete("pipe-del", TENANT_A.value())); // GH-90000

            Optional<PipelineRegistration> result = runPromise(() -> // GH-90000
                    repository.findById("pipe-del", TENANT_A.value())); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("delete is no-op for wrong tenant [GH-90000]")
        void deleteWrongTenantLeavesIntact() { // GH-90000
            runPromise(() -> repository.save(buildPipeline("pipe-keep", TENANT_A, "Survivor"))); // GH-90000

            runPromise(() -> repository.delete("pipe-keep", TENANT_B.value())); // GH-90000

            Optional<PipelineRegistration> result = runPromise(() -> // GH-90000
                    repository.findById("pipe-keep", TENANT_A.value())); // GH-90000

            assertThat(result).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("delete unknown id is no-op [GH-90000]")
        void deleteUnknownIdIsNoOp() { // GH-90000
            // Should not throw
            runPromise(() -> repository.delete("nope", TENANT_A.value())); // GH-90000
        }
    }

    // =========================================================================
    // versioning (AEP-07) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("versioning [GH-90000]")
    class Versioning {

        @Test
        @DisplayName("saveVersionSnapshot allows retrieving version history [GH-90000]")
        void versionHistoryIsPreserved() { // GH-90000
            PipelineRegistration v1 = buildPipeline("versioned", TENANT_A, "Pipeline V1"); // GH-90000
            v1.setVersion(1); // GH-90000
            PipelineRegistration v2 = buildPipeline("versioned", TENANT_A, "Pipeline V2"); // GH-90000
            v2.setVersion(2); // GH-90000

            runPromise(() -> repository.saveVersionSnapshot("versioned", v1)); // GH-90000
            runPromise(() -> repository.saveVersionSnapshot("versioned", v2)); // GH-90000

            List<PipelineRegistration> history = runPromise(() -> // GH-90000
                    repository.findVersionHistory("versioned", TENANT_A.value())); // GH-90000

            assertThat(history).hasSize(2); // GH-90000
            assertThat(history.get(0).getVersion()).isEqualTo(1); // GH-90000
            assertThat(history.get(1).getVersion()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("findVersionHistory filters by tenant [GH-90000]")
        void historyFilteredByTenant() { // GH-90000
            PipelineRegistration snap = buildPipeline("pipe-shared", TENANT_A, "A Snap"); // GH-90000
            snap.setVersion(1); // GH-90000
            PipelineRegistration snapB = buildPipeline("pipe-shared", TENANT_B, "B Snap"); // GH-90000
            snapB.setVersion(1); // GH-90000

            runPromise(() -> repository.saveVersionSnapshot("pipe-shared", snap)); // GH-90000
            runPromise(() -> repository.saveVersionSnapshot("pipe-shared", snapB)); // GH-90000

            List<PipelineRegistration> history = runPromise(() -> // GH-90000
                    repository.findVersionHistory("pipe-shared", TENANT_A.value())); // GH-90000

            assertThat(history).hasSize(1); // GH-90000
            assertThat(history.get(0).getTenantId()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("findVersionSnapshot retrieves specific version [GH-90000]")
        void findSpecificVersion() { // GH-90000
            PipelineRegistration v3 = buildPipeline("ver-pipe", TENANT_A, "V3"); // GH-90000
            v3.setVersion(3); // GH-90000

            runPromise(() -> repository.saveVersionSnapshot("ver-pipe", v3)); // GH-90000

            Optional<PipelineRegistration> found = runPromise(() -> // GH-90000
                    repository.findVersionSnapshot("ver-pipe", 3, TENANT_A.value())); // GH-90000

            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().getVersion()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("findVersionSnapshot returns empty for missing version number [GH-90000]")
        void findMissingVersionReturnsEmpty() { // GH-90000
            Optional<PipelineRegistration> result = runPromise(() -> // GH-90000
                    repository.findVersionSnapshot("any", 99, TENANT_A.value())); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("history is empty for unknown pipeline id [GH-90000]")
        void emptyHistoryForUnknownId() { // GH-90000
            List<PipelineRegistration> history = runPromise(() -> // GH-90000
                    repository.findVersionHistory("ghost", TENANT_A.value())); // GH-90000

            assertThat(history).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private PipelineRegistration buildPipeline(String id, TenantId tenantId, String name) { // GH-90000
        return PipelineRegistration.builder() // GH-90000
                .id(id) // GH-90000
                .tenantId(tenantId) // GH-90000
                .name(name) // GH-90000
                .updatedBy("test-user [GH-90000]")
                .active(true) // GH-90000
                .versionStatus(PipelineVersionStatus.DRAFT) // GH-90000
                .build(); // GH-90000
    }
}
