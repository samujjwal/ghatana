package com.ghatana.yappc.services.artifact.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the Tree-sitter JNI bridge.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Helpful error messages when the native library is missing</li>
 *   <li>JSON AST deserialization from the JNI bridge (when library is present)</li> // GH-90000
 * </ul>
 */
@DisplayName("TreeSitterParser JNI Bridge Tests")
@Tag("native")
class TreeSitterParserTest {

    private static final boolean NATIVE_AVAILABLE;

    static {
        boolean available = false;
        try {
            System.loadLibrary("tree_sitter_jni");
            available = true;
        } catch (UnsatisfiedLinkError ignored) { // GH-90000
            available = false;
        }
        NATIVE_AVAILABLE = available;
    }

    /**
     * Verify that when the native library is absent the static initializer
     * throws an {@code ExceptionInInitializerError} whose wrapped
     * {@code UnsatisfiedLinkError} contains actionable guidance.
     *
     * <p>This test loads the class in an isolated child ClassLoader so that
     * the failed initialisation does not poison the test JVM for any
     * subsequent native-present tests.
     */
    @Test
    @DisplayName("Should throw helpful error when native library is missing")
    void shouldThrowHelpfulErrorWhenNativeLibraryMissing() throws Exception { // GH-90000
        if (NATIVE_AVAILABLE) { // GH-90000
            return; // Nothing to assert when library is present
        }

        Class<?> isolated = loadClassInIsolatedLoader(TreeSitterParser.class); // GH-90000

        assertThatThrownBy(() -> { // GH-90000
            isolated.getDeclaredConstructor(String.class).newInstance("java");
        })
                .isInstanceOf(InvocationTargetException.class) // GH-90000
                .hasCauseInstanceOf(ExceptionInInitializerError.class) // GH-90000
                .satisfies(ex -> { // GH-90000
                    Throwable root = ex.getCause(); // GH-90000
                    while (root.getCause() != null) { // GH-90000
                        root = root.getCause(); // GH-90000
                    }
                    assertThat(root) // GH-90000
                            .isInstanceOf(UnsatisfiedLinkError.class) // GH-90000
                            .hasMessageContaining("tree_sitter_jni")
                            .hasMessageContaining("build_native.sh")
                            .hasMessageContaining("java.library.path");
                });
    }

    /**
     * Smoke-test the JNI parse round-trip when the native library is present.
     * Skipped automatically when the library has not been built.
     */
    @Test
    @DisplayName("Should parse Java source and return AST map when native library is present")
    void shouldParseJavaSourceWhenNativeLibraryPresent() { // GH-90000
        org.junit.jupiter.api.Assumptions.assumeTrue(NATIVE_AVAILABLE, // GH-90000
                "Native library 'tree_sitter_jni' not available — run './build_native.sh' in src/main/native");

        String source = "public class Hello { public static void main(String[] args) { } }"; // GH-90000

        try (TreeSitterParser parser = new TreeSitterParser("java")) {
            java.util.Map<String, Object> ast = parser.parse(source); // GH-90000

            assertThat(ast).isNotNull(); // GH-90000
            assertThat(ast).containsKey("root");
            assertThat(ast).containsKey("_language");

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> root = (java.util.Map<String, Object>) ast.get("root");
            assertThat(root).containsKey("type");
            assertThat(root).containsKey("children");
            assertThat(root.get("type")).isEqualTo("program");
        }
    }

    /** Loads the given class in a fresh child ClassLoader (no shared state). */ // GH-90000
    private Class<?> loadClassInIsolatedLoader(Class<?> clazz) throws IOException, ClassNotFoundException { // GH-90000
        String resourceName = clazz.getName().replace('.', '/') + ".class"; // GH-90000
        byte[] bytes;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) { // GH-90000
            assertThat(is).as("Class file %s must be on test classpath", resourceName).isNotNull(); // GH-90000
            bytes = readAllBytes(is); // GH-90000
        }

        ClassLoader parent = getClass().getClassLoader(); // GH-90000
        ClassLoader isolated = new ClassLoader(parent) { // GH-90000
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException { // GH-90000
                if (name.equals(clazz.getName())) { // GH-90000
                    return defineClass(name, bytes, 0, bytes.length); // GH-90000
                }
                return super.loadClass(name); // GH-90000
            }
        };

        return isolated.loadClass(clazz.getName()); // GH-90000
    }

    private byte[] readAllBytes(InputStream is) throws IOException { // GH-90000
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); // GH-90000
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) { // GH-90000
            buffer.write(tmp, 0, n); // GH-90000
        }
        return buffer.toByteArray(); // GH-90000
    }
}
