package com.ghatana.yappc.services.artifact.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Parses Java source files using JavaParser to extract class/method/field metadata and import dependency graph nodes
 * @doc.layer service
 * @doc.pattern Extractor
 */
public final class JavaSourceParser {

    private static final Logger log = LoggerFactory.getLogger(JavaSourceParser.class);
    private final JavaParser javaParser;

    public JavaSourceParser() {
        this.javaParser = new JavaParser();
    }

    /**
     * Parse a Java source file and extract structural metadata.
     *
     * @param filePath absolute path to the .java file
     * @return parsed model map with className, methods, fields, imports, annotations
     */
    public Map<String, Object> parseFile(Path filePath) {
        try {
            ParseResult<CompilationUnit> result = javaParser.parse(filePath);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                log.warn("Failed to parse Java file: {}", filePath);
                return Map.of("error", "Parse failed", "filePath", filePath.toString());
            }

            CompilationUnit cu = result.getResult().get();
            List<Map<String, Object>> classes = new ArrayList<>();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(type -> {
                Map<String, Object> classModel = Map.of(
                        "name", type.getNameAsString(),
                        "isInterface", type.isInterface(),
                        "isAbstract", type.isAbstract(),
                        "isPublic", type.isPublic(),
                        "annotations", type.getAnnotations().stream()
                                .map(AnnotationExpr::getNameAsString)
                                .collect(Collectors.toList()),
                        "methods", type.getMethods().stream()
                                .map(m -> Map.of(
                                        "name", m.getNameAsString(),
                                        "returnType", m.getType().asString(),
                                        "parameters", m.getParameters().stream()
                                                .map(p -> Map.of("name", p.getNameAsString(), "type", p.getType().asString()))
                                                .collect(Collectors.toList()),
                                        "isPublic", m.isPublic()
                                ))
                                .collect(Collectors.toList()),
                        "fields", type.getFields().stream()
                                .map(f -> {
                                    String fieldType = f.getVariables().isEmpty()
                                            ? "unknown"
                                            : f.getVariable(0).getType().asString();
                                    return Map.of(
                                            "names", f.getVariables().stream()
                                                    .map(v -> v.getNameAsString())
                                                    .collect(Collectors.toList()),
                                            "type", fieldType,
                                            "annotations", f.getAnnotations().stream()
                                                    .map(AnnotationExpr::getNameAsString)
                                                    .collect(Collectors.toList())
                                    );
                                })
                                .collect(Collectors.toList())
                );
                classes.add(classModel);
            });

            List<String> imports = cu.getImports().stream()
                    .map(i -> i.getName().asString())
                    .collect(Collectors.toList());

            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            return Map.of(
                    "filePath", filePath.toString(),
                    "packageName", packageName,
                    "imports", imports,
                    "classes", classes,
                    "primaryClass", classes.isEmpty() ? "" : classes.get(0).get("name"),
                    "isParsed", true
            );

        } catch (IOException e) {
            log.error("IOException parsing Java file: {}", filePath, e);
            return Map.of("error", "IOException: " + e.getMessage(), "filePath", filePath.toString());
        }
    }

    /**
     * Parse Java source text from memory.
     *
     * @param sourceCode raw Java source text
     * @return parsed model map with className, methods, fields, imports, annotations
     */
    public Map<String, Object> parseString(String sourceCode) {
        try {
            ParseResult<CompilationUnit> result = javaParser.parse(sourceCode);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                log.warn("Failed to parse Java source from string");
                return Map.of("error", "Parse failed");
            }

            CompilationUnit cu = result.getResult().get();
            List<Map<String, Object>> classes = new ArrayList<>();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(type -> {
                Map<String, Object> classModel = Map.of(
                        "name", type.getNameAsString(),
                        "isInterface", type.isInterface(),
                        "isAbstract", type.isAbstract(),
                        "isPublic", type.isPublic(),
                        "annotations", type.getAnnotations().stream()
                                .map(AnnotationExpr::getNameAsString)
                                .collect(Collectors.toList()),
                        "methods", type.getMethods().stream()
                                .map(m -> Map.of(
                                        "name", m.getNameAsString(),
                                        "returnType", m.getType().asString(),
                                        "parameters", m.getParameters().stream()
                                                .map(p -> Map.of("name", p.getNameAsString(), "type", p.getType().asString()))
                                                .collect(Collectors.toList()),
                                        "isPublic", m.isPublic()
                                ))
                                .collect(Collectors.toList()),
                        "fields", type.getFields().stream()
                                .flatMap(f -> f.getVariables().stream()
                                        .map(v -> Map.of("name", v.getNameAsString(),
                                                "type", v.getType().asString())))
                                .collect(Collectors.toList())
                );
                classes.add(classModel);
            });

            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getName().asString())
                    .orElse("");

            List<String> imports = cu.findAll(ImportDeclaration.class).stream()
                    .map(i -> i.getName().asString())
                    .collect(Collectors.toList());

            return Map.of(
                    "packageName", packageName,
                    "imports", imports,
                    "classes", classes,
                    "primaryClass", classes.isEmpty() ? "" : classes.get(0).get("name"),
                    "isParsed", true
            );

        } catch (Exception e) {
            log.error("Error parsing Java source string", e);
            return Map.of("error", "Parse failed: " + e.getMessage());
        }
    }
}
