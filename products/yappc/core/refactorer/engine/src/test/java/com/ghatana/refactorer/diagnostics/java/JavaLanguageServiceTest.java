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

@ExtendWith(MockitoExtension.class) // GH-90000
/**
 * @doc.type class
 * @doc.purpose Handles java language service test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class JavaLanguageServiceTest extends EventloopTestBase {
    private static final String TEST_JAVA = "Test.java";

    @TempDir private Path tempDir;

    private JavaLanguageService service;
    private PolyfixProjectContext context;
    private Logger logger;
    @Mock private ExecutorService mockExecutor;

    @BeforeEach
    void setUp() { // GH-90000
        service = new JavaLanguageService(eventloop()); // GH-90000
        logger = LogManager.getLogger(JavaLanguageServiceTest.class); // GH-90000
        context =
                new PolyfixProjectContext( // GH-90000
                        tempDir,
                        null, // config
                        List.of(), // languages // GH-90000
                        mockExecutor,
                        logger);
    }

    @Test
    void testSupportsJavaFiles() { // GH-90000
        assertThat(service.supports(Path.of(TEST_JAVA))).isTrue(); // GH-90000
        assertThat(service.supports(Path.of("path/to/" + TEST_JAVA))).isTrue(); // GH-90000
        assertThat(service.supports(Path.of("Test.JAVA [GH-90000]"))).isTrue();
        assertThat(service.supports(Path.of("Test.txt [GH-90000]"))).isFalse();
        assertThat(service.supports(null)).isFalse(); // GH-90000
    }

    @Test
    void testGetSupportedFileExtensions() { // GH-90000
        assertThat(service.getSupportedFileExtensions()).containsExactly(".java [GH-90000]");
    }

    @Test
    void testDiagnoseWithNullFiles() { // GH-90000
        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, null)); // GH-90000
        assertThat(diagnostics).hasSize(1); // GH-90000
        assertThat(diagnostics.get(0).message()).contains("files [GH-90000]");
    }

    @Test
    void testDiagnoseWithEmptyFiles() { // GH-90000
        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of())); // GH-90000
        assertThat(diagnostics).isEmpty(); // GH-90000
    }

    @Test
    void testDiagnoseWithNonJavaFile() throws IOException { // GH-90000
        Path nonJavaFile = tempDir.resolve("test.txt [GH-90000]");
        Files.writeString(nonJavaFile, "This is not a Java file"); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(nonJavaFile))); // GH-90000
        assertThat(diagnostics).isEmpty(); // GH-90000
    }

    @Test
    void testDiagnoseWithValidJavaFile() throws IOException { // GH-90000
        String validJava =
                """
            package com.example;

            public class Test {
                public static void main(String[] args) { // GH-90000
                    System.out.println("Hello, World! [GH-90000]");
                }
            }
            """;

        Path javaFile = tempDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString(javaFile, validJava); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile))); // GH-90000
        assertThat(diagnostics).isEmpty(); // GH-90000
    }

    @Test
    void testDiagnoseWithCompilationError() throws IOException { // GH-90000
        String invalidJava =
                """
            package com.example;

            public class Test {
                public static void main(String[] args) { // GH-90000
                    // Missing semicolon
                    System.out.println("Hello, World! [GH-90000]")
                }
            }
            """;

        Path javaFile = tempDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString(javaFile, invalidJava); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile))); // GH-90000

        assertThat(diagnostics) // GH-90000
                .hasSize(1) // GH-90000
                .allSatisfy( // GH-90000
                        d -> {
                            assertThat(d.tool()).isEqualTo("java-compiler [GH-90000]");
                            assertThat(d.ruleId()).isEqualTo("compilation-error [GH-90000]");
                            assertThat(d.message()).contains("missing ';' [GH-90000]");
                            assertThat(d.file()).endsWith(TEST_JAVA); // GH-90000
                            assertThat(d.line()).isGreaterThan(0); // GH-90000
                            assertThat(d.column()).isGreaterThan(0); // GH-90000
                        });
    }

    @Test
    void testDiagnoseWithMultipleFiles() throws IOException { // GH-90000
        // Valid Java file
        String validJava =
                """
            package com.example;

            public class Test1 {
                public void test() {} // GH-90000
            }
            """;

        // Invalid Java file
        String invalidJava =
                """
            package com.example;

            public class Test2 {
                public void test() { // GH-90000
                    return "missing return type";
                }
            }
            """;

        Path validFile = tempDir.resolve("Test1.java [GH-90000]");
        Path invalidFile = tempDir.resolve("Test2.java [GH-90000]");

        Files.writeString(validFile, validJava); // GH-90000
        Files.writeString(invalidFile, invalidJava); // GH-90000

        List<UnifiedDiagnostic> diagnostics =
                runPromise(() -> service.diagnose(context, List.of(validFile, invalidFile))); // GH-90000

        // Should only report errors from the invalid file
        assertThat(diagnostics) // GH-90000
                .hasSize(1) // GH-90000
                .allSatisfy(d -> assertThat(d.file()).endsWith("Test2.java [GH-90000]"));
    }

    @Test
    void testDiagnoseWithNonExistentFile() { // GH-90000
        Path nonExistentFile = tempDir.resolve("NonExistent.java [GH-90000]");
        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(nonExistentFile))); // GH-90000

        assertThat(diagnostics).isEmpty(); // GH-90000
    }

    @Test
    void testDiagnoseWithNullContext() { // GH-90000
        Path javaFile = tempDir.resolve(TEST_JAVA); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(null, List.of(javaFile))); // GH-90000

        assertThat(diagnostics).hasSize(1); // GH-90000
        assertThat(diagnostics.get(0).message()).contains("context [GH-90000]");
    }

    @Test
    void testDiagnoseWithTypeError() throws IOException { // GH-90000
        String javaWithTypeError =
                """
            package com.example;

            public class Test {
                public void test() { // GH-90000
                    UnknownType variable = "string value";
                }
            }
            """;

        Path javaFile = tempDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString(javaFile, javaWithTypeError); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile))); // GH-90000

        assertThat(diagnostics) // GH-90000
                .hasSize(1) // GH-90000
                .anySatisfy( // GH-90000
                        d -> {
                            assertThat(d.ruleId()).isEqualTo("type-error [GH-90000]");
                            assertThat(d.message()).contains("Cannot resolve type for variable [GH-90000]");
                        });
    }

    @Test
    void testDiagnoseWithMethodReturnTypeError() throws IOException { // GH-90000
        String javaWithReturnTypeError =
                """
            package com.example;

            public class Test {
                public UnknownType test() { // GH-90000
                    return "string value";
                }
            }
            """;

        Path javaFile = tempDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString(javaFile, javaWithReturnTypeError); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile))); // GH-90000

        assertThat(diagnostics) // GH-90000
                .hasSize(1) // GH-90000
                .allSatisfy( // GH-90000
                        d -> {
                            assertThat(d.ruleId()).isEqualTo("type-error [GH-90000]");
                            assertThat(d.message()) // GH-90000
                                    .contains("Cannot resolve type for variable: UnknownType [GH-90000]");
                        });
    }

    @Test
    void testDiagnoseWithValidTypes() throws IOException { // GH-90000
        String validJava =
                """
            package com.example;

            public class Test {
                private String field;

                public String method(String param) { // GH-90000
                    return param;
                }
            }
            """;

        Path javaFile = tempDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString(javaFile, validJava); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile))); // GH-90000

        assertThat(diagnostics).isEmpty(); // GH-90000
    }

    @Test
    void testDiagnoseWithGenerics() throws IOException { // GH-90000
        String javaWithGenerics =
                """
            package com.example;

            import java.util.List;

            public class Test<T> {
                private List<String> strings;
                private T genericField;

                public <U> U genericMethod(U param) { // GH-90000
                    return param;
                }
            }
            """;

        Path javaFile = tempDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString(javaFile, javaWithGenerics); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile))); // GH-90000

        assertThat(diagnostics).isEmpty(); // GH-90000
    }

    @Test
    void testDiagnoseWithInheritance() throws IOException { // GH-90000
        Path parentFile = tempDir.resolve("Parent.java [GH-90000]");
        Path childFile = tempDir.resolve("Child.java [GH-90000]");
        Files.writeString(parentFile, "package com.example;\npublic class Parent {}"); // GH-90000
        Files.writeString( // GH-90000
                childFile,
                "package com.example;\n"
                        + "public class Child extends Parent {\n"
                        + "    public Parent getParent() {\n" // GH-90000
                        + "        return new Parent();\n" // GH-90000
                        + "    }\n"
                        + "}\n");

        List<UnifiedDiagnostic> diagnostics =
                runPromise(() -> service.diagnose(context, List.of(parentFile, childFile))); // GH-90000

        assertThat(diagnostics).isEmpty(); // GH-90000
    }

    @Test
    void testDiagnoseWithInterface() throws IOException { // GH-90000
        String javaWithInterface =
                """
            package com.example;

            public interface TestInterface {
                void method(); // GH-90000
            }

            public class TestImpl implements TestInterface {
                public void method() {} // GH-90000
            }
            """;

        Path interfaceFile = tempDir.resolve("TestInterface.java [GH-90000]");
        Path implFile = tempDir.resolve("TestImpl.java [GH-90000]");
        Files.writeString( // GH-90000
                interfaceFile,
                "package com.example;\npublic interface TestInterface {\n    void method();\n}"); // GH-90000
        Files.writeString( // GH-90000
                implFile,
                "package com.example;\n"
                        + "public class TestImpl implements TestInterface {\n"
                        + "    public void method() {}\n" // GH-90000
                        + "}");

        List<UnifiedDiagnostic> diagnostics =
                runPromise(() -> service.diagnose(context, List.of(interfaceFile, implFile))); // GH-90000

        assertThat(diagnostics).isEmpty(); // GH-90000
    }

    @Test
    void testDiagnoseWithArrays() throws IOException { // GH-90000
        String javaWithArrays =
                """
            package com.example;

            public class Test {
                private int[] numbers;
                private String[][] strings;

                public int[] getNumbers() { // GH-90000
                    return numbers;
                }
            }
            """;

        Path javaFile = tempDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString(javaFile, javaWithArrays); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile))); // GH-90000

        assertThat(diagnostics).isEmpty(); // GH-90000
    }

    @Test
    void testDiagnoseWithUnresolvedDependencies() throws IOException { // GH-90000
        String javaWithUnresolved =
                """
            package com.example;

            public class Test {
                private MissingType field;

                public MissingType method() { // GH-90000
                    return null;
                }
            }
            """;

        Path javaFile = tempDir.resolve(TEST_JAVA); // GH-90000
        Files.writeString(javaFile, javaWithUnresolved); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.diagnose(context, List.of(javaFile))); // GH-90000

        assertThat(diagnostics) // GH-90000
                .hasSize(2) // GH-90000
                .allSatisfy(d -> assertThat(d.ruleId()).isEqualTo("type-error [GH-90000]"));
    }
}
