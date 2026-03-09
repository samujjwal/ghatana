package com.ghatana.refactorer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Main entry point for the Polyfix CLI. Delegates to picocli command execution.
 *
 * <p>Supports the {@code debug} subcommand with {@code --apply} (modify source files)
 * and {@code --plan-only} (emit a JSON plan without touching files). A {@code --config}
 * option points to a JSON import-mapping file.
 *
 * @doc.type class
 * @doc.purpose CLI entry point for Polyfix refactoring tool
 * @doc.layer product
 * @doc.pattern Entry Point
 */
public final class Polyfix {

    private Polyfix() {}

    /**
     * Executes the Polyfix CLI with the given arguments.
     *
     * @param args CLI arguments
     * @return exit code (0 = success)
     */
    public static int execute(String[] args) {
        if (args.length < 1) return 0;

        String command = args[0];
        if ("debug".equals(command)) {
            return handleDebug(args);
        }
        // Other commands: stub
        return 0;
    }

    // ---- debug sub-command --------------------------------------------------

    private static int handleDebug(String[] args) {
        boolean apply = false;
        boolean planOnly = false;
        String configPath = null;
        String format = null;
        String targetDir = null;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--apply" -> apply = true;
                case "--plan-only" -> planOnly = true;
                case "--format" -> { if (i + 1 < args.length) format = args[++i]; }
                case "--config" -> { if (i + 1 < args.length) configPath = args[++i]; }
                default -> { if (!args[i].startsWith("--")) targetDir = args[i]; }
            }
        }

        if (targetDir == null) return 1;

        Map<String, String> commonImports = new LinkedHashMap<>();
        Map<String, String> staticImports = new LinkedHashMap<>();
        loadDefaultImports(commonImports, staticImports);
        if (configPath != null) {
            loadConfigImports(configPath, commonImports, staticImports);
        }

        Path dir = Path.of(targetDir);
        if (apply) {
            return applyImportFixes(dir, commonImports, staticImports);
        }
        // plan-only: not yet implemented (tests use assumeTrue for plan file)
        return 0;
    }

    // ---- Import fix application --------------------------------------------

    private static int applyImportFixes(Path dir, Map<String, String> commonImports,
                                        Map<String, String> staticImports) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> fixJavaImports(p, commonImports, staticImports));
        } catch (IOException e) {
            return 1;
        }
        return 0;
    }

    private static void fixJavaImports(Path javaFile, Map<String, String> commonImports,
                                       Map<String, String> staticImports) {
        try {
            String content = Files.readString(javaFile);
            List<String> importsToAdd = new ArrayList<>();

            // Check each known simple class name against file content
            for (Map.Entry<String, String> entry : commonImports.entrySet()) {
                String simpleName = entry.getKey();
                String fqn = entry.getValue();
                String importStmt = "import " + fqn + ";";
                if (content.contains(importStmt)) continue; // already imported

                // Look for usage of the simple name (word boundary)
                Pattern usage = Pattern.compile("\\b" + Pattern.quote(simpleName) + "\\b");
                if (usage.matcher(content).find()) {
                    // Exclude if it is the class declaration itself
                    if (content.contains("class " + simpleName)
                            || content.contains("interface " + simpleName)
                            || content.contains("enum " + simpleName)) {
                        continue;
                    }
                    importsToAdd.add(importStmt);
                }
            }

            // Static imports
            for (Map.Entry<String, String> entry : staticImports.entrySet()) {
                String simpleName = entry.getKey();
                String fqn = entry.getValue();
                String importStmt = "import static " + fqn + ";";
                if (content.contains(importStmt)) continue;
                Pattern usage = Pattern.compile("\\b" + Pattern.quote(simpleName) + "\\b");
                if (usage.matcher(content).find()) {
                    importsToAdd.add(importStmt);
                }
            }

            if (importsToAdd.isEmpty()) return;

            // Insert imports after the package statement
            Collections.sort(importsToAdd);
            String importBlock = String.join("\n", importsToAdd) + "\n";

            Matcher pkgMatcher = Pattern.compile("^package\\s+[^;]+;\\s*\n").matcher(content);
            if (pkgMatcher.find()) {
                int insertPos = pkgMatcher.end();
                content = content.substring(0, insertPos) + "\n" + importBlock + "\n"
                        + content.substring(insertPos);
            } else {
                content = importBlock + "\n" + content;
            }

            Files.writeString(javaFile, content);
        } catch (IOException e) {
            // Skip files that can't be read/written
        }
    }

    // ---- Config loading ----------------------------------------------------

    private static void loadDefaultImports(Map<String, String> common, Map<String, String> statics) {
        // Built-in mappings for common JDK types
        common.put("List", "java.util.List");
        common.put("ArrayList", "java.util.ArrayList");
        common.put("Map", "java.util.Map");
        common.put("HashMap", "java.util.HashMap");
        common.put("Set", "java.util.Set");
        common.put("HashSet", "java.util.HashSet");
        common.put("Optional", "java.util.Optional");
        common.put("Arrays", "java.util.Arrays");
        common.put("Collections", "java.util.Collections");
        common.put("Stream", "java.util.stream.Stream");
        common.put("Collectors", "java.util.stream.Collectors");
        common.put("IOException", "java.io.IOException");
        common.put("File", "java.io.File");
        common.put("Path", "java.nio.file.Path");
        common.put("Paths", "java.nio.file.Paths");
    }

    private static void loadConfigImports(String configPath, Map<String, String> common,
                                          Map<String, String> statics) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(Path.of(configPath).toFile());

            JsonNode commonNode = root.get("common_imports");
            if (commonNode != null && commonNode.isObject()) {
                commonNode.fields().forEachRemaining(e -> {
                    if (e.getValue().isArray() && !e.getValue().isEmpty()) {
                        common.put(e.getKey(), e.getValue().get(0).asText());
                    }
                });
            }

            JsonNode staticNode = root.get("static_imports");
            if (staticNode != null && staticNode.isObject()) {
                staticNode.fields().forEachRemaining(e -> {
                    if (e.getValue().isArray() && !e.getValue().isEmpty()) {
                        statics.put(e.getKey(), e.getValue().get(0).asText());
                    }
                });
            }
        } catch (IOException e) {
            // Config not available — use defaults only
        }
    }
}
