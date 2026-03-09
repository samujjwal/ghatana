package com.ghatana.refactorer.diagnostics.tsjs;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.codemods.CodeModificationPlan;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link TsJsLanguageService}. 
 * @doc.type class
 * @doc.purpose Handles ts js language service test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class TsJsLanguageServiceTest extends EventloopTestBase {

    @TempDir static Path tempDir;
    private TsJsLanguageService languageService;
    private PolyfixProjectContext context;

    @BeforeEach
    void setUp() {
        // Enable debug logging for tests
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
        ctx.updateLoggers(config);

        this.context =
                new PolyfixProjectContext(
                        tempDir,
                        null,
                        List.of(),
                        null,
                        LogManager.getLogger(TsJsLanguageServiceTest.class));
        this.languageService = new TsJsLanguageService(context, eventloop());
    }

    @Test
    void testIsAvailable() {
        // This test will pass if either TSC or ESLint is available
        boolean isAvailable = languageService.isAvailable();
        System.out.println(
                "TypeScript/JavaScript language service is "
                        + (isAvailable ? "available" : "not available"));

        // We don't fail the test if not available, just log it
        if (!isAvailable) {
            System.out.println(
                    "Skipping TypeScript/JavaScript tests because neither TSC nor ESLint is"
                            + " available");
        }
    }

    @Test
    void testRunWithValidProject() throws IOException {
        if (!languageService.isAvailable()) {
            System.out.println(
                    "Skipping testRunWithValidProject: TypeScript/JavaScript not available");
            return;
        }

        // Create a simple TypeScript project
        createTestProject(tempDir);

        // Run the language service
        List<UnifiedDiagnostic> diagnostics = runPromise(() -> languageService.run(tempDir));

        // Should have no errors in valid code
        assertTrue(
                diagnostics.isEmpty(),
                "Expected no errors in valid code, but found: " + diagnostics);
    }

    @Test
    void testPlanFixes() {
        if (!languageService.isAvailable()) {
            System.out.println("Skipping testPlanFixes: TypeScript/JavaScript not available");
            return;
        }

        // This is a placeholder test - actual implementation would test fix planning
        List<CodeModificationPlan> plans = runPromise(() -> languageService.planFixes(tempDir, List.of()));
        assertNotNull(plans, "Plans should not be null");
        assertTrue(plans.isEmpty(), "No fixes should be planned for empty diagnostics");
    }

    private void createTestProject(Path projectDir) throws IOException {
        // Create tsconfig.json
        String tsConfig =
                """
        {
          "compilerOptions": {
            "target": "es2018",
            "module": "commonjs",
            "strict": true,
            "esModuleInterop": true,
            "skipLibCheck": true,
            "forceConsistentCasingInFileNames": true
          },
          "include": ["src/**/*"],
          "exclude": ["node_modules"]
        }
        """;

        // Create a simple TypeScript file
        String sourceCode =
                """
        // Valid TypeScript code
        function greet(name: string): string {
            return `Hello, ${name}!`;
        }

        console.log(greet('World'));
        """;

        // Create project structure
        Path srcDir = projectDir.resolve("src");
        Files.createDirectories(srcDir);

        Files.writeString(projectDir.resolve("tsconfig.json"), tsConfig);
        Files.writeString(srcDir.resolve("index.ts"), sourceCode);
    }
}
