/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ghatana.refactorer.shared.FixAction;
import com.ghatana.platform.domain.domain.Severity;
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

@ExtendWith(MockitoExtension.class)
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

    @BeforeEach
    void setUpService() {
        // Initialize service with Reactor from EventloopTestBase
        javaService = new JavaLanguageService(eventloop());
    }

    @Test
    void testId() {
        assertEquals("java", javaService.id());
    }

    @Test
    void testSupportsJavaFile() {
        assertTrue(javaService.supports(Path.of("Test.java")));
        assertTrue(javaService.supports(Path.of("src/main/java/com/example/Test.java")));
    }

    @Test
    void testDoesNotSupportNonJavaFiles() {
        assertFalse(javaService.supports(Path.of("test.py")));
        assertFalse(javaService.supports(Path.of("pom.xml")));
        assertFalse(javaService.supports(Path.of("src/main/resources/application.properties")));
    }

    @Test
    void testDiagnoseEmptyProject() {
        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> javaService.diagnose(projectContext, List.of()));
        assertNotNull(diagnostics);
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void testDiagnoseValidJavaFile(@TempDir Path tempDir) throws Exception {
        // Create a simple valid Java file
        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(
                javaFile,
                """
            public class Test {
                public static void main(String[] args) {
                    System.out.println(\"Hello, World!\");
                }
            }
            """);

        List<UnifiedDiagnostic> diagnostics = runPromise(
                () -> javaService.diagnose(projectContext, List.of(javaFile)));
        assertNotNull(diagnostics);
        // No errors expected in valid Java code
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void testPlanFixes() {
        // Create a test diagnostic with required fields
        UnifiedDiagnostic diagnostic
                = new UnifiedDiagnostic(
                        "test-tool", // tool
                        "test-rule", // rule
                        "Test message", // message
                        "Test.java", // file
                        1, // startLine
                        1, // startColumn
                        Severity.ERROR, // severity
                        Map.of() // meta
                );

        List<FixAction> fixes = runPromise(
                () -> javaService.planFixes(diagnostic, projectContext));
        assertNotNull(fixes);
        // TODO: Add more specific assertions once fix planning is implemented
    }

    @Test
    void isEnabledWhenJavaIsInConfigShouldReturnTrue() {
        // The base class sets up the config with "java" in the languages list
        assertTrue(javaService.supports(Path.of("Test.java")));
    }

    @Test
    void planFixesShouldReturnEmptyListByDefault() {
        // Create a test diagnostic with required fields
        UnifiedDiagnostic diagnostic
                = new UnifiedDiagnostic(
                        "test-tool", // tool
                        "test-rule", // rule
                        "Test message", // message
                        "Test.java", // file
                        1, // startLine
                        1, // startColumn
                        Severity.ERROR, // severity
                        Map.of() // meta
                );

        List<FixAction> fixes = runPromise(
                () -> javaService.planFixes(diagnostic, projectContext));
        assertThat(fixes).isEmpty();
    }

    @Test
    void initializeShouldNotThrow() {
        // The current LanguageService interface doesn't have an initialize method
        // This test is no longer needed
    }

    @Test
    void shutdownShouldNotThrow() {
        // The current LanguageService interface doesn't have a shutdown method
        // This test is no longer needed
    }
}
