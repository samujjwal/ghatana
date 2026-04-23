/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.pipeline.registry.repository;

import com.ghatana.pipeline.registry.model.Pattern;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InMemoryPatternRepository}.
 *
 * @doc.type class
 * @doc.purpose Tests for in-memory pattern CRUD, status indexing, and tenant isolation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryPatternRepository")
class InMemoryPatternRepositoryTest extends EventloopTestBase {

    private InMemoryPatternRepository repository;

    private static final TenantId TENANT_A = TenantId.of("tenant-alpha");
    private static final TenantId TENANT_B = TenantId.of("tenant-beta");

    @BeforeEach
    void setUp() { // GH-90000
        repository = new InMemoryPatternRepository(); // GH-90000
    }

    // =========================================================================
    // save / findByIdAndTenant
    // =========================================================================

    @Nested
    @DisplayName("save and find by id")
    class SaveAndFind {

        @Test
        @DisplayName("saved pattern can be found by id and tenant")
        void savedPatternIsFound() { // GH-90000
            Pattern pattern = buildPattern(TENANT_A, "fraud_pattern", "SEQ(A,B)", "DRAFT"); // GH-90000

            runPromise(() -> repository.save(pattern)); // GH-90000

            Optional<Pattern> result = runPromise(() -> // GH-90000
                    repository.findByIdAndTenant(pattern.getId(), TENANT_A)); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getName()).isEqualTo("fraud_pattern");
        }

        @Test
        @DisplayName("findByIdAndTenant returns empty for wrong tenant")
        void wrongTenantReturnsEmpty() { // GH-90000
            Pattern pattern = buildPattern(TENANT_A, "tenant_a_pattern", "SEQ(A)", "DRAFT"); // GH-90000
            runPromise(() -> repository.save(pattern)); // GH-90000

            Optional<Pattern> result = runPromise(() -> // GH-90000
                    repository.findByIdAndTenant(pattern.getId(), TENANT_B)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findByIdAndTenant returns empty for unknown id")
        void unknownIdReturnsEmpty() { // GH-90000
            Optional<Pattern> result = runPromise(() -> // GH-90000
                    repository.findByIdAndTenant("no-such-id", TENANT_A)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("save returns the saved pattern")
        void saveReturnsSavedPattern() { // GH-90000
            Pattern pattern = buildPattern(TENANT_A, "my_pattern", "SEQ(X)", "DRAFT"); // GH-90000

            Pattern returned = runPromise(() -> repository.save(pattern)); // GH-90000

            assertThat(returned.getId()).isEqualTo(pattern.getId()); // GH-90000
            assertThat(returned.getName()).isEqualTo("my_pattern");
        }
    }

    // =========================================================================
    // update
    // =========================================================================

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("update replaces existing entry")
        void updateReplacesEntry() { // GH-90000
            Pattern original = buildPattern(TENANT_A, "original_name", "SEQ(A)", "DRAFT"); // GH-90000
            runPromise(() -> repository.save(original)); // GH-90000

            Pattern updated = Pattern.builder() // GH-90000
                    .id(original.getId()) // GH-90000
                    .tenantId(TENANT_A) // GH-90000
                    .name("updated_name")
                    .specification("SEQ(A,B)")
                    .status("COMPILED")
                    .build(); // GH-90000

            runPromise(() -> repository.update(updated)); // GH-90000

            Optional<Pattern> found = runPromise(() -> // GH-90000
                    repository.findByIdAndTenant(original.getId(), TENANT_A)); // GH-90000

            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().getName()).isEqualTo("updated_name");
            assertThat(found.get().getStatus()).isEqualTo("COMPILED");
        }

        @Test
        @DisplayName("update changes the status index")
        void updateChangesStatusIndex() { // GH-90000
            Pattern pattern = buildPattern(TENANT_A, "p", "SEQ(A)", "DRAFT"); // GH-90000
            runPromise(() -> repository.save(pattern)); // GH-90000

            // Assert it's in DRAFT
            List<Pattern> drafts = runPromise(() -> repository.findByTenant(TENANT_A, "DRAFT")); // GH-90000
            assertThat(drafts).hasSize(1); // GH-90000

            // Update to COMPILED
            Pattern compiled = Pattern.builder() // GH-90000
                    .id(pattern.getId()) // GH-90000
                    .tenantId(TENANT_A) // GH-90000
                    .name(pattern.getName()) // GH-90000
                    .specification(pattern.getSpecification()) // GH-90000
                    .status("COMPILED")
                    .build(); // GH-90000
            runPromise(() -> repository.update(compiled)); // GH-90000

            // Now COMPILED list should have it, DRAFT should not
            List<Pattern> compiledPatterns = runPromise(() -> repository.findByTenant(TENANT_A, "COMPILED")); // GH-90000
            List<Pattern> draftPatterns = runPromise(() -> repository.findByTenant(TENANT_A, "DRAFT")); // GH-90000

            assertThat(compiledPatterns).hasSize(1); // GH-90000
            assertThat(draftPatterns).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("update returns the updated pattern")
        void updateReturnUpdatedPattern() { // GH-90000
            Pattern pattern = buildPattern(TENANT_A, "p", "SEQ(A)", "DRAFT"); // GH-90000
            runPromise(() -> repository.save(pattern)); // GH-90000

            Pattern changed = Pattern.builder() // GH-90000
                    .id(pattern.getId()) // GH-90000
                    .tenantId(TENANT_A) // GH-90000
                    .name("changed")
                    .specification("SEQ(A,B,C)")
                    .status("DRAFT")
                    .build(); // GH-90000

            Pattern returned = runPromise(() -> repository.update(changed)); // GH-90000

            assertThat(returned.getName()).isEqualTo("changed");
        }
    }

    // =========================================================================
    // findByTenant (with status filter) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("findByTenant")
    class FindByTenant {

        @Test
        @DisplayName("null status returns all patterns for tenant")
        void nullStatusReturnsAll() { // GH-90000
            runPromise(() -> repository.save(buildPattern(TENANT_A, "d1", "SEQ(A)", "DRAFT"))); // GH-90000
            runPromise(() -> repository.save(buildPattern(TENANT_A, "c1", "SEQ(B)", "COMPILED"))); // GH-90000
            runPromise(() -> repository.save(buildPattern(TENANT_B, "d_b", "SEQ(C)", "DRAFT"))); // GH-90000

            List<Pattern> all = runPromise(() -> repository.findByTenant(TENANT_A, null)); // GH-90000

            assertThat(all).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("status filter narrows results")
        void statusFilterNarrows() { // GH-90000
            runPromise(() -> repository.save(buildPattern(TENANT_A, "d1", "SEQ(A)", "DRAFT"))); // GH-90000
            runPromise(() -> repository.save(buildPattern(TENANT_A, "d2", "SEQ(B)", "DRAFT"))); // GH-90000
            runPromise(() -> repository.save(buildPattern(TENANT_A, "c1", "SEQ(C)", "COMPILED"))); // GH-90000

            List<Pattern> drafts = runPromise(() -> repository.findByTenant(TENANT_A, "DRAFT")); // GH-90000

            assertThat(drafts).hasSize(2); // GH-90000
            assertThat(drafts).allMatch(p -> "DRAFT".equals(p.getStatus())); // GH-90000
        }

        @Test
        @DisplayName("returns empty for unknown status")
        void emptyForUnknownStatus() { // GH-90000
            runPromise(() -> repository.save(buildPattern(TENANT_A, "p", "SEQ(A)", "DRAFT"))); // GH-90000

            List<Pattern> result = runPromise(() -> repository.findByTenant(TENANT_A, "BOGUS_STATUS")); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("tenant isolation: does not return other tenants patterns")
        void tenantIsolation() { // GH-90000
            runPromise(() -> repository.save(buildPattern(TENANT_B, "b_pattern", "SEQ(B)", "ACTIVE"))); // GH-90000

            List<Pattern> result = runPromise(() -> repository.findByTenant(TENANT_A, null)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // delete
    // =========================================================================

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deleted pattern is no longer findable")
        void deletedPatternIsGone() { // GH-90000
            Pattern pattern = buildPattern(TENANT_A, "to_delete", "SEQ(X)", "DRAFT"); // GH-90000
            runPromise(() -> repository.save(pattern)); // GH-90000

            runPromise(() -> repository.delete(pattern.getId())); // GH-90000

            Optional<Pattern> result = runPromise(() -> // GH-90000
                    repository.findByIdAndTenant(pattern.getId(), TENANT_A)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("delete removes pattern from find results")
        void deletedPatternNotInList() { // GH-90000
            Pattern p1 = buildPattern(TENANT_A, "keep", "SEQ(A)", "DRAFT"); // GH-90000
            Pattern p2 = buildPattern(TENANT_A, "remove", "SEQ(B)", "DRAFT"); // GH-90000
            runPromise(() -> repository.save(p1)); // GH-90000
            runPromise(() -> repository.save(p2)); // GH-90000

            runPromise(() -> repository.delete(p2.getId())); // GH-90000

            List<Pattern> remaining = runPromise(() -> repository.findByTenant(TENANT_A, null)); // GH-90000

            assertThat(remaining).hasSize(1); // GH-90000
            assertThat(remaining.get(0).getName()).isEqualTo("keep");
        }

        @Test
        @DisplayName("delete unknown id is no-op")
        void deleteUnknownIdIsNoOp() { // GH-90000
            // Must not throw
            runPromise(() -> repository.delete("ghost-id"));
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private Pattern buildPattern(TenantId tenantId, String name, String spec, String status) { // GH-90000
        return Pattern.builder() // GH-90000
                .id(UUID.randomUUID().toString()) // GH-90000
                .tenantId(tenantId) // GH-90000
                .name(name) // GH-90000
                .specification(spec) // GH-90000
                .status(status) // GH-90000
                .confidence(0) // GH-90000
                .build(); // GH-90000
    }
}
