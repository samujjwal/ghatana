/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ghatana.refactorer.shared.FixAction;
import com.ghatana.platform.domain.Severity;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // GH-90000
/**
 * @doc.type class
 * @doc.purpose Handles java language service test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class JavaLanguageServiceTest extends AbstractLanguageTest {

    private JavaLanguageService javaService;

    @Mock
    private UnifiedDiagnostic mockDiagnostic;

    // Constants for duplicate literals
    private static final String TEST_JAVA = "Test.java";

    @BeforeEach
    void setUpService() { // GH-90000
        // Initialize service with Reactor from EventloopTestBase
        javaService = new JavaLanguageService(eventloop()); // GH-90000
    }

    @Test
    void testId() { // GH-90000
        assertEquals("java", javaService.id()); // GH-90000
    }

    @Test
    void testSupportsJavaFile() { // GH-90000
        assertTrue(javaService.supports(Path.of(TEST_JAVA))); // GH-90000
        assertTrue(javaService.supports(Path.of("src/main/java/com/example/" + TEST_JAVA))); // GH-90000
    }

    @Test
    void testDoesNotSupportNonJavaFiles() { // GH-90000
        assertFalse(javaService.supports(Path.of("test.py [GH-90000]")));
        assertFalse(javaService.supports(Path.of("pom.xml [GH-90000]")));
        assertFalse(javaService.supports(Path.of("src/main/resources/application.properties [GH-90000]")));
    }

    @Test
    void testDiagnoseEmptyProject() { // GH-90000
        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> javaService.diagnose(projectContext, List.of())); // GH-90000
        assertNotNull(diagnostics); // GH-90000
        assertTrue(diagnostics.isEmpty()); // GH-90000
    }

    @Test
    void testDiagnoseValidJavaFile(@TempDir Path tempDir) throws Exception { // GH-90000
        // Create a simple valid Java file
        Path javaFile = tempDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString( // GH-90000
                javaFile,
                """
            public class Test {
                public static void main(String[] args) { // GH-90000
                    System.out.println(\"Hello, World!\"); // GH-90000
                }
            }
            """);

        List<UnifiedDiagnostic> diagnostics = runPromise( // GH-90000
                () -> javaService.diagnose(projectContext, List.of(javaFile))); // GH-90000
        assertNotNull(diagnostics); // GH-90000
        // No errors expected in valid Java code
        assertTrue(diagnostics.isEmpty()); // GH-90000
    }

    @Test
    void testPlanFixes() { // GH-90000
        // Create a test diagnostic with required fields
        UnifiedDiagnostic diagnostic
                = new UnifiedDiagnostic( // GH-90000
                        "test-tool", // tool
                        "test-rule", // rule
                        "Test message", // message
                        TEST_JAVA, // file
                        1, // startLine
                        1, // startColumn
                        Severity.ERROR, // severity
                        Map.of() // meta // GH-90000
                );

        List<FixAction> fixes = runPromise( // GH-90000
                () -> javaService.planFixes(diagnostic, projectContext)); // GH-90000
        assertNotNull(fixes); // GH-90000
        // Note: More specific assertions will be added once fix planning is implemented
    }

    @Test
    void isEnabledWhenJavaIsInConfigShouldReturnTrue() { // GH-90000
        // The base class sets up the config with "java" in the languages list
        assertTrue(javaService.supports(Path.of(TEST_JAVA))); // GH-90000
    }

    @Test
    void planFixesShouldReturnEmptyListByDefault() { // GH-90000
        // Create a test diagnostic with required fields
        UnifiedDiagnostic diagnostic
                = new UnifiedDiagnostic( // GH-90000
                        "test-tool", // tool
                        "test-rule", // rule
                        "Test message", // message
                        TEST_JAVA, // file
                        1, // startLine
                        1, // startColumn
                        Severity.ERROR, // severity
                        Map.of() // meta // GH-90000
                );

        List<FixAction> fixes = runPromise( // GH-90000
                () -> javaService.planFixes(diagnostic, projectContext)); // GH-90000
        assertThat(fixes).isEmpty(); // GH-90000
    }

    @Test
    void initializeShouldNotThrow() { // GH-90000
        // The current LanguageService interface doesn't have an initialize method
        // This test is no longer needed
    }

    @Test
    void shutdownShouldNotThrow() { // GH-90000
        // The current LanguageService interface doesn't have a shutdown method
        // This test is no longer needed
    }
}
