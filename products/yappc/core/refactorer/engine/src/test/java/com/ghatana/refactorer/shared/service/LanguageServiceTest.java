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
        public String id() { 
            return TEST;
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
                            TEST,
                            TEST_RULE,
                            "Test diagnostic",
                            TEST_TXT,
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
        LanguageService service = new LanguageServiceTestImpl(); 

        // When
        String id = service.id(); 

        // Then
        assertEquals(TEST, id); 
    }

    @Test
    void supports_shouldCheckFileExtension() { 
        // Given
        LanguageService service = new LanguageServiceTestImpl(); 
        Path supportedFile = Path.of(TEST_TXT); 
        Path unsupportedFile = Path.of("test.test");

        // When / Then
        assertTrue(service.supports(supportedFile)); 
        assertFalse(service.supports(unsupportedFile)); 
    }

    @Test
    void diagnose_shouldReturnDiagnostics() { 
        // Given
        LanguageService service = new LanguageServiceTestImpl(); 
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
                runPromise(() -> service.diagnose(context, List.of(Path.of(TEST_TXT)))); 

        // Then
        assertNotNull(diagnostics); 
        assertFalse(diagnostics.isEmpty()); 
        UnifiedDiagnostic diagnostic = diagnostics.get(0); 
        assertEquals(TEST, diagnostic.tool()); 
        assertEquals(TEST_RULE, diagnostic.ruleId()); 
        assertEquals("Test diagnostic", diagnostic.message()); 
        assertEquals(TEST_TXT, diagnostic.file()); 
        assertEquals(1, diagnostic.line()); 
        assertEquals(1, diagnostic.column()); 
        assertEquals(Severity.ERROR, diagnostic.severity()); 
        assertEquals("test-value", diagnostic.metadata().get("test-key"));
    }

    @Test
    void planFixes_shouldReturnEmptyListByDefault() { 
        // Given
        LanguageService service = new LanguageServiceTestImpl(); 
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
                        TEST,
                        TEST_RULE,
                        "Test message",
                        TEST_TXT,
                        1,
                        1,
                        Severity.ERROR,
                        Map.of(TEST, TEST)); 

        // When
        List<FixAction> fixes = runPromise(() -> service.planFixes(diagnostic, context)); 

        // Then - Default implementation should return an empty list
        assertNotNull(fixes); 
        assertTrue(fixes.isEmpty()); 
    }

    @Test
    void getSupportedFileExtensions_shouldReturnExtensions() { 
        // Given
        LanguageService service = new LanguageServiceTestImpl(); 

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
        LanguageService service = new LanguageServiceTestImpl(); 
        UnifiedDiagnostic diagnostic =
                new UnifiedDiagnostic( 
                        TEST,
                        TEST_RULE,
                        "Test message",
                        "test.test",
                        1,
                        1,
                        Severity.ERROR,
                        Map.of(TEST, TEST)); 

        // When
        List<FixAction> fixes = runPromise(() -> service.planFixes(diagnostic, null)); 

        // Then - Default implementation should return an empty list
        assertNotNull(fixes); 
        assertTrue(fixes.isEmpty()); 
    }
}
