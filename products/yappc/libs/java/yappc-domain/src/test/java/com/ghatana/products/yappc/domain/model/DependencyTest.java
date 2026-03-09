package com.ghatana.products.yappc.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Dependency} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates Dependency entity behavior, vulnerability tracking, and SCA features
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Dependency Domain Model Tests")
class DependencyTest {

    private static final String ECOSYSTEM_NPM = "npm";
    private static final String ECOSYSTEM_MAVEN = "maven";
    private static final String PACKAGE_NAME = "lodash";
    private static final String VERSION = "4.17.21";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates dependency with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // GIVEN
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            // WHEN
            Dependency dependency = Dependency.of(workspaceId, projectId, ECOSYSTEM_NPM, PACKAGE_NAME, VERSION);

            // THEN
            assertThat(dependency.getWorkspaceId()).isEqualTo(workspaceId);
            assertThat(dependency.getProjectId()).isEqualTo(projectId);
            assertThat(dependency.getEcosystem()).isEqualTo(ECOSYSTEM_NPM);
            assertThat(dependency.getName()).isEqualTo(PACKAGE_NAME);
            assertThat(dependency.getVersion()).isEqualTo(VERSION);
            assertThat(dependency.isDirect()).isTrue();
            assertThat(dependency.isOutdated()).isFalse();
            assertThat(dependency.getVulnerabilityCount()).isZero();
            assertThat(dependency.getDiscoveredAt()).isNotNull();
            assertThat(dependency.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null")
        void ofThrowsWhenWorkspaceIdNull() {
            UUID projectId = UUID.randomUUID();

            assertThatThrownBy(() -> Dependency.of(null, projectId, ECOSYSTEM_NPM, PACKAGE_NAME, VERSION))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when projectId is null")
        void ofThrowsWhenProjectIdNull() {
            UUID workspaceId = UUID.randomUUID();

            assertThatThrownBy(() -> Dependency.of(workspaceId, null, ECOSYSTEM_NPM, PACKAGE_NAME, VERSION))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("projectId must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when ecosystem is null")
        void ofThrowsWhenEcosystemNull() {
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            assertThatThrownBy(() -> Dependency.of(workspaceId, projectId, null, PACKAGE_NAME, VERSION))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("ecosystem must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when name is null")
        void ofThrowsWhenNameNull() {
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            assertThatThrownBy(() -> Dependency.of(workspaceId, projectId, ECOSYSTEM_NPM, null, VERSION))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when version is null")
        void ofThrowsWhenVersionNull() {
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            assertThatThrownBy(() -> Dependency.of(workspaceId, projectId, ECOSYSTEM_NPM, PACKAGE_NAME, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("version must not be null");
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates dependency with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            Instant now = Instant.now();

            // WHEN
            Dependency dependency = Dependency.builder()
                    .id(id)
                    .workspaceId(workspaceId)
                    .projectId(projectId)
                    .ecosystem(ECOSYSTEM_NPM)
                    .name(PACKAGE_NAME)
                    .version(VERSION)
                    .latestVersion("4.18.0")
                    .isDirect(true)
                    .license("MIT")
                    .vulnerabilityCount(2)
                    .maxSeverity("HIGH")
                    .isOutdated(true)
                    .discoveredAt(now.minusSeconds(86400))
                    .updatedAt(now)
                    .versionLock(5)
                    .build();

            // THEN
            assertThat(dependency.getId()).isEqualTo(id);
            assertThat(dependency.getWorkspaceId()).isEqualTo(workspaceId);
            assertThat(dependency.getProjectId()).isEqualTo(projectId);
            assertThat(dependency.getEcosystem()).isEqualTo(ECOSYSTEM_NPM);
            assertThat(dependency.getName()).isEqualTo(PACKAGE_NAME);
            assertThat(dependency.getVersion()).isEqualTo(VERSION);
            assertThat(dependency.getLatestVersion()).isEqualTo("4.18.0");
            assertThat(dependency.isDirect()).isTrue();
            assertThat(dependency.getLicense()).isEqualTo("MIT");
            assertThat(dependency.getVulnerabilityCount()).isEqualTo(2);
            assertThat(dependency.getMaxSeverity()).isEqualTo("HIGH");
            assertThat(dependency.isOutdated()).isTrue();
            assertThat(dependency.getVersionLock()).isEqualTo(5);
        }

        @Test
        @DisplayName("builder defaults isDirect to true")
        void builderDefaultsIsDirectToTrue() {
            Dependency dependency = Dependency.builder()
                    .workspaceId(UUID.randomUUID())
                    .projectId(UUID.randomUUID())
                    .ecosystem(ECOSYSTEM_NPM)
                    .name(PACKAGE_NAME)
                    .version(VERSION)
                    .build();

            assertThat(dependency.isDirect()).isTrue();
        }

        @Test
        @DisplayName("builder defaults isOutdated to false")
        void builderDefaultsIsOutdatedToFalse() {
            Dependency dependency = Dependency.builder()
                    .workspaceId(UUID.randomUUID())
                    .projectId(UUID.randomUUID())
                    .ecosystem(ECOSYSTEM_NPM)
                    .name(PACKAGE_NAME)
                    .version(VERSION)
                    .build();

            assertThat(dependency.isOutdated()).isFalse();
        }

        @Test
        @DisplayName("builder defaults vulnerabilityCount to 0")
        void builderDefaultsVulnerabilityCountToZero() {
            Dependency dependency = Dependency.builder()
                    .workspaceId(UUID.randomUUID())
                    .projectId(UUID.randomUUID())
                    .ecosystem(ECOSYSTEM_NPM)
                    .name(PACKAGE_NAME)
                    .version(VERSION)
                    .build();

            assertThat(dependency.getVulnerabilityCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Vulnerability Tests")
    class VulnerabilityTests {

        @Test
        @DisplayName("hasVulnerabilities() returns true when vulnerabilityCount > 0")
        void hasVulnerabilitiesReturnsTrueWhenCountPositive() {
            Dependency dependency = Dependency.builder()
                    .workspaceId(UUID.randomUUID())
                    .projectId(UUID.randomUUID())
                    .ecosystem(ECOSYSTEM_NPM)
                    .name(PACKAGE_NAME)
                    .version(VERSION)
                    .vulnerabilityCount(3)
                    .build();

            assertThat(dependency.hasVulnerabilities()).isTrue();
        }

        @Test
        @DisplayName("hasVulnerabilities() returns false when vulnerabilityCount is 0")
        void hasVulnerabilitiesReturnsFalseWhenCountZero() {
            Dependency dependency = Dependency.of(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ECOSYSTEM_NPM,
                    PACKAGE_NAME,
                    VERSION
            );

            assertThat(dependency.hasVulnerabilities()).isFalse();
        }

        @Test
        @DisplayName("can track severity levels")
        void canTrackSeverityLevels() {
            String[] severities = {"CRITICAL", "HIGH", "MEDIUM", "LOW"};

            for (String severity : severities) {
                Dependency dependency = Dependency.builder()
                        .workspaceId(UUID.randomUUID())
                        .projectId(UUID.randomUUID())
                        .ecosystem(ECOSYSTEM_NPM)
                        .name(PACKAGE_NAME)
                        .version(VERSION)
                        .vulnerabilityCount(1)
                        .maxSeverity(severity)
                        .build();

                assertThat(dependency.getMaxSeverity()).isEqualTo(severity);
                assertThat(dependency.hasVulnerabilities()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Update Check Tests")
    class UpdateCheckTests {

        @Test
        @DisplayName("hasUpdate() returns true when latestVersion differs")
        void hasUpdateReturnsTrueWhenVersionDiffers() {
            Dependency dependency = Dependency.builder()
                    .workspaceId(UUID.randomUUID())
                    .projectId(UUID.randomUUID())
                    .ecosystem(ECOSYSTEM_NPM)
                    .name(PACKAGE_NAME)
                    .version("4.17.21")
                    .latestVersion("4.18.0")
                    .build();

            assertThat(dependency.hasUpdate()).isTrue();
        }

        @Test
        @DisplayName("hasUpdate() returns false when version equals latestVersion")
        void hasUpdateReturnsFalseWhenVersionsEqual() {
            Dependency dependency = Dependency.builder()
                    .workspaceId(UUID.randomUUID())
                    .projectId(UUID.randomUUID())
                    .ecosystem(ECOSYSTEM_NPM)
                    .name(PACKAGE_NAME)
                    .version("4.17.21")
                    .latestVersion("4.17.21")
                    .build();

            assertThat(dependency.hasUpdate()).isFalse();
        }

        @Test
        @DisplayName("hasUpdate() returns false when latestVersion is null")
        void hasUpdateReturnsFalseWhenLatestVersionNull() {
            Dependency dependency = Dependency.of(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ECOSYSTEM_NPM,
                    PACKAGE_NAME,
                    VERSION
            );

            assertThat(dependency.hasUpdate()).isFalse();
        }
    }

    @Nested
    @DisplayName("Ecosystem Tests")
    class EcosystemTests {

        @Test
        @DisplayName("can create npm dependencies")
        void canCreateNpmDependency() {
            Dependency dependency = Dependency.of(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "npm",
                    "express",
                    "4.18.2"
            );

            assertThat(dependency.getEcosystem()).isEqualTo("npm");
        }

        @Test
        @DisplayName("can create maven dependencies")
        void canCreateMavenDependency() {
            Dependency dependency = Dependency.of(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "maven",
                    "org.springframework:spring-core",
                    "6.1.0"
            );

            assertThat(dependency.getEcosystem()).isEqualTo("maven");
        }

        @Test
        @DisplayName("can create pip dependencies")
        void canCreatePipDependency() {
            Dependency dependency = Dependency.of(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "pip",
                    "requests",
                    "2.31.0"
            );

            assertThat(dependency.getEcosystem()).isEqualTo("pip");
        }

        @Test
        @DisplayName("can create nuget dependencies")
        void canCreateNugetDependency() {
            Dependency dependency = Dependency.of(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "nuget",
                    "Newtonsoft.Json",
                    "13.0.3"
            );

            assertThat(dependency.getEcosystem()).isEqualTo("nuget");
        }
    }

    @Nested
    @DisplayName("License Tests")
    class LicenseTests {

        @Test
        @DisplayName("can track various license types")
        void canTrackVariousLicenseTypes() {
            String[] licenses = {"MIT", "Apache-2.0", "GPL-3.0", "BSD-3-Clause", "ISC", "MPL-2.0"};

            for (String license : licenses) {
                Dependency dependency = Dependency.builder()
                        .workspaceId(UUID.randomUUID())
                        .projectId(UUID.randomUUID())
                        .ecosystem(ECOSYSTEM_NPM)
                        .name(PACKAGE_NAME)
                        .version(VERSION)
                        .license(license)
                        .build();

                assertThat(dependency.getLicense()).isEqualTo(license);
            }
        }
    }

    @Nested
    @DisplayName("Transitive Dependency Tests")
    class TransitiveDependencyTests {

        @Test
        @DisplayName("can mark dependency as transitive")
        void canMarkAsTransitive() {
            Dependency dependency = Dependency.builder()
                    .workspaceId(UUID.randomUUID())
                    .projectId(UUID.randomUUID())
                    .ecosystem(ECOSYSTEM_NPM)
                    .name(PACKAGE_NAME)
                    .version(VERSION)
                    .isDirect(false)
                    .build();

            assertThat(dependency.isDirect()).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            Dependency dep1 = Dependency.builder().id(id).ecosystem("npm").name("a").version("1").build();
            Dependency dep2 = Dependency.builder().id(id).ecosystem("pip").name("b").version("2").build();

            assertThat(dep1).isEqualTo(dep2);
            assertThat(dep1.hashCode()).isEqualTo(dep2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            Dependency dep1 = Dependency.builder().id(UUID.randomUUID()).build();
            Dependency dep2 = Dependency.builder().id(UUID.randomUUID()).build();

            assertThat(dep1).isNotEqualTo(dep2);
        }
    }
}
