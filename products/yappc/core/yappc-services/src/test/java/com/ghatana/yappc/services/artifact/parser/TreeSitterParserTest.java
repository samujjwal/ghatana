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
 *   <li>JSON AST deserialization from the JNI bridge (when library is present)</li>
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
        } catch (UnsatisfiedLinkError ignored) {
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
    void shouldThrowHelpfulErrorWhenNativeLibraryMissing() throws Exception {
        if (NATIVE_AVAILABLE) {
            return; // Nothing to assert when library is present
        }

        Class<?> isolated = loadClassInIsolatedLoader(TreeSitterParser.class);

        assertThatThrownBy(() -> {
            isolated.getDeclaredConstructor(String.class).newInstance("java");
        })
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(ExceptionInInitializerError.class)
                .satisfies(ex -> {
                    Throwable root = ex.getCause();
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertThat(root)
                            .isInstanceOf(UnsatisfiedLinkError.class)
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
    void shouldParseJavaSourceWhenNativeLibraryPresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(NATIVE_AVAILABLE,
                "Native library 'tree_sitter_jni' not available — run './build_native.sh' in src/main/native");

        String source = "public class Hello { public static void main(String[] args) { } }";

        try (TreeSitterParser parser = new TreeSitterParser("java")) {
            java.util.Map<String, Object> ast = parser.parse(source);

            assertThat(ast).isNotNull();
            assertThat(ast).containsKey("root");
            assertThat(ast).containsKey("_language");

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> root = (java.util.Map<String, Object>) ast.get("root");
            assertThat(root).containsKey("type");
            assertThat(root).containsKey("children");
            assertThat(root.get("type")).isEqualTo("program");
        }
    }

    /** Loads the given class in a fresh child ClassLoader (no shared state). */
    private Class<?> loadClassInIsolatedLoader(Class<?> clazz) throws IOException, ClassNotFoundException {
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(is).as("Class file %s must be on test classpath", resourceName).isNotNull();
            bytes = readAllBytes(is);
        }

        ClassLoader parent = getClass().getClassLoader();
        ClassLoader isolated = new ClassLoader(parent) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals(clazz.getName())) {
                    return defineClass(name, bytes, 0, bytes.length);
                }
                return super.loadClass(name);
            }
        };

        return isolated.loadClass(clazz.getName());
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) {
            buffer.write(tmp, 0, n);
        }
        return buffer.toByteArray();
    }
}
