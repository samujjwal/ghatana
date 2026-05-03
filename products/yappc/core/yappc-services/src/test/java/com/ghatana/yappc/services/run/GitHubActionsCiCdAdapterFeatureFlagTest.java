package com.ghatana.yappc.services.run;

import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.run.RunTask;
import com.ghatana.yappc.domain.run.TaskResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for GitHub Actions CI/CD adapter feature flag behavior.
 *
 * <p><b>Purpose</b><br>
 * Tests that the GITHUB_CI_CD_ENABLED feature flag properly controls whether
 * the GitHub Actions adapter is used or falls back to NoOpCiCdAdapter.
 *
 * <p><b>Test Coverage</b><br>
 * - When GITHUB_CI_CD_ENABLED is not set or false, NoOpCiCdAdapter is used
 * - When GITHUB_CI_CD_ENABLED is true, GitHubActionsCiCdAdapter is used
 * - When GitHub Actions adapter is used but not implemented, returns NOT_READY (fail-closed)
 * - When GitHub Actions adapter is configured but disabled, returns NOT_READY
 *
 * @doc.type class
 * @doc.purpose Integration test for GitHub Actions CI/CD feature flag
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("GitHub Actions CI/CD Adapter Feature Flag")
class GitHubActionsCiCdAdapterFeatureFlagTest extends EventloopTestBase {

    private String originalGithubToken;
    private String originalGithubRepo;
    private String originalGithubApiUrl;
    private String originalEnabledFlag;

    @BeforeEach
    void setUp() {
        // Save original environment variables
        originalGithubToken = System.getenv("GITHUB_TOKEN");
        originalGithubRepo = System.getenv("GITHUB_REPO");
        originalGithubApiUrl = System.getenv("GITHUB_API_URL");
        originalEnabledFlag = System.getenv("GITHUB_CI_CD_ENABLED");

        // Clear environment variables for clean test state
        clearEnvVars();
    }

    @AfterEach
    void tearDown() {
        // Restore original environment variables
        restoreEnvVars();
    }

    @Test
    @DisplayName("fromEnvironment: missing GITHUB_TOKEN → returns NoOpCiCdAdapter")
    void shouldReturnNoOpAdapterWhenTokenMissing() {
        System.setProperty("GITHUB_TOKEN", "");
        System.setProperty("GITHUB_REPO", "owner/repo");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();

        assertThat(adapter).isInstanceOf(NoOpCiCdAdapter.class);
        assertThat(adapter.isReady()).isFalse();
    }

    @Test
    @DisplayName("fromEnvironment: missing GITHUB_REPO → returns NoOpCiCdAdapter")
    void shouldReturnNoOpAdapterWhenRepoMissing() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();

        assertThat(adapter).isInstanceOf(NoOpCiCdAdapter.class);
        assertThat(adapter.isReady()).isFalse();
    }

    @Test
    @DisplayName("fromEnvironment: GITHUB_CI_CD_ENABLED not set → returns NoOpCiCdAdapter")
    void shouldReturnNoOpAdapterWhenFeatureFlagNotSet() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "owner/repo");
        // GITHUB_CI_CD_ENABLED not set

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();

        assertThat(adapter).isInstanceOf(NoOpCiCdAdapter.class);
        assertThat(adapter.isReady()).isFalse();
    }

    @Test
    @DisplayName("fromEnvironment: GITHUB_CI_CD_ENABLED=false → returns NoOpCiCdAdapter")
    void shouldReturnNoOpAdapterWhenFeatureFlagFalse() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "owner/repo");
        System.setProperty("GITHUB_CI_CD_ENABLED", "false");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();

        assertThat(adapter).isInstanceOf(NoOpCiCdAdapter.class);
        assertThat(adapter.isReady()).isFalse();
    }

    @Test
    @DisplayName("fromEnvironment: GITHUB_CI_CD_ENABLED=0 → returns NoOpCiCdAdapter")
    void shouldReturnNoOpAdapterWhenFeatureFlagZero() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "owner/repo");
        System.setProperty("GITHUB_CI_CD_ENABLED", "0");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();

        assertThat(adapter).isInstanceOf(NoOpCiCdAdapter.class);
        assertThat(adapter.isReady()).isFalse();
    }

    @Test
    @DisplayName("fromEnvironment: GITHUB_CI_CD_ENABLED=true → returns GitHubActionsCiCdAdapter")
    void shouldReturnGitHubActionsAdapterWhenFeatureFlagTrue() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "owner/repo");
        System.setProperty("GITHUB_CI_CD_ENABLED", "true");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();

        assertThat(adapter).isInstanceOf(GitHubActionsCiCdAdapter.class);
        // Adapter is configured but not ready because GitHub API integration not implemented
        assertThat(adapter.isReady()).isTrue();
    }

    @Test
    @DisplayName("fromEnvironment: GITHUB_CI_CD_ENABLED=1 → returns GitHubActionsCiCdAdapter")
    void shouldReturnGitHubActionsAdapterWhenFeatureFlagOne() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "owner/repo");
        System.setProperty("GITHUB_CI_CD_ENABLED", "1");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();

        assertThat(adapter).isInstanceOf(GitHubActionsCiCdAdapter.class);
        assertThat(adapter.isReady()).isTrue();
    }

    @Test
    @DisplayName("build: with feature flag enabled but not implemented → returns NOT_READY")
    void shouldReturnNotReadyWhenFeatureFlagEnabledButNotImplemented() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "owner/repo");
        System.setProperty("GITHUB_CI_CD_ENABLED", "true");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();
        RunTask task = RunTask.builder()
                .id("task-build-1")
                .type("build")
                .name("Build")
                .config(Map.of())
                .build();

        TaskResult result = runPromise(() -> adapter.build(task));

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
        assertThat(result.error()).contains("not yet implemented");
        assertThat(result.output()).contains("[NOT_IMPLEMENTED]");
        assertThat(result.output()).contains("GITHUB_CI_CD_ENABLED=true");
    }

    @Test
    @DisplayName("test: with feature flag enabled but not implemented → returns NOT_READY")
    void shouldReturnNotReadyForTestWhenFeatureFlagEnabledButNotImplemented() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "owner/repo");
        System.setProperty("GITHUB_CI_CD_ENABLED", "true");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();
        RunTask task = RunTask.builder()
                .id("task-test-1")
                .type("test")
                .name("Test")
                .config(Map.of())
                .build();

        TaskResult result = runPromise(() -> adapter.test(task));

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
        assertThat(result.error()).contains("not yet implemented");
        assertThat(result.output()).contains("[NOT_IMPLEMENTED]");
    }

    @Test
    @DisplayName("deploy: with feature flag enabled but not implemented → returns NOT_READY")
    void shouldReturnNotReadyForDeployWhenFeatureFlagEnabledButNotImplemented() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "owner/repo");
        System.setProperty("GITHUB_CI_CD_ENABLED", "true");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();
        RunTask task = RunTask.builder()
                .id("task-deploy-1")
                .type("deploy")
                .name("Deploy")
                .config(Map.of("environment", "production"))
                .build();

        TaskResult result = runPromise(() -> adapter.deploy(task));

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
        assertThat(result.error()).contains("not yet implemented");
        assertThat(result.output()).contains("[NOT_IMPLEMENTED]");
        assertThat(result.output()).contains("production");
    }

    @Test
    @DisplayName("migrate: with feature flag enabled but not implemented → returns NOT_READY")
    void shouldReturnNotReadyForMigrateWhenFeatureFlagEnabledButNotImplemented() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "owner/repo");
        System.setProperty("GITHUB_CI_CD_ENABLED", "true");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();
        RunTask task = RunTask.builder()
                .id("task-migrate-1")
                .type("migrate")
                .name("Migrate")
                .config(Map.of())
                .build();

        TaskResult result = runPromise(() -> adapter.migrate(task));

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
        assertThat(result.error()).contains("not yet implemented");
        assertThat(result.output()).contains("[NOT_IMPLEMENTED]");
    }

    @Test
    @DisplayName("rollback: with feature flag enabled but not implemented → returns NOT_READY")
    void shouldReturnNotReadyForRollbackWhenFeatureFlagEnabledButNotImplemented() {
        System.setProperty("GITHUB_TOKEN", "ghp_test_token");
        System.setProperty("GITHUB_REPO", "owner/repo");
        System.setProperty("GITHUB_CI_CD_ENABLED", "true");

        CiCdPort adapter = GitHubActionsCiCdAdapter.fromEnvironment();

        TaskResult result = runPromise(() -> adapter.rollback("deploy-123", "v1.0.0"));

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
        assertThat(result.error()).contains("not yet implemented");
        assertThat(result.output()).contains("[NOT_IMPLEMENTED]");
        assertThat(result.output()).contains("v1.0.0");
    }

    @Test
    @DisplayName("constructor: enabled=false → isReady returns false")
    void shouldReturnNotReadyWhenExplicitlyDisabled() {
        GitHubActionsCiCdAdapter adapter = new GitHubActionsCiCdAdapter(
                "ghp_test_token",
                "owner/repo",
                "https://api.github.com",
                false  // explicitly disabled
        );

        assertThat(adapter.isReady()).isFalse();
    }

    @Test
    @DisplayName("constructor: enabled=true → isReady returns true")
    void shouldReturnReadyWhenExplicitlyEnabled() {
        GitHubActionsCiCdAdapter adapter = new GitHubActionsCiCdAdapter(
                "ghp_test_token",
                "owner/repo",
                "https://api.github.com",
                true  // explicitly enabled
        );

        assertThat(adapter.isReady()).isTrue();
    }

    @Test
    @DisplayName("constructor: enabled=false with task → returns NOT_READY")
    void shouldReturnNotReadyForTaskWhenExplicitlyDisabled() {
        GitHubActionsCiCdAdapter adapter = new GitHubActionsCiCdAdapter(
                "ghp_test_token",
                "owner/repo",
                "https://api.github.com",
                false  // explicitly disabled
        );
        RunTask task = RunTask.builder()
                .id("task-build-1")
                .type("build")
                .name("Build")
                .config(Map.of())
                .build();

        TaskResult result = runPromise(() -> adapter.build(task));

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
        assertThat(result.error()).contains("disabled via feature flag");
    }

    private void clearEnvVars() {
        System.clearProperty("GITHUB_TOKEN");
        System.clearProperty("GITHUB_REPO");
        System.clearProperty("GITHUB_API_URL");
        System.clearProperty("GITHUB_CI_CD_ENABLED");
    }

    private void restoreEnvVars() {
        clearEnvVars();
        if (originalGithubToken != null) {
            System.setProperty("GITHUB_TOKEN", originalGithubToken);
        }
        if (originalGithubRepo != null) {
            System.setProperty("GITHUB_REPO", originalGithubRepo);
        }
        if (originalGithubApiUrl != null) {
            System.setProperty("GITHUB_API_URL", originalGithubApiUrl);
        }
        if (originalEnabledFlag != null) {
            System.setProperty("GITHUB_CI_CD_ENABLED", originalEnabledFlag);
        }
    }
}
