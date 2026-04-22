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
    void setUp() { // GH-90000
        // Enable debug logging for tests
        try (LoggerContext ctx = (LoggerContext) LogManager.getContext(false)) { // GH-90000
            Configuration config = ctx.getConfiguration(); // GH-90000
            config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.DEBUG); // GH-90000
            ctx.updateLoggers(config); // GH-90000
        }

        this.context =
                new PolyfixProjectContext( // GH-90000
                        tempDir,
                        null,
                        List.of(), // GH-90000
                        null,
                        LogManager.getLogger(TsJsLanguageServiceTest.class)); // GH-90000
        this.languageService = new TsJsLanguageService(context, eventloop()); // GH-90000
    }

    @Test
    void testIsAvailable() { // GH-90000
        // This test will pass if either TSC or ESLint is available
        boolean isAvailable = languageService.isAvailable(); // GH-90000
        System.out.println( // GH-90000
                "TypeScript/JavaScript language service is "
                        + (isAvailable ? "available" : "not available")); // GH-90000

        // We don't fail the test if not available, just log it
        if (!isAvailable) { // GH-90000
            System.out.println( // GH-90000
                    "Skipping TypeScript/JavaScript tests because neither TSC nor ESLint is"
                            + " available");
        }
    }

    @Test
    void testRunWithValidProject() throws IOException { // GH-90000
        if (!languageService.isAvailable()) { // GH-90000
            System.out.println( // GH-90000
                    "Skipping testRunWithValidProject: TypeScript/JavaScript not available");
            return;
        }

        // Create a simple TypeScript project
        createTestProject(tempDir); // GH-90000

        // Run the language service
        List<UnifiedDiagnostic> diagnostics = runPromise(() -> languageService.run(tempDir)); // GH-90000

        // Should have no errors in valid code
        assertTrue( // GH-90000
                diagnostics.isEmpty(), // GH-90000
                "Expected no errors in valid code, but found: " + diagnostics);
    }

    @Test
    void testPlanFixes() { // GH-90000
        if (!languageService.isAvailable()) { // GH-90000
            System.out.println("Skipping testPlanFixes: TypeScript/JavaScript not available [GH-90000]");
            return;
        }

        // This is a placeholder test - actual implementation would test fix planning
        List<CodeModificationPlan> plans = runPromise(() -> languageService.planFixes(tempDir, List.of())); // GH-90000
        assertNotNull(plans, "Plans should not be null"); // GH-90000
        assertTrue(plans.isEmpty(), "No fixes should be planned for empty diagnostics"); // GH-90000
    }

    private void createTestProject(Path projectDir) throws IOException { // GH-90000
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
        function greet(name: string): string { // GH-90000
            return `Hello, ${name}!`;
        }

        console.log(greet('World')); // GH-90000
        """;

        // Create project structure
        Path srcDir = projectDir.resolve("src [GH-90000]");
        Files.createDirectories(srcDir); // GH-90000

        Files.writeString(projectDir.resolve("tsconfig.json [GH-90000]"), tsConfig);
        Files.writeString(srcDir.resolve("index.ts [GH-90000]"), sourceCode);
    }
}
