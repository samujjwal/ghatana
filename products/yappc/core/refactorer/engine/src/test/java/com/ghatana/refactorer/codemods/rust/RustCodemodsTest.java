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
    void setUp() {
        // Configure logging
        Configurator.setRootLevel(Level.DEBUG);

        // Initialize executor service
        executorService = Executors.newSingleThreadExecutor();

        // Create a test configuration with required parameters
        PolyfixConfig.Budgets budgets = new PolyfixConfig.Budgets(5, 100);
        PolyfixConfig.Policies policies =
                new PolyfixConfig.Policies(
                        true, // tsAllowTemporaryAny
                        true, // pythonAddMissingImports
                        true, // bashEnforceStrictMode
                        true // jsonAutofillRequiredDefaults
                        );
        PolyfixConfig.Tools tools =
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
                        "semgrep");

        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("rust"), // languages
                        List.of("schema"), // schemaPaths
                        budgets,
                        policies,
                        tools);

        // Create project context with required parameters
        PolyfixProjectContext context =
                new PolyfixProjectContext(
                        tempDir,
                        config,
                        Collections.emptyList(), // No language services for this test
                        executorService,
                        LogManager.getLogger(RustCodemodsTest.class));

        rustCodemods = new RustCodemods(context);
        testFile = tempDir.resolve("test.rs");

        Logger logger = LogManager.getLogger(RustCodemodsTest.class);
        logger.info("Test logging initialized with Log4j2");
    }

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Test
    void testFixRedundantClosure() {
        String content = "let x = vec![1, 2, 3].iter().map(|x| x).collect::<Vec<_>>();";
        String expected = "let x = vec![1, 2, 3].iter().collect::<Vec<_>>();";

        UnifiedDiagnostic diagnostic =
                createMockDiagnostic("clippy::redundant_closure", 1, 25, 1, 35);
        String result = rustCodemods.getFix("clippy::redundant_closure", content, diagnostic);

        assertEquals(expected, result);
    }

    @Test
    void testFixNeedlessReturn() {
        String content = "fn add(a: i32, b: i32) -> i32 {\n    return a + b;\n}";
        String expected = "fn add(a: i32, b: i32) -> i32 {\n    a + b;\n}";

        UnifiedDiagnostic diagnostic = createMockDiagnostic("clippy::needless_return", 2, 5, 2, 16);
        String result = rustCodemods.getFix("clippy::needless_return", content, diagnostic);

        assertEquals(expected, result);
    }

    @Test
    @Disabled(
            "TODO: Implement clippy::single_match fix - test disabled until implementation is"
                    + " complete. See RustCodemods.fixSingleMatch() for details.")
    void testFixSingleMatch() {
        Logger logger = LogManager.getLogger(RustCodemodsTest.class);

        String content = "match x {\n    Some(val) => { println!(\"{}\", val); }\n}";
        String expected = "if let Some(val) = x { println!(\"{}\", val); }";

        logger.info("=== Test Input ===");
        logger.info("\n{}", content);
        logger.info("=== Expected Output ===");
        logger.info("\n{}", expected);

        UnifiedDiagnostic diagnostic = createMockDiagnostic("clippy::single_match", 1, 1, 3, 2);
        String result = rustCodemods.getFix("clippy::single_match", content, diagnostic);

        logger.info("=== Actual Output ===");
        logger.info("\n{}", result);

        assertEquals(expected, result);
    }

    @Test
    void testFixSingleCharPattern() {
        String content = "let s = \"hello\".chars().filter(|c| c == 'l').collect::<String>();";
        String expected = content;

        UnifiedDiagnostic diagnostic =
                createMockDiagnostic("clippy::single_char_pattern", 1, 1, 1, 60);
        String result = rustCodemods.getFix("clippy::single_char_pattern", content, diagnostic);

        assertEquals(expected, result);
    }

    @Test
    void testFixNeedlessBorrow() {
        String content = "fn get_len(s: &String) -> usize {\n    s.len()\n}";
        // The method should remove the unnecessary borrow
        String expected = "fn get_len(s: String) -> usize {\n    s.len()\n}";

        // The diagnostic points to the & before String
        UnifiedDiagnostic diagnostic =
                createMockDiagnostic("clippy::needless_borrow", 1, 15, 1, 16);
        String result = rustCodemods.getFix("clippy::needless_borrow", content, diagnostic);

        assertEquals(expected, result);
    }

    @Test
    void testGetFix_UnknownRule_ReturnsOriginalContent() {
        String content = "let x = 42;";
        UnifiedDiagnostic diagnostic = createMockDiagnostic("unknown::rule", 1, 1, 1, 10);

        String result = rustCodemods.getFix("unknown::rule", content, diagnostic);

        assertEquals(content, result);
    }

    private UnifiedDiagnostic createMockDiagnostic(
            String ruleId, int startLine, int startCol, int endLine, int endCol) {
        return UnifiedDiagnostic.builder()
                .file(testFile)
                .code(ruleId)
                .message("Test diagnostic")
                .startLine(startLine)
                .startColumn(startCol)
                .endLine(endLine)
                .endColumn(endCol)
                .build();
    }
}
