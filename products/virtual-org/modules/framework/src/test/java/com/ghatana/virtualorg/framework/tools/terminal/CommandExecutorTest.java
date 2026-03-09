package com.ghatana.virtualorg.framework.tools.terminal;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * Tests for CommandExecutor.
 *
 * @doc.type class
 * @doc.purpose Unit tests for command execution
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CommandExecutor Tests")
class CommandExecutorTest extends EventloopTestBase {

    private MetricsCollector metrics;
    private CommandExecutor executor;

    @BeforeEach
    void setUp() {
        metrics = mock(MetricsCollector.class);
        executor = CommandExecutor.builder()
                .allowedCommands(List.of("echo", "pwd", "ls"))
                .timeout(Duration.ofSeconds(10))
                .metrics(metrics)
                .build();
    }

    @Test
    @DisplayName("should execute allowed command successfully")
    void shouldExecuteAllowedCommand() {
        // WHEN
        CommandExecutor.CommandResult result = runPromise(()
                -> executor.execute("echo", List.of("hello", "world"))
        );

        // THEN
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.output()).contains("hello world");
    }

    @Test
    @DisplayName("should block non-allowed command")
    void shouldBlockNonAllowedCommand() {
        // WHEN
        CommandExecutor.CommandResult result = runPromise(()
                -> executor.execute("rm", List.of("-rf", "/"))
        );

        // THEN
        assertThat(result.isBlocked()).isTrue();
        assertThat(result.status()).isEqualTo(CommandExecutor.CommandResult.Status.BLOCKED);
        assertThat(result.errorMessage()).contains("not in allowlist");
    }

    @Test
    @DisplayName("should execute shell command")
    void shouldExecuteShellCommand() {
        // WHEN
        CommandExecutor.CommandResult result = runPromise(()
                -> executor.executeShell("echo test")
        );

        // THEN
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).contains("test");
    }

    @Test
    @DisplayName("should handle command with non-zero exit code")
    void shouldHandleNonZeroExitCode() {
        // GIVEN - executor without allowlist for testing
        CommandExecutor unrestricted = CommandExecutor.builder()
                .timeout(Duration.ofSeconds(5))
                .metrics(metrics)
                .build();

        // WHEN
        CommandExecutor.CommandResult result = runPromise(()
                -> unrestricted.executeShell("ls /nonexistent/path/xyz")
        );

        // THEN
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.exitCode()).isNotEqualTo(0);
    }

    @Test
    @DisplayName("should capture command output")
    void shouldCaptureOutput() {
        // WHEN
        CommandExecutor.CommandResult result = runPromise(()
                -> executor.executeShell("pwd")
        );

        // THEN
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isNotEmpty();
    }

    @Test
    @DisplayName("should track duration")
    void shouldTrackDuration() {
        // WHEN
        CommandExecutor.CommandResult result = runPromise(()
                -> executor.execute("echo", List.of("fast"))
        );

        // THEN
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }
}
