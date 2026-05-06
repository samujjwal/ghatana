package com.ghatana.kernel.testing;

import java.nio.file.Path;
import java.util.Set;

/**
 * Test helper for reading product-declared policy resource namespaces from domain-pack manifests.
 *
 * @doc.type class
 * @doc.purpose Reads declared policy resource namespaces from product manifests for contract tests
 * @doc.layer testing
 * @doc.pattern Utility
 */
public final class DeclaredPolicyResourcesReader {

    private DeclaredPolicyResourcesReader() {
    }

    public static Set<String> read(Path manifestPath) {
        return DeclaredPolicyManifestListReader.read(manifestPath, "policyResources");
    }
}
