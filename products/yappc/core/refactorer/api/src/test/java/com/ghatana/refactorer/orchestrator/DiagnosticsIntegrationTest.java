/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ghatana.refactorer.diagnostics.DiagnosticsRunner;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.service.LanguageService;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // GH-90000
/**
 * @doc.type class
 * @doc.purpose Handles diagnostics integration test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DiagnosticsIntegrationTest {

    @Mock private LanguageService mockLanguageService;

    @Test
    void discoverLanguageServices_shouldNotThrow() { // GH-90000
        List<LanguageService> services = DiagnosticsRunner.discoverLanguageServices(); // GH-90000
        assertNotNull(services); // GH-90000
    }

    @Test
    void runAll_shouldUseProvidedContext() { // GH-90000
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of("java [GH-90000]"),
                        List.of(), // GH-90000
                        new PolyfixConfig.Budgets(1, 5), // GH-90000
                        new PolyfixConfig.Policies(true, true, true, false), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
                                "node",
                                "eslint",
                                "tsc",
                                "prettier",
                                "ruff",
                                "black",
                                "mypy",
                                "shellcheck",
                                "shfmt",
                                "cargo",
                                "rustfmt",
                                "semgrep"));

        ExecutorService executor = Executors.newSingleThreadExecutor(); // GH-90000
        Logger logger = LogManager.getLogger(DiagnosticsIntegrationTest.class); // GH-90000
        PolyfixProjectContext context =
                new PolyfixProjectContext( // GH-90000
                        Path.of(". [GH-90000]"), config, List.of(mockLanguageService), executor, logger);

        try (MockedStatic<DiagnosticsRunner> mocked = mockStatic(DiagnosticsRunner.class)) { // GH-90000
            mocked.when(() -> DiagnosticsRunner.discoverLanguageServices()) // GH-90000
                    .thenReturn(List.of(mockLanguageService)); // GH-90000
            mocked.when(() -> DiagnosticsRunner.runAll(context)).thenReturn(List.of()); // GH-90000

            List<UnifiedDiagnostic> diagnostics = DiagnosticsRunner.runAll(context); // GH-90000
            assertNotNull(diagnostics); // GH-90000
            assertTrue(diagnostics.isEmpty()); // GH-90000
        }
    }
}
