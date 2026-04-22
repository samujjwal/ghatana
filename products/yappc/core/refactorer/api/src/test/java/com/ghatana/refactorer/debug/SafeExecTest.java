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

@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        // Create a real SafeExec instance
        safeExec = new SafeExec(); // GH-90000
        safeExec.setPolicy(mockPolicy); // GH-90000
    }

    @Test
    void run_disallowedCommand_throwsSecurityException() { // GH-90000
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) { // GH-90000
            // Arrange
            var command = List.of("echo", "hello"); // GH-90000
            when(mockPolicy.commands()).thenReturn(List.of()); // GH-90000

            // Act & Assert
            assertThrows( // GH-90000
                    SecurityException.class,
                    () -> safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis())); // GH-90000

            // Verify ProcessExec was never called
            mocked.verifyNoInteractions(); // GH-90000
        }
    }

    @Test
    void run_allowedCommand_returnsResult() throws Exception { // GH-90000
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) { // GH-90000
            // Arrange
            var command = List.of("echo", "hello"); // GH-90000
            var allowedCommand = new AllowlistPolicy.AllowedCommand("test", command, List.of()); // GH-90000
            when(mockPolicy.commands()).thenReturn(List.of(allowedCommand)); // GH-90000

            // Set up the mock to return a successful result
            when(ProcessExec.run(any(Path.class), any(Duration.class), anyList(), anyMap())) // GH-90000
                    .thenReturn(new ProcessExec.Result(0, "output", "")); // GH-90000

            // Act
            var result = safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis()); // GH-90000

            // Assert
            verify(mockPolicy).commands(); // GH-90000
            assertEquals(0, result.exitCode()); // GH-90000
            assertEquals("output", result.out()); // GH-90000

            // Verify ProcessExec.run was called with the expected parameters
            mocked.verify( // GH-90000
                    () -> ProcessExec.run(eq(tempDir), any(Duration.class), eq(command), anyMap())); // GH-90000
        }
    }

    @Test
    void run_commandWithArguments_allowsWithPrefixMatch() throws Exception { // GH-90000
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) { // GH-90000
            // Arrange
            var baseCommand = List.of("git", "commit"); // GH-90000
            var fullCommand = List.of("git", "commit", "-m", "Initial commit"); // GH-90000
            var allowedCommand =
                    new AllowlistPolicy.AllowedCommand("git-commit", baseCommand, List.of()); // GH-90000
            when(mockPolicy.commands()).thenReturn(List.of(allowedCommand)); // GH-90000

            // Set up the mock to return a successful result
            when(ProcessExec.run(any(Path.class), any(Duration.class), anyList(), anyMap())) // GH-90000
                    .thenReturn(new ProcessExec.Result(0, "commit output", "")); // GH-90000

            // Act
            var result = safeExec.run(tempDir, fullCommand, Duration.ofSeconds(1).toMillis()); // GH-90000

            // Assert
            assertEquals(0, result.exitCode()); // GH-90000
            assertEquals("commit output", result.out()); // GH-90000

            // Verify ProcessExec.run was called with the expected parameters
            mocked.verify( // GH-90000
                    () -> // GH-90000
                            ProcessExec.run( // GH-90000
                                    eq(tempDir), any(Duration.class), eq(fullCommand), anyMap())); // GH-90000
        }
    }

    @Test
    void run_commandWithDifferentCasing_allowsCaseInsensitiveMatch() throws Exception { // GH-90000
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) { // GH-90000
            // Arrange
            var command = List.of("ECHO", "hello"); // GH-90000
            var allowedCommand =
                    new AllowlistPolicy.AllowedCommand("echo-cmd", List.of("echo [GH-90000]"), List.of());
            when(mockPolicy.commands()).thenReturn(List.of(allowedCommand)); // GH-90000

            // Set up the mock to return a successful result
            when(ProcessExec.run(any(Path.class), any(Duration.class), anyList(), anyMap())) // GH-90000
                    .thenReturn(new ProcessExec.Result(0, "HELLO", "")); // GH-90000

            // Act & Assert - Should not throw SecurityException
            var result =
                    assertDoesNotThrow( // GH-90000
                            () -> safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis())); // GH-90000

            // Assert
            assertEquals(0, result.exitCode()); // GH-90000
            assertEquals("HELLO", result.out()); // GH-90000

            // Verify ProcessExec.run was called with the expected parameters
            mocked.verify( // GH-90000
                    () -> ProcessExec.run(eq(tempDir), any(Duration.class), eq(command), anyMap())); // GH-90000
        }
    }

    @Test
    void run_commandFails_throwsIOException() { // GH-90000
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) { // GH-90000
            // Arrange
            var command = List.of("failing-command [GH-90000]");
            var allowedCommand = new AllowlistPolicy.AllowedCommand("test", command, List.of()); // GH-90000
            when(mockPolicy.commands()).thenReturn(List.of(allowedCommand)); // GH-90000

            // Set up the mock to simulate a failing command
            when(ProcessExec.run(any(Path.class), any(Duration.class), anyList(), anyMap())) // GH-90000
                    .thenReturn(new ProcessExec.Result(1, "", "Command failed")); // GH-90000

            // Act & Assert
            var exception =
                    assertThrows( // GH-90000
                            IOException.class,
                            () -> safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis())); // GH-90000

            // Verify the exception message
            assertTrue(exception.getMessage().contains("Command failed [GH-90000]"));

            // Verify ProcessExec.run was called with the expected parameters
            mocked.verify( // GH-90000
                    () -> ProcessExec.run(eq(tempDir), any(Duration.class), eq(command), anyMap())); // GH-90000
        }
    }

    @Test
    void run_withoutSettingPolicy_throwsIllegalStateException() { // GH-90000
        // Arrange
        var safeExec = new SafeExec(); // No policy set // GH-90000
        var command = List.of("echo", "test"); // GH-90000

        // Act & Assert
        assertThrows( // GH-90000
                IllegalStateException.class,
                () -> safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis())); // GH-90000
    }

    @Test
    void run_withNullCommand_throwsNullPointerException() { // GH-90000
        // Act & Assert
        assertThrows( // GH-90000
                NullPointerException.class,
                () -> safeExec.run(tempDir, null, Duration.ofSeconds(1).toMillis())); // GH-90000
    }

    @Test
    void run_withNullCwd_throwsNullPointerException() { // GH-90000
        // Arrange
        var command = List.of("echo", "test"); // GH-90000

        // Act & Assert
        assertThrows( // GH-90000
                NullPointerException.class,
                () -> safeExec.run(null, command, Duration.ofSeconds(1).toMillis())); // GH-90000
    }

    @Test
    void run_withEmptyCommand_throwsSecurityException() { // GH-90000
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) { // GH-90000
            // Act & Assert
            assertThrows( // GH-90000
                    SecurityException.class,
                    () -> safeExec.run(tempDir, List.of(), Duration.ofSeconds(1).toMillis())); // GH-90000

            // Verify ProcessExec was never called
            mocked.verifyNoInteractions(); // GH-90000
        }
    }

    @Test
    void run_failingCommand_throwsIOException() { // GH-90000
        try (MockedStatic<ProcessExec> mocked = Mockito.mockStatic(ProcessExec.class)) { // GH-90000
            // Arrange
            var command = List.of("failing-command [GH-90000]");
            var allowedCommand = new AllowlistPolicy.AllowedCommand("test", command, List.of()); // GH-90000
            when(mockPolicy.commands()).thenReturn(List.of(allowedCommand)); // GH-90000

            // Set up the mock to simulate a failing command
            when(ProcessExec.run(any(Path.class), any(Duration.class), anyList(), anyMap())) // GH-90000
                    .thenReturn(new ProcessExec.Result(1, "", "Command failed")); // GH-90000

            // Act & Assert
            var exception =
                    assertThrows( // GH-90000
                            IOException.class,
                            () -> safeExec.run(tempDir, command, Duration.ofSeconds(1).toMillis())); // GH-90000

            // Verify the exception message
            assertTrue(exception.getMessage().contains("Command failed [GH-90000]"));

            // Verify ProcessExec.run was called with the expected parameters
            mocked.verify( // GH-90000
                    () -> ProcessExec.run(eq(tempDir), any(Duration.class), eq(command), anyMap())); // GH-90000
        }
    }
}
