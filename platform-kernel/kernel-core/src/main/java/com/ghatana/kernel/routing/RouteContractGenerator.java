/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.routing;

import com.ghatana.kernel.contracts.ProductContract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kernel route contract generator.
 *
 * <p>Generates TypeScript manifests, backend entitlement data, and route documentation
 * from a single ProductContract source. This ensures consistency across web, mobile,
 * and backend surfaces by deriving all artifacts from the canonical contract.</p>
 *
 * @doc.type class
 * @doc.purpose Generates TS manifest, backend entitlement data, and route docs from ProductContract
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class RouteContractGenerator {

    private final ProductContract contract;
    private final boolean cleanupMode;
    private final Path outputDirectory;

    private RouteContractGenerator(ProductContract contract, boolean cleanupMode, Path outputDirectory) {
        this.contract = contract;
        this.cleanupMode = cleanupMode;
        this.outputDirectory = outputDirectory;
    }

    /**
     * Creates a new generator for the given product contract.
     *
     * @param contract the product contract to generate from
     * @return a new RouteContractGenerator instance
     */
    public static RouteContractGenerator forContract(ProductContract contract) {
        return new RouteContractGenerator(contract, false, Paths.get("."));
    }

    /**
     * Creates a new generator for the given product contract with cleanup mode enabled.
     *
     * @param contract the product contract to generate from
     * @param outputDirectory the directory where artifacts are generated
     * @return a new RouteContractGenerator instance with cleanup mode enabled
     */
    public static RouteContractGenerator forContractWithCleanup(ProductContract contract, Path outputDirectory) {
        return new RouteContractGenerator(contract, true, outputDirectory);
    }

    /**
     * Generates TypeScript route manifest.
     *
     * <p>The manifest includes route paths, methods, required roles, and metadata
     * in a format consumable by TypeScript route definitions.</p>
     *
     * @return TypeScript manifest as a string
     */
    public String generateTypeScriptManifest() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated from ProductContract: ").append(contract.getContractId()).append("\n");
        sb.append("// DO NOT EDIT - Regenerate from source contract\n\n");
        sb.append("export type RouteState = \"ACTIVE\" | \"DEPRECATED\" | \"REMOVED\" | \"MIGRATION\";\n\n");
        sb.append("export interface UIStateDeclaration {\n");
        sb.append("  requiresLoading: boolean;\n");
        sb.append("  requiresError: boolean;\n");
        sb.append("  requiresEmpty: boolean;\n");
        sb.append("  requiresForbidden: boolean;\n");
        sb.append("  loadingMessageKey: string;\n");
        sb.append("  errorMessageKey: string;\n");
        sb.append("  emptyMessageKey: string;\n");
        sb.append("  forbiddenMessageKey: string;\n");
        sb.append("}\n\n");
        sb.append("export interface AccessibilityDeclaration {\n");
        sb.append("  titleKey: string;\n");
        sb.append("  descriptionKey: string;\n");
        sb.append("  ariaLabelKey: string;\n");
        sb.append("  keyboardNavigable: boolean;\n");
        sb.append("  screenReaderAnnounce: boolean;\n");
        sb.append("}\n\n");
        sb.append("export interface RouteDeclaration {\n");
        sb.append("  id: string;\n");
        sb.append("  path: string;\n");
        sb.append("  method: string;\n");
        sb.append("  requiredRoles: string[];\n");
        sb.append("  requiredCapabilities: string[];\n");
        sb.append("  metadata: Record<string, string>;\n");
        sb.append("  state: RouteState;\n");
        sb.append("  uiState: UIStateDeclaration;\n");
        sb.append("  accessibility: AccessibilityDeclaration;\n");
        sb.append("}\n\n");
        sb.append("export const routes: RouteDeclaration[] = [\n");

        for (ProductContract.RouteDeclaration route : contract.getRoutes()) {
            sb.append("  {\n");
            sb.append("    id: \"").append(escapeTsString(route.id())).append("\",\n");
            sb.append("    path: \"").append(escapeTsString(route.path())).append("\",\n");
            sb.append("    method: \"").append(escapeTsString(route.method())).append("\",\n");
            sb.append("    requiredRoles: [").append(joinTsStrings(route.requiredRoles())).append("],\n");
            sb.append("    requiredCapabilities: [").append(joinTsStrings(route.requiredCapabilities())).append("],\n");
            sb.append("    metadata: {").append(joinTsMap(route.metadata())).append("},\n");
            sb.append("    state: \"").append(route.state().name()).append("\" as RouteState,\n");
            sb.append("    uiState: {\n");
            sb.append("      requiresLoading: ").append(route.uiState().requiresLoading()).append(",\n");
            sb.append("      requiresError: ").append(route.uiState().requiresError()).append(",\n");
            sb.append("      requiresEmpty: ").append(route.uiState().requiresEmpty()).append(",\n");
            sb.append("      requiresForbidden: ").append(route.uiState().requiresForbidden()).append(",\n");
            sb.append("      loadingMessageKey: \"").append(escapeTsString(route.uiState().loadingMessageKey())).append("\",\n");
            sb.append("      errorMessageKey: \"").append(escapeTsString(route.uiState().errorMessageKey())).append("\",\n");
            sb.append("      emptyMessageKey: \"").append(escapeTsString(route.uiState().emptyMessageKey())).append("\",\n");
            sb.append("      forbiddenMessageKey: \"").append(escapeTsString(route.uiState().forbiddenMessageKey())).append("\"\n");
            sb.append("    },\n");
            sb.append("    accessibility: {\n");
            sb.append("      titleKey: \"").append(escapeTsString(route.accessibility().titleKey())).append("\",\n");
            sb.append("      descriptionKey: \"").append(escapeTsString(route.accessibility().descriptionKey())).append("\",\n");
            sb.append("      ariaLabelKey: \"").append(escapeTsString(route.accessibility().ariaLabelKey())).append("\",\n");
            sb.append("      keyboardNavigable: ").append(route.accessibility().keyboardNavigable()).append(",\n");
            sb.append("      screenReaderAnnounce: ").append(route.accessibility().screenReaderAnnounce()).append("\n");
            sb.append("    },\n");
            sb.append("  },\n");
        }

        sb.append("];\n");
        return sb.toString();
    }

    /**
     * Generates backend entitlement data.
     *
     * <p>The entitlement data includes route-to-role mappings, capability requirements,
     * and policy metadata for backend access control enforcement.</p>
     *
     * @return Entitlement data as a JSON string
     */
    public String generateBackendEntitlementData() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"productId\": \"").append(escapeJsonString(contract.getProductId())).append("\",\n");
        sb.append("  \"contractId\": \"").append(escapeJsonString(contract.getContractId())).append("\",\n");
        sb.append("  \"version\": \"").append(escapeJsonString(contract.getVersion())).append("\",\n");
        sb.append("  \"routes\": [\n");

        for (ProductContract.RouteDeclaration route : contract.getRoutes()) {
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(escapeJsonString(route.id())).append("\",\n");
            sb.append("      \"path\": \"").append(escapeJsonString(route.path())).append("\",\n");
            sb.append("      \"method\": \"").append(escapeJsonString(route.method())).append("\",\n");
            sb.append("      \"requiredRoles\": [").append(joinJsonStrings(route.requiredRoles())).append("],\n");
            sb.append("      \"requiredCapabilities\": [").append(joinJsonStrings(route.requiredCapabilities())).append("],\n");
            sb.append("      \"metadata\": {").append(joinJsonMap(route.metadata())).append("},\n");
            sb.append("      \"state\": \"").append(route.state().name()).append("\",\n");
            sb.append("      \"uiState\": {\n");
            sb.append("        \"requiresLoading\": ").append(route.uiState().requiresLoading()).append(",\n");
            sb.append("        \"requiresError\": ").append(route.uiState().requiresError()).append(",\n");
            sb.append("        \"requiresEmpty\": ").append(route.uiState().requiresEmpty()).append(",\n");
            sb.append("        \"requiresForbidden\": ").append(route.uiState().requiresForbidden()).append(",\n");
            sb.append("        \"loadingMessageKey\": \"").append(escapeJsonString(route.uiState().loadingMessageKey())).append("\",\n");
            sb.append("        \"errorMessageKey\": \"").append(escapeJsonString(route.uiState().errorMessageKey())).append("\",\n");
            sb.append("        \"emptyMessageKey\": \"").append(escapeJsonString(route.uiState().emptyMessageKey())).append("\",\n");
            sb.append("        \"forbiddenMessageKey\": \"").append(escapeJsonString(route.uiState().forbiddenMessageKey())).append("\"\n");
            sb.append("      },\n");
            sb.append("      \"accessibility\": {\n");
            sb.append("        \"titleKey\": \"").append(escapeJsonString(route.accessibility().titleKey())).append("\",\n");
            sb.append("        \"descriptionKey\": \"").append(escapeJsonString(route.accessibility().descriptionKey())).append("\",\n");
            sb.append("        \"ariaLabelKey\": \"").append(escapeJsonString(route.accessibility().ariaLabelKey())).append("\",\n");
            sb.append("        \"keyboardNavigable\": ").append(route.accessibility().keyboardNavigable()).append(",\n");
            sb.append("        \"screenReaderAnnounce\": ").append(route.accessibility().screenReaderAnnounce()).append("\n");
            sb.append("      }\n");
            sb.append("    },\n");
        }

        sb.append("  ],\n");
        sb.append("  \"capabilities\": [\n");

        for (ProductContract.CapabilityDeclaration capability : contract.getCapabilities()) {
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(escapeJsonString(capability.id())).append("\",\n");
            sb.append("      \"name\": \"").append(escapeJsonString(capability.name())).append("\",\n");
            sb.append("      \"description\": \"").append(escapeJsonString(capability.description())).append("\",\n");
            sb.append("      \"requiredRoles\": [").append(joinJsonStrings(capability.requiredRoles())).append("],\n");
            sb.append("      \"metadata\": {").append(joinJsonMap(capability.metadata())).append("}\n");
            sb.append("    },\n");
        }

        sb.append("  ],\n");
        sb.append("  \"personas\": [\n");

        for (ProductContract.PersonaDeclaration persona : contract.getPersonas()) {
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(escapeJsonString(persona.id())).append("\",\n");
            sb.append("      \"name\": \"").append(escapeJsonString(persona.name())).append("\",\n");
            sb.append("      \"defaultRole\": \"").append(escapeJsonString(persona.defaultRole())).append("\",\n");
            sb.append("      \"allowedCapabilities\": [").append(joinJsonStrings(persona.allowedCapabilities())).append("],\n");
            sb.append("      \"metadata\": {").append(joinJsonMap(persona.metadata())).append("}\n");
            sb.append("    },\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Generates route documentation in Markdown format.
     *
     * <p>The documentation includes route descriptions, required roles, capabilities,
     * and metadata for developer reference.</p>
     *
     * @return Markdown documentation as a string
     */
    public String generateRouteDocumentation() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Route Documentation\n\n");
        sb.append("**Product ID:** ").append(contract.getProductId()).append("\n");
        sb.append("**Contract ID:** ").append(contract.getContractId()).append("\n");
        sb.append("**Version:** ").append(contract.getVersion()).append("\n\n");
        sb.append("---\n\n");

        sb.append("## Routes\n\n");
        for (ProductContract.RouteDeclaration route : contract.getRoutes()) {
            sb.append("### ").append(route.method()).append(" ").append(route.path()).append("\n\n");
            sb.append("**ID:** `").append(route.id()).append("`\n\n");
            sb.append("**Required Roles:** ").append(String.join(", ", route.requiredRoles())).append("\n\n");
            sb.append("**Required Capabilities:** ").append(String.join(", ", route.requiredCapabilities())).append("\n\n");
            if (!route.metadata().isEmpty()) {
                sb.append("**Metadata:**\n");
                for (Map.Entry<String, String> entry : route.metadata().entrySet()) {
                    sb.append("- `").append(entry.getKey()).append("`: ").append(entry.getValue()).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("## Capabilities\n\n");
        for (ProductContract.CapabilityDeclaration capability : contract.getCapabilities()) {
            sb.append("### ").append(capability.name()).append("\n\n");
            sb.append("**ID:** `").append(capability.id()).append("`\n\n");
            sb.append("**Description:** ").append(capability.description()).append("\n\n");
            sb.append("**Required Roles:** ").append(String.join(", ", capability.requiredRoles())).append("\n\n");
        }

        sb.append("## Personas\n\n");
        for (ProductContract.PersonaDeclaration persona : contract.getPersonas()) {
            sb.append("### ").append(persona.name()).append("\n\n");
            sb.append("**ID:** `").append(persona.id()).append("`\n\n");
            sb.append("**Default Role:** `").append(persona.defaultRole()).append("`\n\n");
            sb.append("**Allowed Capabilities:** ").append(String.join(", ", persona.allowedCapabilities())).append("\n\n");
        }

        return sb.toString();
    }

    private String escapeTsString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String joinTsStrings(java.util.List<String> values) {
        return values.stream()
                .map(this::escapeTsString)
                .map(s -> "\"" + s + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String joinTsMap(Map<String, String> map) {
        return map.entrySet().stream()
                .map(e -> "\"" + escapeTsString(e.getKey()) + "\": \"" + escapeTsString(e.getValue()) + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String escapeJsonString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String joinJsonStrings(java.util.List<String> values) {
        return values.stream()
                .map(this::escapeJsonString)
                .map(s -> "\"" + s + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String joinJsonMap(Map<String, String> map) {
        return map.entrySet().stream()
                .map(e -> "\"" + escapeJsonString(e.getKey()) + "\": \"" + escapeJsonString(e.getValue()) + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    /**
     * Cleans up stale artifacts from the output directory.
     *
     * <p>When cleanup mode is enabled, this method deletes generated artifacts
     * that are no longer present in the current contract. This prevents accumulation
     * of legacy files.</p>
     *
     * @throws IOException if file operations fail
     */
    public void cleanupStaleArtifacts() throws IOException {
        if (!cleanupMode) {
            return;
        }

        if (!Files.exists(outputDirectory)) {
            return;
        }

        Set<String> currentRouteIds = contract.getRoutes().stream()
                .map(ProductContract.RouteDeclaration::id)
                .collect(Collectors.toSet());

        Files.walk(outputDirectory)
                .filter(Files::isRegularFile)
                .filter(file -> isGeneratedArtifact(file))
                .filter(file -> !isCurrentArtifact(file, currentRouteIds))
                .forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        // Log but continue with other files
                        System.err.println("Failed to delete stale artifact: " + file + " - " + e.getMessage());
                    }
                });
    }

    private boolean isGeneratedArtifact(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.startsWith("route-") || 
               fileName.startsWith("Route") ||
               fileName.endsWith(".generated.ts") ||
               fileName.endsWith(".generated.json") ||
               fileName.endsWith(".generated.md");
    }

    private boolean isCurrentArtifact(Path file, Set<String> currentRouteIds) {
        String fileName = file.getFileName().toString();
        for (String routeId : currentRouteIds) {
            if (fileName.contains(routeId)) {
                return true;
            }
        }
        return false;
    }
}
