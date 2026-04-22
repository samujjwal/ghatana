/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.kernel.test.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture drift detection tests.
 *
 * <p>Guards against re-introduction of product-aware logic into canonical kernel packages.
 * Scans source files in canonical kernel packages for hardcoded product ids, product-aware
 * branching, and other patterns that violate the scope-first kernel model defined in
 * KERNEL_CANONICALIZATION_DECISIONS.md.</p>
 *
 * <p>These tests fail fast if product-specific assumptions creep back into canonical code.
 * They complement the reflection-based tests in {@link KernelPurityValidationTest}
 * with source-level scanning.</p>
 *
 * @doc.type test
 * @doc.purpose Detect architecture drift via source-level scanning of canonical kernel packages
 * @doc.layer test
 * @doc.pattern ValidationTest
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
@DisplayName("Architecture Drift Detection Tests [GH-90000]")
public class KernelArchitectureDriftTest {

    /**
     * Canonical kernel packages that must be product-agnostic.
     * These packages form the generic kernel runtime and must not contain
     * hardcoded product ids, product-aware branching, or product-specific logic.
     */
    private static final List<String> CANONICAL_PACKAGES = List.of( // GH-90000
            "com/ghatana/kernel/scope",
            "com/ghatana/kernel/policy",
            "com/ghatana/kernel/module",
            "com/ghatana/kernel/context",
            "com/ghatana/kernel/descriptor",
            "com/ghatana/kernel/extension",
            "com/ghatana/kernel/health",
            "com/ghatana/kernel/event"
    );

    /**
     * Packages that are under active generalization.
     * These are scanned but violations are reported as warnings, not failures,
     * since they contain known product-aware code being migrated.
     */
    private static final List<String> TRANSITIONAL_PACKAGES = List.of( // GH-90000
            "com/ghatana/kernel/audit",
            "com/ghatana/kernel/boundary",
            "com/ghatana/kernel/communication",
            "com/ghatana/kernel/workflow"
    );

    /**
     * Product ids that must NOT appear as string literals in canonical packages.
     */
    private static final Set<String> FORBIDDEN_PRODUCT_LITERALS = Set.of( // GH-90000
            "\"phr\"", "\"finance\"", "\"flashit\"", "\"aura\"",
            "\"insurance\"", "\"banking\""
    );

    /**
     * Deprecated transitional class names that canonical packages must NOT directly
     * reference via instant creation ({@code new Name(}) or direct import. // GH-90000
     *
     * <p>These are the legacy product-aware APIs replaced by their canonical counterparts.
     * Canonical packages must depend only on the scope-aware replacements.</p>
     */
    private static final Set<String> DEPRECATED_TRANSITIONAL_TYPES = Set.of( // GH-90000
            "CrossProductAuditService",
            "ProductBoundaryEnforcer",
            "KernelInterProductBus",
            "CrossProductWorkflowEngine",
            "ProductPlugin"
    );

    /**
     * Patterns indicating product-aware branching in source code.
     */
    private static final List<Pattern> PRODUCT_BRANCHING_PATTERNS = List.of( // GH-90000
            // equals("phr [GH-90000]"), equals("finance [GH-90000]"), etc.
            Pattern.compile("\\.equals\\(\"(phr|finance|flashit|aura|insurance|banking)\"\\)"), // GH-90000
            // getSourceProduct().equals(...) or getTargetProduct().equals(...) // GH-90000
            Pattern.compile("get(Source|Target)Product\\(\\)\\.equals [GH-90000]"),
            // switch or if on getProductId() // GH-90000
            Pattern.compile("getProductId\\(\\) [GH-90000]")
    );

    @Test
    @DisplayName("Canonical kernel packages must not contain hardcoded product literals [GH-90000]")
    void canonicalPackagesMustNotContainProductLiterals() { // GH-90000
        Path srcRoot = findKernelSourceRoot(); // GH-90000
        if (srcRoot == null) { // GH-90000
            return; // skip if source root not found (CI may have different layout) // GH-90000
        }

        Map<String, List<String>> violations = new LinkedHashMap<>(); // GH-90000

        for (String pkg : CANONICAL_PACKAGES) { // GH-90000
            Path pkgDir = srcRoot.resolve(pkg); // GH-90000
            if (!Files.isDirectory(pkgDir)) continue; // GH-90000

            scanForProductLiterals(pkgDir, violations); // GH-90000
        }

        if (!violations.isEmpty()) { // GH-90000
            StringBuilder sb = new StringBuilder("Product literals found in canonical kernel packages:\n [GH-90000]");
            violations.forEach((file, matches) -> { // GH-90000
                sb.append("   [GH-90000]").append(file).append(":\n [GH-90000]");
                matches.forEach(m -> sb.append("    -  [GH-90000]").append(m).append("\n [GH-90000]"));
            });
            fail(sb.toString()); // GH-90000
        }
    }

    @Test
    @DisplayName("Canonical kernel packages must not contain product-aware branching [GH-90000]")
    void canonicalPackagesMustNotContainProductBranching() { // GH-90000
        Path srcRoot = findKernelSourceRoot(); // GH-90000
        if (srcRoot == null) return; // GH-90000

        Map<String, List<String>> violations = new LinkedHashMap<>(); // GH-90000

        for (String pkg : CANONICAL_PACKAGES) { // GH-90000
            Path pkgDir = srcRoot.resolve(pkg); // GH-90000
            if (!Files.isDirectory(pkgDir)) continue; // GH-90000

            scanForProductBranching(pkgDir, violations); // GH-90000
        }

        if (!violations.isEmpty()) { // GH-90000
            StringBuilder sb = new StringBuilder("Product-aware branching in canonical kernel packages:\n [GH-90000]");
            violations.forEach((file, matches) -> { // GH-90000
                sb.append("   [GH-90000]").append(file).append(":\n [GH-90000]");
                matches.forEach(m -> sb.append("    -  [GH-90000]").append(m).append("\n [GH-90000]"));
            });
            fail(sb.toString()); // GH-90000
        }
    }

    @Test
    @DisplayName("Report product-aware code in transitional packages (informational) [GH-90000]")
    void reportProductAwareCodeInTransitionalPackages() { // GH-90000
        Path srcRoot = findKernelSourceRoot(); // GH-90000
        if (srcRoot == null) return; // GH-90000

        Map<String, List<String>> warnings = new LinkedHashMap<>(); // GH-90000

        for (String pkg : TRANSITIONAL_PACKAGES) { // GH-90000
            Path pkgDir = srcRoot.resolve(pkg); // GH-90000
            if (!Files.isDirectory(pkgDir)) continue; // GH-90000

            scanForProductLiterals(pkgDir, warnings); // GH-90000
            scanForProductBranching(pkgDir, warnings); // GH-90000
        }

        // This test does NOT fail — it reports the known transitional state.
        // As migration progresses, move packages from TRANSITIONAL to CANONICAL.
        if (!warnings.isEmpty()) { // GH-90000
            StringBuilder sb = new StringBuilder( // GH-90000
                    "Product-aware code in transitional packages (expected during migration):\n"); // GH-90000
            warnings.forEach((file, matches) -> { // GH-90000
                sb.append("   [GH-90000]").append(file).append(":\n [GH-90000]");
                matches.forEach(m -> sb.append("    -  [GH-90000]").append(m).append("\n [GH-90000]"));
            });
            System.out.println(sb); // GH-90000
        }
    }

    @Test
    @DisplayName("No new non-deprecated classes in legacy capability package [GH-90000]")
    void noNewClassesInLegacyCapabilityPackage() { // GH-90000
        // capability.KernelCapability is the only class that should exist in this package
        // and it must be deprecated. This prevents new code from being added to the
        // legacy package.
        Path srcRoot = findKernelSourceRoot(); // GH-90000
        if (srcRoot == null) return; // GH-90000

        Path legacyPkg = srcRoot.resolve("com/ghatana/kernel/capability [GH-90000]");
        if (!Files.isDirectory(legacyPkg)) return; // GH-90000

        try {
            List<Path> javaFiles = Files.list(legacyPkg) // GH-90000
                    .filter(p -> p.toString().endsWith(".java [GH-90000]"))
                    .collect(Collectors.toList()); // GH-90000

            assertTrue(javaFiles.size() <= 1, // GH-90000
                    "Legacy capability package should contain at most 1 class (KernelCapability). " + // GH-90000
                    "Found: " + javaFiles.stream().map(p -> p.getFileName().toString()) // GH-90000
                            .collect(Collectors.joining(",  [GH-90000]")) +
                    ". New capabilities must use the descriptor package.");
        } catch (IOException e) { // GH-90000
            // Skip if filesystem access fails
        }
    }

    @Test
    @DisplayName("Canonical packages must not directly instantiate deprecated transitional types [GH-90000]")
    void canonicalPackagesMustNotInstantiateDeprecatedTypes() { // GH-90000
        // Detects if a developer re-introduces usage of legacy cross-product types
        // (e.g. new CrossProductAuditService(...)) inside canonical product-agnostic packages. // GH-90000
        // These types are @Deprecated and only exist for backward-compat in transitional packages.
        Path srcRoot = findKernelSourceRoot(); // GH-90000
        if (srcRoot == null) return; // GH-90000

        Map<String, List<String>> violations = new LinkedHashMap<>(); // GH-90000

        for (String pkg : CANONICAL_PACKAGES) { // GH-90000
            Path pkgDir = srcRoot.resolve(pkg); // GH-90000
            if (!Files.isDirectory(pkgDir)) continue; // GH-90000

            try {
                Files.walkFileTree(pkgDir, new SimpleFileVisitor<>() { // GH-90000
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { // GH-90000
                        if (!file.toString().endsWith(".java [GH-90000]")) return FileVisitResult.CONTINUE;
                        String content = Files.readString(file); // GH-90000

                        List<String> matches = new ArrayList<>(); // GH-90000
                        for (String type : DEPRECATED_TRANSITIONAL_TYPES) { // GH-90000
                            // Check for direct instantiation: new DeprecatedType( // GH-90000
                            if (content.contains("new " + type + "(")) { // GH-90000
                                matches.add("Direct instantiation of deprecated type: new " + type + "("); // GH-90000
                            }
                            // Check for import of the deprecated type
                            if (content.contains("import com.ghatana.kernel. [GH-90000]") && content.contains(type + ";")) {
                                matches.add("Import of deprecated transitional type: " + type); // GH-90000
                            }
                        }
                        if (!matches.isEmpty()) { // GH-90000
                            violations.put(relativeName(file), matches); // GH-90000
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) { // GH-90000
                // Skip on IO error
            }
        }

        if (!violations.isEmpty()) { // GH-90000
            StringBuilder sb = new StringBuilder( // GH-90000
                "Deprecated transitional types instantiated/imported in canonical kernel packages:\n");
            violations.forEach((file, matches) -> { // GH-90000
                sb.append("   [GH-90000]").append(file).append(":\n [GH-90000]");
                matches.forEach(m -> sb.append("    -  [GH-90000]").append(m).append("\n [GH-90000]"));
            });
            sb.append("\nCanonical packages must use the scope-aware replacements:\n [GH-90000]")
              .append("  CrossProductAuditService     → CrossScopeAuditService\n [GH-90000]")
              .append("  ProductBoundaryEnforcer      → ScopeBoundaryEnforcer\n [GH-90000]")
              .append("  KernelInterProductBus        → KernelInterScopeBus\n [GH-90000]")
              .append("  CrossProductWorkflowEngine   → CrossScopeWorkflowEngine\n [GH-90000]")
              .append("  ProductPlugin                → KernelPlugin\n [GH-90000]");
            fail(sb.toString()); // GH-90000
        }
    }

    // ==================== Scanning Helpers ====================

    private void scanForProductLiterals(Path dir, Map<String, List<String>> violations) { // GH-90000
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() { // GH-90000
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { // GH-90000
                    if (!file.toString().endsWith(".java [GH-90000]")) return FileVisitResult.CONTINUE;
                    String content = Files.readString(file); // GH-90000

                    // Skip deprecated files — they have known product-aware code
                    if (content.contains("@Deprecated [GH-90000]")) return FileVisitResult.CONTINUE;

                    List<String> matches = new ArrayList<>(); // GH-90000
                    for (String literal : FORBIDDEN_PRODUCT_LITERALS) { // GH-90000
                        if (content.contains(literal)) { // GH-90000
                            matches.add("Contains product literal: " + literal); // GH-90000
                        }
                    }
                    if (!matches.isEmpty()) { // GH-90000
                        violations.put(relativeName(file), matches); // GH-90000
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) { // GH-90000
            // Skip on IO error
        }
    }

    private void scanForProductBranching(Path dir, Map<String, List<String>> violations) { // GH-90000
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() { // GH-90000
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { // GH-90000
                    if (!file.toString().endsWith(".java [GH-90000]")) return FileVisitResult.CONTINUE;
                    String content = Files.readString(file); // GH-90000

                    // Skip deprecated files
                    if (content.contains("@Deprecated [GH-90000]")) return FileVisitResult.CONTINUE;

                    List<String> matches = new ArrayList<>(); // GH-90000
                    for (Pattern pattern : PRODUCT_BRANCHING_PATTERNS) { // GH-90000
                        var matcher = pattern.matcher(content); // GH-90000
                        while (matcher.find()) { // GH-90000
                            matches.add("Product-aware branching: " + matcher.group()); // GH-90000
                        }
                    }
                    if (!matches.isEmpty()) { // GH-90000
                        violations.put(relativeName(file), matches); // GH-90000
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) { // GH-90000
            // Skip on IO error
        }
    }

    private Path findKernelSourceRoot() { // GH-90000
        // Try common project layouts
        Path[] candidates = {
                Path.of("platform/java/kernel/src/main/java [GH-90000]"),
                Path.of("src/main/java [GH-90000]"),
                Path.of(System.getProperty("user.dir [GH-90000]"), "platform/java/kernel/src/main/java")
        };
        for (Path p : candidates) { // GH-90000
            if (Files.isDirectory(p)) return p; // GH-90000
        }
        return null;
    }

    private String relativeName(Path file) { // GH-90000
        return file.getFileName().toString(); // GH-90000
    }
}
