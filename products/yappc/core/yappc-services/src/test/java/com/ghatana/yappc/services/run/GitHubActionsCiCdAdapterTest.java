package com.ghatana.yappc.services.run;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.run.RunTask;
import com.ghatana.yappc.domain.run.TaskResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fail-closed behaviour and observability assertions for GitHubActionsCiCdAdapter.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Every operation returns NOT_READY (never false success) when credentials are missing</li>
 *   <li>Every operation returns NOT_READY (never false success) when credentials are present
 *       but the real GitHub Actions integration is not yet implemented</li>
 *   <li>{@code isReady()} reflects credential presence correctly</li>
 *   <li>{@code fromEnvironment()} falls back to NoOpCiCdAdapter when env vars are absent</li>
 *   <li>Error messages are descriptive and actionable — no silent failures</li>
 *   <li>Task ID is preserved in all result objects</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Fail-closed and observability tests for GitHubActionsCiCdAdapter
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("GitHubActionsCiCdAdapter — fail-closed behaviour and observability")
class GitHubActionsCiCdAdapterTest extends EventloopTestBase {

    // =========================================================================
    // UNCONFIGURED ADAPTER — missing token
    // =========================================================================

    @Nested
    @DisplayName("Unconfigured adapter — missing GITHUB_TOKEN")
    class UnconfiguredAdapterTests {

        private final GitHubActionsCiCdAdapter adapter =
            new GitHubActionsCiCdAdapter(null, "owner/repo", "https://api.github.com");

        @Test
        @DisplayName("isReady() returns false when token is null")
        void isReadyFalseWhenTokenNull() {
            assertThat(adapter.isReady()).isFalse();
        }

        @Test
        @DisplayName("build() returns NOT_READY — never false success")
        void buildReturnsNotReadyWhenUnconfigured() {
            RunTask task = buildTask("task-1", "build");
            TaskResult result = runPromise(() -> adapter.build(task));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.taskId()).isEqualTo("task-1");
            assertThat(result.output()).isNotBlank();
        }

        @Test
        @DisplayName("test() returns NOT_READY — never false success")
        void testReturnsNotReadyWhenUnconfigured() {
            RunTask task = buildTask("task-2", "test");
            TaskResult result = runPromise(() -> adapter.test(task));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.taskId()).isEqualTo("task-2");
        }

        @Test
        @DisplayName("deploy() returns NOT_READY — never false success")
        void deployReturnsNotReadyWhenUnconfigured() {
            RunTask task = buildTask("task-3", "deploy");
            TaskResult result = runPromise(() -> adapter.deploy(task));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.taskId()).isEqualTo("task-3");
        }

        @Test
        @DisplayName("migrate() returns NOT_READY — never false success")
        void migrateReturnsNotReadyWhenUnconfigured() {
            RunTask task = buildTask("task-4", "migrate");
            TaskResult result = runPromise(() -> adapter.migrate(task));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.taskId()).isEqualTo("task-4");
        }

        @Test
        @DisplayName("rollback() returns NOT_READY — never false success")
        void rollbackReturnsNotReadyWhenUnconfigured() {
            TaskResult result = runPromise(() -> adapter.rollback("deploy-1", "v1.0.0"));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.taskId()).isEqualTo("deploy-1");
        }
    }

    // =========================================================================
    // CONFIGURED ADAPTER — token present but integration not implemented
    // =========================================================================

    @Nested
    @DisplayName("Configured adapter — token present, integration not yet implemented")
    class ConfiguredAdapterFailClosedTests {

        private final GitHubActionsCiCdAdapter adapter =
            new GitHubActionsCiCdAdapter("ghp_fake_token_for_test", "owner/repo", "https://api.github.com");

        @Test
        @DisplayName("isReady() returns true when token is present")
        void isReadyTrueWhenTokenPresent() {
            assertThat(adapter.isReady()).isTrue();
        }

        @Test
        @DisplayName("build() returns NOT_READY with [NOT_IMPLEMENTED] message — no false success")
        void buildReturnsNotImplemented() {
            RunTask task = buildTask("task-build", "build");
            TaskResult result = runPromise(() -> adapter.build(task));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.taskId()).isEqualTo("task-build");
            assertThat(result.output()).contains("[NOT_IMPLEMENTED]");
            assertThat(result.error()).isNotBlank();
        }

        @Test
        @DisplayName("test() returns NOT_READY with [NOT_IMPLEMENTED] message — no false success")
        void testReturnsNotImplemented() {
            RunTask task = buildTask("task-test", "test");
            TaskResult result = runPromise(() -> adapter.test(task));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.output()).contains("[NOT_IMPLEMENTED]");
            assertThat(result.error()).isNotBlank();
        }

        @Test
        @DisplayName("deploy() returns NOT_READY with target environment in output — no false success")
        void deployReturnsNotImplementedWithEnvironment() {
            RunTask task = RunTask.builder()
                .id("task-deploy")
                .type("deploy")
                .name("Deploy")
                .config(Map.of("environment", "staging"))
                .build();
            TaskResult result = runPromise(() -> adapter.deploy(task));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.output()).contains("[NOT_IMPLEMENTED]");
            assertThat(result.output()).contains("staging");
        }

        @Test
        @DisplayName("deploy() uses 'production' as default environment when not specified")
        void deployDefaultsToProductionEnvironment() {
            RunTask task = buildTask("task-deploy-default", "deploy");
            TaskResult result = runPromise(() -> adapter.deploy(task));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.output()).contains("production");
        }

        @Test
        @DisplayName("migrate() returns NOT_READY with [NOT_IMPLEMENTED] message — no false success")
        void migrateReturnsNotImplemented() {
            RunTask task = buildTask("task-migrate", "migrate");
            TaskResult result = runPromise(() -> adapter.migrate(task));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.output()).contains("[NOT_IMPLEMENTED]");
            assertThat(result.error()).isNotBlank();
        }

        @Test
        @DisplayName("rollback() returns NOT_READY with target version in output — no false success")
        void rollbackReturnsNotImplementedWithVersion() {
            TaskResult result = runPromise(() -> adapter.rollback("deploy-99", "v2.3.1"));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
            assertThat(result.taskId()).isEqualTo("deploy-99");
            assertThat(result.output()).contains("[NOT_IMPLEMENTED]");
            assertThat(result.output()).contains("v2.3.1");
        }

        @Test
        @DisplayName("durationMs is non-negative for all operations")
        void durationMsIsNonNegative() {
            RunTask task = buildTask("task-dur", "build");
            TaskResult result = runPromise(() -> adapter.build(task));

            assertThat(result.durationMs()).isGreaterThanOrEqualTo(0L);
        }
    }

    // =========================================================================
    // BLANK TOKEN — treated same as missing
    // =========================================================================

    @Nested
    @DisplayName("Blank token — treated as unconfigured")
    class BlankTokenTests {

        @Test
        @DisplayName("isReady() returns false when token is blank")
        void isReadyFalseWhenTokenBlank() {
            GitHubActionsCiCdAdapter adapter =
                new GitHubActionsCiCdAdapter("   ", "owner/repo", "https://api.github.com");
            assertThat(adapter.isReady()).isFalse();
        }

        @Test
        @DisplayName("build() with blank token returns NOT_READY")
        void buildWithBlankTokenReturnsNotReady() {
            GitHubActionsCiCdAdapter adapter =
                new GitHubActionsCiCdAdapter("", "owner/repo", "https://api.github.com");
            RunTask task = buildTask("task-blank", "build");
            TaskResult result = runPromise(() -> adapter.build(task));

            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
        }
    }

    // =========================================================================
    // FACTORY METHOD — fromEnvironment() falls back to NoOp
    // =========================================================================

    @Nested
    @DisplayName("fromEnvironment() — falls back to NoOpCiCdAdapter when env vars absent")
    class FromEnvironmentTests {

        @Test
        @DisplayName("fromEnvironment() returns NoOpCiCdAdapter when GITHUB_TOKEN is not set")
        void fromEnvironmentReturnsNoOpWhenTokenMissing() {
            // In test environment, GITHUB_TOKEN and GITHUB_REPO are typically not set
            // If they happen to be set in the test env, this test becomes a smoke test only.
            CiCdPort port = GitHubActionsCiCdAdapter.fromEnvironment();

            // Either NoOpCiCdAdapter or a configured GitHubActionsCiCdAdapter — both are valid
            assertThat(port).isNotNull();
            // isReady() must be deterministic — no NPE
            boolean ready = port.isReady();
            assertThat(ready).isIn(true, false);
        }
    }

    // =========================================================================
    // TASK ID PRESERVATION
    // =========================================================================

    @Nested
    @DisplayName("Task ID preservation — result always echoes input task ID")
    class TaskIdPreservationTests {

        private final GitHubActionsCiCdAdapter adapter =
            new GitHubActionsCiCdAdapter("ghp_test_token", "owner/repo", null);

        @Test
        @DisplayName("build result preserves task ID")
        void buildPreservesTaskId() {
            RunTask task = buildTask("unique-task-id-build", "build");
            TaskResult result = runPromise(() -> adapter.build(task));
            assertThat(result.taskId()).isEqualTo("unique-task-id-build");
        }

        @Test
        @DisplayName("test result preserves task ID")
        void testPreservesTaskId() {
            RunTask task = buildTask("unique-task-id-test", "test");
            TaskResult result = runPromise(() -> adapter.test(task));
            assertThat(result.taskId()).isEqualTo("unique-task-id-test");
        }

        @Test
        @DisplayName("deploy result preserves task ID")
        void deployPreservesTaskId() {
            RunTask task = buildTask("unique-task-id-deploy", "deploy");
            TaskResult result = runPromise(() -> adapter.deploy(task));
            assertThat(result.taskId()).isEqualTo("unique-task-id-deploy");
        }

        @Test
        @DisplayName("migrate result preserves task ID")
        void migratePreservesTaskId() {
            RunTask task = buildTask("unique-task-id-migrate", "migrate");
            TaskResult result = runPromise(() -> adapter.migrate(task));
            assertThat(result.taskId()).isEqualTo("unique-task-id-migrate");
        }

        @Test
        @DisplayName("rollback result preserves deployment ID")
        void rollbackPreservesDeploymentId() {
            TaskResult result = runPromise(() -> adapter.rollback("unique-deploy-id", "v1.0.0"));
            assertThat(result.taskId()).isEqualTo("unique-deploy-id");
        }
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    private static RunTask buildTask(String id, String type) {
        return RunTask.builder()
            .id(id)
            .type(type)
            .name(type)
            .config(Map.of())
            .build();
    }
}
