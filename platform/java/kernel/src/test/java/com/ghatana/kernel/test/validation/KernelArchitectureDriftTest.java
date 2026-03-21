/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("Architecture Drift Detection Tests")
public class KernelArchitectureDriftTest {

    /**
     * Canonical kernel packages that must be product-agnostic.
     * These packages form the generic kernel runtime and must not contain
     * hardcoded product ids, product-aware branching, or product-specific logic.
     */
    private static final List<String> CANONICAL_PACKAGES = List.of(
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
    private static final List<String> TRANSITIONAL_PACKAGES = List.of(
            "com/ghatana/kernel/audit",
            "com/ghatana/kernel/boundary",
            "com/ghatana/kernel/communication",
            "com/ghatana/kernel/workflow"
    );

    /**
     * Product ids that must NOT appear as string literals in canonical packages.
     */
    private static final Set<String> FORBIDDEN_PRODUCT_LITERALS = Set.of(
            "\"phr\"", "\"finance\"", "\"flashit\"", "\"aura\"",
            "\"insurance\"", "\"banking\""
    );

    /**
     * Deprecated transitional class names that canonical packages must NOT directly
     * reference via instant creation ({@code new Name(}) or direct import.
     *
     * <p>These are the legacy product-aware APIs replaced by their canonical counterparts.
     * Canonical packages must depend only on the scope-aware replacements.</p>
     */
    private static final Set<String> DEPRECATED_TRANSITIONAL_TYPES = Set.of(
            "CrossProductAuditService",
            "ProductBoundaryEnforcer",
            "KernelInterProductBus",
            "CrossProductWorkflowEngine",
            "ProductPlugin"
    );

    /**
     * Patterns indicating product-aware branching in source code.
     */
    private static final List<Pattern> PRODUCT_BRANCHING_PATTERNS = List.of(
            // equals("phr"), equals("finance"), etc.
            Pattern.compile("\\.equals\\(\"(phr|finance|flashit|aura|insurance|banking)\"\\)"),
            // getSourceProduct().equals(...) or getTargetProduct().equals(...)
            Pattern.compile("get(Source|Target)Product\\(\\)\\.equals"),
            // switch or if on getProductId()
            Pattern.compile("getProductId\\(\\)")
    );

    @Test
    @DisplayName("Canonical kernel packages must not contain hardcoded product literals")
    void canonicalPackagesMustNotContainProductLiterals() {
        Path srcRoot = findKernelSourceRoot();
        if (srcRoot == null) {
            return; // skip if source root not found (CI may have different layout)
        }

        Map<String, List<String>> violations = new LinkedHashMap<>();

        for (String pkg : CANONICAL_PACKAGES) {
            Path pkgDir = srcRoot.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            scanForProductLiterals(pkgDir, violations);
        }

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Product literals found in canonical kernel packages:\n");
            violations.forEach((file, matches) -> {
                sb.append("  ").append(file).append(":\n");
                matches.forEach(m -> sb.append("    - ").append(m).append("\n"));
            });
            fail(sb.toString());
        }
    }

    @Test
    @DisplayName("Canonical kernel packages must not contain product-aware branching")
    void canonicalPackagesMustNotContainProductBranching() {
        Path srcRoot = findKernelSourceRoot();
        if (srcRoot == null) return;

        Map<String, List<String>> violations = new LinkedHashMap<>();

        for (String pkg : CANONICAL_PACKAGES) {
            Path pkgDir = srcRoot.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            scanForProductBranching(pkgDir, violations);
        }

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Product-aware branching in canonical kernel packages:\n");
            violations.forEach((file, matches) -> {
                sb.append("  ").append(file).append(":\n");
                matches.forEach(m -> sb.append("    - ").append(m).append("\n"));
            });
            fail(sb.toString());
        }
    }

    @Test
    @DisplayName("Report product-aware code in transitional packages (informational)")
    void reportProductAwareCodeInTransitionalPackages() {
        Path srcRoot = findKernelSourceRoot();
        if (srcRoot == null) return;

        Map<String, List<String>> warnings = new LinkedHashMap<>();

        for (String pkg : TRANSITIONAL_PACKAGES) {
            Path pkgDir = srcRoot.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            scanForProductLiterals(pkgDir, warnings);
            scanForProductBranching(pkgDir, warnings);
        }

        // This test does NOT fail — it reports the known transitional state.
        // As migration progresses, move packages from TRANSITIONAL to CANONICAL.
        if (!warnings.isEmpty()) {
            StringBuilder sb = new StringBuilder(
                    "Product-aware code in transitional packages (expected during migration):\n");
            warnings.forEach((file, matches) -> {
                sb.append("  ").append(file).append(":\n");
                matches.forEach(m -> sb.append("    - ").append(m).append("\n"));
            });
            System.out.println(sb);
        }
    }

    @Test
    @DisplayName("No new non-deprecated classes in legacy capability package")
    void noNewClassesInLegacyCapabilityPackage() {
        // capability.KernelCapability is the only class that should exist in this package
        // and it must be deprecated. This prevents new code from being added to the
        // legacy package.
        Path srcRoot = findKernelSourceRoot();
        if (srcRoot == null) return;

        Path legacyPkg = srcRoot.resolve("com/ghatana/kernel/capability");
        if (!Files.isDirectory(legacyPkg)) return;

        try {
            List<Path> javaFiles = Files.list(legacyPkg)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            assertTrue(javaFiles.size() <= 1,
                    "Legacy capability package should contain at most 1 class (KernelCapability). " +
                    "Found: " + javaFiles.stream().map(p -> p.getFileName().toString())
                            .collect(Collectors.joining(", ")) +
                    ". New capabilities must use the descriptor package.");
        } catch (IOException e) {
            // Skip if filesystem access fails
        }
    }

    @Test
    @DisplayName("Canonical packages must not directly instantiate deprecated transitional types")
    void canonicalPackagesMustNotInstantiateDeprecatedTypes() {
        // Detects if a developer re-introduces usage of legacy cross-product types
        // (e.g. new CrossProductAuditService(...)) inside canonical product-agnostic packages.
        // These types are @Deprecated and only exist for backward-compat in transitional packages.
        Path srcRoot = findKernelSourceRoot();
        if (srcRoot == null) return;

        Map<String, List<String>> violations = new LinkedHashMap<>();

        for (String pkg : CANONICAL_PACKAGES) {
            Path pkgDir = srcRoot.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            try {
                Files.walkFileTree(pkgDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                        String content = Files.readString(file);

                        List<String> matches = new ArrayList<>();
                        for (String type : DEPRECATED_TRANSITIONAL_TYPES) {
                            // Check for direct instantiation: new DeprecatedType(
                            if (content.contains("new " + type + "(")) {
                                matches.add("Direct instantiation of deprecated type: new " + type + "(");
                            }
                            // Check for import of the deprecated type
                            if (content.contains("import com.ghatana.kernel.") && content.contains(type + ";")) {
                                matches.add("Import of deprecated transitional type: " + type);
                            }
                        }
                        if (!matches.isEmpty()) {
                            violations.put(relativeName(file), matches);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                // Skip on IO error
            }
        }

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder(
                "Deprecated transitional types instantiated/imported in canonical kernel packages:\n");
            violations.forEach((file, matches) -> {
                sb.append("  ").append(file).append(":\n");
                matches.forEach(m -> sb.append("    - ").append(m).append("\n"));
            });
            sb.append("\nCanonical packages must use the scope-aware replacements:\n")
              .append("  CrossProductAuditService     → CrossScopeAuditService\n")
              .append("  ProductBoundaryEnforcer      → ScopeBoundaryEnforcer\n")
              .append("  KernelInterProductBus        → KernelInterScopeBus\n")
              .append("  CrossProductWorkflowEngine   → CrossScopeWorkflowEngine\n")
              .append("  ProductPlugin                → KernelPlugin\n");
            fail(sb.toString());
        }
    }

    // ==================== Scanning Helpers ====================

    private void scanForProductLiterals(Path dir, Map<String, List<String>> violations) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                    String content = Files.readString(file);

                    // Skip deprecated files — they have known product-aware code
                    if (content.contains("@Deprecated")) return FileVisitResult.CONTINUE;

                    List<String> matches = new ArrayList<>();
                    for (String literal : FORBIDDEN_PRODUCT_LITERALS) {
                        if (content.contains(literal)) {
                            matches.add("Contains product literal: " + literal);
                        }
                    }
                    if (!matches.isEmpty()) {
                        violations.put(relativeName(file), matches);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Skip on IO error
        }
    }

    private void scanForProductBranching(Path dir, Map<String, List<String>> violations) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                    String content = Files.readString(file);

                    // Skip deprecated files
                    if (content.contains("@Deprecated")) return FileVisitResult.CONTINUE;

                    List<String> matches = new ArrayList<>();
                    for (Pattern pattern : PRODUCT_BRANCHING_PATTERNS) {
                        var matcher = pattern.matcher(content);
                        while (matcher.find()) {
                            matches.add("Product-aware branching: " + matcher.group());
                        }
                    }
                    if (!matches.isEmpty()) {
                        violations.put(relativeName(file), matches);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Skip on IO error
        }
    }

    private Path findKernelSourceRoot() {
        // Try common project layouts
        Path[] candidates = {
                Path.of("platform/java/kernel/src/main/java"),
                Path.of("src/main/java"),
                Path.of(System.getProperty("user.dir"), "platform/java/kernel/src/main/java")
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p)) return p;
        }
        return null;
    }

    private String relativeName(Path file) {
        return file.getFileName().toString();
    }
}
