package com.ghatana.refactorer.orchestrator.debug;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ghatana.refactorer.debug.DebugController;
import com.ghatana.refactorer.orchestrator.TestResourceInitializer;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
/**
 * @doc.type class
 * @doc.purpose Handles debug entry test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DebugEntryTest {

    @Mock private PolyfixProjectContext mockContext;
    private Path tempDir;

    @BeforeAll
    static void setUpClass() {
        // Initialize test resources before any tests run
        TestResourceInitializer.initialize();
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        this.tempDir = tempDir;
        // No default stubbing here to avoid UnnecessaryStubbingException.
        // Stub only in tests that actually execute a process and need the working dir.

        // Create a simple test file to verify command execution if needed by tests
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");
    }

    @AfterEach
    void tearDown() {
        // Clean up any resources if needed
    }

    @Test
    void run_executesCommandAndReturnsResult() throws Exception {
        // Arrange - Use a simple command that's available on all platforms
        List<String> command;
        String expectedOutput;

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            command = List.of("cmd", "/c", "echo", "hello");
            expectedOutput = "hello";
        } else {
            command = List.of("echo", "hello");
            expectedOutput = "hello";
        }

        // Arrange working directory for process execution
        when(mockContext.root()).thenReturn(tempDir);
        // Act
        DebugController.ParseResult result =
                DebugEntry.run(mockContext, command, Duration.ofSeconds(5));

        // Assert
        assertNotNull(result, "Result should not be null");
        String output = result.raw().trim();
        assertTrue(
                output.endsWith(expectedOutput),
                "Output should contain '" + expectedOutput + "' but was: " + output);
    }

    @Test
    void run_withNullContext_usesCurrentDirectory() throws Exception {
        // Skip this test on Windows as it may not work consistently
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return;
        }

        // Use a simple command that's available on all Unix-like systems
        List<String> command = List.of("echo", "test");

        // Act - Run with null context which should use the current directory
        DebugController.ParseResult result = DebugEntry.run(null, command, Duration.ofSeconds(5));

        // Assert
        assertNotNull(result, "Result should not be null");
        String output = result.raw().trim();
        assertEquals("test", output, "Output should match the expected value");
    }

    @Test
    void run_withTimeout_throwsException() {
        // Skip this test on Windows as it may not work consistently
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return;
        }

        // Arrange - Use a command that will take longer than the timeout
        List<String> command = List.of("sleep", "10");
        // Arrange working directory for process execution
        when(mockContext.root()).thenReturn(tempDir);

        // Act & Assert - Should throw when command times out
        Exception exception =
                assertThrows(
                        RuntimeException.class,
                        () -> DebugEntry.run(mockContext, command, Duration.ofMillis(100)),
                        "Expected timeout exception");

        assertTrue(
                exception.getMessage().contains("timed out"),
                "Exception message should indicate timeout");
    }

    @Test
    void run_withEmptyCommand_throwsException() {
        // Arrange
        List<String> command = List.of();

        // Act & Assert - Empty command should throw IllegalArgumentException
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DebugEntry.run(mockContext, command, Duration.ofSeconds(1)),
                        "Expected IllegalArgumentException for empty command");

        assertEquals(
                "Command cannot be empty",
                exception.getMessage(),
                "Exception message should indicate empty command");
    }

    @Test
    void run_withNullCommand_throwsException() {
        // Act & Assert - Null command should throw IllegalArgumentException
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DebugEntry.run(mockContext, null, Duration.ofSeconds(1)),
                        "Expected IllegalArgumentException for null command");

        assertEquals(
                "Command cannot be null",
                exception.getMessage(),
                "Exception message should indicate null command");
    }

    @Test
    void run_withNullTimeout_usesDefault() throws Exception {
        // Arrange - Use a simple command that's available on all platforms
        List<String> command;
        String expectedOutput;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            command = List.of("cmd", "/c", "echo", "default_timeout");
            expectedOutput = "default_timeout";
        } else {
            command = List.of("echo", "default_timeout");
            expectedOutput = "default_timeout";
        }

        // Arrange working directory for process execution
        when(mockContext.root()).thenReturn(tempDir);
        // Act - Pass null for timeout to use default
        DebugController.ParseResult result = DebugEntry.run(mockContext, command, null);

        // Assert - Should complete successfully with default timeout
        assertNotNull(result, "Result should not be null");
        String output = result.raw().trim();
        assertTrue(
                output.endsWith(expectedOutput),
                "Output should contain '" + expectedOutput + "' but was: " + output);
    }
}
