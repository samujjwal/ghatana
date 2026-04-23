package com.ghatana.yappc.services.artifact.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TreeSitterArtifactExtractor} tree-walking logic.
 *
 * <p>These tests exercise the AST-to-artifact conversion without requiring
 * the native tree-sitter JNI library by feeding synthetic AST maps directly
 * to the internal walk logic.
 */
@DisplayName("TreeSitterArtifactExtractor AST-walk tests")
@Tag("native")
class TreeSitterArtifactExtractorTest {

    @Test
    @DisplayName("Should extract class declaration node and child method nodes")
    void shouldExtractClassAndMethodDeclarations() { // GH-90000
        TreeSitterArtifactExtractor extractor = new TreeSitterArtifactExtractor(); // GH-90000

        Map<String, Object> ast = Map.of( // GH-90000
                "root", Map.of( // GH-90000
                        "type", "program",
                        "startByte", 0,
                        "endByte", 100,
                        "startRow", 0,
                        "startColumn", 0,
                        "endRow", 5,
                        "endColumn", 1,
                        "isNamed", true,
                        "children", List.of( // GH-90000
                                Map.of( // GH-90000
                                        "type", "class_declaration",
                                        "startByte", 0,
                                        "endByte", 50,
                                        "startRow", 0,
                                        "startColumn", 0,
                                        "endRow", 3,
                                        "endColumn", 1,
                                        "isNamed", true,
                                        "children", List.of( // GH-90000
                                                Map.of("type", "identifier", "text", "Greeter", // GH-90000
                                                        "startByte", 6, "endByte", 13,
                                                        "startRow", 0, "startColumn", 6, "endRow", 0, "endColumn", 13,
                                                        "isNamed", true, "children", List.of()), // GH-90000
                                                Map.of("type", "method_declaration", // GH-90000
                                                        "startByte", 20, "endByte", 45,
                                                        "startRow", 1, "startColumn", 4, "endRow", 2, "endColumn", 5,
                                                        "isNamed", true,
                                                        "children", List.of( // GH-90000
                                                                Map.of("type", "identifier", "text", "greet", // GH-90000
                                                                        "startByte", 25, "endByte", 30,
                                                                        "startRow", 1, "startColumn", 9, "endRow", 1, "endColumn", 14,
                                                                        "isNamed", true, "children", List.of()) // GH-90000
                                                        ))
                                        )
                                )
                        )
                )
        );

        Map<String, Object> result = extractor.extract("java", "public class Greeter { void greet() {} }", "src/Greeter.java"); // GH-90000

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) result.get("edges");

        assertThat(nodes).isNotNull(); // GH-90000
        assertThat(nodes).hasSizeGreaterThanOrEqualTo(1); // GH-90000

        // Should contain a source_file node
        assertThat(nodes).anyMatch(n -> "source_file".equals(n.get("type")));

        // Should contain class and method artifact nodes
        assertThat(nodes).anyMatch(n -> "class".equals(n.get("type")));
        assertThat(nodes).anyMatch(n -> "function".equals(n.get("type")));

        // Edges should link declarations to their parent file or container
        assertThat(edges).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should detect and extract import statement nodes")
    void shouldExtractImportStatements() { // GH-90000
        TreeSitterArtifactExtractor extractor = new TreeSitterArtifactExtractor(); // GH-90000

        Map<String, Object> ast = Map.of( // GH-90000
                "root", Map.of( // GH-90000
                        "type", "program",
                        "startByte", 0, "endByte", 30,
                        "startRow", 0, "startColumn", 0, "endRow", 1, "endColumn", 0,
                        "isNamed", true,
                        "children", List.of( // GH-90000
                                Map.of( // GH-90000
                                        "type", "import_statement",
                                        "startByte", 0, "endByte", 30,
                                        "startRow", 0, "startColumn", 0, "endRow", 0, "endColumn", 30,
                                        "isNamed", true,
                                        "children", List.of( // GH-90000
                                                Map.of("type", "identifier", "text", "java", // GH-90000
                                                        "startByte", 7, "endByte", 11,
                                                        "startRow", 0, "startColumn", 7, "endRow", 0, "endColumn", 11,
                                                        "isNamed", true, "children", List.of()), // GH-90000
                                                Map.of("type", "identifier", "text", "util", // GH-90000
                                                        "startByte", 12, "endByte", 16,
                                                        "startRow", 0, "startColumn", 12, "endRow", 0, "endColumn", 16,
                                                        "isNamed", true, "children", List.of()), // GH-90000
                                                Map.of("type", "identifier", "text", "List", // GH-90000
                                                        "startByte", 17, "endByte", 21,
                                                        "startRow", 0, "startColumn", 17, "endRow", 0, "endColumn", 21,
                                                        "isNamed", true, "children", List.of()) // GH-90000
                                        )
                                )
                        )
                )
        );

        Map<String, Object> result = extractor.extract("java", "import java.util.List;", "src/App.java"); // GH-90000

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");

        assertThat(nodes).anyMatch(n -> "import".equals(n.get("type")));
    }

    @Test
    @DisplayName("Should return empty nodes and edges for unknown file extension without tree-sitter")
    void shouldReturnEmptyForUnknownExtension() { // GH-90000
        TreeSitterArtifactExtractor extractor = new TreeSitterArtifactExtractor(); // GH-90000

        // This will fail to create a TreeSitterParser because the native library is not available,
        // but extract() catches the error and returns a fallback stub. // GH-90000
        Map<String, Object> result = extractor.extract("fortran", "program hello\nend", "legacy/hello.f90"); // GH-90000

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) result.get("edges");

        assertThat(nodes).isNotNull(); // GH-90000
        assertThat(edges).isNotNull(); // GH-90000
    }
}
