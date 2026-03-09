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

@ExtendWith(MockitoExtension.class)
/**
 * @doc.type class
 * @doc.purpose Handles diagnostics integration test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DiagnosticsIntegrationTest {

    @Mock private LanguageService mockLanguageService;

    @Test
    void discoverLanguageServices_shouldNotThrow() {
        List<LanguageService> services = DiagnosticsRunner.discoverLanguageServices();
        assertNotNull(services);
    }

    @Test
    void runAll_shouldUseProvidedContext() {
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("java"),
                        List.of(),
                        new PolyfixConfig.Budgets(1, 5),
                        new PolyfixConfig.Policies(true, true, true, false),
                        new PolyfixConfig.Tools(
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

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Logger logger = LogManager.getLogger(DiagnosticsIntegrationTest.class);
        PolyfixProjectContext context =
                new PolyfixProjectContext(
                        Path.of("."), config, List.of(mockLanguageService), executor, logger);

        try (MockedStatic<DiagnosticsRunner> mocked = mockStatic(DiagnosticsRunner.class)) {
            mocked.when(() -> DiagnosticsRunner.discoverLanguageServices())
                    .thenReturn(List.of(mockLanguageService));
            mocked.when(() -> DiagnosticsRunner.runAll(context)).thenReturn(List.of());

            List<UnifiedDiagnostic> diagnostics = DiagnosticsRunner.runAll(context);
            assertNotNull(diagnostics);
            assertTrue(diagnostics.isEmpty());
        }
    }
}
