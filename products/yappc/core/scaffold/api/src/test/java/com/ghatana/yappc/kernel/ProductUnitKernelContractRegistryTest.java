/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract parity tests for imported Kernel ProductUnit public values.
 *
 * @doc.type test
 * @doc.purpose Fails when YAPPC ProductUnit contract import drifts from Kernel public TypeScript constants
 * @doc.layer product
 * @doc.pattern ContractTest
 */
class ProductUnitKernelContractRegistryTest {

    private static final Pattern ARRAY_PATTERN = Pattern.compile(
            "export\\s+const\\s+%s\\s*=\\s*\\[(.*?)\\]\\s*as\\s+const",
            Pattern.DOTALL);
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"([^\"]+)\"");

    @Test
    void importedContractValuesMatchKernelProductUnitPublicConstants() throws IOException {
        ProductUnitKernelContractRegistry registry = new ProductUnitKernelContractRegistry();
        Path repoRoot = findRepoRoot(Path.of("").toAbsolutePath());

        String surfaceSource = Files.readString(repoRoot.resolve(
                "platform/typescript/kernel-product-contracts/src/product-unit/ProductUnitSurface.ts"));
        String kindSource = Files.readString(repoRoot.resolve(
                "platform/typescript/kernel-product-contracts/src/product-unit/ProductUnitKind.ts"));

        assertThat(registry.surfaces())
                .containsExactlyInAnyOrderElementsOf(exportedArray(surfaceSource, "PRODUCT_UNIT_SURFACE_TYPES"));
        assertThat(registry.productUnitKinds())
                .containsExactlyInAnyOrderElementsOf(exportedArray(kindSource, "PRODUCT_UNIT_KINDS"));
        assertThat(registry.implementationStatuses())
                .containsExactlyInAnyOrderElementsOf(exportedArray(surfaceSource, "IMPLEMENTATION_STATUSES"));
    }

    private static Path findRepoRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve(".github/copilot-instructions.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root from " + start);
    }

    private static Set<String> exportedArray(String source, String constantName) {
        Pattern pattern = Pattern.compile(String.format(ARRAY_PATTERN.pattern(), constantName), Pattern.DOTALL);
        Matcher arrayMatcher = pattern.matcher(source);
        if (!arrayMatcher.find()) {
            throw new IllegalStateException("Missing exported Kernel contract array: " + constantName);
        }

        Matcher literalMatcher = STRING_LITERAL_PATTERN.matcher(arrayMatcher.group(1));
        Set<String> values = new LinkedHashSet<>();
        while (literalMatcher.find()) {
            values.add(literalMatcher.group(1));
        }
        return values;
    }
}
