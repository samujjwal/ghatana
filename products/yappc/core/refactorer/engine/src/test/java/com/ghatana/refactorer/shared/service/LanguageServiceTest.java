/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared.service;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.FixAction;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.domain.Severity;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles language service test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class LanguageServiceTest extends EventloopTestBase {

    // Constants for duplicate literals
    private static final String TEST = "test";
    private static final String TEST_RULE = "test-rule";
    private static final String TEST_TXT = "test.txt";

    // Test implementation of LanguageService
    private static class LanguageServiceTestImpl implements LanguageService {
        @Override
        public String id() { // GH-90000
            return TEST;
        }

        @Override
        public boolean supports(Path file) { // GH-90000
            return file.toString().endsWith(".txt [GH-90000]");
        }

        @Override
        public Promise<List<UnifiedDiagnostic>> diagnose(PolyfixProjectContext ctx, List<Path> files) { // GH-90000
            List<UnifiedDiagnostic> diagnostics = new ArrayList<>(); // GH-90000
            diagnostics.add( // GH-90000
                    new UnifiedDiagnostic( // GH-90000
                            TEST,
                            TEST_RULE,
                            "Test diagnostic",
                            TEST_TXT,
                            1,
                            1,
                            Severity.ERROR,
                            Map.of("test-key", "test-value"))); // GH-90000
            return Promise.of(diagnostics); // GH-90000
        }

        @Override
        public List<String> getSupportedFileExtensions() { // GH-90000
            List<String> extensions = new ArrayList<>(); // GH-90000
            extensions.add(".txt [GH-90000]");
            return extensions;
        }
    }

    @Test
    void id_shouldReturnServiceId() { // GH-90000
        // Given
        LanguageService service = new LanguageServiceTestImpl(); // GH-90000

        // When
        String id = service.id(); // GH-90000

        // Then
        assertEquals(TEST, id); // GH-90000
    }

    @Test
    void supports_shouldCheckFileExtension() { // GH-90000
        // Given
        LanguageService service = new LanguageServiceTestImpl(); // GH-90000
        Path supportedFile = Path.of(TEST_TXT); // GH-90000
        Path unsupportedFile = Path.of("test.test [GH-90000]");

        // When / Then
        assertTrue(service.supports(supportedFile)); // GH-90000
        assertFalse(service.supports(unsupportedFile)); // GH-90000
    }

    @Test
    void diagnose_shouldReturnDiagnostics() { // GH-90000
        // Given
        LanguageService service = new LanguageServiceTestImpl(); // GH-90000
        PolyfixProjectContext context =
                new PolyfixProjectContext( // GH-90000
                        Path.of(". [GH-90000]"),
                        null, // config
                        null, // languages
                        null, // executor
                        null // logger
                        );

        // When
        List<UnifiedDiagnostic> diagnostics =
                runPromise(() -> service.diagnose(context, List.of(Path.of(TEST_TXT)))); // GH-90000

        // Then
        assertNotNull(diagnostics); // GH-90000
        assertFalse(diagnostics.isEmpty()); // GH-90000
        UnifiedDiagnostic diagnostic = diagnostics.get(0); // GH-90000
        assertEquals(TEST, diagnostic.tool()); // GH-90000
        assertEquals(TEST_RULE, diagnostic.ruleId()); // GH-90000
        assertEquals("Test diagnostic", diagnostic.message()); // GH-90000
        assertEquals(TEST_TXT, diagnostic.file()); // GH-90000
        assertEquals(1, diagnostic.line()); // GH-90000
        assertEquals(1, diagnostic.column()); // GH-90000
        assertEquals(Severity.ERROR, diagnostic.severity()); // GH-90000
        assertEquals("test-value", diagnostic.metadata().get("test-key [GH-90000]"));
    }

    @Test
    void planFixes_shouldReturnEmptyListByDefault() { // GH-90000
        // Given
        LanguageService service = new LanguageServiceTestImpl(); // GH-90000
        PolyfixProjectContext context =
                new PolyfixProjectContext( // GH-90000
                        Path.of(". [GH-90000]"),
                        null, // config
                        null, // languages
                        null, // executor
                        null // logger
                        );

        UnifiedDiagnostic diagnostic =
                new UnifiedDiagnostic( // GH-90000
                        TEST,
                        TEST_RULE,
                        "Test message",
                        TEST_TXT,
                        1,
                        1,
                        Severity.ERROR,
                        Map.of(TEST, TEST)); // GH-90000

        // When
        List<FixAction> fixes = runPromise(() -> service.planFixes(diagnostic, context)); // GH-90000

        // Then - Default implementation should return an empty list
        assertNotNull(fixes); // GH-90000
        assertTrue(fixes.isEmpty()); // GH-90000
    }

    @Test
    void getSupportedFileExtensions_shouldReturnExtensions() { // GH-90000
        // Given
        LanguageService service = new LanguageServiceTestImpl(); // GH-90000

        // When
        List<String> extensions = service.getSupportedFileExtensions(); // GH-90000

        // Then
        assertNotNull(extensions); // GH-90000
        assertFalse(extensions.isEmpty()); // GH-90000
        assertEquals(".txt", extensions.get(0)); // GH-90000
    }

    @Test
    void planFixes_shouldHandleNullContext() { // GH-90000
        // Given
        LanguageService service = new LanguageServiceTestImpl(); // GH-90000
        UnifiedDiagnostic diagnostic =
                new UnifiedDiagnostic( // GH-90000
                        TEST,
                        TEST_RULE,
                        "Test message",
                        "test.test",
                        1,
                        1,
                        Severity.ERROR,
                        Map.of(TEST, TEST)); // GH-90000

        // When
        List<FixAction> fixes = runPromise(() -> service.planFixes(diagnostic, null)); // GH-90000

        // Then - Default implementation should return an empty list
        assertNotNull(fixes); // GH-90000
        assertTrue(fixes.isEmpty()); // GH-90000
    }
}
