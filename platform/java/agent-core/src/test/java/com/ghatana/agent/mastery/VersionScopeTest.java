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
        VersionConstraint constraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        assertThat(scope.active()).hasSize(1);
        assertThat(scope.maintenance()).isEmpty();
        assertThat(scope.obsolete()).isEmpty();
    }

    @Test
    @DisplayName("Should classify version context as ACTIVE")
    void shouldClassifyAsActive() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0", "npm");
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
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react", ">=17.0.0 <18.0.0", "npm");
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
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react", "<16.0.0", "npm");
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
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0", "npm");
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
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react", ">=17.0.0 <18.0.0", "npm");
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
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0", "npm");
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
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react", ">=17.0.0 <18.0.0", "npm");
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
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react", "<16.0.0", "npm");
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
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react", "<16.0.0", "npm");
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
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0", "npm");
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
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react", ">=18.0.0 <19.0.0", "npm");
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react", "<16.0.0", "npm");
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

    @Test
    @DisplayName("Should match version with >= constraint")
    void shouldMatchVersionWithGreaterOrEqualConstraint() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", ">=18.0.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("react", "18.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isTrue();
    }

    @Test
    @DisplayName("Should reject version below >= constraint")
    void shouldRejectVersionBelowGreaterOrEqualConstraint() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", ">=18.0.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

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
    @DisplayName("Should match version with <= constraint")
    void shouldMatchVersionWithLessOrEqualConstraint() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", "<=19.0.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("react", "18.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isTrue();
    }

    @Test
    @DisplayName("Should reject version above <= constraint")
    void shouldRejectVersionAboveLessOrEqualConstraint() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", "<=19.0.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("react", "19.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isFalse();
    }

    @Test
    @DisplayName("Should match version with exact constraint")
    void shouldMatchVersionWithExactConstraint() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", "18.0.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("react", "18.0.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isTrue();
    }

    @Test
    @DisplayName("Should reject version with different patch in exact constraint")
    void shouldRejectVersionWithDifferentPatchInExactConstraint() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", "18.0.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("react", "18.0.1"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isFalse();
    }

    @Test
    @DisplayName("Should handle multi-part version comparison")
    void shouldHandleMultiPartVersionComparison() {
        VersionConstraint constraint = VersionConstraint.packageVersion("java", ">=17.0.0", "jvm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("java", "21.0.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isTrue();
    }

    // Phase 1 FIX: Overlap validation tests

    @Test
    @DisplayName("Should reject overlapping active and maintenance constraints")
    void shouldRejectOverlappingActiveAndMaintenanceConstraints() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react-router", ">=6.0.0 <7.0.0", "npm");
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react-router", ">=6.4.0 <7.0.0", "npm");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new VersionScope(
                        List.of(activeConstraint),
                        List.of(maintenanceConstraint),
                        List.of()
                )
        );
    }

    @Test
    @DisplayName("Should reject overlapping active and obsolete constraints")
    void shouldRejectOverlappingActiveAndObsoleteConstraints() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react-router", ">=6.0.0 <7.0.0", "npm");
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react-router", ">=5.0.0 <6.5.0", "npm");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new VersionScope(
                        List.of(activeConstraint),
                        List.of(),
                        List.of(obsoleteConstraint)
                )
        );
    }

    @Test
    @DisplayName("Should reject overlapping maintenance and obsolete constraints")
    void shouldRejectOverlappingMaintenanceAndObsoleteConstraints() {
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react-router", ">=5.0.0 <6.0.0", "npm");
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react-router", ">=5.5.0 <6.5.0", "npm");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new VersionScope(
                        List.of(),
                        List.of(maintenanceConstraint),
                        List.of(obsoleteConstraint)
                )
        );
    }

    @Test
    @DisplayName("Should allow non-overlapping constraints for same package")
    void shouldAllowNonOverlappingConstraintsForSamePackage() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react-router", ">=7.0.0 <8.0.0", "npm");
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react-router", ">=6.0.0 <7.0.0", "npm");
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react-router", ">=3.0.0 <5.0.0", "npm");

        VersionScope scope = new VersionScope(
                List.of(activeConstraint),
                List.of(maintenanceConstraint),
                List.of(obsoleteConstraint)
        );

        assertThat(scope.active()).hasSize(1);
        assertThat(scope.maintenance()).hasSize(1);
        assertThat(scope.obsolete()).hasSize(1);
    }

    @Test
    @DisplayName("Should allow constraints for different packages")
    void shouldAllowConstraintsForDifferentPackages() {
        VersionConstraint activeConstraint1 = VersionConstraint.packageVersion("react", ">=18.0.0", "npm");
        VersionConstraint activeConstraint2 = VersionConstraint.packageVersion("react-router", ">=6.0.0", "npm");

        VersionScope scope = VersionScope.activeOnly(List.of(activeConstraint1, activeConstraint2));

        assertThat(scope.active()).hasSize(2);
    }

    @Test
    @DisplayName("Should reject invalid npm range syntax")
    void shouldRejectInvalidNpmRangeSyntax() {
        VersionConstraint invalidConstraint = VersionConstraint.packageVersion("react", "invalid-range-syntax", "npm");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new VersionScope(
                        List.of(invalidConstraint),
                        List.of(),
                        List.of()
                )
        );
    }

    @Test
    @DisplayName("Should reject invalid Maven range syntax")
    void shouldRejectInvalidMavenRangeSyntax() {
        VersionConstraint invalidConstraint = VersionConstraint.packageVersion("spring-core", "not-a-maven-range", "maven");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new VersionScope(
                        List.of(invalidConstraint),
                        List.of(),
                        List.of()
                )
        );
    }

    @Test
    @DisplayName("Should reject empty range syntax")
    void shouldRejectEmptyRangeSyntax() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> VersionConstraint.packageVersion("react", "", "npm")
        );
    }

    // React Router version scenario tests

    @Test
    @DisplayName("React Router v7 should resolve to ACTIVE")
    void reactRouterV7ShouldResolveToActive() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react-router", ">=7.0.0 <8.0.0", "npm");
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react-router", ">=6.0.0 <7.0.0", "npm");
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react-router", ">=3.0.0 <6.0.0", "npm");

        VersionScope scope = new VersionScope(
                List.of(activeConstraint),
                List.of(maintenanceConstraint),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("react-router", "7.1.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.ACTIVE);
    }

    @Test
    @DisplayName("React Router v6 should resolve to MAINTENANCE")
    void reactRouterV6ShouldResolveToMaintenance() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react-router", ">=7.0.0 <8.0.0", "npm");
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react-router", ">=6.0.0 <7.0.0", "npm");
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react-router", ">=3.0.0 <6.0.0", "npm");

        VersionScope scope = new VersionScope(
                List.of(activeConstraint),
                List.of(maintenanceConstraint),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("react-router", "6.4.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.MAINTENANCE);
    }

    @Test
    @DisplayName("React Router v4 should resolve to OBSOLETE")
    void reactRouterV4ShouldResolveToObsolete() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react-router", ">=7.0.0 <8.0.0", "npm");
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react-router", ">=6.0.0 <7.0.0", "npm");
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react-router", ">=3.0.0 <6.0.0", "npm");

        VersionScope scope = new VersionScope(
                List.of(activeConstraint),
                List.of(maintenanceConstraint),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("react-router", "4.3.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.OBSOLETE);
    }

    @Test
    @DisplayName("Unknown React Router version should resolve to UNKNOWN")
    void unknownReactRouterVersionShouldResolveToUnknown() {
        VersionConstraint activeConstraint = VersionConstraint.packageVersion("react-router", ">=7.0.0 <8.0.0", "npm");
        VersionConstraint maintenanceConstraint = VersionConstraint.packageVersion("react-router", ">=6.0.0 <7.0.0", "npm");
        VersionConstraint obsoleteConstraint = VersionConstraint.packageVersion("react-router", ">=3.0.0 <6.0.0", "npm");

        VersionScope scope = new VersionScope(
                List.of(activeConstraint),
                List.of(maintenanceConstraint),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("react-router", "99.0.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.UNKNOWN);
    }

    // Java version range tests

    @Test
    @DisplayName("Java 21 should resolve to ACTIVE")
    void java21ShouldResolveToActive() {
        VersionConstraint activeConstraint = VersionConstraint.runtimeVersion("java", ">=21", "jvm");
        VersionConstraint maintenanceConstraint = VersionConstraint.runtimeVersion("java", ">=17 <21", "jvm");
        VersionConstraint obsoleteConstraint = VersionConstraint.runtimeVersion("java", ">=11 <17", "jvm");

        VersionScope scope = new VersionScope(
                List.of(activeConstraint),
                List.of(maintenanceConstraint),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("java", "21.0.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.ACTIVE);
    }

    @Test
    @DisplayName("Java 17 should resolve to MAINTENANCE")
    void java17ShouldResolveToMaintenance() {
        VersionConstraint activeConstraint = VersionConstraint.runtimeVersion("java", ">=21", "jvm");
        VersionConstraint maintenanceConstraint = VersionConstraint.runtimeVersion("java", ">=17 <21", "jvm");
        VersionConstraint obsoleteConstraint = VersionConstraint.runtimeVersion("java", ">=11 <17", "jvm");

        VersionScope scope = new VersionScope(
                List.of(activeConstraint),
                List.of(maintenanceConstraint),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("java", "17.0.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.MAINTENANCE);
    }

    @Test
    @DisplayName("Java 11 should resolve to OBSOLETE")
    void java11ShouldResolveToObsolete() {
        VersionConstraint activeConstraint = VersionConstraint.runtimeVersion("java", ">=21", "jvm");
        VersionConstraint maintenanceConstraint = VersionConstraint.runtimeVersion("java", ">=17 <21", "jvm");
        VersionConstraint obsoleteConstraint = VersionConstraint.runtimeVersion("java", ">=11 <17", "jvm");

        VersionScope scope = new VersionScope(
                List.of(activeConstraint),
                List.of(maintenanceConstraint),
                List.of(obsoleteConstraint)
        );

        VersionContext context = new VersionContext(
                Map.of("java", "11.0.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.classify(context)).isEqualTo(VersionApplicability.OBSOLETE);
    }

    // NPM semver range tests

    @Test
    @DisplayName("Should handle npm caret range correctly")
    void shouldHandleNpmCaretRangeCorrectly() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", "^18.0.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

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
    @DisplayName("Should reject version outside npm caret range")
    void shouldRejectVersionOutsideNpmCaretRange() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", "^18.0.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("react", "19.0.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isFalse();
    }

    @Test
    @DisplayName("Should handle npm tilde range correctly")
    void shouldHandleNpmTildeRangeCorrectly() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", "~18.2.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("react", "18.2.5"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isTrue();
    }

    @Test
    @DisplayName("Should reject version outside npm tilde range")
    void shouldRejectVersionOutsideNpmTildeRange() {
        VersionConstraint constraint = VersionConstraint.packageVersion("react", "~18.2.0", "npm");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("react", "18.3.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isFalse();
    }

    // Maven range tests

    @Test
    @DisplayName("Should handle Maven inclusive range")
    void shouldHandleMavenInclusiveRange() {
        VersionConstraint constraint = VersionConstraint.packageVersion("spring-core", "[5.0,6.0]", "maven");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("spring-core", "5.3.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isTrue();
    }

    @Test
    @DisplayName("Should handle Maven exclusive range")
    void shouldHandleMavenExclusiveRange() {
        VersionConstraint constraint = VersionConstraint.packageVersion("spring-core", "(5.0,6.0)", "maven");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("spring-core", "5.5.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isTrue();
    }

    @Test
    @DisplayName("Should reject version at Maven exclusive bound")
    void shouldRejectVersionAtMavenExclusiveBound() {
        VersionConstraint constraint = VersionConstraint.packageVersion("spring-core", "(5.0,6.0)", "maven");
        VersionScope scope = VersionScope.activeOnly(List.of(constraint));

        VersionContext context = new VersionContext(
                Map.of("spring-core", "5.0.0"),
                Map.of(),
                Map.of(),
                Map.of(),
                "test",
                Instant.now()
        );

        assertThat(scope.supportsActive(context)).isFalse();
    }
}
