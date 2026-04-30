/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.catalog.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for {@link AgentDefinitionValidator}.
 *
 * <p>Covers: schema/business-rule validation, duplicate capability detection, invalid tool
 * endpoint formats, malformed catalog metadata, and the business rules that guard high-criticality
 * and autonomous agents.
 *
 * @doc.type class
 * @doc.purpose Verify AgentDefinitionValidator catches schema, business-rule, and structural errors
 * @doc.layer catalog
 * @doc.pattern Test
 */
@DisplayName("AgentDefinitionValidator")
class AgentDefinitionValidatorTest {

    @TempDir
    Path tempDir;

    // ==================== Helper ====================

    private Path writeYaml(String filename, String content) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }

    // ==================== Required fields ====================

    @Nested
    @DisplayName("required field validation")
    class RequiredFields {

        @Test
        @DisplayName("valid minimal definition passes")
        void validMinimalDefinitionPasses() throws IOException {
            Path def = writeYaml("valid.yaml", minimalValid());
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"id", "namespace", "name", "version", "status"})
        @DisplayName("missing required field produces error")
        void missingRequiredFieldProducesError(String missingField) throws IOException {
            String yaml = minimalValid().replace(missingField + ": ", "# " + missingField + ": ");
            Path def = writeYaml("missing-" + missingField + ".yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors())
                .anyMatch(e -> e.contains("'" + missingField + "'") && e.contains("missing"));
        }

        @Test
        @DisplayName("missing file returns error — does not throw")
        void missingFileReturnsError() {
            Path nonExistent = tempDir.resolve("does-not-exist.yaml");
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(nonExistent);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("does not exist"));
        }

        @Test
        @DisplayName("malformed YAML returns parse error")
        void malformedYamlReturnsError() throws IOException {
            Path def = writeYaml("bad.yaml", "id: ok\nname: [unclosed\nstatus: active");
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.toLowerCase().contains("parse")
                || e.toLowerCase().contains("failed"));
        }
    }

    // ==================== Field value validation ====================

    @Nested
    @DisplayName("field value validation")
    class FieldValues {

        @Test
        @DisplayName("invalid id format (uppercase) produces error")
        void invalidIdFormatProducesError() throws IOException {
            Path def = writeYaml("bad-id.yaml", minimalValid().replace("id: entity-storage-agent", "id: Entity_Storage_Agent"));
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("'id'") && e.contains("lowercase"));
        }

        @Test
        @DisplayName("invalid version (not semver) produces error")
        void invalidVersionProducesError() throws IOException {
            Path def = writeYaml("bad-ver.yaml", minimalValid().replace("version: 1.0.0", "version: latest"));
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("'version'") && e.contains("semantic"));
        }

        @Test
        @DisplayName("invalid status value produces error")
        void invalidStatusProducesError() throws IOException {
            Path def = writeYaml("bad-status.yaml", minimalValid().replace("status: active", "status: unknown-status"));
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("'status'") && e.contains("invalid value"));
        }

        @Test
        @DisplayName("invalid agentType produces error")
        void invalidAgentTypeProducesError() throws IOException {
            String yaml = minimalValid() + "\nidentity:\n  agentType: magic\n";
            Path def = writeYaml("bad-type.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("'agentType'"));
        }

        @Test
        @DisplayName("metadata.level below 1 produces error")
        void metadataLevelTooLowProducesError() throws IOException {
            String yaml = minimalValid() + "\nmetadata:\n  level: 0\n";
            Path def = writeYaml("bad-level.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("metadata.level"));
        }

        @Test
        @DisplayName("metadata.level above 5 produces error")
        void metadataLevelTooHighProducesError() throws IOException {
            String yaml = minimalValid() + "\nmetadata:\n  level: 6\n";
            Path def = writeYaml("too-high-level.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("metadata.level"));
        }
    }

    // ==================== Duplicate capability detection ====================

    @Nested
    @DisplayName("duplicate capability detection")
    class DuplicateCapabilities {

        @Test
        @DisplayName("duplicate capability names produce warning")
        void duplicateCapabilityNamesProduceWarning() throws IOException {
            String yaml = minimalValid()
                + "\ncapabilities:\n  - entity-storage\n  - entity-storage\n  - schema-validation\n";
            Path def = writeYaml("dup-caps.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            // Duplicate capabilities produce a warning (not a hard error)
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.toLowerCase().contains("duplicate")
                && w.contains("entity-storage"));
        }

        @Test
        @DisplayName("unique capabilities produce no duplicate warning")
        void uniqueCapabilitiesProduceNoDuplicateWarning() throws IOException {
            String yaml = minimalValid()
                + "\ncapabilities:\n  - entity-storage\n  - schema-validation\n  - versioning\n";
            Path def = writeYaml("unique-caps.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.warnings())
                .noneMatch(w -> w.toLowerCase().contains("duplicate"));
        }
    }

    // ==================== Tool endpoint validation ====================

    @Nested
    @DisplayName("tool endpoint validation")
    class ToolEndpoints {

        @Test
        @DisplayName("tool without name produces error")
        void toolWithoutNameProducesError() throws IOException {
            String yaml = minimalValid()
                + "\ntools:\n  - type: SERVICE\n    endpoint: /some/endpoint\n";
            Path def = writeYaml("tool-no-name.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("name") && e.toLowerCase().contains("tool"));
        }

        @Test
        @DisplayName("invalid tool type produces error")
        void invalidToolTypeProducesError() throws IOException {
            String yaml = minimalValid()
                + "\ntools:\n  - name: myTool\n    type: INVALID_TYPE\n    endpoint: /some/ep\n";
            Path def = writeYaml("bad-tool-type.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("'type'"));
        }
    }

    // ==================== Business rules ====================

    @Nested
    @DisplayName("business rule validation")
    class BusinessRules {

        @Test
        @DisplayName("high-criticality agent without owners produces error")
        void highCriticalityAgentWithoutOwnersProducesError() throws IOException {
            String yaml = minimalValid()
                + "\nidentity:\n  criticality: high\n  agentType: deterministic\n"
                + "  stateMutability: stateless\n  autonomyLevel: manual\n";
            // No owners: block is intentionally absent
            Path def = writeYaml("high-crit-no-owners.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.toLowerCase().contains("owner"));
        }

        @Test
        @DisplayName("critical agent without owners produces error")
        void criticalAgentWithoutOwnersProducesError() throws IOException {
            String yaml = minimalValid()
                + "\nidentity:\n  criticality: critical\n  agentType: deterministic\n"
                + "  stateMutability: stateless\n  autonomyLevel: manual\n";
            Path def = writeYaml("critical-no-owners.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.toLowerCase().contains("owner"));
        }

        @Test
        @DisplayName("high-criticality agent with owners passes business rule")
        void highCriticalityAgentWithOwnersPassesBusinessRule() throws IOException {
            String yaml = minimalValid()
                + "\nidentity:\n  criticality: high\n  agentType: deterministic\n"
                + "  stateMutability: stateless\n  autonomyLevel: manual\n"
                + "owners:\n  - team: data-cloud-platform\n    contact: dc@ghatana.com\n";
            Path def = writeYaml("high-crit-with-owners.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.errors())
                .noneMatch(e -> e.toLowerCase().contains("owner"));
        }

        @Test
        @DisplayName("external-state agent without tools produces error")
        void externalStateAgentWithoutToolsProducesError() throws IOException {
            String yaml = minimalValid()
                + "\nidentity:\n  criticality: low\n  agentType: deterministic\n"
                + "  stateMutability: external-state\n  autonomyLevel: manual\n";
            Path def = writeYaml("ext-state-no-tools.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.toLowerCase().contains("tools"));
        }

        @Test
        @DisplayName("autonomous agent without memory produces warning")
        void autonomousAgentWithoutMemoryProducesWarning() throws IOException {
            String yaml = minimalValid()
                + "\nidentity:\n  criticality: low\n  agentType: probabilistic\n"
                + "  stateMutability: stateless\n  autonomyLevel: autonomous\n"
                + "owners:\n  - team: dc-team\n    contact: dc@example.com\n";
            Path def = writeYaml("auto-no-memory.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.toLowerCase().contains("memory"));
        }

        @Test
        @DisplayName("deprecated agent without 'deprecated' in description produces warning")
        void deprecatedAgentWithoutDescriptionTagProducesWarning() throws IOException {
            String yaml = minimalValid()
                .replace("status: active", "status: deprecated")
                .replace("description: Some agent description", "description: This is an old agent");
            Path def = writeYaml("deprecated-no-desc.yaml", yaml);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.toLowerCase().contains("deprecat"));
        }
    }

    // ==================== Capabilities taxonomy cross-reference ====================

    @Nested
    @DisplayName("capabilities taxonomy cross-reference")
    class CapabilitiesTaxonomy {

        @Test
        @DisplayName("capability not in taxonomy produces warning")
        void capabilityNotInTaxonomyProducesWarning() throws IOException {
            Path capFile = writeYaml("capabilities.yaml",
                "capabilities:\n  entity-storage: {}\n  schema-validation: {}\n");
            String yaml = minimalValid()
                + "\ncapabilities:\n  - entity-storage\n  - unknown-capability-xyz\n";
            Path def = writeYaml("unknown-cap.yaml", yaml);

            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def, capFile);
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("unknown-capability-xyz"));
        }

        @Test
        @DisplayName("capability present in taxonomy produces no warning")
        void capabilityPresentInTaxonomyProducesNoWarning() throws IOException {
            Path capFile = writeYaml("capabilities.yaml",
                "capabilities:\n  entity-storage: {}\n  schema-validation: {}\n");
            String yaml = minimalValid()
                + "\ncapabilities:\n  - entity-storage\n  - schema-validation\n";
            Path def = writeYaml("known-caps.yaml", yaml);

            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def, capFile);
            assertThat(result.warnings())
                .noneMatch(w -> w.contains("not defined in the capabilities taxonomy"));
        }

        @Test
        @DisplayName("tool endpoint not starting with / produces error")
        void toolEndpointMissingLeadingSlashProducesError() throws IOException {
            Path capFile = writeYaml("caps.yaml", "capabilities:\n  entity-storage: {}\n");
            String yaml = minimalValid()
                + "\ntools:\n  - name: myTool\n    type: SERVICE\n    endpoint: data-cloud/bad\n";
            Path def = writeYaml("bad-endpoint.yaml", yaml);

            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def, capFile);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("endpoint") && e.contains("'/'"));
        }
    }

    // ==================== Real catalog definitions ====================

    @Nested
    @DisplayName("real catalog definitions in definitions/")
    class RealDefinitions {

        private Path catalogRoot() {
            // Resolve from the test working directory; Gradle sets it to the module root.
            return Path.of("").toAbsolutePath();
        }

        @Test
        @DisplayName("entity-storage-agent.yaml passes validation")
        void entityStorageAgentPassesValidation() {
            Path def = catalogRoot().resolve("definitions/storage/entity-storage-agent.yaml");
            org.junit.jupiter.api.Assumptions.assumeTrue(def.toFile().exists(),
                "Skipping — definition file not found at " + def);
            AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.errors())
                .as("entity-storage-agent.yaml must have no validation errors")
                .isEmpty();
        }

        @Test
        @DisplayName("all agent definitions in definitions/ pass validation")
        void allRealDefinitionsPassValidation() throws IOException {
            Path defsDir = catalogRoot().resolve("definitions");
            org.junit.jupiter.api.Assumptions.assumeTrue(defsDir.toFile().isDirectory(),
                "Skipping — definitions/ directory not found at " + defsDir);

            java.util.List<String> allErrors = new java.util.ArrayList<>();

            try (var stream = Files.walk(defsDir)) {
                stream.filter(p -> p.toString().endsWith(".yaml"))
                    .forEach(yaml -> {
                        AgentDefinitionValidator.ValidationResult r = AgentDefinitionValidator.validate(yaml);
                        if (!r.isValid()) {
                            allErrors.add(yaml.getFileName() + ": " + r.getErrorMessage());
                        }
                    });
            }

            assertThat(allErrors)
                .as("No real agent definition file should have validation errors")
                .isEmpty();
        }

        @Test
        @DisplayName("no duplicate agent IDs across all definitions")
        void noDuplicateAgentIdsAcrossAllDefinitions() throws IOException {
            Path defsDir = catalogRoot().resolve("definitions");
            org.junit.jupiter.api.Assumptions.assumeTrue(defsDir.toFile().isDirectory(),
                "Skipping — definitions/ directory not found at " + defsDir);

            var idsSeen = new java.util.HashMap<String, String>();
            var duplicates = new java.util.ArrayList<String>();

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper(
                    new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

            try (var stream = Files.walk(defsDir)) {
                stream.filter(p -> p.toString().endsWith(".yaml"))
                    .forEach(yaml -> {
                        try {
                            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(yaml.toFile());
                            if (root.has("id")) {
                                String id = root.get("id").asText();
                                String prev = idsSeen.put(id, yaml.getFileName().toString());
                                if (prev != null) {
                                    duplicates.add("Duplicate agent ID '" + id + "' in "
                                        + yaml.getFileName() + " (also in " + prev + ")");
                                }
                            }
                        } catch (IOException e) {
                            // parse errors are caught by the main validation test above
                        }
                    });
            }

            assertThat(duplicates)
                .as("Duplicate agent IDs detected across catalog definitions")
                .isEmpty();
        }
    }

    // ==================== ValidationResult record ====================

    @Nested
    @DisplayName("ValidationResult record behaviour")
    class ValidationResultRecord {

        @Test
        @DisplayName("hasErrors returns true when errors list is non-empty")
        void hasErrorsReturnsTrueWhenErrorsPresent() {
            var result = new AgentDefinitionValidator.ValidationResult(
                java.util.List.of("err1"), java.util.List.of(), false);
            assertThat(result.hasErrors()).isTrue();
        }

        @Test
        @DisplayName("hasErrors returns false when errors list is empty")
        void hasErrorsReturnsFalseWhenNoErrors() {
            var result = new AgentDefinitionValidator.ValidationResult(
                java.util.List.of(), java.util.List.of(), true);
            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        @DisplayName("getErrorMessage returns joined errors")
        void getErrorMessageReturnsJoinedErrors() {
            var result = new AgentDefinitionValidator.ValidationResult(
                java.util.List.of("error-a", "error-b"), java.util.List.of(), false);
            assertThat(result.getErrorMessage()).contains("error-a").contains("error-b");
        }

        @Test
        @DisplayName("getErrorMessage returns 'No errors' when list is empty")
        void getErrorMessageReturnsNoErrorsWhenEmpty() {
            var result = new AgentDefinitionValidator.ValidationResult(
                java.util.List.of(), java.util.List.of(), true);
            assertThat(result.getErrorMessage()).isEqualTo("No errors");
        }
    }

    // ==================== Fixture factory ====================

    /** Returns a minimal, valid agent definition YAML string. */
    private static String minimalValid() {
        return """
            id: entity-storage-agent
            namespace: data-cloud.storage
            name: Entity Storage Agent
            version: 1.0.0
            status: active
            description: Some agent description
            """;
    }
}
