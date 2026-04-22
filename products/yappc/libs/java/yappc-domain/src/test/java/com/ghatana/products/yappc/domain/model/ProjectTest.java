package com.ghatana.products.yappc.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Project} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates Project entity behavior, archive lifecycle, and scan tracking
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Project Domain Model Tests [GH-90000]")
class ProjectTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID(); // GH-90000
    private static final String PROJECT_NAME = "my-security-app";
    private static final String REPO_URL = "https://github.com/org/my-security-app";

    @Nested
    @DisplayName("Factory Method Tests [GH-90000]")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates project with required fields and defaults [GH-90000]")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // WHEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME); // GH-90000

            // THEN
            assertThat(project.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(project.getName()).isEqualTo(PROJECT_NAME); // GH-90000
            assertThat(project.getDefaultBranch()).isEqualTo("main [GH-90000]");
            assertThat(project.isArchived()).isFalse(); // GH-90000
            assertThat(project.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(project.getUpdatedAt()).isNotNull(); // GH-90000
            assertThat(project.getLastScanAt()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null [GH-90000]")
        void ofThrowsWhenWorkspaceIdNull() { // GH-90000
            assertThatThrownBy(() -> Project.of(null, PROJECT_NAME)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when name is null [GH-90000]")
        void ofThrowsWhenNameNull() { // GH-90000
            assertThatThrownBy(() -> Project.of(WORKSPACE_ID, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("name must not be null [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Archive Lifecycle Tests [GH-90000]")
    class ArchiveLifecycleTests {

        @Test
        @DisplayName("archive() sets archived to true [GH-90000]")
        void archiveSetsArchivedTrue() { // GH-90000
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME); // GH-90000
            assertThat(project.isArchived()).isFalse(); // GH-90000

            // WHEN
            Project result = project.archive(); // GH-90000

            // THEN
            assertThat(result).isSameAs(project); // GH-90000
            assertThat(project.isArchived()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("unarchive() sets archived to false [GH-90000]")
        void unarchiveSetsArchivedFalse() { // GH-90000
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME); // GH-90000
            project.archive(); // GH-90000
            assertThat(project.isArchived()).isTrue(); // GH-90000

            // WHEN
            Project result = project.unarchive(); // GH-90000

            // THEN
            assertThat(result).isSameAs(project); // GH-90000
            assertThat(project.isArchived()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("archive and unarchive update updatedAt [GH-90000]")
        void archiveUnarchiveUpdateTimestamp() throws InterruptedException { // GH-90000
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME); // GH-90000
            Instant originalUpdatedAt = project.getUpdatedAt(); // GH-90000
            Thread.sleep(10); // GH-90000

            // WHEN
            project.archive(); // GH-90000
            Instant afterArchive = project.getUpdatedAt(); // GH-90000
            Thread.sleep(10); // GH-90000
            project.unarchive(); // GH-90000
            Instant afterUnarchive = project.getUpdatedAt(); // GH-90000

            // THEN
            assertThat(afterArchive).isAfter(originalUpdatedAt); // GH-90000
            assertThat(afterUnarchive).isAfter(afterArchive); // GH-90000
        }
    }

    @Nested
    @DisplayName("Scan Tracking Tests [GH-90000]")
    class ScanTrackingTests {

        @Test
        @DisplayName("recordScan() sets lastScanAt [GH-90000]")
        void recordScanSetsLastScanAt() { // GH-90000
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME); // GH-90000
            assertThat(project.getLastScanAt()).isNull(); // GH-90000
            Instant beforeScan = Instant.now(); // GH-90000

            // WHEN
            Project result = project.recordScan(); // GH-90000

            // THEN
            assertThat(result).isSameAs(project); // GH-90000
            assertThat(project.getLastScanAt()).isAfterOrEqualTo(beforeScan); // GH-90000
        }

        @Test
        @DisplayName("recordScan() updates updatedAt [GH-90000]")
        void recordScanUpdatesTimestamp() throws InterruptedException { // GH-90000
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME); // GH-90000
            Instant originalUpdatedAt = project.getUpdatedAt(); // GH-90000
            Thread.sleep(10); // GH-90000

            // WHEN
            project.recordScan(); // GH-90000

            // THEN
            assertThat(project.getUpdatedAt()).isAfter(originalUpdatedAt); // GH-90000
        }

        @Test
        @DisplayName("multiple scans update lastScanAt each time [GH-90000]")
        void multipleScanTracking() throws InterruptedException { // GH-90000
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME); // GH-90000

            // WHEN
            project.recordScan(); // GH-90000
            Instant firstScan = project.getLastScanAt(); // GH-90000
            Thread.sleep(10); // GH-90000
            project.recordScan(); // GH-90000
            Instant secondScan = project.getLastScanAt(); // GH-90000

            // THEN
            assertThat(secondScan).isAfter(firstScan); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder Tests [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builder creates project with all fields [GH-90000]")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000
            String settings = """
                    {"notifications": true, "autoScan": true}
                    """;

            // WHEN
            Project project = Project.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(PROJECT_NAME) // GH-90000
                    .description("A test project for security scanning [GH-90000]")
                    .repositoryUrl(REPO_URL) // GH-90000
                    .defaultBranch("develop [GH-90000]")
                    .language("Java [GH-90000]")
                    .archived(false) // GH-90000
                    .settings(settings) // GH-90000
                    .createdAt(now) // GH-90000
                    .updatedAt(now) // GH-90000
                    .lastScanAt(now) // GH-90000
                    .version(2) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(project.getId()).isEqualTo(id); // GH-90000
            assertThat(project.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(project.getName()).isEqualTo(PROJECT_NAME); // GH-90000
            assertThat(project.getDescription()).isEqualTo("A test project for security scanning [GH-90000]");
            assertThat(project.getRepositoryUrl()).isEqualTo(REPO_URL); // GH-90000
            assertThat(project.getDefaultBranch()).isEqualTo("develop [GH-90000]");
            assertThat(project.getLanguage()).isEqualTo("Java [GH-90000]");
            assertThat(project.isArchived()).isFalse(); // GH-90000
            assertThat(project.getSettings()).isEqualTo(settings); // GH-90000
            assertThat(project.getCreatedAt()).isEqualTo(now); // GH-90000
            assertThat(project.getUpdatedAt()).isEqualTo(now); // GH-90000
            assertThat(project.getLastScanAt()).isEqualTo(now); // GH-90000
            assertThat(project.getVersion()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("builder defaults defaultBranch to main [GH-90000]")
        void builderDefaultsDefaultBranchToMain() { // GH-90000
            Project project = Project.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(PROJECT_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(project.getDefaultBranch()).isEqualTo("main [GH-90000]");
        }

        @Test
        @DisplayName("builder defaults archived to false [GH-90000]")
        void builderDefaultsArchivedToFalse() { // GH-90000
            Project project = Project.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(PROJECT_NAME) // GH-90000
                    .build(); // GH-90000

            assertThat(project.isArchived()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("toBuilder creates modifiable copy [GH-90000]")
        void toBuilderCreatesModifiableCopy() { // GH-90000
            // GIVEN
            Project original = Project.of(WORKSPACE_ID, PROJECT_NAME); // GH-90000

            // WHEN
            Project copy = original.toBuilder() // GH-90000
                    .repositoryUrl(REPO_URL) // GH-90000
                    .language("TypeScript [GH-90000]")
                    .build(); // GH-90000

            // THEN
            assertThat(copy.getWorkspaceId()).isEqualTo(original.getWorkspaceId()); // GH-90000
            assertThat(copy.getName()).isEqualTo(original.getName()); // GH-90000
            assertThat(copy.getRepositoryUrl()).isEqualTo(REPO_URL); // GH-90000
            assertThat(copy.getLanguage()).isEqualTo("TypeScript [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Equality Tests [GH-90000]")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id [GH-90000]")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Project project1 = Project.builder().id(id).name("Project 1 [GH-90000]").build();
            Project project2 = Project.builder().id(id).name("Project 2 [GH-90000]").build();

            assertThat(project1).isEqualTo(project2); // GH-90000
            assertThat(project1.hashCode()).isEqualTo(project2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids [GH-90000]")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            Project project1 = Project.builder().id(UUID.randomUUID()).build(); // GH-90000
            Project project2 = Project.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(project1).isNotEqualTo(project2); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for null [GH-90000]")
        void equalsReturnsFalseForNull() { // GH-90000
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME); // GH-90000
            assertThat(project).isNotEqualTo(null); // GH-90000
        }
    }

    @Nested
    @DisplayName("Language Tests [GH-90000]")
    class LanguageTests {

        @Test
        @DisplayName("can set various programming languages [GH-90000]")
        void canSetVariousLanguages() { // GH-90000
            String[] languages = {"Java", "TypeScript", "Python", "Go", "Rust", "C#"};

            for (String lang : languages) { // GH-90000
                Project project = Project.builder() // GH-90000
                        .workspaceId(WORKSPACE_ID) // GH-90000
                        .name(PROJECT_NAME) // GH-90000
                        .language(lang) // GH-90000
                        .build(); // GH-90000

                assertThat(project.getLanguage()).isEqualTo(lang); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Repository URL Tests [GH-90000]")
    class RepositoryUrlTests {

        @Test
        @DisplayName("accepts GitHub URLs [GH-90000]")
        void acceptsGitHubUrls() { // GH-90000
            Project project = Project.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(PROJECT_NAME) // GH-90000
                    .repositoryUrl("https://github.com/org/repo [GH-90000]")
                    .build(); // GH-90000

            assertThat(project.getRepositoryUrl()).startsWith("https://github.com [GH-90000]");
        }

        @Test
        @DisplayName("accepts GitLab URLs [GH-90000]")
        void acceptsGitLabUrls() { // GH-90000
            Project project = Project.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(PROJECT_NAME) // GH-90000
                    .repositoryUrl("https://gitlab.com/org/repo [GH-90000]")
                    .build(); // GH-90000

            assertThat(project.getRepositoryUrl()).startsWith("https://gitlab.com [GH-90000]");
        }

        @Test
        @DisplayName("accepts null repository URL [GH-90000]")
        void acceptsNullRepositoryUrl() { // GH-90000
            Project project = Project.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .name(PROJECT_NAME) // GH-90000
                    .repositoryUrl(null) // GH-90000
                    .build(); // GH-90000

            assertThat(project.getRepositoryUrl()).isNull(); // GH-90000
        }
    }
}
