/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.integration.registry;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.StateMutability;
import com.ghatana.agent.framework.runtime.AutonomyLevel;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Integration test that reads every agent-spec YAML file from the AEP agent catalog
 * operators directory and asserts that all {@code identity.*} enum fields resolve to
 * canonical platform constants.
 *
 * <p>This test acts as the enforcement gate for {@code P1-T6}. Any new catalog file
 * containing an unknown or deprecated enum value will cause this test to fail.
 *
 * <p>AEP operator YAML files use a simplified format (not the full 18-section platform // GH-90000
 * spec), so this test reads the raw {@code identity} block via YAML parsing and validates
 * the string values against the canonical enums via their resolution methods.
 *
 * @doc.type class
 * @doc.purpose Catalog-wide canonical enum validation for all AEP operator YAML files
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("CatalogCanonicalValues — all AEP operator YAMLs have canonical enum values")
class CatalogCanonicalValuesTest {

    private static final String CATALOG_OPERATORS_DIR = "products/aep/agent-catalog/operators";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()); // GH-90000

    /**
     * Generates one {@link DynamicTest} per YAML file found under the AEP catalog
     * operators directory. Each test validates the {@code identity.*} enum fields.
     */
    @TestFactory
    Stream<DynamicTest> allOperatorYamlsHaveCanonicalEnumValues() throws IOException { // GH-90000
        Path catalogDir = resolveRepoRoot().resolve(CATALOG_OPERATORS_DIR); // GH-90000
        assertThat(catalogDir).isDirectory().as("AEP catalog operators directory must exist at %s", catalogDir); // GH-90000

        return Files.walk(catalogDir) // GH-90000
                .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                .sorted() // GH-90000
                .map(yamlFile -> DynamicTest.dynamicTest( // GH-90000
                        "catalog: " + catalogDir.relativize(yamlFile), () -> validateIdentityBlock(yamlFile))); // GH-90000
    }

    private void validateIdentityBlock(Path yamlFile) throws IOException { // GH-90000
        JsonNode root = YAML.readTree(yamlFile.toFile()); // GH-90000
        JsonNode identity = root.path("identity");

        assertThat(identity.isMissingNode()) // GH-90000
                .as("'identity' block is missing in %s", yamlFile.getFileName()) // GH-90000
                .isFalse(); // GH-90000

        // agentType — must resolve to a non-deprecated canonical AgentType
        String rawAgentType = identity.path("agentType").asText(null);
        assertThat(rawAgentType) // GH-90000
                .as("identity.agentType must be present in %s", yamlFile.getFileName()) // GH-90000
                .isNotBlank(); // GH-90000
        AgentType agentType;
        try {
            agentType = AgentType.valueOf(rawAgentType.toUpperCase().replace('-', '_')); // GH-90000
        } catch (IllegalArgumentException e) { // GH-90000
            throw new AssertionError( // GH-90000
                    "identity.agentType '" + rawAgentType + "' in " + yamlFile.getFileName() // GH-90000
                            + " is not a valid AgentType. Valid values: " + java.util.Arrays.asList(AgentType.values()), // GH-90000
                    e);
        }
        // All AgentType values are now canonical (deprecated LLM type was removed) // GH-90000
        assertThat(agentType) // GH-90000
                .as("identity.agentType in %s must be a valid AgentType", yamlFile.getFileName()) // GH-90000
                .isNotNull(); // GH-90000

        // autonomyLevel — must resolve to a canonical AutonomyLevel (or legacy alias) // GH-90000
        String rawAnalomy = identity.path("autonomyLevel").asText(null);
        if (rawAnalomy != null && !rawAnalomy.isBlank()) { // GH-90000
            AutonomyLevel resolved = AutonomyLevel.fromString(rawAnalomy); // GH-90000
            assertThat(resolved) // GH-90000
                    .as( // GH-90000
                            "identity.autonomyLevel '%s' in %s is not a known canonical value or alias."
                                    + " Canonical: ADVISORY, DRAFT, SUPERVISED, BOUNDED_AUTONOMOUS, AUTONOMOUS."
                                    + " Aliases: semi-autonomous → SUPERVISED, manual → DRAFT, assisted → DRAFT.",
                            rawAnalomy, yamlFile.getFileName()) // GH-90000
                    .isNotNull(); // GH-90000
        }

        // determinismGuarantee — must be a canonical DeterminismGuarantee
        String rawDeterminism = identity.path("determinismGuarantee").asText(null);
        if (rawDeterminism != null && !rawDeterminism.isBlank()) { // GH-90000
            try {
                DeterminismGuarantee.valueOf(rawDeterminism.toUpperCase().replace('-', '_')); // GH-90000
            } catch (IllegalArgumentException e) { // GH-90000
                throw new AssertionError( // GH-90000
                        "identity.determinismGuarantee '" + rawDeterminism + "' in "
                                + yamlFile.getFileName() // GH-90000
                                + " is not a canonical value. Valid: FULL, CONFIG_SCOPED, NONE, EVENTUAL.",
                        e);
            }
        }

        // stateMutability — must be a canonical StateMutability
        String rawStateMutability = identity.path("stateMutability").asText(null);
        if (rawStateMutability != null && !rawStateMutability.isBlank()) { // GH-90000
            try {
                StateMutability.valueOf(rawStateMutability.toUpperCase().replace('-', '_')); // GH-90000
            } catch (IllegalArgumentException e) { // GH-90000
                throw new AssertionError( // GH-90000
                        "identity.stateMutability '" + rawStateMutability + "' in "
                                + yamlFile.getFileName() // GH-90000
                                + " is not a canonical value. Valid: STATELESS, LOCAL_STATE, EXTERNAL_STATE, HYBRID_STATE.",
                        e);
            }
        }
    }

    /**
     * Resolves the repo root directory by walking up from the test class location.
     * Works in both Gradle and IDE environments.
     */
    private static Path resolveRepoRoot() { // GH-90000
        Path current;
        try {
            current = Paths.get(CatalogCanonicalValuesTest.class // GH-90000
                    .getProtectionDomain() // GH-90000
                    .getCodeSource() // GH-90000
                    .getLocation() // GH-90000
                    .toURI()); // GH-90000
        } catch (URISyntaxException e) { // GH-90000
            throw new IllegalStateException("Cannot resolve class location", e); // GH-90000
        }
        Path candidate = current;
        for (int i = 0; i < 12; i++) { // GH-90000
            if (candidate != null && Files.exists(candidate.resolve("gradlew"))) {
                return candidate;
            }
            candidate = candidate != null ? candidate.getParent() : null; // GH-90000
        }
        throw new IllegalStateException("Cannot locate repo root (gradlew not found) from: " + current); // GH-90000
    }
}
