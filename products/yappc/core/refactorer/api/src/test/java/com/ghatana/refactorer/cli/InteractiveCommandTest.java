/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ghatana.refactorer.orchestrator.PolyfixOrchestrator;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.Rule;
import com.ghatana.platform.domain.domain.Severity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
/**
 * @doc.type class
 * @doc.purpose Handles interactive command test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class InteractiveCommandTest {

    private static final java.util.regex.Pattern ANSI_PATTERN = 
            java.util.regex.Pattern.compile("\\x1B\\[[;\\d]*m");

    @Mock private PolyfixCommand parentCommand;

    @Mock private PolyfixProjectContext mockContext;

    @Mock private PolyfixOrchestrator mockOrchestrator;

    private InteractiveCommand command;
    private ByteArrayOutputStream output;
    private Terminal terminal;
    private LineReader reader;

    @BeforeEach
    void setUp() throws Exception {
        // Setup test terminal
        output = new ByteArrayOutputStream();
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), output);

        // Setup test command
        command =
                new InteractiveCommand() {
                    @Override
                    protected void initialize() throws IOException {
                        this.terminal = InteractiveCommandTest.this.terminal;
                        this.reader = mock(LineReader.class);
                        this.context = mockContext;
                        this.orchestrator = mockOrchestrator;
                    }
                };

        // Setup parent command
        command.parent = parentCommand;

        // Setup mocks
        when(mockContext.getProjectRoot()).thenReturn(Path.of("/test/project"));
        when(mockContext.getMaxPasses()).thenReturn(3);
        when(mockContext.isDryRun()).thenReturn(false);
        when(mockContext.getSourceFiles())
                .thenReturn(Set.of(Path.of("test1.java"), Path.of("test2.py")));
        when(mockContext.getActiveRules())
                .thenReturn(
                        List.of(
                                new Rule(
                                        "test-rule-1",
                                        "Test Rule 1",
                                        "First test rule",
                                        Severity.ERROR),
                                new Rule(
                                        "test-rule-2",
                                        "Test Rule 2",
                                        "Second test rule",
                                        Severity.WARNING)));

        when(mockOrchestrator.getSupportedLanguages())
                .thenReturn(
                        Map.of(
                                "Java", true,
                                "Python", true,
                                "TypeScript", false));
    }

    @Test
    void testDiagnoseCommand() throws Exception {
        // Setup
        command.initialize();

        // Execute
        command.processCommand("diagnose");
        terminal.writer().flush();

        // Verify output contains expected sections
        String outputStr = ANSI_PATTERN.matcher(output.toString()).replaceAll("");

        assertAll(
                () ->
                        assertTrue(
                                outputStr.contains("=== Running Diagnostics ==="),
                                "Should include diagnostics header"),
                () ->
                        assertTrue(
                                outputStr.contains("Project Structure"),
                                "Should include project structure section"),
                () ->
                        assertTrue(
                                outputStr.contains("Language Support"),
                                "Should include language support section"),
                () ->
                        assertTrue(
                                outputStr.contains("Configuration"),
                                "Should include configuration section"),
                () ->
                        assertTrue(
                                outputStr.contains("File Analysis"),
                                "Should include file analysis section"),
                () ->
                        assertTrue(
                                outputStr.contains("Active Rules"),
                                "Should include active rules section"),
                () ->
                        assertTrue(
                                outputStr.contains("Performance Metrics"),
                                "Should include performance metrics section"),
                () ->
                        assertTrue(
                                outputStr.contains("=== End of Diagnostics ==="),
                                "Should include end of diagnostics marker"));
    }

    @Test
    void testDiagnoseWithError() throws Exception {
        // Setup to throw an exception
        when(mockContext.getProjectRoot()).thenThrow(new RuntimeException("Test error"));
        command.initialize();

        // Execute
        command.processCommand("diagnose");
        terminal.writer().flush();

        // Verify error is handled and output contains error message
        String outputStr = ANSI_PATTERN.matcher(output.toString()).replaceAll("");
        assertTrue(outputStr.contains("Error running diagnostics"), "Should include error message");
        assertTrue(outputStr.contains("Test error"), "Should include the actual error message");
    }

    @Test
    void testHelpCommand() throws Exception {
        command.initialize();
        command.processCommand("help");
        terminal.writer().flush();

        String outputStr = ANSI_PATTERN.matcher(output.toString()).replaceAll("");
        assertAll(
                () ->
                        assertTrue(
                                outputStr.contains("Available commands:"),
                                "Should include available commands header"),
                () -> assertTrue(outputStr.contains("run"), "Should include run command"),
                () -> assertTrue(outputStr.contains("diagnose"), "Should include diagnose command"),
                () -> assertTrue(outputStr.contains("help"), "Should include help command"),
                () -> assertTrue(outputStr.contains("clear"), "Should include clear command"),
                () ->
                        assertTrue(
                                outputStr.contains("exit|quit"),
                                "Should include exit/quit commands"));
    }
}
