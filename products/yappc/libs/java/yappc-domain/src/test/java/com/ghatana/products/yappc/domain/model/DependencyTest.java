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
@DisplayName("Dependency Domain Model Tests [GH-90000]")
class DependencyTest {

    private static final String ECOSYSTEM_NPM = "npm";
    private static final String ECOSYSTEM_MAVEN = "maven";
    private static final String PACKAGE_NAME = "lodash";
    private static final String VERSION = "4.17.21";

    @Nested
    @DisplayName("Factory Method Tests [GH-90000]")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates dependency with required fields and defaults [GH-90000]")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // GIVEN
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000

            // WHEN
            Dependency dependency = Dependency.of(workspaceId, projectId, ECOSYSTEM_NPM, PACKAGE_NAME, VERSION); // GH-90000

            // THEN
            assertThat(dependency.getWorkspaceId()).isEqualTo(workspaceId); // GH-90000
            assertThat(dependency.getProjectId()).isEqualTo(projectId); // GH-90000
            assertThat(dependency.getEcosystem()).isEqualTo(ECOSYSTEM_NPM); // GH-90000
            assertThat(dependency.getName()).isEqualTo(PACKAGE_NAME); // GH-90000
            assertThat(dependency.getVersion()).isEqualTo(VERSION); // GH-90000
            assertThat(dependency.isDirect()).isTrue(); // GH-90000
            assertThat(dependency.isOutdated()).isFalse(); // GH-90000
            assertThat(dependency.getVulnerabilityCount()).isZero(); // GH-90000
            assertThat(dependency.getDiscoveredAt()).isNotNull(); // GH-90000
            assertThat(dependency.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null [GH-90000]")
        void ofThrowsWhenWorkspaceIdNull() { // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000

            assertThatThrownBy(() -> Dependency.of(null, projectId, ECOSYSTEM_NPM, PACKAGE_NAME, VERSION)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when projectId is null [GH-90000]")
        void ofThrowsWhenProjectIdNull() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            assertThatThrownBy(() -> Dependency.of(workspaceId, null, ECOSYSTEM_NPM, PACKAGE_NAME, VERSION)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("projectId must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when ecosystem is null [GH-90000]")
        void ofThrowsWhenEcosystemNull() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000

            assertThatThrownBy(() -> Dependency.of(workspaceId, projectId, null, PACKAGE_NAME, VERSION)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("ecosystem must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when name is null [GH-90000]")
        void ofThrowsWhenNameNull() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000

            assertThatThrownBy(() -> Dependency.of(workspaceId, projectId, ECOSYSTEM_NPM, null, VERSION)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("name must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when version is null [GH-90000]")
        void ofThrowsWhenVersionNull() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000

            assertThatThrownBy(() -> Dependency.of(workspaceId, projectId, ECOSYSTEM_NPM, PACKAGE_NAME, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("version must not be null [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Builder Tests [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builder creates dependency with all fields [GH-90000]")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            // WHEN
            Dependency dependency = Dependency.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(workspaceId) // GH-90000
                    .projectId(projectId) // GH-90000
                    .ecosystem(ECOSYSTEM_NPM) // GH-90000
                    .name(PACKAGE_NAME) // GH-90000
                    .version(VERSION) // GH-90000
                    .latestVersion("4.18.0 [GH-90000]")
                    .isDirect(true) // GH-90000
                    .license("MIT [GH-90000]")
                    .vulnerabilityCount(2) // GH-90000
                    .maxSeverity("HIGH [GH-90000]")
                    .isOutdated(true) // GH-90000
                    .discoveredAt(now.minusSeconds(86400)) // GH-90000
                    .updatedAt(now) // GH-90000
                    .versionLock(5) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(dependency.getId()).isEqualTo(id); // GH-90000
            assertThat(dependency.getWorkspaceId()).isEqualTo(workspaceId); // GH-90000
            assertThat(dependency.getProjectId()).isEqualTo(projectId); // GH-90000
            assertThat(dependency.getEcosystem()).isEqualTo(ECOSYSTEM_NPM); // GH-90000
            assertThat(dependency.getName()).isEqualTo(PACKAGE_NAME); // GH-90000
            assertThat(dependency.getVersion()).isEqualTo(VERSION); // GH-90000
            assertThat(dependency.getLatestVersion()).isEqualTo("4.18.0 [GH-90000]");
            assertThat(dependency.isDirect()).isTrue(); // GH-90000
            assertThat(dependency.getLicense()).isEqualTo("MIT [GH-90000]");
            assertThat(dependency.getVulnerabilityCount()).isEqualTo(2); // GH-90000
            assertThat(dependency.getMaxSeverity()).isEqualTo("HIGH [GH-90000]");
            assertThat(dependency.isOutdated()).isTrue(); // GH-90000
            assertThat(dependency.getVersionLock()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("builder defaults isDirect to true [GH-90000]")
        void builderDefaultsIsDirectToTrue() { // GH-90000
            Dependency dependency = Dependency.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .projectId(UUID.randomUUID()) // GH-90000
                    .ecosystem(ECOSYSTEM_NPM) // GH-90000
                    .name(PACKAGE_NAME) // GH-90000
                    .version(VERSION) // GH-90000
                    .build(); // GH-90000

            assertThat(dependency.isDirect()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("builder defaults isOutdated to false [GH-90000]")
        void builderDefaultsIsOutdatedToFalse() { // GH-90000
            Dependency dependency = Dependency.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .projectId(UUID.randomUUID()) // GH-90000
                    .ecosystem(ECOSYSTEM_NPM) // GH-90000
                    .name(PACKAGE_NAME) // GH-90000
                    .version(VERSION) // GH-90000
                    .build(); // GH-90000

            assertThat(dependency.isOutdated()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("builder defaults vulnerabilityCount to 0 [GH-90000]")
        void builderDefaultsVulnerabilityCountToZero() { // GH-90000
            Dependency dependency = Dependency.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .projectId(UUID.randomUUID()) // GH-90000
                    .ecosystem(ECOSYSTEM_NPM) // GH-90000
                    .name(PACKAGE_NAME) // GH-90000
                    .version(VERSION) // GH-90000
                    .build(); // GH-90000

            assertThat(dependency.getVulnerabilityCount()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Vulnerability Tests [GH-90000]")
    class VulnerabilityTests {

        @Test
        @DisplayName("hasVulnerabilities() returns true when vulnerabilityCount > 0 [GH-90000]")
        void hasVulnerabilitiesReturnsTrueWhenCountPositive() { // GH-90000
            Dependency dependency = Dependency.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .projectId(UUID.randomUUID()) // GH-90000
                    .ecosystem(ECOSYSTEM_NPM) // GH-90000
                    .name(PACKAGE_NAME) // GH-90000
                    .version(VERSION) // GH-90000
                    .vulnerabilityCount(3) // GH-90000
                    .build(); // GH-90000

            assertThat(dependency.hasVulnerabilities()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("hasVulnerabilities() returns false when vulnerabilityCount is 0 [GH-90000]")
        void hasVulnerabilitiesReturnsFalseWhenCountZero() { // GH-90000
            Dependency dependency = Dependency.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    UUID.randomUUID(), // GH-90000
                    ECOSYSTEM_NPM,
                    PACKAGE_NAME,
                    VERSION
            );

            assertThat(dependency.hasVulnerabilities()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("can track severity levels [GH-90000]")
        void canTrackSeverityLevels() { // GH-90000
            String[] severities = {"CRITICAL", "HIGH", "MEDIUM", "LOW"};

            for (String severity : severities) { // GH-90000
                Dependency dependency = Dependency.builder() // GH-90000
                        .workspaceId(UUID.randomUUID()) // GH-90000
                        .projectId(UUID.randomUUID()) // GH-90000
                        .ecosystem(ECOSYSTEM_NPM) // GH-90000
                        .name(PACKAGE_NAME) // GH-90000
                        .version(VERSION) // GH-90000
                        .vulnerabilityCount(1) // GH-90000
                        .maxSeverity(severity) // GH-90000
                        .build(); // GH-90000

                assertThat(dependency.getMaxSeverity()).isEqualTo(severity); // GH-90000
                assertThat(dependency.hasVulnerabilities()).isTrue(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Update Check Tests [GH-90000]")
    class UpdateCheckTests {

        @Test
        @DisplayName("hasUpdate() returns true when latestVersion differs [GH-90000]")
        void hasUpdateReturnsTrueWhenVersionDiffers() { // GH-90000
            Dependency dependency = Dependency.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .projectId(UUID.randomUUID()) // GH-90000
                    .ecosystem(ECOSYSTEM_NPM) // GH-90000
                    .name(PACKAGE_NAME) // GH-90000
                    .version("4.17.21 [GH-90000]")
                    .latestVersion("4.18.0 [GH-90000]")
                    .build(); // GH-90000

            assertThat(dependency.hasUpdate()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("hasUpdate() returns false when version equals latestVersion [GH-90000]")
        void hasUpdateReturnsFalseWhenVersionsEqual() { // GH-90000
            Dependency dependency = Dependency.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .projectId(UUID.randomUUID()) // GH-90000
                    .ecosystem(ECOSYSTEM_NPM) // GH-90000
                    .name(PACKAGE_NAME) // GH-90000
                    .version("4.17.21 [GH-90000]")
                    .latestVersion("4.17.21 [GH-90000]")
                    .build(); // GH-90000

            assertThat(dependency.hasUpdate()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasUpdate() returns false when latestVersion is null [GH-90000]")
        void hasUpdateReturnsFalseWhenLatestVersionNull() { // GH-90000
            Dependency dependency = Dependency.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    UUID.randomUUID(), // GH-90000
                    ECOSYSTEM_NPM,
                    PACKAGE_NAME,
                    VERSION
            );

            assertThat(dependency.hasUpdate()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Ecosystem Tests [GH-90000]")
    class EcosystemTests {

        @Test
        @DisplayName("can create npm dependencies [GH-90000]")
        void canCreateNpmDependency() { // GH-90000
            Dependency dependency = Dependency.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    UUID.randomUUID(), // GH-90000
                    "npm",
                    "express",
                    "4.18.2"
            );

            assertThat(dependency.getEcosystem()).isEqualTo("npm [GH-90000]");
        }

        @Test
        @DisplayName("can create maven dependencies [GH-90000]")
        void canCreateMavenDependency() { // GH-90000
            Dependency dependency = Dependency.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    UUID.randomUUID(), // GH-90000
                    "maven",
                    "org.springframework:spring-core",
                    "6.1.0"
            );

            assertThat(dependency.getEcosystem()).isEqualTo("maven [GH-90000]");
        }

        @Test
        @DisplayName("can create pip dependencies [GH-90000]")
        void canCreatePipDependency() { // GH-90000
            Dependency dependency = Dependency.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    UUID.randomUUID(), // GH-90000
                    "pip",
                    "requests",
                    "2.31.0"
            );

            assertThat(dependency.getEcosystem()).isEqualTo("pip [GH-90000]");
        }

        @Test
        @DisplayName("can create nuget dependencies [GH-90000]")
        void canCreateNugetDependency() { // GH-90000
            Dependency dependency = Dependency.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    UUID.randomUUID(), // GH-90000
                    "nuget",
                    "Newtonsoft.Json",
                    "13.0.3"
            );

            assertThat(dependency.getEcosystem()).isEqualTo("nuget [GH-90000]");
        }
    }

    @Nested
    @DisplayName("License Tests [GH-90000]")
    class LicenseTests {

        @Test
        @DisplayName("can track various license types [GH-90000]")
        void canTrackVariousLicenseTypes() { // GH-90000
            String[] licenses = {"MIT", "Apache-2.0", "GPL-3.0", "BSD-3-Clause", "ISC", "MPL-2.0"};

            for (String license : licenses) { // GH-90000
                Dependency dependency = Dependency.builder() // GH-90000
                        .workspaceId(UUID.randomUUID()) // GH-90000
                        .projectId(UUID.randomUUID()) // GH-90000
                        .ecosystem(ECOSYSTEM_NPM) // GH-90000
                        .name(PACKAGE_NAME) // GH-90000
                        .version(VERSION) // GH-90000
                        .license(license) // GH-90000
                        .build(); // GH-90000

                assertThat(dependency.getLicense()).isEqualTo(license); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Transitive Dependency Tests [GH-90000]")
    class TransitiveDependencyTests {

        @Test
        @DisplayName("can mark dependency as transitive [GH-90000]")
        void canMarkAsTransitive() { // GH-90000
            Dependency dependency = Dependency.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .projectId(UUID.randomUUID()) // GH-90000
                    .ecosystem(ECOSYSTEM_NPM) // GH-90000
                    .name(PACKAGE_NAME) // GH-90000
                    .version(VERSION) // GH-90000
                    .isDirect(false) // GH-90000
                    .build(); // GH-90000

            assertThat(dependency.isDirect()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Equality Tests [GH-90000]")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id [GH-90000]")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Dependency dep1 = Dependency.builder().id(id).ecosystem("npm [GH-90000]").name("a [GH-90000]").version("1 [GH-90000]").build();
            Dependency dep2 = Dependency.builder().id(id).ecosystem("pip [GH-90000]").name("b [GH-90000]").version("2 [GH-90000]").build();

            assertThat(dep1).isEqualTo(dep2); // GH-90000
            assertThat(dep1.hashCode()).isEqualTo(dep2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids [GH-90000]")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            Dependency dep1 = Dependency.builder().id(UUID.randomUUID()).build(); // GH-90000
            Dependency dep2 = Dependency.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(dep1).isNotEqualTo(dep2); // GH-90000
        }
    }
}
