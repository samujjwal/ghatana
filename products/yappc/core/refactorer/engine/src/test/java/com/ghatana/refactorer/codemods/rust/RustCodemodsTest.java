package com.ghatana.refactorer.codemods.rust;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles rust codemods test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class RustCodemodsTest {

    @TempDir Path tempDir;
    private RustCodemods rustCodemods;
    private Path testFile;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() { // GH-90000
        // Configure logging
        Configurator.setRootLevel(Level.DEBUG); // GH-90000

        // Initialize executor service
        executorService = Executors.newSingleThreadExecutor(); // GH-90000

        // Create a test configuration with required parameters
        PolyfixConfig.Budgets budgets = new PolyfixConfig.Budgets(5, 100); // GH-90000
        PolyfixConfig.Policies policies =
                new PolyfixConfig.Policies( // GH-90000
                        true, // tsAllowTemporaryAny
                        true, // pythonAddMissingImports
                        true, // bashEnforceStrictMode
                        true // jsonAutofillRequiredDefaults
                        );
        PolyfixConfig.Tools tools =
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
                        "semgrep");

        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of("rust"), // languages
                        List.of("schema"), // schemaPaths
                        budgets,
                        policies,
                        tools);

        // Create project context with required parameters
        PolyfixProjectContext context =
                new PolyfixProjectContext( // GH-90000
                        tempDir,
                        config,
                        Collections.emptyList(), // No language services for this test // GH-90000
                        executorService,
                        LogManager.getLogger(RustCodemodsTest.class)); // GH-90000

        rustCodemods = new RustCodemods(context); // GH-90000
        testFile = tempDir.resolve("test.rs");

        Logger logger = LogManager.getLogger(RustCodemodsTest.class); // GH-90000
        logger.info("Test logging initialized with Log4j2");
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (executorService != null) { // GH-90000
            executorService.shutdown(); // GH-90000
        }
    }

    @Test
    void testFixRedundantClosure() { // GH-90000
        String content = "let x = vec![1, 2, 3].iter().map(|x| x).collect::<Vec<_>>();"; // GH-90000
        String expected = "let x = vec![1, 2, 3].iter().collect::<Vec<_>>();"; // GH-90000

        UnifiedDiagnostic diagnostic =
                createMockDiagnostic("clippy::redundant_closure", 1, 25, 1, 35); // GH-90000
        String result = rustCodemods.getFix("clippy::redundant_closure", content, diagnostic); // GH-90000

        assertEquals(expected, result); // GH-90000
    }

    @Test
    void testFixNeedlessReturn() { // GH-90000
        String content = "fn add(a: i32, b: i32) -> i32 {\n    return a + b;\n}"; // GH-90000
        String expected = "fn add(a: i32, b: i32) -> i32 {\n    a + b;\n}"; // GH-90000

        UnifiedDiagnostic diagnostic = createMockDiagnostic("clippy::needless_return", 2, 5, 2, 16); // GH-90000
        String result = rustCodemods.getFix("clippy::needless_return", content, diagnostic); // GH-90000

        assertEquals(expected, result); // GH-90000
    }

    @Test
    @Disabled( // GH-90000
            "Note: clippy::single_match fix not yet implemented - test disabled"
                    + " complete. See RustCodemods.fixSingleMatch() for details.") // GH-90000
    void testFixSingleMatch() { // GH-90000
        Logger logger = LogManager.getLogger(RustCodemodsTest.class); // GH-90000

        String content = "match x {\n    Some(val) => { println!(\"{}\", val); }\n}"; // GH-90000
        String expected = "if let Some(val) = x { println!(\"{}\", val); }"; // GH-90000

        logger.info("=== Test Input ===");
        logger.info("\n{}", content); // GH-90000
        logger.info("=== Expected Output ===");
        logger.info("\n{}", expected); // GH-90000

        UnifiedDiagnostic diagnostic = createMockDiagnostic("clippy::single_match", 1, 1, 3, 2); // GH-90000
        String result = rustCodemods.getFix("clippy::single_match", content, diagnostic); // GH-90000

        logger.info("=== Actual Output ===");
        logger.info("\n{}", result); // GH-90000

        assertEquals(expected, result); // GH-90000
    }

    @Test
    void testFixSingleCharPattern() { // GH-90000
        String content = "let s = \"hello\".chars().filter(|c| c == 'l').collect::<String>();"; // GH-90000
        String expected = content;

        UnifiedDiagnostic diagnostic =
                createMockDiagnostic("clippy::single_char_pattern", 1, 1, 1, 60); // GH-90000
        String result = rustCodemods.getFix("clippy::single_char_pattern", content, diagnostic); // GH-90000

        assertEquals(expected, result); // GH-90000
    }

    @Test
    void testFixNeedlessBorrow() { // GH-90000
        String content = "fn get_len(s: &String) -> usize {\n    s.len()\n}"; // GH-90000
        // The method should remove the unnecessary borrow
        String expected = "fn get_len(s: String) -> usize {\n    s.len()\n}"; // GH-90000

        // The diagnostic points to the & before String
        UnifiedDiagnostic diagnostic =
                createMockDiagnostic("clippy::needless_borrow", 1, 15, 1, 16); // GH-90000
        String result = rustCodemods.getFix("clippy::needless_borrow", content, diagnostic); // GH-90000

        assertEquals(expected, result); // GH-90000
    }

    @Test
    void testGetFix_UnknownRule_ReturnsOriginalContent() { // GH-90000
        String content = "let x = 42;";
        UnifiedDiagnostic diagnostic = createMockDiagnostic("unknown::rule", 1, 1, 1, 10); // GH-90000

        String result = rustCodemods.getFix("unknown::rule", content, diagnostic); // GH-90000

        assertEquals(content, result); // GH-90000
    }

    private UnifiedDiagnostic createMockDiagnostic( // GH-90000
            String ruleId, int startLine, int startCol, int endLine, int endCol) {
        return UnifiedDiagnostic.builder() // GH-90000
                .file(testFile) // GH-90000
                .code(ruleId) // GH-90000
                .message("Test diagnostic")
                .startLine(startLine) // GH-90000
                .startColumn(startCol) // GH-90000
                .endLine(endLine) // GH-90000
                .endColumn(endCol) // GH-90000
                .build(); // GH-90000
    }
}
