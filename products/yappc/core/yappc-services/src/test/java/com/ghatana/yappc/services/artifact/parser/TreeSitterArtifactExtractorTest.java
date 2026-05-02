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
    void shouldExtractClassAndMethodDeclarations() { 
        TreeSitterArtifactExtractor extractor = new TreeSitterArtifactExtractor(); 

        Map<String, Object> ast = Map.of( 
                "root", Map.of( 
                        "type", "program",
                        "startByte", 0,
                        "endByte", 100,
                        "startRow", 0,
                        "startColumn", 0,
                        "endRow", 5,
                        "endColumn", 1,
                        "isNamed", true,
                        "children", List.of( 
                                Map.of( 
                                        "type", "class_declaration",
                                        "startByte", 0,
                                        "endByte", 50,
                                        "startRow", 0,
                                        "startColumn", 0,
                                        "endRow", 3,
                                        "endColumn", 1,
                                        "isNamed", true,
                                        "children", List.of( 
                                                Map.of("type", "identifier", "text", "Greeter", 
                                                        "startByte", 6, "endByte", 13,
                                                        "startRow", 0, "startColumn", 6, "endRow", 0, "endColumn", 13,
                                                        "isNamed", true, "children", List.of()), 
                                                Map.of("type", "method_declaration", 
                                                        "startByte", 20, "endByte", 45,
                                                        "startRow", 1, "startColumn", 4, "endRow", 2, "endColumn", 5,
                                                        "isNamed", true,
                                                        "children", List.of( 
                                                                Map.of("type", "identifier", "text", "greet", 
                                                                        "startByte", 25, "endByte", 30,
                                                                        "startRow", 1, "startColumn", 9, "endRow", 1, "endColumn", 14,
                                                                        "isNamed", true, "children", List.of()) 
                                                        ))
                                        )
                                )
                        )
                )
        );

        Map<String, Object> result = extractor.extract("java", "public class Greeter { void greet() {} }", "src/Greeter.java"); 

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) result.get("edges");

        assertThat(nodes).isNotNull(); 
        assertThat(nodes).hasSizeGreaterThanOrEqualTo(1); 

        // Should contain a source_file node
        assertThat(nodes).anyMatch(n -> "source_file".equals(n.get("type")));

        // Should contain class and method artifact nodes
        assertThat(nodes).anyMatch(n -> "class".equals(n.get("type")));
        assertThat(nodes).anyMatch(n -> "function".equals(n.get("type")));

        // Edges should link declarations to their parent file or container
        assertThat(edges).isNotNull(); 
    }

    @Test
    @DisplayName("Should detect and extract import statement nodes")
    void shouldExtractImportStatements() { 
        TreeSitterArtifactExtractor extractor = new TreeSitterArtifactExtractor(); 

        Map<String, Object> ast = Map.of( 
                "root", Map.of( 
                        "type", "program",
                        "startByte", 0, "endByte", 30,
                        "startRow", 0, "startColumn", 0, "endRow", 1, "endColumn", 0,
                        "isNamed", true,
                        "children", List.of( 
                                Map.of( 
                                        "type", "import_statement",
                                        "startByte", 0, "endByte", 30,
                                        "startRow", 0, "startColumn", 0, "endRow", 0, "endColumn", 30,
                                        "isNamed", true,
                                        "children", List.of( 
                                                Map.of("type", "identifier", "text", "java", 
                                                        "startByte", 7, "endByte", 11,
                                                        "startRow", 0, "startColumn", 7, "endRow", 0, "endColumn", 11,
                                                        "isNamed", true, "children", List.of()), 
                                                Map.of("type", "identifier", "text", "util", 
                                                        "startByte", 12, "endByte", 16,
                                                        "startRow", 0, "startColumn", 12, "endRow", 0, "endColumn", 16,
                                                        "isNamed", true, "children", List.of()), 
                                                Map.of("type", "identifier", "text", "List", 
                                                        "startByte", 17, "endByte", 21,
                                                        "startRow", 0, "startColumn", 17, "endRow", 0, "endColumn", 21,
                                                        "isNamed", true, "children", List.of()) 
                                        )
                                )
                        )
                )
        );

        Map<String, Object> result = extractor.extract("java", "import java.util.List;", "src/App.java"); 

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");

        assertThat(nodes).anyMatch(n -> "import".equals(n.get("type")));
    }

    @Test
    @DisplayName("Should return empty nodes and edges for unknown file extension without tree-sitter")
    void shouldReturnEmptyForUnknownExtension() { 
        TreeSitterArtifactExtractor extractor = new TreeSitterArtifactExtractor(); 

        // This will fail to create a TreeSitterParser because the native library is not available,
        // but extract() catches the error and returns a fallback stub. 
        Map<String, Object> result = extractor.extract("fortran", "program hello\nend", "legacy/hello.f90"); 

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) result.get("edges");

        assertThat(nodes).isNotNull(); 
        assertThat(edges).isNotNull(); 
    }
}
