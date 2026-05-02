/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("InMemoryPipelineRepository")
class InMemoryPipelineRepositoryTest extends EventloopTestBase {

    private InMemoryPipelineRepository repository;

    private static final TenantId TENANT_A = TenantId.of("tenant-alpha");
    private static final TenantId TENANT_B = TenantId.of("tenant-beta");

    @BeforeEach
    void setUp() { 
        repository = new InMemoryPipelineRepository(); 
    }

    // =========================================================================
    // save / findById
    // =========================================================================

    @Nested
    @DisplayName("save and findById")
    class SaveAndFind {

        @Test
        @DisplayName("saved pipeline can be retrieved by id and tenant")
        void savedPipelineCanBeFound() { 
            PipelineRegistration pipeline = buildPipeline("pipe-001", TENANT_A, "My Pipeline"); 
            runPromise(() -> repository.save(pipeline)); 

            Optional<PipelineRegistration> result = runPromise(() -> 
                    repository.findById("pipe-001", TENANT_A.value())); 

            assertThat(result).isPresent(); 
            assertThat(result.get().getName()).isEqualTo("My Pipeline");
        }

        @Test
        @DisplayName("findById returns empty for wrong tenant")
        void findByIdWrongTenantReturnsEmpty() { 
            PipelineRegistration pipeline = buildPipeline("pipe-001", TENANT_A, "Tenant A Pipeline"); 
            runPromise(() -> repository.save(pipeline)); 

            Optional<PipelineRegistration> result = runPromise(() -> 
                    repository.findById("pipe-001", TENANT_B.value())); 

            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("findById returns empty for unknown id")
        void findByIdUnknownReturnsEmpty() { 
            Optional<PipelineRegistration> result = runPromise(() -> 
                    repository.findById("does-not-exist", TENANT_A.value())); 

            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("save overwrites existing pipeline with same id")
        void saveOverwritesExisting() { 
            PipelineRegistration v1 = buildPipeline("pipe-001", TENANT_A, "Version 1"); 
            runPromise(() -> repository.save(v1)); 

            PipelineRegistration v2 = buildPipeline("pipe-001", TENANT_A, "Version 2"); 
            runPromise(() -> repository.save(v2)); 

            Optional<PipelineRegistration> result = runPromise(() -> 
                    repository.findById("pipe-001", TENANT_A.value())); 

            assertThat(result).isPresent(); 
            assertThat(result.get().getName()).isEqualTo("Version 2");
        }

        @Test
        @DisplayName("save returns the saved pipeline")
        void saveReturnsSavedPipeline() { 
            PipelineRegistration pipeline = buildPipeline("pipe-010", TENANT_A, "Returns Me"); 

            PipelineRegistration returned = runPromise(() -> repository.save(pipeline)); 

            assertThat(returned.getId()).isEqualTo("pipe-010");
        }
    }

    // =========================================================================
    // findByTenantId
    // =========================================================================

    @Nested
    @DisplayName("findByTenantId")
    class FindByTenant {

        @Test
        @DisplayName("returns only pipelines owned by the given tenant")
        void findsOnlyTenantPipelines() { 
            runPromise(() -> repository.save(buildPipeline("a1", TENANT_A, "A one"))); 
            runPromise(() -> repository.save(buildPipeline("a2", TENANT_A, "A two"))); 
            runPromise(() -> repository.save(buildPipeline("b1", TENANT_B, "B one"))); 

            List<PipelineRegistration> result = runPromise(() -> 
                    repository.findByTenantId(TENANT_A.value())); 

            assertThat(result).hasSize(2); 
            assertThat(result).extracting(PipelineRegistration::getId) 
                    .containsExactlyInAnyOrder("a1", "a2"); 
        }

        @Test
        @DisplayName("returns empty list for tenant with no pipelines")
        void emptyListForUnknownTenant() { 
            List<PipelineRegistration> result = runPromise(() -> 
                    repository.findByTenantId("ghost-tenant"));

            assertThat(result).isEmpty(); 
        }
    }

    // =========================================================================
    // countByTenantId
    // =========================================================================

    @Nested
    @DisplayName("countByTenantId")
    class Count {

        @Test
        @DisplayName("returns correct count per tenant")
        void correctCount() { 
            runPromise(() -> repository.save(buildPipeline("p1", TENANT_A, "P1"))); 
            runPromise(() -> repository.save(buildPipeline("p2", TENANT_A, "P2"))); 
            runPromise(() -> repository.save(buildPipeline("p3", TENANT_B, "P3"))); 

            long countA = runPromise(() -> repository.countByTenantId(TENANT_A.value())); 
            long countB = runPromise(() -> repository.countByTenantId(TENANT_B.value())); 

            assertThat(countA).isEqualTo(2); 
            assertThat(countB).isEqualTo(1); 
        }

        @Test
        @DisplayName("returns 0 for unknown tenant")
        void zeroForUnknownTenant() { 
            long count = runPromise(() -> repository.countByTenantId("nosuch"));

            assertThat(count).isZero(); 
        }
    }

    // =========================================================================
    // delete
    // =========================================================================

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deleted pipeline is no longer findable")
        void deletedPipelineIsGone() { 
            runPromise(() -> repository.save(buildPipeline("pipe-del", TENANT_A, "To Delete"))); 

            runPromise(() -> repository.delete("pipe-del", TENANT_A.value())); 

            Optional<PipelineRegistration> result = runPromise(() -> 
                    repository.findById("pipe-del", TENANT_A.value())); 

            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("delete is no-op for wrong tenant")
        void deleteWrongTenantLeavesIntact() { 
            runPromise(() -> repository.save(buildPipeline("pipe-keep", TENANT_A, "Survivor"))); 

            runPromise(() -> repository.delete("pipe-keep", TENANT_B.value())); 

            Optional<PipelineRegistration> result = runPromise(() -> 
                    repository.findById("pipe-keep", TENANT_A.value())); 

            assertThat(result).isPresent(); 
        }

        @Test
        @DisplayName("delete unknown id is no-op")
        void deleteUnknownIdIsNoOp() { 
            // Should not throw
            runPromise(() -> repository.delete("nope", TENANT_A.value())); 
        }
    }

    // =========================================================================
    // versioning (AEP-07) 
    // =========================================================================

    @Nested
    @DisplayName("versioning")
    class Versioning {

        @Test
        @DisplayName("saveVersionSnapshot allows retrieving version history")
        void versionHistoryIsPreserved() { 
            PipelineRegistration v1 = buildPipeline("versioned", TENANT_A, "Pipeline V1"); 
            v1.setVersion(1); 
            PipelineRegistration v2 = buildPipeline("versioned", TENANT_A, "Pipeline V2"); 
            v2.setVersion(2); 

            runPromise(() -> repository.saveVersionSnapshot("versioned", v1)); 
            runPromise(() -> repository.saveVersionSnapshot("versioned", v2)); 

            List<PipelineRegistration> history = runPromise(() -> 
                    repository.findVersionHistory("versioned", TENANT_A.value())); 

            assertThat(history).hasSize(2); 
            assertThat(history.get(0).getVersion()).isEqualTo(1); 
            assertThat(history.get(1).getVersion()).isEqualTo(2); 
        }

        @Test
        @DisplayName("findVersionHistory filters by tenant")
        void historyFilteredByTenant() { 
            PipelineRegistration snap = buildPipeline("pipe-shared", TENANT_A, "A Snap"); 
            snap.setVersion(1); 
            PipelineRegistration snapB = buildPipeline("pipe-shared", TENANT_B, "B Snap"); 
            snapB.setVersion(1); 

            runPromise(() -> repository.saveVersionSnapshot("pipe-shared", snap)); 
            runPromise(() -> repository.saveVersionSnapshot("pipe-shared", snapB)); 

            List<PipelineRegistration> history = runPromise(() -> 
                    repository.findVersionHistory("pipe-shared", TENANT_A.value())); 

            assertThat(history).hasSize(1); 
            assertThat(history.get(0).getTenantId()).isEqualTo(TENANT_A); 
        }

        @Test
        @DisplayName("findVersionSnapshot retrieves specific version")
        void findSpecificVersion() { 
            PipelineRegistration v3 = buildPipeline("ver-pipe", TENANT_A, "V3"); 
            v3.setVersion(3); 

            runPromise(() -> repository.saveVersionSnapshot("ver-pipe", v3)); 

            Optional<PipelineRegistration> found = runPromise(() -> 
                    repository.findVersionSnapshot("ver-pipe", 3, TENANT_A.value())); 

            assertThat(found).isPresent(); 
            assertThat(found.get().getVersion()).isEqualTo(3); 
        }

        @Test
        @DisplayName("findVersionSnapshot returns empty for missing version number")
        void findMissingVersionReturnsEmpty() { 
            Optional<PipelineRegistration> result = runPromise(() -> 
                    repository.findVersionSnapshot("any", 99, TENANT_A.value())); 

            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("history is empty for unknown pipeline id")
        void emptyHistoryForUnknownId() { 
            List<PipelineRegistration> history = runPromise(() -> 
                    repository.findVersionHistory("ghost", TENANT_A.value())); 

            assertThat(history).isEmpty(); 
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private PipelineRegistration buildPipeline(String id, TenantId tenantId, String name) { 
        return PipelineRegistration.builder() 
                .id(id) 
                .tenantId(tenantId) 
                .name(name) 
                .updatedBy("test-user")
                .active(true) 
                .versionStatus(PipelineVersionStatus.DRAFT) 
                .build(); 
    }
}
