/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * <p>AEP operator YAML files use a simplified format (not the full 18-section platform
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
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /**
     * Generates one {@link DynamicTest} per YAML file found under the AEP catalog
     * operators directory. Each test validates the {@code identity.*} enum fields.
     */
    @TestFactory
    Stream<DynamicTest> allOperatorYamlsHaveCanonicalEnumValues() throws IOException {
        Path catalogDir = resolveRepoRoot().resolve(CATALOG_OPERATORS_DIR);
        assertThat(catalogDir).isDirectory().as("AEP catalog operators directory must exist at %s", catalogDir);

        return Files.walk(catalogDir)
                .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                .sorted()
                .map(yamlFile -> DynamicTest.dynamicTest(
                        "catalog: " + catalogDir.relativize(yamlFile), () -> validateIdentityBlock(yamlFile)));
    }

    private void validateIdentityBlock(Path yamlFile) throws IOException {
        JsonNode root = YAML.readTree(yamlFile.toFile());
        JsonNode identity = root.path("identity");

        assertThat(identity.isMissingNode())
                .as("'identity' block is missing in %s", yamlFile.getFileName())
                .isFalse();

        // agentType — must resolve to a non-deprecated canonical AgentType
        String rawAgentType = identity.path("agentType").asText(null);
        assertThat(rawAgentType)
                .as("identity.agentType must be present in %s", yamlFile.getFileName())
                .isNotBlank();
        AgentType agentType;
        try {
            agentType = AgentType.valueOf(rawAgentType.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new AssertionError(
                    "identity.agentType '" + rawAgentType + "' in " + yamlFile.getFileName()
                            + " is not a valid AgentType. Valid values: " + java.util.Arrays.asList(AgentType.values()),
                    e);
        }
        assertThat(agentType.isCanonical())
                .as(
                        "identity.agentType '%s' in %s is deprecated — use a canonical type",
                        agentType, yamlFile.getFileName())
                .isTrue();

        // autonomyLevel — must resolve to a canonical AutonomyLevel (or legacy alias)
        String rawAnalomy = identity.path("autonomyLevel").asText(null);
        if (rawAnalomy != null && !rawAnalomy.isBlank()) {
            AutonomyLevel resolved = AutonomyLevel.fromString(rawAnalomy);
            assertThat(resolved)
                    .as(
                            "identity.autonomyLevel '%s' in %s is not a known canonical value or alias."
                                    + " Canonical: ADVISORY, DRAFT, SUPERVISED, BOUNDED_AUTONOMOUS, AUTONOMOUS."
                                    + " Aliases: semi-autonomous → SUPERVISED, manual → DRAFT, assisted → DRAFT.",
                            rawAnalomy, yamlFile.getFileName())
                    .isNotNull();
        }

        // determinismGuarantee — must be a canonical DeterminismGuarantee
        String rawDeterminism = identity.path("determinismGuarantee").asText(null);
        if (rawDeterminism != null && !rawDeterminism.isBlank()) {
            try {
                DeterminismGuarantee.valueOf(rawDeterminism.toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new AssertionError(
                        "identity.determinismGuarantee '" + rawDeterminism + "' in "
                                + yamlFile.getFileName()
                                + " is not a canonical value. Valid: FULL, CONFIG_SCOPED, NONE, EVENTUAL.",
                        e);
            }
        }

        // stateMutability — must be a canonical StateMutability
        String rawStateMutability = identity.path("stateMutability").asText(null);
        if (rawStateMutability != null && !rawStateMutability.isBlank()) {
            try {
                StateMutability.valueOf(rawStateMutability.toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new AssertionError(
                        "identity.stateMutability '" + rawStateMutability + "' in "
                                + yamlFile.getFileName()
                                + " is not a canonical value. Valid: STATELESS, LOCAL_STATE, EXTERNAL_STATE, HYBRID_STATE.",
                        e);
            }
        }
    }

    /**
     * Resolves the repo root directory by walking up from the test class location.
     * Works in both Gradle and IDE environments.
     */
    private static Path resolveRepoRoot() {
        Path current;
        try {
            current = Paths.get(CatalogCanonicalValuesTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve class location", e);
        }
        Path candidate = current;
        for (int i = 0; i < 12; i++) {
            if (candidate != null && Files.exists(candidate.resolve("gradlew"))) {
                return candidate;
            }
            candidate = candidate != null ? candidate.getParent() : null;
        }
        throw new IllegalStateException("Cannot locate repo root (gradlew not found) from: " + current);
    }
}
