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
@DisplayName("Project Domain Model Tests")
class ProjectTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String PROJECT_NAME = "my-security-app";
    private static final String REPO_URL = "https://github.com/org/my-security-app";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates project with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // WHEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME);

            // THEN
            assertThat(project.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(project.getName()).isEqualTo(PROJECT_NAME);
            assertThat(project.getDefaultBranch()).isEqualTo("main");
            assertThat(project.isArchived()).isFalse();
            assertThat(project.getCreatedAt()).isNotNull();
            assertThat(project.getUpdatedAt()).isNotNull();
            assertThat(project.getLastScanAt()).isNull();
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null")
        void ofThrowsWhenWorkspaceIdNull() {
            assertThatThrownBy(() -> Project.of(null, PROJECT_NAME))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when name is null")
        void ofThrowsWhenNameNull() {
            assertThatThrownBy(() -> Project.of(WORKSPACE_ID, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name must not be null");
        }
    }

    @Nested
    @DisplayName("Archive Lifecycle Tests")
    class ArchiveLifecycleTests {

        @Test
        @DisplayName("archive() sets archived to true")
        void archiveSetsArchivedTrue() {
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME);
            assertThat(project.isArchived()).isFalse();

            // WHEN
            Project result = project.archive();

            // THEN
            assertThat(result).isSameAs(project);
            assertThat(project.isArchived()).isTrue();
        }

        @Test
        @DisplayName("unarchive() sets archived to false")
        void unarchiveSetsArchivedFalse() {
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME);
            project.archive();
            assertThat(project.isArchived()).isTrue();

            // WHEN
            Project result = project.unarchive();

            // THEN
            assertThat(result).isSameAs(project);
            assertThat(project.isArchived()).isFalse();
        }

        @Test
        @DisplayName("archive and unarchive update updatedAt")
        void archiveUnarchiveUpdateTimestamp() throws InterruptedException {
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME);
            Instant originalUpdatedAt = project.getUpdatedAt();
            Thread.sleep(10);

            // WHEN
            project.archive();
            Instant afterArchive = project.getUpdatedAt();
            Thread.sleep(10);
            project.unarchive();
            Instant afterUnarchive = project.getUpdatedAt();

            // THEN
            assertThat(afterArchive).isAfter(originalUpdatedAt);
            assertThat(afterUnarchive).isAfter(afterArchive);
        }
    }

    @Nested
    @DisplayName("Scan Tracking Tests")
    class ScanTrackingTests {

        @Test
        @DisplayName("recordScan() sets lastScanAt")
        void recordScanSetsLastScanAt() {
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME);
            assertThat(project.getLastScanAt()).isNull();
            Instant beforeScan = Instant.now();

            // WHEN
            Project result = project.recordScan();

            // THEN
            assertThat(result).isSameAs(project);
            assertThat(project.getLastScanAt()).isAfterOrEqualTo(beforeScan);
        }

        @Test
        @DisplayName("recordScan() updates updatedAt")
        void recordScanUpdatesTimestamp() throws InterruptedException {
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME);
            Instant originalUpdatedAt = project.getUpdatedAt();
            Thread.sleep(10);

            // WHEN
            project.recordScan();

            // THEN
            assertThat(project.getUpdatedAt()).isAfter(originalUpdatedAt);
        }

        @Test
        @DisplayName("multiple scans update lastScanAt each time")
        void multipleScanTracking() throws InterruptedException {
            // GIVEN
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME);

            // WHEN
            project.recordScan();
            Instant firstScan = project.getLastScanAt();
            Thread.sleep(10);
            project.recordScan();
            Instant secondScan = project.getLastScanAt();

            // THEN
            assertThat(secondScan).isAfter(firstScan);
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates project with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            String settings = """
                    {"notifications": true, "autoScan": true}
                    """;

            // WHEN
            Project project = Project.builder()
                    .id(id)
                    .workspaceId(WORKSPACE_ID)
                    .name(PROJECT_NAME)
                    .description("A test project for security scanning")
                    .repositoryUrl(REPO_URL)
                    .defaultBranch("develop")
                    .language("Java")
                    .archived(false)
                    .settings(settings)
                    .createdAt(now)
                    .updatedAt(now)
                    .lastScanAt(now)
                    .version(2)
                    .build();

            // THEN
            assertThat(project.getId()).isEqualTo(id);
            assertThat(project.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(project.getName()).isEqualTo(PROJECT_NAME);
            assertThat(project.getDescription()).isEqualTo("A test project for security scanning");
            assertThat(project.getRepositoryUrl()).isEqualTo(REPO_URL);
            assertThat(project.getDefaultBranch()).isEqualTo("develop");
            assertThat(project.getLanguage()).isEqualTo("Java");
            assertThat(project.isArchived()).isFalse();
            assertThat(project.getSettings()).isEqualTo(settings);
            assertThat(project.getCreatedAt()).isEqualTo(now);
            assertThat(project.getUpdatedAt()).isEqualTo(now);
            assertThat(project.getLastScanAt()).isEqualTo(now);
            assertThat(project.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("builder defaults defaultBranch to main")
        void builderDefaultsDefaultBranchToMain() {
            Project project = Project.builder()
                    .workspaceId(WORKSPACE_ID)
                    .name(PROJECT_NAME)
                    .build();

            assertThat(project.getDefaultBranch()).isEqualTo("main");
        }

        @Test
        @DisplayName("builder defaults archived to false")
        void builderDefaultsArchivedToFalse() {
            Project project = Project.builder()
                    .workspaceId(WORKSPACE_ID)
                    .name(PROJECT_NAME)
                    .build();

            assertThat(project.isArchived()).isFalse();
        }

        @Test
        @DisplayName("toBuilder creates modifiable copy")
        void toBuilderCreatesModifiableCopy() {
            // GIVEN
            Project original = Project.of(WORKSPACE_ID, PROJECT_NAME);

            // WHEN
            Project copy = original.toBuilder()
                    .repositoryUrl(REPO_URL)
                    .language("TypeScript")
                    .build();

            // THEN
            assertThat(copy.getWorkspaceId()).isEqualTo(original.getWorkspaceId());
            assertThat(copy.getName()).isEqualTo(original.getName());
            assertThat(copy.getRepositoryUrl()).isEqualTo(REPO_URL);
            assertThat(copy.getLanguage()).isEqualTo("TypeScript");
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            Project project1 = Project.builder().id(id).name("Project 1").build();
            Project project2 = Project.builder().id(id).name("Project 2").build();

            assertThat(project1).isEqualTo(project2);
            assertThat(project1.hashCode()).isEqualTo(project2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            Project project1 = Project.builder().id(UUID.randomUUID()).build();
            Project project2 = Project.builder().id(UUID.randomUUID()).build();

            assertThat(project1).isNotEqualTo(project2);
        }

        @Test
        @DisplayName("equals returns false for null")
        void equalsReturnsFalseForNull() {
            Project project = Project.of(WORKSPACE_ID, PROJECT_NAME);
            assertThat(project).isNotEqualTo(null);
        }
    }

    @Nested
    @DisplayName("Language Tests")
    class LanguageTests {

        @Test
        @DisplayName("can set various programming languages")
        void canSetVariousLanguages() {
            String[] languages = {"Java", "TypeScript", "Python", "Go", "Rust", "C#"};

            for (String lang : languages) {
                Project project = Project.builder()
                        .workspaceId(WORKSPACE_ID)
                        .name(PROJECT_NAME)
                        .language(lang)
                        .build();

                assertThat(project.getLanguage()).isEqualTo(lang);
            }
        }
    }

    @Nested
    @DisplayName("Repository URL Tests")
    class RepositoryUrlTests {

        @Test
        @DisplayName("accepts GitHub URLs")
        void acceptsGitHubUrls() {
            Project project = Project.builder()
                    .workspaceId(WORKSPACE_ID)
                    .name(PROJECT_NAME)
                    .repositoryUrl("https://github.com/org/repo")
                    .build();

            assertThat(project.getRepositoryUrl()).startsWith("https://github.com");
        }

        @Test
        @DisplayName("accepts GitLab URLs")
        void acceptsGitLabUrls() {
            Project project = Project.builder()
                    .workspaceId(WORKSPACE_ID)
                    .name(PROJECT_NAME)
                    .repositoryUrl("https://gitlab.com/org/repo")
                    .build();

            assertThat(project.getRepositoryUrl()).startsWith("https://gitlab.com");
        }

        @Test
        @DisplayName("accepts null repository URL")
        void acceptsNullRepositoryUrl() {
            Project project = Project.builder()
                    .workspaceId(WORKSPACE_ID)
                    .name(PROJECT_NAME)
                    .repositoryUrl(null)
                    .build();

            assertThat(project.getRepositoryUrl()).isNull();
        }
    }
}
