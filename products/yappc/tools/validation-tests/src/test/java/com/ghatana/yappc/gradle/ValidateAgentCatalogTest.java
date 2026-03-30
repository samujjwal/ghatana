package com.ghatana.yappc.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

/**
 * Gradle TestKit integration tests for the {@code validateAgentCatalog} task defined in
 * {@code gradle/yappc-validations.gradle.kts}.
 *
 * <p>Each test case creates a minimal Gradle project in a temporary directory that applies the
 * real validations script (via an absolute path injected as a system property), populates
 * {@code config/agents/} fixture files, then runs {@link GradleRunner} to assert the expected
 * task outcome.
 *
 * @doc.type class
 * @doc.purpose Gradle TestKit tests for validateAgentCatalog task
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("validateAgentCatalog Gradle task")
class ValidateAgentCatalogTest {

    @TempDir
    Path projectDir;

    private String validationScriptPath;

    // -------------------------------------------------------------------------
    // Minimal valid fixture YAML fragments
    // -------------------------------------------------------------------------

    private static final String VALID_AGENT_YAML = """
            id: intake-specialist
            name: Intake Specialist Agent
            version: "1.0"
            metadata:
              level: 1
              capabilities: [requirements.intake]
            delegation:
              can_delegate_to: []
              escalates_to: []
            """;

    private static final String REGISTRY_YAML = """
            agents:
              - id: intake-specialist
                definition: definitions/intake-specialist.yaml
            """;

    private static final String CAPABILITIES_YAML = """
            capabilities:
              - id: requirements.intake
                description: Handles intake and validation of requirements
            """;

    private static final String MAPPINGS_YAML = """
            agents:
              - id: intake-specialist
                capabilities:
                  - requirements.intake
            """;

    private static final String EVENT_ROUTING_YAML = """
            event_routing:
              - topic: requirements.submitted
                agent_id: intake-specialist
            """;

    @BeforeEach
    void setUp() throws IOException {
        validationScriptPath = System.getProperty("validationScriptPath");
        if (validationScriptPath == null || validationScriptPath.isBlank()) {
            throw new IllegalStateException(
                    "System property 'validationScriptPath' is not set. " +
                    "Ensure the test task in build.gradle.kts passes it via systemProperty(...).");
        }

        // Write a settings.gradle.kts so Gradle recognises this as a project root
        Files.writeString(projectDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"validate-agent-catalog-test\"\n");
    }

    // -------------------------------------------------------------------------
    // Helper utilities
    // -------------------------------------------------------------------------

    /** Write the build file that applies the real validation script. */
    private void writeBuildFile() throws IOException {
        // Use escaped path — backslashes on Windows need doubling inside a string literal
        String escaped = validationScriptPath.replace("\\", "\\\\");
        Files.writeString(projectDir.resolve("build.gradle.kts"),
                "apply(from = \"" + escaped + "\")\n");
    }

    private void writeAgentDefinition(String filename, String content) throws IOException {
        Path defsDir = projectDir.resolve("config/agents/definitions");
        Files.createDirectories(defsDir);
        Files.writeString(defsDir.resolve(filename), content);
    }

    private void writeConfigFile(String relativePath, String content) throws IOException {
        Path target = projectDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private void writeValidBaselineConfig() throws IOException {
        writeAgentDefinition("intake-specialist.yaml", VALID_AGENT_YAML);
        writeConfigFile("config/agents/registry.yaml", REGISTRY_YAML);
        writeConfigFile("config/agents/capabilities.yaml", CAPABILITIES_YAML);
        writeConfigFile("config/agents/mappings.yaml", MAPPINGS_YAML);
        writeConfigFile("config/agents/event-routing.yaml", EVENT_ROUTING_YAML);
    }

    private GradleRunner gradleRunner() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .forwardOutput();
    }

        private GradleRunner gradleRunnerWithEnv(Map<String, String> env) {
                return GradleRunner.create()
                                .withProjectDir(projectDir.toFile())
                                .withEnvironment(env)
                                .forwardOutput();
        }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("passes when all agent config files are present and valid")
    void shouldPassForValidAgentCatalog() throws IOException {
        writeBuildFile();
        writeValidBaselineConfig();

        BuildResult result = gradleRunner()
                .withArguments("validateAgentCatalog", "--stacktrace")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":validateAgentCatalog").getOutcome(),
                "Task should succeed for a fully valid agent catalog");
        assertTrue(result.getOutput().contains("validation PASSED"),
                "Output should contain 'validation PASSED'");
    }

    @Test
    @DisplayName("fails when a required field is missing from an agent YAML")
    void shouldFailWhenRequiredFieldMissing() throws IOException {
        writeBuildFile();

        // Write an agent definition that omits the required 'version' field
        writeAgentDefinition("bad-agent.yaml", """
                id: bad-agent
                name: Bad Agent
                metadata:
                  level: 2
                """);

        writeConfigFile("config/agents/registry.yaml", """
                agents:
                  - id: bad-agent
                    definition: definitions/bad-agent.yaml
                """);
        writeConfigFile("config/agents/capabilities.yaml", CAPABILITIES_YAML);
        writeConfigFile("config/agents/mappings.yaml", """
                agents:
                  - id: bad-agent
                    capabilities: []
                """);
        writeConfigFile("config/agents/event-routing.yaml", """
                event_routing: []
                """);

        BuildResult result = gradleRunner()
                .withArguments("validateAgentCatalog")
                .buildAndFail();

        assertTrue(result.getOutput().contains("missing required field: version"),
                "Output should report the missing 'version' field");
        assertEquals(TaskOutcome.FAILED, result.task(":validateAgentCatalog").getOutcome());
    }

    @Test
    @DisplayName("fails when two agent definitions share the same id")
    void shouldFailOnDuplicateAgentId() throws IOException {
        writeBuildFile();

        String agentYaml = """
                id: duplicate-agent
                name: Duplicate Agent
                version: "1.0"
                metadata:
                  level: 1
                """;
        writeAgentDefinition("agent-a.yaml", agentYaml);
        writeAgentDefinition("agent-b.yaml", agentYaml);  // same id

        writeConfigFile("config/agents/registry.yaml", """
                agents:
                  - id: duplicate-agent
                    definition: definitions/agent-a.yaml
                """);
        writeConfigFile("config/agents/capabilities.yaml", CAPABILITIES_YAML);
        writeConfigFile("config/agents/mappings.yaml", """
                agents:
                  - id: duplicate-agent
                    capabilities: []
                """);
        writeConfigFile("config/agents/event-routing.yaml", "event_routing: []\n");

        BuildResult result = gradleRunner()
                .withArguments("validateAgentCatalog")
                .buildAndFail();

        assertTrue(result.getOutput().contains("Duplicate agent ID 'duplicate-agent'"),
                "Output should report the duplicate agent ID");
    }

    @Test
    @DisplayName("fails when a delegation target references an unknown agent id")
    void shouldFailOnDanglingDelegationTarget() throws IOException {
        writeBuildFile();

        writeAgentDefinition("agent-with-dangling-ref.yaml", """
                id: agent-a
                name: Agent A
                version: "1.0"
                metadata:
                  level: 2
                delegation:
                  can_delegate_to: [non-existent-agent]
                  escalates_to: []
                """);

        writeConfigFile("config/agents/registry.yaml", """
                agents:
                  - id: agent-a
                    definition: definitions/agent-with-dangling-ref.yaml
                """);
        writeConfigFile("config/agents/capabilities.yaml", CAPABILITIES_YAML);
        writeConfigFile("config/agents/mappings.yaml", "agents:\n  - id: agent-a\n    capabilities: []\n");
        writeConfigFile("config/agents/event-routing.yaml", "event_routing: []\n");

        BuildResult result = gradleRunner()
                .withArguments("validateAgentCatalog")
                .buildAndFail();

        assertTrue(result.getOutput().contains("references unknown agent: non-existent-agent"),
                "Output should report the dangling delegation reference");
    }

    @Test
    @DisplayName("fails when an agent uses a capability not declared in capabilities.yaml")
    void shouldFailOnUndeclaredCapabilityInMappings() throws IOException {
        writeBuildFile();
        writeAgentDefinition("intake-specialist.yaml", VALID_AGENT_YAML);

        writeConfigFile("config/agents/registry.yaml", REGISTRY_YAML);
        writeConfigFile("config/agents/capabilities.yaml", CAPABILITIES_YAML);
        // mappings.yaml references an undeclared capability
        writeConfigFile("config/agents/mappings.yaml", """
                agents:
                  - id: intake-specialist
                    capabilities:
                      - requirements.intake
                      - undeclared.capability
                """);
        writeConfigFile("config/agents/event-routing.yaml", EVENT_ROUTING_YAML);

        BuildResult result = gradleRunner()
                .withArguments("validateAgentCatalog")
                .buildAndFail();

        assertTrue(result.getOutput().contains("references unknown capability: undeclared.capability"),
                "Output should report the undeclared capability reference");
    }

    @Test
    @DisplayName("fails when an agent metadata.level is outside [1, 2, 3]")
    void shouldFailOnInvalidMetadataLevel() throws IOException {
        writeBuildFile();

        writeAgentDefinition("bad-level-agent.yaml", """
                id: bad-level-agent
                name: Bad Level Agent
                version: "1.0"
                metadata:
                  level: 99
                """);

        writeConfigFile("config/agents/registry.yaml", """
                agents:
                  - id: bad-level-agent
                    definition: definitions/bad-level-agent.yaml
                """);
        writeConfigFile("config/agents/capabilities.yaml", CAPABILITIES_YAML);
        writeConfigFile("config/agents/mappings.yaml", "agents:\n  - id: bad-level-agent\n    capabilities: []\n");
        writeConfigFile("config/agents/event-routing.yaml", "event_routing: []\n");

        BuildResult result = gradleRunner()
                .withArguments("validateAgentCatalog")
                .buildAndFail();

        assertTrue(result.getOutput().contains("invalid metadata.level=99"),
                "Output should report the invalid metadata level");
    }

    @Test
    @DisplayName("fails in strict mode when unregistered definitions exist")
    void shouldFailOnUnregisteredDefinitionsWhenStrictModeEnabled() throws IOException {
        writeBuildFile();
        writeValidBaselineConfig();

        // This file is not referenced by registry.yaml and no phase/domain directory
        // mappings are declared in this minimal fixture.
        writeAgentDefinition("orphan-agent.yaml", """
                id: orphan-agent
                name: Orphan Agent
                version: "1.0"
                metadata:
                  level: 2
                delegation:
                  can_delegate_to: []
                  escalates_to: []
                """);

        BuildResult result = gradleRunnerWithEnv(Map.of(
                        "YAPPC_FAIL_ON_UNREGISTERED_AGENT_DEFS", "true"
                ))
                .withArguments("validateAgentCatalog")
                .buildAndFail();

        assertTrue(result.getOutput().contains("Unregistered agent definitions detected"),
                "Output should report unregistered definition files in strict mode");
        assertEquals(TaskOutcome.FAILED, result.task(":validateAgentCatalog").getOutcome());
    }
}
