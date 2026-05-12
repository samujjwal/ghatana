/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.governance.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generates GeneratedRouteRegistry.java from route-manifest.yaml.
 *
 * <p>This generator reads the canonical route manifest and produces
 * a Java source file with all routes registered in the RouteManifest.
 *
 * @doc.type class
 * @doc.purpose Generates route registry from manifest
 * @doc.layer governance
 * @doc.pattern Generator
 */
public final class RouteRegistryGenerator {

    private static final Logger log = LoggerFactory.getLogger(RouteRegistryGenerator.class);

    private final ObjectMapper yamlMapper;
    private final File manifestFile;
    private final File outputFile;

    public RouteRegistryGenerator(File manifestFile, File outputFile) {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.manifestFile = manifestFile;
        this.outputFile = outputFile;
    }

    /**
     * Main entry point for command-line execution.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: RouteRegistryGenerator <manifest-file> <output-file>");
            System.exit(1);
        }

        File manifestFile = new File(args[0]);
        File outputFile = new File(args[1]);

        RouteRegistryGenerator generator = new RouteRegistryGenerator(manifestFile, outputFile);
        generator.generate();
    }

    /**
     * Generates the route registry Java file.
     */
    public void generate() throws IOException {
        log.info("Generating route registry from: {}", manifestFile.getAbsolutePath());

        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = yamlMapper.readValue(manifestFile, Map.class);

        StringBuilder javaCode = new StringBuilder();
        javaCode.append(generateHeader());
        javaCode.append(generateInitializer(manifest));
        javaCode.append(generateFooter());

        // Ensure output directory exists
        outputFile.getParentFile().mkdirs();

        // Write generated file
        Files.writeString(outputFile.toPath(), javaCode.toString());

        log.info("Generated route registry to: {}", outputFile.getAbsolutePath());
    }

    private String generateHeader() {
        return """
package com.ghatana.yappc.api.generated;

/**
 * AUTO-GENERATED - DO NOT EDIT
 * Generated from docs/api/route-manifest.yaml
 * Run: ./gradlew :products:yappc:core:yappc-services:generateRouteRegistry
 */
import com.ghatana.yappc.governance.route.AuthMode;
import com.ghatana.yappc.governance.route.Boundary;
import com.ghatana.yappc.governance.route.PrivacyClassification;
import com.ghatana.yappc.governance.route.RouteEntry;
import com.ghatana.yappc.governance.route.RouteManifest;
import java.util.List;
import java.util.Set;

public final class GeneratedRouteRegistry {
    private static final RouteManifest MANIFEST = new RouteManifest();
    
    static {
        initializeManifest();
    }
    
    private static void initializeManifest() {
""";
    }

    @SuppressWarnings("unchecked")
    private String generateInitializer(Map<String, Object> manifest) {
        StringBuilder sb = new StringBuilder();
        
        // Process each server section
        for (Map.Entry<String, Object> entry : manifest.entrySet()) {
            String server = entry.getKey();
            
            // Skip non-server sections (like schema definitions)
            if (server.startsWith("#") || server.equals("schema")) {
                continue;
            }

            Object routesObj = entry.getValue();
            if (!(routesObj instanceof List)) {
                continue;
            }

            List<Map<String, Object>> routes = (List<Map<String, Object>>) routesObj;

            for (Map<String, Object> route : routes) {
                String method = toUpper((String) route.get("method"));
                String path = (String) route.get("path");
                String auth = toUpper((String) route.get("auth"));
                
                @SuppressWarnings("unchecked")
                List<String> scopesList = (List<String>) route.get("scopes");
                Set<String> scopes = scopesList != null ? new HashSet<>(scopesList) : Set.of();
                
                String owner = (String) route.get("owner");
                String boundary = toUpper((String) route.get("boundary"));
                String operationId = (String) route.get("operationId");
                String auditEventType = (String) route.getOrDefault("auditEventType", toSnakeCase(operationId).toUpperCase());
                String privacyClassification = toUpper((String) route.getOrDefault("privacyClassification", 
                    "public".equalsIgnoreCase(auth) ? "PUBLIC" : "INTERNAL"));

                sb.append(String.format(
                    "        MANIFEST.addRoute(\"%s\", new RouteEntry(\n" +
                    "            \"%s\",\n" +
                    "            \"%s\",\n" +
                    "            Set.of(%s),\n" +
                    "            \"%s\",\n" +
                    "            Boundary.%s,\n" +
                    "            \"%s\",\n" +
                    "            \"%s\",\n" +
                    "            PrivacyClassification.%s\n" +
                    "        ));\n",
                    server, method, path, formatScopes(scopes), owner, boundary, 
                    operationId, auditEventType, privacyClassification
                ));
            }
        }

        return sb.toString();
    }

    private String generateFooter() {
        return """
    }
    
    public static RouteManifest getManifest() {
        return MANIFEST;
    }
}
""";
    }

    private String toUpper(String value) {
        return value != null ? value.toUpperCase() : null;
    }

    private String toSnakeCase(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private String formatScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "";
        }
        List<String> quoted = scopes.stream()
            .map(s -> "\"" + s + "\"")
            .toList();
        return String.join(", ", quoted);
    }
}
