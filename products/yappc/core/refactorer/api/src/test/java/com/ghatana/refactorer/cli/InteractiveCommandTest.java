/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ghatana.refactorer.orchestrator.PolyfixOrchestrator;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.Rule;
import com.ghatana.platform.domain.Severity;
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

@ExtendWith(MockitoExtension.class) // GH-90000
@MockitoSettings(strictness = Strictness.LENIENT) // GH-90000
/**
 * @doc.type class
 * @doc.purpose Handles interactive command test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class InteractiveCommandTest {

    private static final java.util.regex.Pattern ANSI_PATTERN =
            java.util.regex.Pattern.compile("\\x1B\\[[;\\d]*m [GH-90000]");

    @Mock private PolyfixCommand parentCommand;

    @Mock private PolyfixProjectContext mockContext;

    @Mock private PolyfixOrchestrator mockOrchestrator;

    private InteractiveCommand command;
    private ByteArrayOutputStream output;
    private Terminal terminal;
    private LineReader reader;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        // Setup test terminal
        output = new ByteArrayOutputStream(); // GH-90000
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), output); // GH-90000

        // Setup test command
        command =
                new InteractiveCommand() { // GH-90000
                    @Override
                    protected void initialize() throws IOException { // GH-90000
                        this.terminal = InteractiveCommandTest.this.terminal;
                        this.reader = mock(LineReader.class); // GH-90000
                        this.context = mockContext;
                        this.orchestrator = mockOrchestrator;
                    }
                };

        // Setup parent command
        command.parent = parentCommand;

        // Setup mocks
        when(mockContext.getProjectRoot()).thenReturn(Path.of("/test/project [GH-90000]"));
        when(mockContext.getMaxPasses()).thenReturn(3); // GH-90000
        when(mockContext.isDryRun()).thenReturn(false); // GH-90000
        when(mockContext.getSourceFiles()) // GH-90000
                .thenReturn(Set.of(Path.of("test1.java [GH-90000]"), Path.of("test2.py [GH-90000]")));
        when(mockContext.getActiveRules()) // GH-90000
                .thenReturn( // GH-90000
                        List.of( // GH-90000
                                new Rule( // GH-90000
                                        "test-rule-1",
                                        "Test Rule 1",
                                        "First test rule",
                                        Severity.ERROR),
                                new Rule( // GH-90000
                                        "test-rule-2",
                                        "Test Rule 2",
                                        "Second test rule",
                                        Severity.WARNING)));

        when(mockOrchestrator.getSupportedLanguages()) // GH-90000
                .thenReturn( // GH-90000
                        Map.of( // GH-90000
                                "Java", true,
                                "Python", true,
                                "TypeScript", false));
    }

    @Test
    void testDiagnoseCommand() throws Exception { // GH-90000
        // Setup
        command.initialize(); // GH-90000

        // Execute
        command.processCommand("diagnose [GH-90000]");
        terminal.writer().flush(); // GH-90000

        // Verify output contains expected sections
        String outputStr = ANSI_PATTERN.matcher(output.toString()).replaceAll(" [GH-90000]");

        assertAll( // GH-90000
                () -> // GH-90000
                        assertTrue( // GH-90000
                                outputStr.contains("=== Running Diagnostics === [GH-90000]"),
                                "Should include diagnostics header"),
                () -> // GH-90000
                        assertTrue( // GH-90000
                                outputStr.contains("Project Structure [GH-90000]"),
                                "Should include project structure section"),
                () -> // GH-90000
                        assertTrue( // GH-90000
                                outputStr.contains("Language Support [GH-90000]"),
                                "Should include language support section"),
                () -> // GH-90000
                        assertTrue( // GH-90000
                                outputStr.contains("Configuration [GH-90000]"),
                                "Should include configuration section"),
                () -> // GH-90000
                        assertTrue( // GH-90000
                                outputStr.contains("File Analysis [GH-90000]"),
                                "Should include file analysis section"),
                () -> // GH-90000
                        assertTrue( // GH-90000
                                outputStr.contains("Active Rules [GH-90000]"),
                                "Should include active rules section"),
                () -> // GH-90000
                        assertTrue( // GH-90000
                                outputStr.contains("Performance Metrics [GH-90000]"),
                                "Should include performance metrics section"),
                () -> // GH-90000
                        assertTrue( // GH-90000
                                outputStr.contains("=== End of Diagnostics === [GH-90000]"),
                                "Should include end of diagnostics marker"));
    }

    @Test
    void testDiagnoseWithError() throws Exception { // GH-90000
        // Setup to throw an exception
        when(mockContext.getProjectRoot()).thenThrow(new RuntimeException("Test error [GH-90000]"));
        command.initialize(); // GH-90000

        // Execute
        command.processCommand("diagnose [GH-90000]");
        terminal.writer().flush(); // GH-90000

        // Verify error is handled and output contains error message
        String outputStr = ANSI_PATTERN.matcher(output.toString()).replaceAll(" [GH-90000]");
        assertTrue(outputStr.contains("Error running diagnostics [GH-90000]"), "Should include error message");
        assertTrue(outputStr.contains("Test error [GH-90000]"), "Should include the actual error message");
    }

    @Test
    void testHelpCommand() throws Exception { // GH-90000
        command.initialize(); // GH-90000
        command.processCommand("help [GH-90000]");
        terminal.writer().flush(); // GH-90000

        String outputStr = ANSI_PATTERN.matcher(output.toString()).replaceAll(" [GH-90000]");
        assertAll( // GH-90000
                () -> // GH-90000
                        assertTrue( // GH-90000
                                outputStr.contains("Available commands: [GH-90000]"),
                                "Should include available commands header"),
                () -> assertTrue(outputStr.contains("run [GH-90000]"), "Should include run command"),
                () -> assertTrue(outputStr.contains("diagnose [GH-90000]"), "Should include diagnose command"),
                () -> assertTrue(outputStr.contains("help [GH-90000]"), "Should include help command"),
                () -> assertTrue(outputStr.contains("clear [GH-90000]"), "Should include clear command"),
                () -> // GH-90000
                        assertTrue( // GH-90000
                                outputStr.contains("exit|quit [GH-90000]"),
                                "Should include exit/quit commands"));
    }
}
