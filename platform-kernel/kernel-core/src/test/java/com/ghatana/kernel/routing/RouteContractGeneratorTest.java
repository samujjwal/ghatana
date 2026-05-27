/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.routing;

import com.ghatana.kernel.contracts.ProductContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RouteContractGenerator.
 */
class RouteContractGeneratorTest {

    @Test
    void generateTypeScriptManifest_shouldGenerateValidTypeScript() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                true, true, true, true,
                "route.loading", "route.error", "route.empty", "route.forbidden"
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "route.aria", true, true
        );
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of(
                        new ProductContract.RouteDeclaration(
                                "route-1",
                                "/api/test",
                                "GET",
                                List.of("patient"),
                                List.of("read:profile"),
                                Map.of("public", "true"),
                                ProductContract.RouteState.ACTIVE,
                                uiState,
                                accessibility
                        )
                ))
                .build();

        RouteContractGenerator generator = RouteContractGenerator.forContract(contract);
        String manifest = generator.generateTypeScriptManifest();

        assertTrue(manifest.contains("export type RouteState"));
        assertTrue(manifest.contains("export interface UIStateDeclaration"));
        assertTrue(manifest.contains("export interface AccessibilityDeclaration"));
        assertTrue(manifest.contains("export interface RouteDeclaration"));
        assertTrue(manifest.contains("id: string"));
        assertTrue(manifest.contains("path: string"));
        assertTrue(manifest.contains("method: string"));
        assertTrue(manifest.contains("requiredRoles: string[]"));
        assertTrue(manifest.contains("requiredCapabilities: string[]"));
        assertTrue(manifest.contains("metadata: Record<string, string>"));
        assertTrue(manifest.contains("state: RouteState"));
        assertTrue(manifest.contains("uiState: UIStateDeclaration"));
        assertTrue(manifest.contains("accessibility: AccessibilityDeclaration"));
        assertTrue(manifest.contains("titleKey: string"));
        assertTrue(manifest.contains("descriptionKey: string"));
        assertTrue(manifest.contains("ariaLabelKey: string"));
        assertTrue(manifest.contains("keyboardNavigable: boolean"));
        assertTrue(manifest.contains("screenReaderAnnounce: boolean"));
        assertTrue(manifest.contains("id: \"route-1\""));
        assertTrue(manifest.contains("path: \"/api/test\""));
        assertTrue(manifest.contains("method: \"GET\""));
        assertTrue(manifest.contains("requiredRoles: [\"patient\"]"));
        assertTrue(manifest.contains("requiredCapabilities: [\"read:profile\"]"));
        assertTrue(manifest.contains("public: \"true\""));
        assertTrue(manifest.contains("state: \"ACTIVE\""));
        assertTrue(manifest.contains("route.loading"));
        assertTrue(manifest.contains("route.error"));
        assertTrue(manifest.contains("route.empty"));
        assertTrue(manifest.contains("route.forbidden"));
        assertTrue(manifest.contains("route.title"));
        assertTrue(manifest.contains("route.description"));
        assertTrue(manifest.contains("route.aria"));
    }

    @Test
    void generateBackendEntitlementData_shouldGenerateValidJson() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                true, true, true, true,
                "route.loading", "route.error", "route.empty", "route.forbidden"
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "route.aria", true, true
        );
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of(
                        new ProductContract.RouteDeclaration(
                                "route-1",
                                "/api/test",
                                "GET",
                                List.of("patient"),
                                List.of("read:profile"),
                                Map.of("public", "true"),
                                ProductContract.RouteState.ACTIVE,
                                uiState,
                                accessibility
                        )
                ))
                .capabilities(List.of(
                        new ProductContract.CapabilityDeclaration(
                                "cap-1",
                                "Read Profile",
                                "Allows reading patient profile",
                                List.of("patient"),
                                Map.of()
                        )
                ))
                .personas(List.of(
                        new ProductContract.PersonaDeclaration(
                                "persona-1",
                                "Patient",
                                "patient",
                                List.of("read:profile"),
                                Map.of()
                        )
                ))
                .build();

        RouteContractGenerator generator = RouteContractGenerator.forContract(contract);
        String entitlementData = generator.generateBackendEntitlementData();

        assertTrue(entitlementData.contains("\"productId\": \"test-product\""));
        assertTrue(entitlementData.contains("\"contractId\": \"test-contract\""));
        assertTrue(entitlementData.contains("\"version\": \"1.0.0\""));
        assertTrue(entitlementData.contains("\"id\": \"route-1\""));
        assertTrue(entitlementData.contains("\"path\": \"/api/test\""));
        assertTrue(entitlementData.contains("\"method\": \"GET\""));
        assertTrue(entitlementData.contains("\"requiredRoles\": [\"patient\"]"));
        assertTrue(entitlementData.contains("\"requiredCapabilities\": [\"read:profile\"]"));
        assertTrue(entitlementData.contains("\"state\": \"ACTIVE\""));
        assertTrue(entitlementData.contains("\"uiState\""));
        assertTrue(entitlementData.contains("\"requiresLoading\": true"));
        assertTrue(entitlementData.contains("\"requiresError\": true"));
        assertTrue(entitlementData.contains("\"requiresEmpty\": true"));
        assertTrue(entitlementData.contains("\"requiresForbidden\": true"));
        assertTrue(entitlementData.contains("\"loadingMessageKey\": \"route.loading\""));
        assertTrue(entitlementData.contains("\"errorMessageKey\": \"route.error\""));
        assertTrue(entitlementData.contains("\"emptyMessageKey\": \"route.empty\""));
        assertTrue(entitlementData.contains("\"forbiddenMessageKey\": \"route.forbidden\""));
        assertTrue(entitlementData.contains("\"accessibility\""));
        assertTrue(entitlementData.contains("\"titleKey\": \"route.title\""));
        assertTrue(entitlementData.contains("\"descriptionKey\": \"route.description\""));
        assertTrue(entitlementData.contains("\"ariaLabelKey\": \"route.aria\""));
        assertTrue(entitlementData.contains("\"keyboardNavigable\": true"));
        assertTrue(entitlementData.contains("\"screenReaderAnnounce\": true"));
        assertTrue(entitlementData.contains("\"id\": \"cap-1\""));
        assertTrue(entitlementData.contains("\"name\": \"Read Profile\""));
        assertTrue(entitlementData.contains("\"id\": \"persona-1\""));
        assertTrue(entitlementData.contains("\"defaultRole\": \"patient\""));
    }

    @Test
    void generateRouteDocumentation_shouldGenerateValidMarkdown() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "route.aria", true, true
        );
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of(
                        new ProductContract.RouteDeclaration(
                                "route-1",
                                "/api/test",
                                "GET",
                                List.of("patient"),
                                List.of("read:profile"),
                                Map.of("public", "true"),
                                ProductContract.RouteState.ACTIVE,
                                uiState,
                                accessibility
                        )
                ))
                .capabilities(List.of(
                        new ProductContract.CapabilityDeclaration(
                                "cap-1",
                                "Read Profile",
                                "Allows reading patient profile",
                                List.of("patient"),
                                Map.of()
                        )
                ))
                .personas(List.of(
                        new ProductContract.PersonaDeclaration(
                                "persona-1",
                                "Patient",
                                "patient",
                                List.of("read:profile"),
                                Map.of()
                        )
                ))
                .build();

        RouteContractGenerator generator = RouteContractGenerator.forContract(contract);
        String documentation = generator.generateRouteDocumentation();

        assertTrue(documentation.contains("# Route Documentation"));
        assertTrue(documentation.contains("**Product ID:** test-product"));
        assertTrue(documentation.contains("**Contract ID:** test-contract"));
        assertTrue(documentation.contains("**Version:** 1.0.0"));
        assertTrue(documentation.contains("## Routes"));
        assertTrue(documentation.contains("### GET /api/test"));
        assertTrue(documentation.contains("**ID:** `route-1`"));
        assertTrue(documentation.contains("**Required Roles:** patient"));
        assertTrue(documentation.contains("**Required Capabilities:** read:profile"));
        assertTrue(documentation.contains("## Capabilities"));
        assertTrue(documentation.contains("### Read Profile"));
        assertTrue(documentation.contains("**ID:** `cap-1`"));
        assertTrue(documentation.contains("## Personas"));
        assertTrue(documentation.contains("### Patient"));
        assertTrue(documentation.contains("**ID:** `persona-1`"));
    }

    @Test
    void generateTypeScriptManifest_shouldHandleEmptyLists() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of(
                        new ProductContract.RouteDeclaration(
                                "route-1",
                                "/api/test",
                                "GET",
                                List.of(),
                                List.of(),
                                Map.of(),
                                ProductContract.RouteState.ACTIVE,
                                uiState,
                                accessibility
                        )
                ))
                .build();

        RouteContractGenerator generator = RouteContractGenerator.forContract(contract);
        String manifest = generator.generateTypeScriptManifest();

        assertTrue(manifest.contains("requiredRoles: []"));
        assertTrue(manifest.contains("requiredCapabilities: []"));
        assertTrue(manifest.contains("metadata: {}"));
    }

    @Test
    void generateBackendEntitlementData_shouldHandleEmptyLists() {
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of())
                .capabilities(List.of())
                .personas(List.of())
                .build();

        RouteContractGenerator generator = RouteContractGenerator.forContract(contract);
        String entitlementData = generator.generateBackendEntitlementData();

        assertTrue(entitlementData.contains("\"routes\": []"));
        assertTrue(entitlementData.contains("\"capabilities\": []"));
        assertTrue(entitlementData.contains("\"personas\": []"));
    }

    @Test
    void escapeTsString_shouldHandleSpecialCharacters() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of(
                        new ProductContract.RouteDeclaration(
                                "route-1",
                                "/api/test",
                                "GET",
                                List.of(),
                                List.of(),
                                Map.of("key", "value with \"quotes\" and \\backslash"),
                                ProductContract.RouteState.ACTIVE,
                                uiState,
                                accessibility
                        )
                ))
                .build();

        RouteContractGenerator generator = RouteContractGenerator.forContract(contract);
        String manifest = generator.generateTypeScriptManifest();

        assertTrue(manifest.contains("\\\""));
        assertTrue(manifest.contains("\\\\"));
    }

    @Test
    void escapeJsonString_shouldHandleSpecialCharacters() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of(
                        new ProductContract.RouteDeclaration(
                                "route-1",
                                "/api/test",
                                "GET",
                                List.of(),
                                List.of(),
                                Map.of("key", "value with \"quotes\" and \\backslash"),
                                ProductContract.RouteState.ACTIVE,
                                uiState,
                                accessibility
                        )
                ))
                .build();

        RouteContractGenerator generator = RouteContractGenerator.forContract(contract);
        String entitlementData = generator.generateBackendEntitlementData();

        assertTrue(entitlementData.contains("\\\""));
        assertTrue(entitlementData.contains("\\\\"));
    }

    @Test
    void noLegacyMode_shouldRejectDeprecatedState() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        assertThrows(IllegalArgumentException.class, () -> {
            ProductContract.builder(
                    "test-contract",
                    "Test Contract",
                    "1.0.0",
                    "test-product"
            )
                    .noLegacyMode(true)
                    .routes(List.of(
                            new ProductContract.RouteDeclaration(
                                    "route-1",
                                    "/api/test",
                                    "GET",
                                    List.of(),
                                    List.of(),
                                    Map.of(),
                                    ProductContract.RouteState.DEPRECATED,
                                    uiState,
                                    accessibility
                            )
                    ))
                    .build();
        });
    }

    @Test
    void noLegacyMode_shouldRejectRemovedState() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        assertThrows(IllegalArgumentException.class, () -> {
            ProductContract.builder(
                    "test-contract",
                    "Test Contract",
                    "1.0.0",
                    "test-product"
            )
                    .noLegacyMode(true)
                    .routes(List.of(
                            new ProductContract.RouteDeclaration(
                                    "route-1",
                                    "/api/test",
                                    "GET",
                                    List.of(),
                                    List.of(),
                                    Map.of(),
                                    ProductContract.RouteState.REMOVED,
                                    uiState,
                                    accessibility
                            )
                    ))
                    .build();
        });
    }

    @Test
    void noLegacyMode_shouldRejectMigrationState() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        assertThrows(IllegalArgumentException.class, () -> {
            ProductContract.builder(
                    "test-contract",
                    "Test Contract",
                    "1.0.0",
                    "test-product"
            )
                    .noLegacyMode(true)
                    .routes(List.of(
                            new ProductContract.RouteDeclaration(
                                    "route-1",
                                    "/api/test",
                                    "GET",
                                    List.of(),
                                    List.of(),
                                    Map.of(),
                                    ProductContract.RouteState.MIGRATION,
                                    uiState,
                                    accessibility
                            )
                    ))
                    .build();
        });
    }

    @Test
    void noLegacyMode_shouldAllowActiveState() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        assertDoesNotThrow(() -> {
            ProductContract.builder(
                    "test-contract",
                    "Test Contract",
                    "1.0.0",
                    "test-product"
            )
                    .noLegacyMode(true)
                    .routes(List.of(
                            new ProductContract.RouteDeclaration(
                                    "route-1",
                                    "/api/test",
                                    "GET",
                                    List.of(),
                                    List.of(),
                                    Map.of(),
                                    ProductContract.RouteState.ACTIVE,
                                    uiState,
                                    accessibility
                            )
                    ))
                    .build();
        });
    }

    @Test
    void legacyMode_shouldAllowAllStates() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        assertDoesNotThrow(() -> {
            ProductContract.builder(
                    "test-contract",
                    "Test Contract",
                    "1.0.0",
                    "test-product"
            )
                    .noLegacyMode(false)
                    .routes(List.of(
                            new ProductContract.RouteDeclaration(
                                    "route-1",
                                    "/api/test",
                                    "GET",
                                    List.of(),
                                    List.of(),
                                    Map.of(),
                                    ProductContract.RouteState.DEPRECATED,
                                    uiState,
                                    accessibility
                            ),
                            new ProductContract.RouteDeclaration(
                                    "route-2",
                                    "/api/test2",
                                    "GET",
                                    List.of(),
                                    List.of(),
                                    Map.of(),
                                    ProductContract.RouteState.REMOVED,
                                    uiState,
                                    accessibility
                            ),
                            new ProductContract.RouteDeclaration(
                                    "route-3",
                                    "/api/test3",
                                    "GET",
                                    List.of(),
                                    List.of(),
                                    Map.of(),
                                    ProductContract.RouteState.MIGRATION,
                                    uiState,
                                    accessibility
                            )
                    ))
                    .build();
        });
    }

    @Test
    void uiStateDeclaration_shouldRequireMessageKeyWhenStateRequired() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProductContract.UIStateDeclaration(
                    true, false, false, false,
                    "", "error", "empty", "forbidden"
            );
        });
    }

    @Test
    void uiStateDeclaration_shouldAllowEmptyMessageKeyWhenStateNotRequired() {
        assertDoesNotThrow(() -> {
            new ProductContract.UIStateDeclaration(
                    false, false, false, false,
                    "", "", "", ""
            );
        });
    }

    @Test
    void accessibilityDeclaration_shouldRequireTitleAndDescription() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ProductContract.AccessibilityDeclaration(
                    "", "description", "", false, false
            );
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new ProductContract.AccessibilityDeclaration(
                    "title", "", "", false, false
            );
        });
    }

    @Test
    void accessibilityDeclaration_shouldAllowOptionalAriaLabel() {
        assertDoesNotThrow(() -> {
            new ProductContract.AccessibilityDeclaration(
                    "title", "description", "", false, false
            );
        });
    }

    @Test
    void cleanupMode_shouldDeleteStaleArtifacts(@TempDir Path tempDir) throws IOException {
        // Create a stale artifact file
        Path staleFile = tempDir.resolve("route-old-route.generated.ts");
        Files.writeString(staleFile, "// stale artifact");

        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of(
                        new ProductContract.RouteDeclaration(
                                "route-1",
                                "/api/test",
                                "GET",
                                List.of(),
                                List.of(),
                                Map.of(),
                                ProductContract.RouteState.ACTIVE,
                                uiState,
                                accessibility
                        )
                ))
                .build();

        RouteContractGenerator generator = RouteContractGenerator.forContractWithCleanup(contract, tempDir);
        generator.cleanupStaleArtifacts();

        // Stale file should be deleted
        assertFalse(Files.exists(staleFile));
    }

    @Test
    void cleanupMode_shouldKeepCurrentArtifacts(@TempDir Path tempDir) throws IOException {
        // Create a current artifact file
        Path currentFile = tempDir.resolve("route-route-1.generated.ts");
        Files.writeString(currentFile, "// current artifact");

        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of(
                        new ProductContract.RouteDeclaration(
                                "route-1",
                                "/api/test",
                                "GET",
                                List.of(),
                                List.of(),
                                Map.of(),
                                ProductContract.RouteState.ACTIVE,
                                uiState,
                                accessibility
                        )
                ))
                .build();

        RouteContractGenerator generator = RouteContractGenerator.forContractWithCleanup(contract, tempDir);
        generator.cleanupStaleArtifacts();

        // Current file should still exist
        assertTrue(Files.exists(currentFile));
    }

    @Test
    void cleanupMode_shouldDoNothingWhenDisabled(@TempDir Path tempDir) throws IOException {
        // Create a stale artifact file
        Path staleFile = tempDir.resolve("route-old-route.generated.ts");
        Files.writeString(staleFile, "// stale artifact");

        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of(
                        new ProductContract.RouteDeclaration(
                                "route-1",
                                "/api/test",
                                "GET",
                                List.of(),
                                List.of(),
                                Map.of(),
                                ProductContract.RouteState.ACTIVE,
                                uiState,
                                accessibility
                        )
                ))
                .build();

        RouteContractGenerator generator = RouteContractGenerator.forContract(contract);
        generator.cleanupStaleArtifacts();

        // Stale file should still exist when cleanup mode is disabled
        assertTrue(Files.exists(staleFile));
    }

    @Test
    void cleanupMode_shouldHandleNonExistentDirectory() {
        ProductContract.UIStateDeclaration uiState = new ProductContract.UIStateDeclaration(
                false, false, false, false,
                "", "", "", ""
        );
        ProductContract.AccessibilityDeclaration accessibility = new ProductContract.AccessibilityDeclaration(
                "route.title", "route.description", "", false, false
        );
        ProductContract contract = ProductContract.builder(
                "test-contract",
                "Test Contract",
                "1.0.0",
                "test-product"
        )
                .routes(List.of(
                        new ProductContract.RouteDeclaration(
                                "route-1",
                                "/api/test",
                                "GET",
                                List.of(),
                                List.of(),
                                Map.of(),
                                ProductContract.RouteState.ACTIVE,
                                uiState,
                                accessibility
                        )
                ))
                .build();

        Path nonExistentDir = Path.of("/tmp/non-existent-dir-" + System.currentTimeMillis());
        RouteContractGenerator generator = RouteContractGenerator.forContractWithCleanup(contract, nonExistentDir);

        assertDoesNotThrow(() -> generator.cleanupStaleArtifacts());
    }
}
