package com.ghatana.yappc.services.artifact.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * JNI bridge to the Tree-sitter incremental parsing library.
 *
 * <p>Loads a platform-specific shared library ({@code libtree_sitter_jni})
 * that wraps the tree-sitter C API using JNI. Each parser instance is bound
 * to a single language grammar loaded dynamically via {@code dlopen}.
 *
 * <p>Requires the native library and language-specific tree-sitter grammar
 * libraries (e.g. {@code libtree-sitter-java.so}) to be available on the
 * system library path or in {@code src/main/native/build/}.
 *
 * @doc.type class
 * @doc.purpose JNI bridge for high-performance incremental language parsing via Tree-sitter
 * @doc.layer service
 * @doc.pattern Adapter
 */
public final class TreeSitterParser implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterParser.class);
    private static final String LIBRARY_NAME = "tree_sitter_jni";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final long parserHandle;
    private final String language;
    private volatile boolean closed = false;

    static {
        loadNativeLibrary();
    }

    private static void loadNativeLibrary() {
        try {
            System.loadLibrary(LIBRARY_NAME);
            log.info("Loaded native tree-sitter JNI library from system path");
        } catch (UnsatisfiedLinkError systemErr) {
            log.debug("System loadLibrary failed: {}", systemErr.getMessage());
            loadFromCustomPaths();
        }
    }

    private static void loadFromCustomPaths() {
        String libSuffix = System.mapLibraryName("").replaceAll("^.*\\.", ".");
        String[] candidates = {
            "native/build/lib" + LIBRARY_NAME + libSuffix,
            "src/main/native/build/lib" + LIBRARY_NAME + libSuffix,
            System.getProperty("user.dir") + "/native/build/lib" + LIBRARY_NAME + libSuffix,
            System.getProperty("user.dir") + "/src/main/native/build/lib" + LIBRARY_NAME + libSuffix,
        };

        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    System.load(f.getAbsolutePath());
                    log.info("Loaded tree-sitter JNI from: {}", path);
                    return;
                } catch (UnsatisfiedLinkError e) {
                    log.warn("Failed to load {}: {}", path, e.getMessage());
                }
            }
        }

        throw new UnsatisfiedLinkError(
                "Could not load native tree-sitter JNI library '" + LIBRARY_NAME + "'. " +
                "Please build the native library (cd src/main/native && ./build_native.sh) " +
                "or ensure it is on the java.library.path."
        );
    }

    /**
     * Create a Tree-sitter parser for the given language.
     *
     * @param language tree-sitter language name, e.g. "java", "javascript", "python", "go", "rust"
     * @throws RuntimeException if the language grammar library cannot be loaded
     */
    public TreeSitterParser(String language) {
        this.language = language;
        this.parserHandle = nativeCreateParser(language);
        if (this.parserHandle == 0L) {
            throw new RuntimeException(
                    "Failed to create tree-sitter parser for language '" + language + "'. " +
                    "Ensure libtree-sitter-" + language + " is installed."
            );
        }
        log.info("Created tree-sitter parser for language '{}' (handle={})", language, parserHandle);
    }

    /**
     * Parse source code and return the AST as a JSON-serialised map.
     *
     * @param sourceCode the source text to parse
     * @return map representation of the tree-sitter CST/AST
     * @throws IllegalStateException if the parser has been closed
     */
    public Map<String, Object> parse(String sourceCode) {
        ensureOpen();
        String json = nativeParseString(parserHandle, sourceCode);
        try {
            Map<String, Object> ast = MAPPER.readValue(json, MAP_TYPE);
            ast.put("_language", language);
            return ast;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize tree-sitter AST JSON", e);
        }
    }

    /**
     * Convenience static parse method that creates, uses, and closes a parser.
     */
    public static Map<String, Object> parse(String language, String sourceCode) {
        try (TreeSitterParser parser = new TreeSitterParser(language)) {
            return parser.parse(sourceCode);
        }
    }

    /**
     * Return a list of languages for which grammar libraries can be discovered.
     */
    public static List<String> getSupportedLanguages() {
        String[] langs = nativeGetSupportedLanguages();
        return List.of(langs);
    }

    @Override
    public void close() {
        if (!closed && parserHandle != 0L) {
            closed = true;
            try {
                nativeDestroyParser(parserHandle);
                log.debug("Destroyed tree-sitter parser for language '{}'", language);
            } catch (Exception e) {
                log.warn("Error destroying tree-sitter parser", e);
            }
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("TreeSitterParser for language '" + language + "' has been closed");
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Native methods                                                    */
    /* ------------------------------------------------------------------ */

    private native long nativeCreateParser(String language);

    private native String nativeParseString(long parserHandle, String sourceCode);

    private native void nativeDestroyParser(long parserHandle);

    public static native String[] nativeGetSupportedLanguages();
}
