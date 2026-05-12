/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import com.ghatana.agent.context.version.VersionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for VersionScope.
 *
 * @doc.type class
 * @doc.purpose Tests for VersionScope
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("VersionScope Tests")
class VersionScopeTest {

    @Test
    @DisplayName("Should create empty version scope")
    void shouldCreateEmptyVersionScope() {
        VersionScope scope = VersionScope.empty();

        assertThat(scope.active()).isEmpty();
        assertThat(scope.maintenance()).isEmpty();
        assertThat(scope.obsolete()).isEmpty();
    }

    @Test
    @DisplayName("Should create active-only version scope")
    void shouldCreateActiveOnlyVersionScope() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        assertThat(scope.active()).hasSize(1);
        assertThat(scope.maintenance()).isEmpty();
        assertThat(scope.obsolete()).isEmpty();
    }

    @Test
    @DisplayName("Should classify version context as ACTIVE")
    void shouldClassifyAsActive() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0");
        VersionScope scope = VersionScope.activeOnly(List.of(activeConstraint));

        VersionContext context = new VersionContext(
                Map.of("react", "18.2.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.ACTIVE);
    }

    @Test
    @DisplayName("Should classify version context as MAINTENANCE")
    void shouldClassifyAsMaintenance() {
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react", ">=17.0.0 <18.0.0");
        VersionScope scope = new VersionScope(
                List.of(),
                List.of(maintenanceConstraint),
                List.of()
        );

        VersionContext context = new VersionContext(
                Map.of("react", "17.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.MAINTENANCE);
    }

    @Test
    @DisplayName("Should classify version context as OBSOLETE")
    void shouldClassifyAsObsolete() {
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react", "<16.0.0");
        VersionScope scope = new VersionScope(
                List.of(),
                List.of(),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("react", "15.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.OBSOLETE);
    }

    @Test
    @DisplayName("Should classify unknown version as UNKNOWN")
    void shouldClassifyUnknownAsUnknown() {
        VersionScope scope = VersionScope.empty();

        VersionContext context = new VersionContext(
                Map.of("unknown-package", "1.0.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.UNKNOWN);
    }

    @Test
    @DisplayName("Should return true for supportsActive when active")
    void shouldReturnTrueForSupportsActiveWhenActive() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0");
        VersionScope scope = VersionScope.activeOnly(List.of(activeConstraint));

        VersionContext context = new VersionContext(
                Map.of("react", "18.2.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isTrue();
    }

    @Test
    @DisplayName("Should return false for supportsActive when maintenance")
    void shouldReturnFalseForSupportsActiveWhenMaintenance() {
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react", ">=17.0.0 <18.0.0");
        VersionScope scope = new VersionScope(
                List.of(),
                List.of(maintenanceConstraint),
                List.of()
        );

        VersionContext context = new VersionContext(
                Map.of("react", "17.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isFalse();
    }

    @Test
    @DisplayName("Should return true for supportsMaintenance when active")
    void shouldReturnTrueForSupportsMaintenanceWhenActive() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0");
        VersionScope scope = VersionScope.activeOnly(List.of(activeConstraint));

        VersionContext context = new VersionContext(
                Map.of("react", "18.2.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsMaintenance(context)).isTrue();
    }

    @Test
    @DisplayName("Should return true for supportsMaintenance when maintenance")
    void shouldReturnTrueForSupportsMaintenanceWhenMaintenance() {
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react", ">=17.0.0 <18.0.0");
        VersionScope scope = new VersionScope(
                List.of(),
                List.of(maintenanceConstraint),
                List.of()
        );

        VersionContext context = new VersionContext(
                Map.of("react", "17.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsMaintenance(context)).isTrue();
    }

    @Test
    @DisplayName("Should return false for supportsMaintenance when obsolete")
    void shouldReturnFalseForSupportsMaintenanceWhenObsolete() {
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react", "<16.0.0");
        VersionScope scope = new VersionScope(
                List.of(),
                List.of(),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("react", "15.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsMaintenance(context)).isFalse();
    }

    @Test
    @DisplayName("Should return true for isObsolete when obsolete")
    void shouldReturnTrueForIsObsoleteWhenObsolete() {
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react", "<16.0.0");
        VersionScope scope = new VersionScope(
                List.of(),
                List.of(),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("react", "15.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.isObsolete(context)).isTrue();
    }

    @Test
    @DisplayName("Should return false for isObsolete when active")
    void shouldReturnFalseForIsObsoleteWhenActive() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0");
        VersionScope scope = VersionScope.activeOnly(List.of(activeConstraint));

        VersionContext context = new VersionContext(
                Map.of("react", "18.2.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.isObsolete(context)).isFalse();
    }

    @Test
    @DisplayName("Obsolete constraints should take precedence")
    void obsoleteConstraintsShouldTakePrecedence() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0");
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react", "<16.0.0");
        VersionScope scope = new VersionScope(
                List.of(activeConstraint),
                List.of(),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("react", "15.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        // Obsolete should take precedence even if active also matches
        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.OBSOLETE);
    }
}
