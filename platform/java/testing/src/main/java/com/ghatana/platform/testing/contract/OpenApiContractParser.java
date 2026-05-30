package com.ghatana.platform.testing.contract;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import java.util.stream.*;
import java.util.Arrays;

/**
 * @doc.type class
 * @doc.purpose Parses OpenAPI YAML/JSON specifications for route contract validation
 * @doc.layer platform
 * @doc.pattern OpenAPI parser
 */
public final class OpenApiContractParser {
    private static final Logger LOG = Logger.getLogger(OpenApiContractParser.class.getName());

    private OpenApiContractParser() {}

    /**
     * Parse OpenAPI specification from a YAML file.
     *
     * @param specFilePath path to openapi.yaml or openapi.json
     * @return parsed contract definition
     */
    public static ApiContractDefinition parseFromFile(String specFilePath) throws IOException {
        Path path = Paths.get(specFilePath);
        if (!path.isAbsolute()) {
            // Try multiple resolution strategies
            Path[] possiblePaths = {
                path, // As provided
                Paths.get(System.getProperty("user.dir"), specFilePath), // From user.dir
                Paths.get(System.getProperty("user.dir")).resolve(specFilePath), // Resolve from user.dir
                Paths.get("..").resolve(specFilePath), // From parent directory
                Paths.get("../..").resolve(specFilePath) // From grandparent directory
            };
            
            for (Path possiblePath : possiblePaths) {
                if (Files.exists(possiblePath)) {
                    path = possiblePath.toAbsolutePath();
                    break;
                }
            }
            
            if (!Files.exists(path)) {
                throw new IOException("OpenAPI spec file not found: " + specFilePath + " (tried: " + Arrays.toString(possiblePaths) + ")");
            }
        }
        String content = Files.readString(path);
        return parseFromString(content, path.getFileName().toString());
    }

    /**
     * Parse OpenAPI specification from a string (YAML or JSON).
     */
    public static ApiContractDefinition parseFromString(String specContent, String filename) {
        // Simple YAML/JSON parser for extracting paths and methods
        // In production, use a real OpenAPI parser library like Swagger-Parser
        Map<String, Set<String>> pathsAndMethods = extractPathsAndMethods(specContent);
        String basePath = extractBasePath(specContent);
        String version = extractVersion(specContent);

        return new OpenApiContractDefinition(basePath, pathsAndMethods, version);
    }

    /**
     * Extract all paths and their HTTP methods from the spec content.
     */
    private static Map<String, Set<String>> extractPathsAndMethods(String content) {
        Map<String, Set<String>> result = new LinkedHashMap<>();

        // Simple regex-based extraction for YAML paths: (lines starting with "/" or indented)
        String[] lines = content.split("\n");
        String currentPath = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Look for path definitions (in YAML, typically at beginning of line or after /paths:)
            if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
                // JSON-style path
                String pathMatch = trimmed.replaceAll("[\"']", "").split(":")[0].trim();
                if (pathMatch.startsWith("/")) {
                    currentPath = pathMatch;
                    result.put(currentPath, new HashSet<>());
                }
            } else if (trimmed.startsWith("/")) {
                // YAML-style path
                currentPath = trimmed.replaceAll(":.*", "").trim();
                result.put(currentPath, new HashSet<>());
            }

            // Look for HTTP methods
            if (currentPath != null) {
                for (String method : new String[]{"get", "post", "put", "delete", "patch", "head", "options"}) {
                    if (trimmed.startsWith(method + ":") || trimmed.equals(method + ":")) {
                        result.computeIfAbsent(currentPath, k -> new HashSet<>()).add(method.toUpperCase());
                    }
                }
            }
        }

        return result;
    }

    /**
     * Extract base path (servers[0].url or basePath field).
     */
    private static String extractBasePath(String content) {
        // Look for servers section in OpenAPI 3.x
        if (content.contains("servers:")) {
            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("servers:")) {
                    // Next lines should have url
                    for (int j = i + 1; j < Math.min(i + 5, lines.length); j++) {
                        if (lines[j].contains("url:")) {
                            String url = lines[j]
                                .replaceFirst(".*url:\\s*", "")
                                .trim()
                                .replaceAll("^[\"']|[\"']$", "");
                            if (!url.isEmpty() && url.contains("/")) {
                                return url;
                            }
                        }
                    }
                }
            }
        }

        return "";
    }

    /**
     * Extract OpenAPI version.
     */
    private static String extractVersion(String content) {
        if (content.contains("openapi:")) {
            String match = content.split("openapi:")[1].split("\n")[0].trim().replaceAll("['\"]", "");
            return match;
        }
        if (content.contains("swagger:")) {
            String match = content.split("swagger:")[1].split("\n")[0].trim().replaceAll("['\"]", "");
            return match;
        }
        return "3.0.0";
    }

    /**
     * Simple in-memory implementation of ApiContractDefinition for parsed specs.
     */
    private static class OpenApiContractDefinition implements ApiContractDefinition {
        private final String basePath;
        private final Map<String, Set<String>> pathsAndMethods;
        private final String version;

        OpenApiContractDefinition(String basePath, Map<String, Set<String>> pathsAndMethods, String version) {
            this.basePath = basePath;
            this.pathsAndMethods = pathsAndMethods;
            this.version = version;
        }

        @Override
        public String getBasePath() {
            return basePath;
        }

        @Override
        public Set<String> getDefinedRoutes() {
            return pathsAndMethods.keySet();
        }

        @Override
        public Set<String> getMethodsForRoute(String route) {
            return pathsAndMethods.getOrDefault(route, Collections.emptySet());
        }

        @Override
        public String getOpenApiVersion() {
            return version;
        }

        @Override
        public String getContractVersion() {
            return "parsed";
        }

        @Override
        public ContractValidationResult validate(String method, String path) {
            Set<String> methods = getMethodsForRoute(path);
            if (!methods.contains(method)) {
                return ContractValidationResult.invalid(
                    String.format("Route %s %s not found in contract. Defined methods: %s", method, path, methods)
                );
            }
            return ContractValidationResult.valid();
        }
    }
}
