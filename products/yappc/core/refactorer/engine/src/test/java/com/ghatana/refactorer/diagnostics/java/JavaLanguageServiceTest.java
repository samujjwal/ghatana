package com.ghatana.refactorer.diagnostics.java;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class JavaLanguageServiceTest extends EventloopTestBase {

    @TempDir private Path tempDir;

    private JavaLanguageService service;
    private PolyfixProjectContext context;
    private Logger logger;
    @Mock private ExecutorService mockExecutor;

    @BeforeEach
    void setUp() {
        service = new JavaLanguageService(eventloop());
        logger = LogManager.getLogger(JavaLanguageServiceTest.class);
        context =
                new PolyfixProjectContext(
                        tempDir,
                        null, // config
                        List.of(), // languages
                        mockExecutor,
                        logger);
    }

    @Test
    void testSupportsJavaFiles() {
        assertThat(service.supports(Path.of("Test.java"))).isTrue();
        assertThat(service.supports(Path.of("path/to/Test.java"))).isTrue();
        assertThat(service.supports(Path.of("Test.JAVA"))).isTrue();
        assertThat(service.supports(Path.of("Test.txt"))).isFalse();
        assertThat(service.supports(null)).isFalse();
    }

    @Test
    void testGetSupportedFileExtensions() {
        assertThat(service.getSupportedFileExtensions()).containsExactly(".java");
    }

    @Test
    void testDiagnoseWithNullFiles() {
        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, null));
        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).message()).contains("files");
    }

    @Test
    void testDiagnoseWithEmptyFiles() {
        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of()));
        assertThat(diagnostics).isEmpty();
    }

    @Test
    void testDiagnoseWithNonJavaFile() throws IOException {
        Path nonJavaFile = tempDir.resolve("test.txt");
        Files.writeString(nonJavaFile, "This is not a Java file");

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(nonJavaFile)));
        assertThat(diagnostics).isEmpty();
    }

    @Test
    void testDiagnoseWithValidJavaFile() throws IOException {
        String validJava =
                """
            package com.example;

            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
            """;

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, validJava);

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile)));
        assertThat(diagnostics).isEmpty();
    }

    @Test
    void testDiagnoseWithCompilationError() throws IOException {
        String invalidJava =
                """
            package com.example;

            public class Test {
                public static void main(String[] args) {
                    // Missing semicolon
                    System.out.println("Hello, World!")
                }
            }
            """;

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, invalidJava);

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile)));

        assertThat(diagnostics)
                .hasSize(1)
                .allSatisfy(
                        d -> {
                            assertThat(d.tool()).isEqualTo("java-compiler");
                            assertThat(d.ruleId()).isEqualTo("compilation-error");
                            assertThat(d.message()).contains("missing ';'");
                            assertThat(d.file()).endsWith("Test.java");
                            assertThat(d.line()).isGreaterThan(0);
                            assertThat(d.column()).isGreaterThan(0);
                        });
    }

    @Test
    void testDiagnoseWithMultipleFiles() throws IOException {
        // Valid Java file
        String validJava =
                """
            package com.example;

            public class Test1 {
                public void test() {}
            }
            """;

        // Invalid Java file
        String invalidJava =
                """
            package com.example;

            public class Test2 {
                public void test() {
                    return "missing return type";
                }
            }
            """;

        Path validFile = tempDir.resolve("Test1.java");
        Path invalidFile = tempDir.resolve("Test2.java");

        Files.writeString(validFile, validJava);
        Files.writeString(invalidFile, invalidJava);

        List<UnifiedDiagnostic> diagnostics =
                runPromise(() -> service.diagnose(context, List.of(validFile, invalidFile)));

        // Should only report errors from the invalid file
        assertThat(diagnostics)
                .hasSize(1)
                .allSatisfy(d -> assertThat(d.file()).endsWith("Test2.java"));
    }

    @Test
    void testDiagnoseWithNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("NonExistent.java");
        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(nonExistentFile)));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void testDiagnoseWithNullContext() {
        Path javaFile = tempDir.resolve("Test.java");

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(null, List.of(javaFile)));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).message()).contains("context");
    }

    @Test
    void testDiagnoseWithTypeError() throws IOException {
        String javaWithTypeError =
                """
            package com.example;

            public class Test {
                public void test() {
                    UnknownType variable = "string value";
                }
            }
            """;

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, javaWithTypeError);

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile)));

        assertThat(diagnostics)
                .hasSize(1)
                .anySatisfy(
                        d -> {
                            assertThat(d.ruleId()).isEqualTo("type-error");
                            assertThat(d.message()).contains("Cannot resolve type for variable");
                        });
    }

    @Test
    void testDiagnoseWithMethodReturnTypeError() throws IOException {
        String javaWithReturnTypeError =
                """
            package com.example;

            public class Test {
                public UnknownType test() {
                    return "string value";
                }
            }
            """;

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, javaWithReturnTypeError);

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile)));

        assertThat(diagnostics)
                .hasSize(1)
                .allSatisfy(
                        d -> {
                            assertThat(d.ruleId()).isEqualTo("type-error");
                            assertThat(d.message())
                                    .contains("Cannot resolve type for variable: UnknownType");
                        });
    }

    @Test
    void testDiagnoseWithValidTypes() throws IOException {
        String validJava =
                """
            package com.example;

            public class Test {
                private String field;

                public String method(String param) {
                    return param;
                }
            }
            """;

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, validJava);

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile)));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void testDiagnoseWithGenerics() throws IOException {
        String javaWithGenerics =
                """
            package com.example;

            import java.util.List;

            public class Test<T> {
                private List<String> strings;
                private T genericField;

                public <U> U genericMethod(U param) {
                    return param;
                }
            }
            """;

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, javaWithGenerics);

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile)));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void testDiagnoseWithInheritance() throws IOException {
        Path parentFile = tempDir.resolve("Parent.java");
        Path childFile = tempDir.resolve("Child.java");
        Files.writeString(parentFile, "package com.example;\npublic class Parent {}");
        Files.writeString(
                childFile,
                "package com.example;\n"
                        + "public class Child extends Parent {\n"
                        + "    public Parent getParent() {\n"
                        + "        return new Parent();\n"
                        + "    }\n"
                        + "}\n");

        List<UnifiedDiagnostic> diagnostics =
                runPromise(() -> service.diagnose(context, List.of(parentFile, childFile)));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void testDiagnoseWithInterface() throws IOException {
        String javaWithInterface =
                """
            package com.example;

            public interface TestInterface {
                void method();
            }

            public class TestImpl implements TestInterface {
                public void method() {}
            }
            """;

        Path interfaceFile = tempDir.resolve("TestInterface.java");
        Path implFile = tempDir.resolve("TestImpl.java");
        Files.writeString(
                interfaceFile,
                "package com.example;\npublic interface TestInterface {\n    void method();\n}");
        Files.writeString(
                implFile,
                "package com.example;\n"
                        + "public class TestImpl implements TestInterface {\n"
                        + "    public void method() {}\n"
                        + "}");

        List<UnifiedDiagnostic> diagnostics =
                runPromise(() -> service.diagnose(context, List.of(interfaceFile, implFile)));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void testDiagnoseWithArrays() throws IOException {
        String javaWithArrays =
                """
            package com.example;

            public class Test {
                private int[] numbers;
                private String[][] strings;

                public int[] getNumbers() {
                    return numbers;
                }
            }
            """;

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, javaWithArrays);

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile)));

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void testDiagnoseWithUnresolvedDependencies() throws IOException {
        String javaWithUnresolved =
                """
            package com.example;

            public class Test {
                private MissingType field;

                public MissingType method() {
                    return null;
                }
            }
            """;

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, javaWithUnresolved);

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile)));

        assertThat(diagnostics)
                .hasSize(2)
                .allSatisfy(d -> assertThat(d.ruleId()).isEqualTo("type-error"));
    }
}
