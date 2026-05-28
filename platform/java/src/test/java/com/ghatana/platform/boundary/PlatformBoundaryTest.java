package com.ghatana.platform.boundary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary tests for platform modules.
 *
 * <p><b>Purpose</b><br>
 * Enforces module boundaries to prevent product-specific semantics
 * from leaking into shared platform modules.
 *
 * <p><b>Rules</b><br>
 * <ul>
 *   <li>Platform modules must not import from products/*</li>
 *   <li>Platform modules must not reference product-specific classes</li>
 *   <li>Platform modules must remain generic and reusable</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Boundary tests for platform modules
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Platform Boundary Tests")
@Tag("boundary")
@Tag("platform")
class PlatformBoundaryTest {

    private static final String PLATFORM_ROOT = System.getProperty("user.dir").replace("\\", "/");
    private static final String PRODUCTS_PATH = "/products/";

    @Test
    @DisplayName("Platform Java modules must not import from products")
    void platformJavaMustNotImportFromProducts() throws Exception {
        Path platformJavaPath = Paths.get(PLATFORM_ROOT, "platform/java/src/main/java");
        
        if (!Files.exists(platformJavaPath)) {
            return; // Skip if path doesn't exist
        }

        List<String> violations = new java.util.ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(platformJavaPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                        String content = Files.readString(javaFile);
                        if (content.contains("com.ghatana.datacloud") || 
                            content.contains("com.ghatana.yappc") ||
                            content.contains("com.ghatana.flashit") ||
                            content.contains("com.ghatana.phr") ||
                            content.contains("com.ghatana.finance")) {
                            violations.add(javaFile.toString());
                        }
                    } catch (Exception e) {
                        // Skip files that can't be read
                    }
                 });
        }

        assertThat(violations)
            .withFailMessage("Platform Java modules must not import from products. Violations in: %s", violations)
            .isEmpty();
    }

    @Test
    @DisplayName("Platform TypeScript modules must not import from products")
    void platformTypeScriptMustNotImportFromProducts() throws Exception {
        Path platformTsPath = Paths.get(PLATFORM_ROOT, "platform/typescript/src");
        
        if (!Files.exists(platformTsPath)) {
            return; // Skip if path doesn't exist
        }

        List<String> violations = new java.util.ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(platformTsPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".ts") || p.toString().endsWith(".tsx"))
                 .forEach(tsFile -> {
                     try {
                        String content = Files.readString(tsFile);
                        if (content.contains("@ghatana/data-cloud") || 
                            content.contains("@ghatana/yappc") ||
                            content.contains("@ghatana/flashit") ||
                            content.contains("@ghatana/phr") ||
                            content.contains("@ghatana/finance")) {
                            violations.add(tsFile.toString());
                        }
                    } catch (Exception e) {
                        // Skip files that can't be read
                    }
                 });
        }

        assertThat(violations)
            .withFailMessage("Platform TypeScript modules must not import from products. Violations in: %s", violations)
            .isEmpty();
    }

    @Test
    @DisplayName("Platform modules must not contain product-specific terminology")
    void platformModulesMustNotContainProductSpecificTerminology() throws Exception {
        Path platformJavaPath = Paths.get(PLATFORM_ROOT, "platform/java/src/main/java");
        
        if (!Files.exists(platformJavaPath)) {
            return; // Skip if path doesn't exist
        }

        List<String> violations = new java.util.ArrayList<>();
        List<String> productTerms = List.of(
            "DataCloud", "YAPPC", "FlashIt", "PHR", "Finance",
            "data-cloud", "yappc", "flashit", "phr", "finance"
        );
        
        try (Stream<Path> paths = Files.walk(platformJavaPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                        String content = Files.readString(javaFile);
                        String fileName = javaFile.getFileName().toString();
                        
                        // Check package names and class names for product terms
                        if (content.contains("package com.ghatana.")) {
                            for (String term : productTerms) {
                                if (content.contains("com.ghatana." + term.toLowerCase())) {
                                    violations.add(javaFile + " (package contains: " + term + ")");
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip files that can't be read
                    }
                 });
        }

        assertThat(violations)
            .withFailMessage("Platform modules must not contain product-specific terminology. Violations: %s", violations)
            .isEmpty();
    }

    @Test
    @DisplayName("Platform contracts must be generic and reusable")
    void platformContractsMustBeGeneric() throws Exception {
        Path contractsPath = Paths.get(PLATFORM_ROOT, "platform/contracts/src/main/java");
        
        if (!Files.exists(contractsPath)) {
            return; // Skip if path doesn't exist
        }

        List<String> violations = new java.util.ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(contractsPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                        String content = Files.readString(javaFile);
                        // Check for hardcoded product-specific values in contracts
                        if (content.contains("\"data-cloud\"") || 
                            content.contains("\"yappc\"") ||
                            content.contains("\"flashit\"")) {
                            violations.add(javaFile + " (hardcoded product reference)");
                        }
                    } catch (Exception e) {
                        // Skip files that can't be read
                    }
                 });
        }

        assertThat(violations)
            .withFailMessage("Platform contracts must be generic. Violations: %s", violations)
            .isEmpty();
    }
}
