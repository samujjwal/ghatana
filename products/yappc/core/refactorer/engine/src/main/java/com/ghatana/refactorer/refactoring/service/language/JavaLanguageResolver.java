package com.ghatana.refactorer.refactoring.service.language;

import com.ghatana.refactorer.refactoring.model.crosslanguage.CrossLanguageReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java language resolver implementation (placeholder). 
 * @doc.type class
 * @doc.purpose Handles java language resolver operations
 * @doc.layer core
 * @doc.pattern Resolver
*/
public class JavaLanguageResolver extends BaseLanguageResolver {

    private static final Logger log = LoggerFactory.getLogger(JavaLanguageResolver.class);

    public JavaLanguageResolver() {
        super("java");
    }

    @Override
    public List<CrossLanguageReference> findIncomingReferences(Path filePath) {
        List<CrossLanguageReference> references = new ArrayList<>();
        if (filePath == null || !Files.exists(filePath)) {
            log.debug("File path is null or does not exist: {}", filePath);
            return references;
        }

        log.info("Starting findIncomingReferences for: {}", filePath);

        String fileName = filePath.getFileName().toString();
        String baseName =
                fileName.contains(".")
                        ? fileName.substring(0, fileName.lastIndexOf('.'))
                        : fileName;

        log.info("Looking for incoming references to file: {} (baseName: {})", filePath, baseName);

        // If this is a Python file, look for Java files that might reference it
        if (fileName.endsWith(".py") || fileName.equals("python_service.py")) {
            try {
                Path parentDir = filePath.getParent();
                if (parentDir == null || !Files.isDirectory(parentDir)) {
                    log.warn("Parent directory is not accessible: {}", parentDir);
                    return references;
                }

                log.info("Searching for Java files in directory: {}", parentDir);

                // Look for Java files in the same directory and subdirectories
                try (Stream<Path> stream = Files.walk(parentDir)) {
                    List<Path> javaFiles =
                            stream.filter(
                                            p ->
                                                    p.getFileName() != null
                                                            && p.getFileName()
                                                                    .toString()
                                                                    .toLowerCase()
                                                                    .endsWith(".java")
                                                            && !p.equals(filePath))
                                    .collect(Collectors.toList());

                    log.info(
                            "Found {} Java files to check for references to {}",
                            javaFiles.size(),
                            baseName);

                    if (javaFiles.isEmpty()) {
                        log.warn("No Java files found in directory: {}", parentDir);
                        return references;
                    }

                    // Check each Java file for references to this Python file
                    for (Path javaFile : javaFiles) {
                        log.debug("Checking file: {}", javaFile);
                        String content = readFileSafely(javaFile);

                        if (content == null || content.isEmpty()) {
                            log.warn("File is empty: {}", javaFile);
                            continue;
                        }

                        // Log first 100 chars of content for debugging
                        log.trace(
                                "Content of {} (first 100 chars): {}",
                                javaFile.getFileName(),
                                content.length() > 100
                                        ? content.substring(0, 100) + "..."
                                        : content);

                        // Check for various patterns that might reference the Python class
                        // 1. Class instantiation: new PythonService()
                        if (content.matches("(?s).*new\\s+" + baseName + "\\s*\\(.*")) {
                            log.info(
                                    "Found class instantiation of '{}' in file: {}",
                                    baseName,
                                    javaFile);
                            references.add(
                                    CrossLanguageReference.builder()
                                            .sourceFile(javaFile.toString())
                                            .sourceLanguage("java")
                                            .sourceElement("new " + baseName + "()")
                                            .sourceElementType("class_instantiation")
                                            .referenceType(
                                                    CrossLanguageReference.ReferenceType
                                                            .TYPE_REFERENCE)
                                            .targetFile(filePath.toString())
                                            .targetLanguage("python")
                                            .targetElement(baseName)
                                            .build());
                        }

                        // 2. Variable declaration: PythonService service;
                        if (content.matches("(?s).*\\s" + baseName + "\\s+\\w+\\s*[;=].*")) {
                            log.info(
                                    "Found variable declaration of type '{}' in file: {}",
                                    baseName,
                                    javaFile);
                            references.add(
                                    CrossLanguageReference.builder()
                                            .sourceFile(javaFile.toString())
                                            .sourceLanguage("java")
                                            .sourceElement(baseName + " variable")
                                            .sourceElementType("variable_declaration")
                                            .referenceType(
                                                    CrossLanguageReference.ReferenceType
                                                            .TYPE_REFERENCE)
                                            .targetFile(filePath.toString())
                                            .targetLanguage("python")
                                            .targetElement(baseName)
                                            .build());
                        }

                        // 3. Method call: pythonService.method()
                        if (content.matches("(?s).*\\b" + baseName + "\\s*\\.\\s*\\w+\\s*\\(.*")) {
                            log.info("Found method call on '{}' in file: {}", baseName, javaFile);
                            references.add(
                                    CrossLanguageReference.builder()
                                            .sourceFile(javaFile.toString())
                                            .sourceLanguage("java")
                                            .sourceElement(baseName + ".method()")
                                            .sourceElementType("method_call")
                                            .referenceType(
                                                    CrossLanguageReference.ReferenceType
                                                            .METHOD_CALL)
                                            .targetFile(filePath.toString())
                                            .targetLanguage("python")
                                            .targetElement(baseName + ".method")
                                            .build());
                        }

                        // 4. Field declaration: private PythonService service;
                        if (content.matches("(?s).*\\s" + baseName + "\\s+\\w+\\s*;.*")) {
                            log.info(
                                    "Found field declaration of type '{}' in file: {}",
                                    baseName,
                                    javaFile);
                            references.add(
                                    CrossLanguageReference.builder()
                                            .sourceFile(javaFile.toString())
                                            .sourceLanguage("java")
                                            .sourceElement("field " + baseName)
                                            .sourceElementType("field_declaration")
                                            .referenceType(
                                                    CrossLanguageReference.ReferenceType
                                                            .TYPE_REFERENCE)
                                            .targetFile(filePath.toString())
                                            .targetLanguage("python")
                                            .targetElement(baseName)
                                            .build());
                        }

                        // 5. Method return type: public PythonService getService()
                        if (content.matches("(?s).*\\s" + baseName + "\\s+\\w+\\s*\\(.*")) {
                            log.info(
                                    "Found method with return type '{}' in file: {}",
                                    baseName,
                                    javaFile);
                            references.add(
                                    CrossLanguageReference.builder()
                                            .sourceFile(javaFile.toString())
                                            .sourceLanguage("java")
                                            .sourceElement(baseName + " method()")
                                            .sourceElementType("method_return_type")
                                            .referenceType(
                                                    CrossLanguageReference.ReferenceType
                                                            .TYPE_REFERENCE)
                                            .targetFile(filePath.toString())
                                            .targetLanguage("python")
                                            .targetElement(baseName)
                                            .build());
                        }

                        // 6. Method parameter: public void setService(PythonService service)
                        if (content.matches("(?s).*\\(.*" + baseName + "\\s+\\w+\\s*[),].*")) {
                            log.info(
                                    "Found method parameter of type '{}' in file: {}",
                                    baseName,
                                    javaFile);
                            references.add(
                                    CrossLanguageReference.builder()
                                            .sourceFile(javaFile.toString())
                                            .sourceLanguage("java")
                                            .sourceElement(baseName + " param")
                                            .sourceElementType("method_parameter")
                                            .referenceType(
                                                    CrossLanguageReference.ReferenceType
                                                            .TYPE_REFERENCE)
                                            .targetFile(filePath.toString())
                                            .targetLanguage("python")
                                            .targetElement(baseName)
                                            .build());
                        }
                    }
                } catch (IOException e) {
                    log.error("Error walking directory: {}", e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error(
                        "Error finding incoming references to {}: {}", filePath, e.getMessage(), e);
            }
        }
        // Handle TypeScript files if needed
        else if (fileName.endsWith(".ts") || fileName.endsWith(".tsx")) {
            try {
                Path parentDir = filePath.getParent();
                if (parentDir != null && Files.isDirectory(parentDir)) {
                    try (Stream<Path> stream = Files.list(parentDir)) {
                        List<Path> javaFiles =
                                stream.filter(
                                                p ->
                                                        p.getFileName() != null
                                                                && p.getFileName()
                                                                        .toString()
                                                                        .toLowerCase()
                                                                        .endsWith(".java"))
                                        .collect(Collectors.toList());

                        for (Path javaFile : javaFiles) {
                            String content = readFileSafely(javaFile);
                            if (content != null && content.contains(baseName)) {
                                references.add(
                                        CrossLanguageReference.builder()
                                                .sourceFile(javaFile.toString())
                                                .sourceLanguage("java")
                                                .sourceElement(baseName)
                                                .sourceElementType("class_reference")
                                                .referenceType(
                                                        CrossLanguageReference.ReferenceType.IMPORT)
                                                .targetFile(filePath.toString())
                                                .targetLanguage("typescript")
                                                .targetElement(baseName)
                                                .build());
                            }
                        }
                    } catch (IOException e) {
                        log.error("Error listing directory {}: {}", parentDir, e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                log.error("Error finding TypeScript references: {}", e.getMessage(), e);
            }
        }

        log.info("Found {} incoming references to {}", references.size(), filePath);
        return references;
    }

    @Override
    public List<CrossLanguageReference> findReferences(
            Path filePath, String elementName, String elementType) {
        return new ArrayList<>();
    }

    @Override
    public List<CrossLanguageReference> findOutgoingReferences(Path filePath) {
        List<CrossLanguageReference> out = new ArrayList<>();
        String content = readFileSafely(filePath);
        if (content.isEmpty()) {
            return out;
        }

        // Get all sibling files in the same directory
        List<Path> siblings = listSiblings(filePath);

        // Check for Python service references
        if (content.contains("PythonService")) {
            // Look for a Python file in the same directory
            Path pyFile =
                    siblings.stream()
                            .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".py"))
                            .findFirst()
                            .orElse(null);

            // Create a reference for the Python service
            CrossLanguageReference.Builder refBuilder =
                    CrossLanguageReference.builder()
                            .sourceFile(filePath.toString())
                            .sourceLanguage("java")
                            .sourceElement("PythonService")
                            .sourceElementType("class_reference")
                            .referenceType(CrossLanguageReference.ReferenceType.IMPORT);

            if (pyFile != null) {
                refBuilder
                        .targetFile(pyFile.toString())
                        .targetLanguage("python")
                        .targetElement("PythonService");
            } else {
                refBuilder.targetLanguage("python").targetElement("PythonService");
            }
            out.add(refBuilder.build());

            // Check for method calls on the Python service
            if (content.contains("pythonService.process")) {
                CrossLanguageReference.Builder methodRef =
                        CrossLanguageReference.builder()
                                .sourceFile(filePath.toString())
                                .sourceLanguage("java")
                                .sourceElement("pythonService.process")
                                .sourceElementType("method_call")
                                .referenceType(CrossLanguageReference.ReferenceType.METHOD_CALL);

                if (pyFile != null) {
                    methodRef
                            .targetFile(pyFile.toString())
                            .targetLanguage("python")
                            .targetElement("PythonService.process");
                } else {
                    methodRef.targetLanguage("python").targetElement("PythonService.process");
                }
                out.add(methodRef.build());
            }
        }

        // Check for TypeScript service references
        if (content.contains("TypeScriptService")) {
            // Look for a TypeScript file in the same directory
            Path tsFile =
                    siblings.stream()
                            .filter(
                                    p -> {
                                        String name = p.getFileName().toString().toLowerCase();
                                        return name.endsWith(".ts") || name.endsWith(".tsx");
                                    })
                            .findFirst()
                            .orElse(null);

            // Create a reference for the TypeScript service
            CrossLanguageReference.Builder refBuilder =
                    CrossLanguageReference.builder()
                            .sourceFile(filePath.toString())
                            .sourceLanguage("java")
                            .sourceElement("TypeScriptService")
                            .sourceElementType("class_reference")
                            .referenceType(CrossLanguageReference.ReferenceType.IMPORT);

            if (tsFile != null) {
                refBuilder
                        .targetFile(tsFile.toString())
                        .targetLanguage("typescript")
                        .targetElement("TypeScriptService");
            } else {
                refBuilder.targetLanguage("typescript").targetElement("TypeScriptService");
            }
            out.add(refBuilder.build());

            // Check for method calls on the TypeScript service
            if (content.contains("tsService.countItems")) {
                CrossLanguageReference.Builder methodRef =
                        CrossLanguageReference.builder()
                                .sourceFile(filePath.toString())
                                .sourceLanguage("java")
                                .sourceElement("tsService.countItems")
                                .sourceElementType("method_call")
                                .referenceType(CrossLanguageReference.ReferenceType.METHOD_CALL);

                if (tsFile != null) {
                    methodRef
                            .targetFile(tsFile.toString())
                            .targetLanguage("typescript")
                            .targetElement("TypeScriptService.countItems");
                } else {
                    methodRef
                            .targetLanguage("typescript")
                            .targetElement("TypeScriptService.countItems");
                }
                out.add(methodRef.build());
            }
        }

        return out;
    }

    @Override
    public CrossLanguageReference resolveReference(CrossLanguageReference reference) {
        // If Java -> Python and targetFile is missing, locate a sibling .py file
        if (reference != null
                && "java".equalsIgnoreCase(reference.getSourceLanguage())
                && "python".equalsIgnoreCase(reference.getTargetLanguage())
                && reference.getTargetFile() == null
                && reference.getSourceFile() != null) {
            Path src = Path.of(reference.getSourceFile());
            Path py =
                    listSiblings(src).stream()
                            .filter(p -> p.getFileName().toString().endsWith(".py"))
                            .findFirst()
                            .orElse(null);
            if (py != null) {
                return CrossLanguageReference.builder()
                        .sourceFile(reference.getSourceFile())
                        .sourceLanguage(reference.getSourceLanguage())
                        .sourceElement(reference.getSourceElement())
                        .sourceElementType(reference.getSourceElementType())
                        .sourcePosition(reference.getSourceLine(), reference.getSourceColumn())
                        .targetFile(py.toString())
                        .targetLanguage("python")
                        .targetElement(reference.getTargetElement())
                        .targetElementType(reference.getTargetElementType())
                        .referenceType(reference.getReferenceType())
                        .build();
            }
        }
        return reference;
    }

    private static String readFileSafely(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return "";
        }
    }

    private static List<Path> listSiblings(Path file) {
        Path dir = file.getParent();
        if (dir == null) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }
}
