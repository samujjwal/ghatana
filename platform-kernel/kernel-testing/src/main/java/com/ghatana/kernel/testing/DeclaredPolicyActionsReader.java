package com.ghatana.kernel.testing;

import java.nio.file.Path;
import java.util.Set;

/**
 * Test helper for reading product-declared policy actions from domain-pack manifests.
 *
 * @doc.type class
 * @doc.purpose Reads declared policy action vocabularies from product manifests for contract tests
 * @doc.layer testing
 * @doc.pattern Utility
 */
public final class DeclaredPolicyActionsReader {
    private DeclaredPolicyActionsReader() {
    }

    public static Set<String> read(Path manifestPath) {
        return DeclaredPolicyManifestListReader.read(manifestPath, "policyActions");
    }
}
