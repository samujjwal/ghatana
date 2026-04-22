package com.ghatana.yappc.services.artifact.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * High-level extractor that uses the Tree-sitter JNI bridge to parse source
 * code in any supported language and emit artifact graph nodes and edges.
 *
 * <p>Walks the concrete syntax tree produced by {@link TreeSitterParser}
 * and identifies declarations, definitions, imports, and structural
 * relationships suitable for ingestion into the YAPPC artifact graph.
 *
 * @doc.type class
 * @doc.purpose Extract artifact graph elements from arbitrary language source via Tree-sitter
 * @doc.layer service
 * @doc.pattern Strategy
 */
public final class TreeSitterArtifactExtractor {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterArtifactExtractor.class);

    /**
     * Parse the given source file and extract artifact nodes + edges.
     *
     * @param language   tree-sitter language name, e.g. "java", "javascript", "python", "go", "rust"
     * @param sourceCode full source text
     * @param filePath   logical file path (used as namespace / provenance)
     * @return map with keys {@code "nodes"} and {@code "edges"}
     */
    public Map<String, Object> extract(String language, String sourceCode, String filePath) {
        try (TreeSitterParser parser = new TreeSitterParser(language)) {
            Map<String, Object> ast = parser.parse(sourceCode);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) ast.get("root");
            return walk(root, filePath, language);
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError e) {
            log.warn("Tree-sitter JNI not available for language '{}' — returning fallback stub", language, e);
            return Map.of(
                    "nodes", List.of(Map.of(
                            "id", "ts-fallback://" + filePath,
                            "type", "source_file",
                            "name", filePath,
                            "filePath", filePath,
                            "language", language,
                            "parseError", e.getClass().getSimpleName() + ": " + e.getMessage()
                    )),
                    "edges", List.of()
            );
        }
    }

    /**
     * Static convenience entry point — create parser, parse, extract, close.
     */
    public static Map<String, Object> extractArtifacts(String language, String sourceCode, String filePath) {
        return new TreeSitterArtifactExtractor().extract(language, sourceCode, filePath);
    }

    /* ------------------------------------------------------------------ */
    /*  Tree walking                                                       */
    /* ------------------------------------------------------------------ */

    private Map<String, Object> walk(Map<String, Object> root, String filePath, String language) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        String fileNodeId = "file://" + filePath;
        nodes.add(createNode(fileNodeId, "source_file", filePath, filePath, language, root));

        walkNode(root, filePath, language, nodes, edges, fileNodeId);

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    private void walkNode(Map<String, Object> node, String filePath, String language,
                          List<Map<String, Object>> nodes, List<Map<String, Object>> edges,
                          String parentId) {
        String type = (String) node.get("type");
        if (type == null) return;

        String nodeId = null;

        // --- Declaration / definition nodes --------------------------------
        if (isDeclarationNode(type)) {
            nodeId = idForNode(node, filePath);
            String artifactType = mapToArtifactType(type);
            String name = extractName(node);
            nodes.add(createNode(nodeId, artifactType, name, filePath, language, node));
            edges.add(createEdge(parentId, nodeId, "CONTAINS"));
        }

        // --- Import / dependency nodes --------------------------------------
        if (isImportNode(type)) {
            nodeId = idForNode(node, filePath);
            String target = extractImportTarget(node);
            nodes.add(createNode(nodeId, "import", target, filePath, language, node));
            edges.add(createEdge(parentId, nodeId, "IMPORTS"));
            // Also create a synthetic edge to the imported symbol if we can resolve it
            if (target != null && !target.isEmpty()) {
                edges.add(createEdge(nodeId, "symbol://" + target, "REFERENCES"));
            }
        }

        // --- Recurse into children ------------------------------------------
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
        if (children != null) {
            String effectiveParent = (nodeId != null) ? nodeId : parentId;
            for (Map<String, Object> child : children) {
                walkNode(child, filePath, language, nodes, edges, effectiveParent);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Node classification                                                */
    /* ------------------------------------------------------------------ */

    private boolean isDeclarationNode(String type) {
        return type.endsWith("_declaration")
                || type.endsWith("_definition")
                || type.endsWith("_specifier")
                || type.equals("class_declaration")
                || type.equals("interface_declaration")
                || type.equals("enum_declaration")
                || type.equals("function_declaration")
                || type.equals("method_definition")
                || type.equals("function_item")
                || type.equals("struct_item")
                || type.equals("impl_item")
                || type.equals("type_declaration")
                || type.equals("lexical_declaration")
                || type.equals("variable_declaration");
    }

    private boolean isImportNode(String type) {
        return type.contains("import")
                || type.contains("use_declaration")
                || type.contains("include")
                || type.contains("require")
                || type.equals("import_statement")
                || type.equals("import_declaration")
                || type.equals("using_directive")
                || type.equals("preproc_include");
    }

    private String mapToArtifactType(String treeSitterType) {
        if (treeSitterType.contains("class")) return "class";
        if (treeSitterType.contains("interface")) return "interface";
        if (treeSitterType.contains("enum")) return "enum";
        if (treeSitterType.contains("function") || treeSitterType.contains("method")) return "function";
        if (treeSitterType.contains("struct")) return "struct";
        if (treeSitterType.contains("impl")) return "impl";
        if (treeSitterType.contains("variable") || treeSitterType.contains("field")) return "variable";
        if (treeSitterType.contains("type")) return "type";
        return "declaration";
    }

    /* ------------------------------------------------------------------ */
    /*  Name / identifier extraction                                       */
    /* ------------------------------------------------------------------ */

    private String extractName(Map<String, Object> node) {
        String type = (String) node.get("type");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
        if (children == null) {
            return fallbackName(node);
        }

        // Prefer explicit identifier / type_identifier children
        for (Map<String, Object> child : children) {
            String childType = (String) child.get("type");
            if ("identifier".equals(childType)
                    || "type_identifier".equals(childType)
                    || "property_identifier".equals(childType)
                    || "scoped_identifier".equals(childType)) {
                String text = (String) child.get("text");
                if (text != null && !text.isEmpty()) {
                    return text;
                }
            }
        }

        // Java: class_declaration -> identifier, method_declaration -> identifier
        // Try first named child that is not a keyword
        for (Map<String, Object> child : children) {
            Boolean isNamed = (Boolean) child.get("isNamed");
            String childType = (String) child.get("type");
            if (Boolean.TRUE.equals(isNamed)
                    && childType != null
                    && !childType.endsWith("_modifier")
                    && !childType.contains("annotation")) {
                String text = (String) child.get("text");
                if (text != null && !text.isEmpty() && !text.matches("\\s+")) {
                    return text;
                }
            }
        }

        return fallbackName(node);
    }

    private String extractImportTarget(Map<String, Object> node) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
        if (children == null) return null;

        // Gather all text fragments from string / scoped_identifier children
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> child : children) {
            String childType = (String) child.get("type");
            if ("string_fragment".equals(childType)
                    || "identifier".equals(childType)
                    || "scoped_identifier".equals(childType)
                    || "type_identifier".equals(childType)) {
                String text = (String) child.get("text");
                if (text != null) {
                    if (sb.length() > 0) sb.append(".");
                    sb.append(text);
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String fallbackName(Map<String, Object> node) {
        String text = (String) node.get("text");
        if (text != null && !text.isEmpty()) {
            // Truncate long snippets
            return text.length() > 80 ? text.substring(0, 80) + "…" : text;
        }
        return (String) node.get("type");
    }

    /* ------------------------------------------------------------------ */
    /*  Id / node / edge factories                                         */
    /* ------------------------------------------------------------------ */

    private String idForNode(Map<String, Object> node, String filePath) {
        Object startByte = node.get("startByte");
        Object endByte = node.get("endByte");
        String type = (String) node.get("type");
        return "ts://" + filePath + "/" + type + "@" + startByte + "-" + endByte;
    }

    private Map<String, Object> createNode(String id, String type, String name,
                                             String filePath, String language,
                                             Map<String, Object> sourceNode) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("type", type);
        n.put("name", name);
        n.put("filePath", filePath);
        n.put("language", language);
        n.put("startByte", sourceNode.get("startByte"));
        n.put("endByte", sourceNode.get("endByte"));
        n.put("startRow", sourceNode.get("startRow"));
        n.put("startColumn", sourceNode.get("startColumn"));
        n.put("endRow", sourceNode.get("endRow"));
        n.put("endColumn", sourceNode.get("endColumn"));
        n.put("isNamed", sourceNode.get("isNamed"));
        n.put("isMissing", sourceNode.get("isMissing"));
        String text = (String) sourceNode.get("text");
        if (text != null && text.length() <= 200) {
            n.put("text", text);
        }
        return n;
    }

    private Map<String, Object> createEdge(String sourceId, String targetId, String relType) {
        Map<String, Object> e = new HashMap<>();
        e.put("sourceNodeId", sourceId);
        e.put("targetNodeId", targetId);
        e.put("relationshipType", relType);
        return e;
    }
}
