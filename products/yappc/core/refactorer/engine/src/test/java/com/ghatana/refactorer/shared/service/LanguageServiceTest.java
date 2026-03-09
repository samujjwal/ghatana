/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared.service;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.FixAction;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.domain.domain.Severity;
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

    // Test implementation of LanguageService
    private static class TestLanguageService implements LanguageService {
        @Override
        public String id() {
            return "test";
        }

        @Override
        public boolean supports(Path file) {
            return file.toString().endsWith(".txt");
        }

        @Override
        public Promise<List<UnifiedDiagnostic>> diagnose(PolyfixProjectContext ctx, List<Path> files) {
            List<UnifiedDiagnostic> diagnostics = new ArrayList<>();
            diagnostics.add(
                    new UnifiedDiagnostic(
                            "test",
                            "test-rule",
                            "Test diagnostic",
                            "test.txt",
                            1,
                            1,
                            Severity.ERROR,
                            Map.of("test-key", "test-value")));
            return Promise.of(diagnostics);
        }

        @Override
        public List<String> getSupportedFileExtensions() {
            List<String> extensions = new ArrayList<>();
            extensions.add(".txt");
            return extensions;
        }
    }

    @Test
    void id_shouldReturnServiceId() {
        // Given
        LanguageService service = new TestLanguageService();

        // When
        String id = service.id();

        // Then
        assertEquals("test", id);
    }

    @Test
    void supports_shouldCheckFileExtension() {
        // Given
        LanguageService service = new TestLanguageService();
        Path supportedFile = Path.of("test.txt");
        Path unsupportedFile = Path.of("test.test");

        // When / Then
        assertTrue(service.supports(supportedFile));
        assertFalse(service.supports(unsupportedFile));
    }

    @Test
    void diagnose_shouldReturnDiagnostics() {
        // Given
        LanguageService service = new TestLanguageService();
        PolyfixProjectContext context =
                new PolyfixProjectContext(
                        Path.of("."),
                        null, // config
                        null, // languages
                        null, // executor
                        null // logger
                        );

        // When
        List<UnifiedDiagnostic> diagnostics =
                runPromise(() -> service.diagnose(context, List.of(Path.of("test.txt"))));

        // Then
        assertNotNull(diagnostics);
        assertFalse(diagnostics.isEmpty());
        UnifiedDiagnostic diagnostic = diagnostics.get(0);
        assertEquals("test", diagnostic.tool());
        assertEquals("test-rule", diagnostic.ruleId());
        assertEquals("Test diagnostic", diagnostic.message());
        assertEquals("test.txt", diagnostic.file());
        assertEquals(1, diagnostic.line());
        assertEquals(1, diagnostic.column());
        assertEquals(Severity.ERROR, diagnostic.severity());
        assertEquals("test-value", diagnostic.metadata().get("test-key"));
    }

    @Test
    void planFixes_shouldReturnEmptyListByDefault() {
        // Given
        LanguageService service = new TestLanguageService();
        PolyfixProjectContext context =
                new PolyfixProjectContext(
                        Path.of("."),
                        null, // config
                        null, // languages
                        null, // executor
                        null // logger
                        );

        UnifiedDiagnostic diagnostic =
                new UnifiedDiagnostic(
                        "test",
                        "test-rule",
                        "Test message",
                        "test.txt",
                        1,
                        1,
                        Severity.ERROR,
                        Map.of("test", "test"));

        // When
        List<FixAction> fixes = runPromise(() -> service.planFixes(diagnostic, context));

        // Then - Default implementation should return an empty list
        assertNotNull(fixes);
        assertTrue(fixes.isEmpty());
    }

    @Test
    void getSupportedFileExtensions_shouldReturnExtensions() {
        // Given
        LanguageService service = new TestLanguageService();

        // When
        List<String> extensions = service.getSupportedFileExtensions();

        // Then
        assertNotNull(extensions);
        assertFalse(extensions.isEmpty());
        assertEquals(".txt", extensions.get(0));
    }

    @Test
    void planFixes_shouldHandleNullContext() {
        // Given
        LanguageService service = new TestLanguageService();
        UnifiedDiagnostic diagnostic =
                new UnifiedDiagnostic(
                        "test",
                        "test-rule",
                        "Test message",
                        "test.test",
                        1,
                        1,
                        Severity.ERROR,
                        Map.of("test", "test"));

        // When
        List<FixAction> fixes = runPromise(() -> service.planFixes(diagnostic, null));

        // Then - Default implementation should return an empty list
        assertNotNull(fixes);
        assertTrue(fixes.isEmpty());
    }
}
