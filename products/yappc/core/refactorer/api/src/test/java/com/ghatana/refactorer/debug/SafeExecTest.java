package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.ghatana.refactorer.shared.util.ProcessExec;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
/**
 * @doc.type class
 * @doc.purpose Handles safe exec test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class SafeExecTest {

    @Mock private ProcessExec mockProcessExec;

    @Mock private AllowlistPolicy mockPolicy;

    @TempDir Path tempDir;

    private SafeExec safeExec;

    @BeforeEach
    void setUp() {
        // Create a real SafeExec instance
        safeExec = new SafeExec();
        safeExec.setPolicy(mockPolicy);
    }

    @Test
    void run_disallowedCommand_throwsSecurityException() {
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) {
            // Arrange
            var command = List.of("echo", "hello");
            when(mockPolicy.commands()).thenReturn(List.of());

            // Act & Assert
            assertThrows(
                    SecurityException.class,
                    () -> safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis()));

            // Verify ProcessExec was never called
            mocked.verifyNoInteractions();
        }
    }

    @Test
    void run_allowedCommand_returnsResult() throws Exception {
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) {
            // Arrange
            var command = List.of("echo", "hello");
            var allowedCommand = new AllowlistPolicy.AllowedCommand("test", command, List.of());
            when(mockPolicy.commands()).thenReturn(List.of(allowedCommand));

            // Set up the mock to return a successful result
            when(ProcessExec.run(any(Path.class), any(Duration.class), anyList(), anyMap()))
                    .thenReturn(new ProcessExec.Result(0, "output", ""));

            // Act
            var result = safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis());

            // Assert
            verify(mockPolicy).commands();
            assertEquals(0, result.exitCode());
            assertEquals("output", result.out());

            // Verify ProcessExec.run was called with the expected parameters
            mocked.verify(
                    () -> ProcessExec.run(eq(tempDir), any(Duration.class), eq(command), anyMap()));
        }
    }

    @Test
    void run_commandWithArguments_allowsWithPrefixMatch() throws Exception {
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) {
            // Arrange
            var baseCommand = List.of("git", "commit");
            var fullCommand = List.of("git", "commit", "-m", "Initial commit");
            var allowedCommand =
                    new AllowlistPolicy.AllowedCommand("git-commit", baseCommand, List.of());
            when(mockPolicy.commands()).thenReturn(List.of(allowedCommand));

            // Set up the mock to return a successful result
            when(ProcessExec.run(any(Path.class), any(Duration.class), anyList(), anyMap()))
                    .thenReturn(new ProcessExec.Result(0, "commit output", ""));

            // Act
            var result = safeExec.run(tempDir, fullCommand, Duration.ofSeconds(1).toMillis());

            // Assert
            assertEquals(0, result.exitCode());
            assertEquals("commit output", result.out());

            // Verify ProcessExec.run was called with the expected parameters
            mocked.verify(
                    () ->
                            ProcessExec.run(
                                    eq(tempDir), any(Duration.class), eq(fullCommand), anyMap()));
        }
    }

    @Test
    void run_commandWithDifferentCasing_allowsCaseInsensitiveMatch() throws Exception {
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) {
            // Arrange
            var command = List.of("ECHO", "hello");
            var allowedCommand =
                    new AllowlistPolicy.AllowedCommand("echo-cmd", List.of("echo"), List.of());
            when(mockPolicy.commands()).thenReturn(List.of(allowedCommand));

            // Set up the mock to return a successful result
            when(ProcessExec.run(any(Path.class), any(Duration.class), anyList(), anyMap()))
                    .thenReturn(new ProcessExec.Result(0, "HELLO", ""));

            // Act & Assert - Should not throw SecurityException
            var result =
                    assertDoesNotThrow(
                            () -> safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis()));

            // Assert
            assertEquals(0, result.exitCode());
            assertEquals("HELLO", result.out());

            // Verify ProcessExec.run was called with the expected parameters
            mocked.verify(
                    () -> ProcessExec.run(eq(tempDir), any(Duration.class), eq(command), anyMap()));
        }
    }

    @Test
    void run_commandFails_throwsIOException() {
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) {
            // Arrange
            var command = List.of("failing-command");
            var allowedCommand = new AllowlistPolicy.AllowedCommand("test", command, List.of());
            when(mockPolicy.commands()).thenReturn(List.of(allowedCommand));

            // Set up the mock to simulate a failing command
            when(ProcessExec.run(any(Path.class), any(Duration.class), anyList(), anyMap()))
                    .thenReturn(new ProcessExec.Result(1, "", "Command failed"));

            // Act & Assert
            var exception =
                    assertThrows(
                            IOException.class,
                            () -> safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis()));

            // Verify the exception message
            assertTrue(exception.getMessage().contains("Command failed"));

            // Verify ProcessExec.run was called with the expected parameters
            mocked.verify(
                    () -> ProcessExec.run(eq(tempDir), any(Duration.class), eq(command), anyMap()));
        }
    }

    @Test
    void run_withoutSettingPolicy_throwsIllegalStateException() {
        // Arrange
        var safeExec = new SafeExec(); // No policy set
        var command = List.of("echo", "test");

        // Act & Assert
        assertThrows(
                IllegalStateException.class,
                () -> safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis()));
    }

    @Test
    void run_withNullCommand_throwsNullPointerException() {
        // Act & Assert
        assertThrows(
                NullPointerException.class,
                () -> safeExec.run(tempDir, null, Duration.ofSeconds(1).toMillis()));
    }

    @Test
    void run_withNullCwd_throwsNullPointerException() {
        // Arrange
        var command = List.of("echo", "test");

        // Act & Assert
        assertThrows(
                NullPointerException.class,
                () -> safeExec.run(null, command, Duration.ofSeconds(1).toMillis()));
    }

    @Test
    void run_withEmptyCommand_throwsSecurityException() {
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) {
            // Act & Assert
            assertThrows(
                    SecurityException.class,
                    () -> safeExec.run(tempDir, List.of(), Duration.ofSeconds(1).toMillis()));

            // Verify ProcessExec was never called
            mocked.verifyNoInteractions();
        }
    }

    @Test
    void run_failingCommand_throwsIOException() {
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) {
            // Arrange
            var command = List.of("failing-command");
            var allowedCommand = new AllowlistPolicy.AllowedCommand("test", command, List.of());
            when(mockPolicy.commands()).thenReturn(List.of(allowedCommand));

            // Set up the mock to simulate a failing command
            when(ProcessExec.run(any(Path.class), any(Duration.class), anyList(), anyMap()))
                    .thenReturn(new ProcessExec.Result(1, "", "Command failed"));

            // Act & Assert
            var exception =
                    assertThrows(
                            IOException.class,
                            () -> safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis()));

            // Verify the exception message
            assertTrue(exception.getMessage().contains("Command failed"));

            // Verify ProcessExec.run was called with the expected parameters
            mocked.verify(
                    () -> ProcessExec.run(eq(tempDir), any(Duration.class), eq(command), anyMap()));
        }
    }
}
