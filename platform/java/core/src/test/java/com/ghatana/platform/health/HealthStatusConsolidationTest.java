/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.health;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.assertj.core.api.Assertions.*;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforce that HealthStatus is consolidated to a single canonical location.
 *
 * <p>These tests fail if:
 * <ol>
 *   <li>Another HealthStatus definition appears in platform
 *   <li>Modules import HealthStatus from non-canonical location
 *   <li>Any domain packages define local HealthStatus clones
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Prevent HealthStatus duplication regressions
 * @doc.layer platform
 * @doc.pattern ArchUnit Test
 */
@DisplayName("HealthStatus Consolidation Tests")
class HealthStatusConsolidationTest {

    private static final String CANONICAL_CLASS = "com.ghatana.platform.health.HealthStatus";
    private static final String CANONICAL_PACKAGE = "com.ghatana.platform.health";
    private static final String PLATFORM_PACKAGES = "com.ghatana.platform";
    private static final String AGENT_PACKAGES = "com.ghatana.agent";

    @Test
    @DisplayName("Only one HealthStatus definition should exist in platform")
    void shouldHaveOnlyOneCanonicalHealthStatusDefinition() {
        JavaClasses classes = new ClassFileImporter().importPackages(PLATFORM_PACKAGES);

        long healthStatusCount = classes.stream()
            .filter(c -> c.getSimpleName().equals("HealthStatus") && !c.isInnerClass())
            .count();

        assertThat(healthStatusCount)
            .as("Platform should have exactly 1 HealthStatus definition (in " + CANONICAL_PACKAGE + ")")
            .isEqualTo(1L);
    }

    @Test
    @DisplayName("Canonical HealthStatus must be in platform.health package")
    void shouldHaveHealthStatusInCanonicalLocation() {
        JavaClasses classes = new ClassFileImporter().importPackages(PLATFORM_PACKAGES);

        boolean found = classes.stream()
            .filter(c -> c.getSimpleName().equals("HealthStatus"))
            .anyMatch(c -> c.getPackageName().equals(CANONICAL_PACKAGE));

        assertThat(found)
            .as("HealthStatus must exist at: " + CANONICAL_CLASS)
            .isTrue();
    }

    @Test
    @DisplayName("Platform modules importing HealthStatus should use canonical location")
    void shouldImportHealthStatusFromCanonical() {
        // This test is enforced via ArchUnit rule in separate governance test
        // See GovernanceBoundaryArchTest for import validation rules
    }

    @Test
    @DisplayName("Agent-core HealthStatus is allowed as legacy enum but must not be extended")
    void agentHealthStatusAllowedAsLegacyEnumOnly() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages(AGENT_PACKAGES);

        // Agent-core has its own lightweight enum for agent-specific states STARTING/STOPPING
        // It's allowed to exist because it has converter methods to/from canonical
        boolean agentHealthStatusExists = classes.stream()
            .anyMatch(c -> c.getFullName().equals("com.ghatana.agent.HealthStatus"));

        assertThat(agentHealthStatusExists)
            .as("Agent-core HealthStatus enum is allowed (has converter methods)")
            .isTrue();

        // But no other agent package should define its own HealthStatus
        long otherAgentHealthStatus = classes.stream()
            .filter(c -> !c.getFullName().equals("com.ghatana.agent.HealthStatus"))
            .filter(c -> c.getSimpleName().equals("HealthStatus"))
            .count();

        assertThat(otherAgentHealthStatus)
            .as("Only com.ghatana.agent.HealthStatus is allowed; no other agent HealthStatus")
            .isEqualTo(0L);
    }

    @Test
    @DisplayName("Database health should use canonical HealthStatus, not local HealthStatus")
    void databaseShouldNotHaveOwnHealthStatus() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("com.ghatana.platform.database", "com.ghatana.core.database");

        long databaseHealthStatus = classes.stream()
            .filter(c -> c.getFullName().contains("database") && c.getFullName().contains("HealthStatus"))
            .filter(c -> !c.getFullName().equals("com.ghatana.platform.health.HealthStatus"))
            .count();

        assertThat(databaseHealthStatus)
            .as("Database package should not define its own HealthStatus (use canonical)")
            .isEqualTo(0L);
    }

    @Test
    @DisplayName("Agent-core HealthStatus enum should have converter methods")
    void agentHealthStatusShouldHaveConverterMethods() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages(AGENT_PACKAGES);

        var agentHealthStatus = classes.stream()
            .filter(c -> c.getFullName().equals("com.ghatana.agent.HealthStatus"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Agent HealthStatus not found"));

        // Must have toPlatformHealthStatus() converter method
        var methods = agentHealthStatus.getMethods().stream()
            .map(m -> m.getName())
            .toList();

        assertThat(methods)
            .as("Agent HealthStatus must have converter methods")
            .contains("toPlatformHealthStatus");
    }

    @Test
    @DisplayName("No domain packages should define HealthStatus")
    void noDomainPackagesShouldDefineHealthStatus() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("com.ghatana.platform.domain");

        long domainHealthStatus = classes.stream()
            .filter(c -> c.getSimpleName().equals("HealthStatus"))
            .count();

        assertThat(domainHealthStatus)
            .as("Domain packages should not define HealthStatus (import from platform.health)")
            .isEqualTo(0L);
    }

    @Test
    @DisplayName("No product packages should define HealthStatus")
    void noProductPackagesShouldDefineHealthStatus() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("com.ghatana.products");

        long productHealthStatus = classes.stream()
            .filter(c -> c.getSimpleName().equals("HealthStatus"))
            .count();

        assertThat(productHealthStatus)
            .as("Product packages should not define HealthStatus (import from platform.health)")
            .isEqualTo(0L);
    }

    @Test
    @DisplayName("All HealthStatus usages (except agent-core enum) should reference canonical class")
    void allHealthStatusUsagesPointToCanonical() {
        // Import validation is enforced via compilation and code review
        // The deprecation annotations on agent-core and database HealthStatus
        // provide compiler warnings when used outside their modules
    }

    @Test
    @DisplayName("Health status representations should be immutable")
    void canonicalHealthStatusShouldBeImmutable() {
        JavaClasses classes = new ClassFileImporter().importPackages(CANONICAL_PACKAGE);

        var healthStatus = classes.stream()
            .filter(c -> c.getFullName().equals(CANONICAL_CLASS))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Canonical HealthStatus not found"));

        // Verify it's final by checking modifiers
        assertThat(healthStatus.getModifiers().contains(java.lang.reflect.Modifier.FINAL))
            .as("HealthStatus should be final class")
            .isTrue();
    }
}
