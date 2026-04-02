/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        repository = new InMemoryPatternRepository();
    }

    // =========================================================================
    // save / findByIdAndTenant
    // =========================================================================

    @Nested
    @DisplayName("save and find by id")
    class SaveAndFind {

        @Test
        @DisplayName("saved pattern can be found by id and tenant")
        void savedPatternIsFound() {
            Pattern pattern = buildPattern(TENANT_A, "fraud_pattern", "SEQ(A,B)", "DRAFT");

            runPromise(() -> repository.save(pattern));

            Optional<Pattern> result = runPromise(() ->
                    repository.findByIdAndTenant(pattern.getId(), TENANT_A));

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("fraud_pattern");
        }

        @Test
        @DisplayName("findByIdAndTenant returns empty for wrong tenant")
        void wrongTenantReturnsEmpty() {
            Pattern pattern = buildPattern(TENANT_A, "tenant_a_pattern", "SEQ(A)", "DRAFT");
            runPromise(() -> repository.save(pattern));

            Optional<Pattern> result = runPromise(() ->
                    repository.findByIdAndTenant(pattern.getId(), TENANT_B));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findByIdAndTenant returns empty for unknown id")
        void unknownIdReturnsEmpty() {
            Optional<Pattern> result = runPromise(() ->
                    repository.findByIdAndTenant("no-such-id", TENANT_A));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("save returns the saved pattern")
        void saveReturnsSavedPattern() {
            Pattern pattern = buildPattern(TENANT_A, "my_pattern", "SEQ(X)", "DRAFT");

            Pattern returned = runPromise(() -> repository.save(pattern));

            assertThat(returned.getId()).isEqualTo(pattern.getId());
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
        void updateReplacesEntry() {
            Pattern original = buildPattern(TENANT_A, "original_name", "SEQ(A)", "DRAFT");
            runPromise(() -> repository.save(original));

            Pattern updated = Pattern.builder()
                    .id(original.getId())
                    .tenantId(TENANT_A)
                    .name("updated_name")
                    .specification("SEQ(A,B)")
                    .status("COMPILED")
                    .build();

            runPromise(() -> repository.update(updated));

            Optional<Pattern> found = runPromise(() ->
                    repository.findByIdAndTenant(original.getId(), TENANT_A));

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("updated_name");
            assertThat(found.get().getStatus()).isEqualTo("COMPILED");
        }

        @Test
        @DisplayName("update changes the status index")
        void updateChangesStatusIndex() {
            Pattern pattern = buildPattern(TENANT_A, "p", "SEQ(A)", "DRAFT");
            runPromise(() -> repository.save(pattern));

            // Assert it's in DRAFT
            List<Pattern> drafts = runPromise(() -> repository.findByTenant(TENANT_A, "DRAFT"));
            assertThat(drafts).hasSize(1);

            // Update to COMPILED
            Pattern compiled = Pattern.builder()
                    .id(pattern.getId())
                    .tenantId(TENANT_A)
                    .name(pattern.getName())
                    .specification(pattern.getSpecification())
                    .status("COMPILED")
                    .build();
            runPromise(() -> repository.update(compiled));

            // Now COMPILED list should have it, DRAFT should not
            List<Pattern> compiledPatterns = runPromise(() -> repository.findByTenant(TENANT_A, "COMPILED"));
            List<Pattern> draftPatterns = runPromise(() -> repository.findByTenant(TENANT_A, "DRAFT"));

            assertThat(compiledPatterns).hasSize(1);
            assertThat(draftPatterns).isEmpty();
        }

        @Test
        @DisplayName("update returns the updated pattern")
        void updateReturnUpdatedPattern() {
            Pattern pattern = buildPattern(TENANT_A, "p", "SEQ(A)", "DRAFT");
            runPromise(() -> repository.save(pattern));

            Pattern changed = Pattern.builder()
                    .id(pattern.getId())
                    .tenantId(TENANT_A)
                    .name("changed")
                    .specification("SEQ(A,B,C)")
                    .status("DRAFT")
                    .build();

            Pattern returned = runPromise(() -> repository.update(changed));

            assertThat(returned.getName()).isEqualTo("changed");
        }
    }

    // =========================================================================
    // findByTenant (with status filter)
    // =========================================================================

    @Nested
    @DisplayName("findByTenant")
    class FindByTenant {

        @Test
        @DisplayName("null status returns all patterns for tenant")
        void nullStatusReturnsAll() {
            runPromise(() -> repository.save(buildPattern(TENANT_A, "d1", "SEQ(A)", "DRAFT")));
            runPromise(() -> repository.save(buildPattern(TENANT_A, "c1", "SEQ(B)", "COMPILED")));
            runPromise(() -> repository.save(buildPattern(TENANT_B, "d_b", "SEQ(C)", "DRAFT")));

            List<Pattern> all = runPromise(() -> repository.findByTenant(TENANT_A, null));

            assertThat(all).hasSize(2);
        }

        @Test
        @DisplayName("status filter narrows results")
        void statusFilterNarrows() {
            runPromise(() -> repository.save(buildPattern(TENANT_A, "d1", "SEQ(A)", "DRAFT")));
            runPromise(() -> repository.save(buildPattern(TENANT_A, "d2", "SEQ(B)", "DRAFT")));
            runPromise(() -> repository.save(buildPattern(TENANT_A, "c1", "SEQ(C)", "COMPILED")));

            List<Pattern> drafts = runPromise(() -> repository.findByTenant(TENANT_A, "DRAFT"));

            assertThat(drafts).hasSize(2);
            assertThat(drafts).allMatch(p -> "DRAFT".equals(p.getStatus()));
        }

        @Test
        @DisplayName("returns empty for unknown status")
        void emptyForUnknownStatus() {
            runPromise(() -> repository.save(buildPattern(TENANT_A, "p", "SEQ(A)", "DRAFT")));

            List<Pattern> result = runPromise(() -> repository.findByTenant(TENANT_A, "BOGUS_STATUS"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("tenant isolation: does not return other tenants patterns")
        void tenantIsolation() {
            runPromise(() -> repository.save(buildPattern(TENANT_B, "b_pattern", "SEQ(B)", "ACTIVE")));

            List<Pattern> result = runPromise(() -> repository.findByTenant(TENANT_A, null));

            assertThat(result).isEmpty();
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
        void deletedPatternIsGone() {
            Pattern pattern = buildPattern(TENANT_A, "to_delete", "SEQ(X)", "DRAFT");
            runPromise(() -> repository.save(pattern));

            runPromise(() -> repository.delete(pattern.getId()));

            Optional<Pattern> result = runPromise(() ->
                    repository.findByIdAndTenant(pattern.getId(), TENANT_A));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("delete removes pattern from find results")
        void deletedPatternNotInList() {
            Pattern p1 = buildPattern(TENANT_A, "keep", "SEQ(A)", "DRAFT");
            Pattern p2 = buildPattern(TENANT_A, "remove", "SEQ(B)", "DRAFT");
            runPromise(() -> repository.save(p1));
            runPromise(() -> repository.save(p2));

            runPromise(() -> repository.delete(p2.getId()));

            List<Pattern> remaining = runPromise(() -> repository.findByTenant(TENANT_A, null));

            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getName()).isEqualTo("keep");
        }

        @Test
        @DisplayName("delete unknown id is no-op")
        void deleteUnknownIdIsNoOp() {
            // Must not throw
            runPromise(() -> repository.delete("ghost-id"));
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private Pattern buildPattern(TenantId tenantId, String name, String spec, String status) {
        return Pattern.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .name(name)
                .specification(spec)
                .status(status)
                .confidence(0)
                .build();
    }
}
