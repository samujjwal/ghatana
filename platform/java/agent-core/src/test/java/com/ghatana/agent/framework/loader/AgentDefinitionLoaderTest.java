package com.ghatana.agent.framework.loader;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.core.template.TemplateContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AgentDefinitionLoader}.
 *
 * <p>Covers: loading from string, loading from file with/without template vars,
 * inheritance chains, directory scanning, required-field validation, and
 * unknown agent-type rejection.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentDefinitionLoader — YAML loading pipeline
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentDefinitionLoader [GH-90000]")
class AgentDefinitionLoaderTest {

    // =========================================================================
    //  loadFromString() // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("loadFromString() [GH-90000]")
    class LoadFromStringTests {

        @Test
        @DisplayName("parses a minimal valid agent YAML string [GH-90000]")
        void minimalValidYaml() throws IOException { // GH-90000
            String yaml = """
                    id: agent-001
                    name: My Agent
                    type: DETERMINISTIC
                    """;
            AgentDefinitionLoader loader = new AgentDefinitionLoader(); // GH-90000

            AgentDefinition def = loader.loadFromString(yaml); // GH-90000

            assertThat(def.getId()).isEqualTo("agent-001 [GH-90000]");
            assertThat(def.getName()).isEqualTo("My Agent [GH-90000]");
            assertThat(def.getType()).isEqualTo(AgentType.DETERMINISTIC); // GH-90000
        }

        @Test
        @DisplayName("parses all optional fields when present [GH-90000]")
        void allOptionalFieldsParsed() throws IOException { // GH-90000
            String yaml = """
                    id: agent-full
                    name: Full Agent
                    type: PROBABILISTIC
                    version: 2.1.0
                    description: A fully specified agent
                    subtype: CLASSIFIER
                    """;
            AgentDefinition def = new AgentDefinitionLoader().loadFromString(yaml); // GH-90000

            assertThat(def.getVersion()).isEqualTo("2.1.0 [GH-90000]");
            assertThat(def.getDescription()).isEqualTo("A fully specified agent [GH-90000]");
            assertThat(def.getSubtype()).isEqualTo("CLASSIFIER [GH-90000]");
        }

        @Test
        @DisplayName("substitutes {{ varName }} placeholders from context [GH-90000]")
        void placeholdersAreSubstituted() throws IOException { // GH-90000
            String yaml = """
                    id: {{ agentId }}
                    name: {{ agentName }}
                    type: DETERMINISTIC
                    """;
            TemplateContext ctx = TemplateContext.builder() // GH-90000
                    .put("agentId", "injected-001") // GH-90000
                    .put("agentName", "Injected Agent") // GH-90000
                    .build(); // GH-90000
            AgentDefinition def = new AgentDefinitionLoader(ctx).loadFromString(yaml); // GH-90000

            assertThat(def.getId()).isEqualTo("injected-001 [GH-90000]");
            assertThat(def.getName()).isEqualTo("Injected Agent [GH-90000]");
        }

        @Test
        @DisplayName("throws ISE when 'id' field is missing [GH-90000]")
        void missingIdThrowsIse() { // GH-90000
            String yaml = """
                    name: My Agent
                    type: DETERMINISTIC
                    """;
            assertThatThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml)) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("id [GH-90000]");
        }

        @Test
        @DisplayName("throws ISE when 'name' field is missing [GH-90000]")
        void missingNameThrowsIse() { // GH-90000
            String yaml = """
                    id: agent-001
                    type: DETERMINISTIC
                    """;
            assertThatThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml)) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("name [GH-90000]");
        }

        @Test
        @DisplayName("throws ISE when 'type' field is missing [GH-90000]")
        void missingTypeThrowsIse() { // GH-90000
            String yaml = """
                    id: agent-001
                    name: My Agent
                    """;
            assertThatThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml)) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("type [GH-90000]");
        }

        @Test
        @DisplayName("throws ISE for unrecognised agent type value [GH-90000]")
        void unknownTypeThrowsIse() { // GH-90000
            String yaml = """
                    id: agent-001
                    name: My Agent
                    type: UNKNOWN_TYPE_XYZ
                    """;
            assertThatThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml)) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("UNKNOWN_TYPE_XYZ [GH-90000]");
        }

        @Test
        @DisplayName("type is case-insensitive ('deterministic' → DETERMINISTIC) [GH-90000]")
        void typeIsCaseInsensitive() throws IOException { // GH-90000
            String yaml = """
                    id: agent-001
                    name: My Agent
                    type: deterministic
                    """;
            AgentDefinition def = new AgentDefinitionLoader().loadFromString(yaml); // GH-90000
            assertThat(def.getType()).isEqualTo(AgentType.DETERMINISTIC); // GH-90000
        }

        @Test
        @DisplayName("throws ISE when template variable is undefined [GH-90000]")
        void undefinedTemplateVariableThrowsIse() { // GH-90000
            String yaml = """
                    id: {{ undefinedVar }}
                    name: My Agent
                    type: DETERMINISTIC
                    """;
            assertThatThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml)) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("undefinedVar [GH-90000]");
        }
    }

    // =========================================================================
    //  load(Path) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("load(Path) [GH-90000]")
    class LoadFromFileTests {

        @TempDir
        Path dir;

        @Test
        @DisplayName("loads a valid YAML file from the filesystem [GH-90000]")
        void loadsYamlFile() throws IOException { // GH-90000
            Path file = writeYaml(dir, "agent.yaml", """
                    id: file-agent
                    name: File Agent
                    type: HYBRID
                    """);
            AgentDefinition def = new AgentDefinitionLoader().load(file); // GH-90000

            assertThat(def.getId()).isEqualTo("file-agent [GH-90000]");
            assertThat(def.getType()).isEqualTo(AgentType.HYBRID); // GH-90000
        }

        @Test
        @DisplayName("resolves extends chain during load [GH-90000]")
        void loadsFileWithInheritance() throws IOException { // GH-90000
            writeYaml(dir, "base.yaml", """
                    id: base-agent
                    name: BaseName
                    type: DETERMINISTIC
                    timeout: 30
                    """);
            Path child = writeYaml(dir, "child.yaml", """
                    extends: base.yaml
                    id: child-agent
                    name: ChildName
                    """);
            AgentDefinition def = new AgentDefinitionLoader().load(child); // GH-90000

            assertThat(def.getId()).isEqualTo("child-agent [GH-90000]");
            assertThat(def.getName()).isEqualTo("ChildName [GH-90000]");
            assertThat(def.getType()).isEqualTo(AgentType.DETERMINISTIC); // inherited // GH-90000
        }
    }

    // =========================================================================
    //  loadFromDirectory() // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("loadFromDirectory() [GH-90000]")
    class LoadFromDirectoryTests {

        @TempDir
        Path dir;

        @Test
        @DisplayName("loads all valid *.yaml files in a directory [GH-90000]")
        void loadsAllYamlFiles() throws IOException { // GH-90000
            writeYaml(dir, "agent1.yaml", "id: a1\nname: A1\ntype: DETERMINISTIC\n"); // GH-90000
            writeYaml(dir, "agent2.yaml", "id: a2\nname: A2\ntype: ADAPTIVE\n"); // GH-90000
            writeYaml(dir, "agent3.yaml", "id: a3\nname: A3\ntype: COMPOSITE\n"); // GH-90000

            List<AgentDefinition> defs = new AgentDefinitionLoader().loadFromDirectory(dir); // GH-90000

            assertThat(defs).hasSize(3) // GH-90000
                    .extracting(AgentDefinition::getId) // GH-90000
                    .containsExactlyInAnyOrder("a1", "a2", "a3"); // GH-90000
        }

        @Test
        @DisplayName("skips non-YAML files [GH-90000]")
        void skipsNonYamlFiles() throws IOException { // GH-90000
            writeYaml(dir, "agent.yaml", "id: a1\nname: A1\ntype: DETERMINISTIC\n"); // GH-90000
            Files.writeString(dir.resolve("README.md [GH-90000]"), "# readme");
            Files.writeString(dir.resolve("data.json [GH-90000]"), "{}");

            List<AgentDefinition> defs = new AgentDefinitionLoader().loadFromDirectory(dir); // GH-90000
            assertThat(defs).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("returns empty list for empty directory [GH-90000]")
        void emptyDirectoryReturnsEmptyList() throws IOException { // GH-90000
            List<AgentDefinition> defs = new AgentDefinitionLoader().loadFromDirectory(dir); // GH-90000
            assertThat(defs).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("skips invalid YAML files and returns rest [GH-90000]")
        void invalidFilesSkippedOthersLoaded() throws IOException { // GH-90000
            writeYaml(dir, "valid.yaml", "id: v1\nname: Valid\ntype: DETERMINISTIC\n"); // GH-90000
            writeYaml(dir, "invalid.yaml", "id: \nname: \ntype: \n"); // missing required // GH-90000

            List<AgentDefinition> defs = new AgentDefinitionLoader().loadFromDirectory(dir); // GH-90000
            assertThat(defs).hasSize(1); // GH-90000
            assertThat(defs.get(0).getId()).isEqualTo("v1 [GH-90000]");
        }

        @Test
        @DisplayName("throws IOException when path is not a directory [GH-90000]")
        void throwsWhenNotDirectory() throws IOException { // GH-90000
            Path file = writeYaml(dir, "file.yaml", "id: x\nname: X\ntype: DETERMINISTIC\n"); // GH-90000
            assertThatIOException().isThrownBy(() -> new AgentDefinitionLoader().loadFromDirectory(file)); // GH-90000
        }
    }

    // =========================================================================
    //  All AgentType values parse correctly
    // =========================================================================

    @Nested
    @DisplayName("AgentType parsing [GH-90000]")
    class AgentTypeParsingTests {

        @Test
        @DisplayName("all declared AgentType enum values are loadable [GH-90000]")
        void allAgentTypesAreLoadable() { // GH-90000
            for (AgentType type : AgentType.values()) { // GH-90000
                String yaml = String.format( // GH-90000
                        "id: agent-%s\nname: Agent %s\ntype: %s\n",
                        type.name().toLowerCase(), type.name(), type.name()); // GH-90000
                assertThatNoException() // GH-90000
                        .as("AgentType." + type) // GH-90000
                        .isThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml)); // GH-90000
            }
        }
    }

    // =========================================================================
    //  New-format spec bridge (agentSpecVersion detection) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("New-spec-format bridge [GH-90000]")
    class NewSpecFormatBridgeTests {

        @Test
        @DisplayName("auto-detects agentSpecVersion YAML and delegates to AgentSpecLoader [GH-90000]")
        void delegatesToAgentSpecLoaderForNewFormat() throws IOException { // GH-90000
            String newFormatYaml = """
                    agentSpecVersion: "2.0.0"
                    metadata:
                      id: agent.bridge.test
                      name: Bridge Test Agent
                      version: "1.0.0"
                    identity:
                      agentType: deterministic
                    """;
            AgentDefinitionLoader loader = new AgentDefinitionLoader(); // GH-90000
            AgentDefinition def = loader.loadFromString(newFormatYaml); // GH-90000

            assertThat(def.getId()).isEqualTo("agent.bridge.test [GH-90000]");
            assertThat(def.getName()).isEqualTo("Bridge Test Agent [GH-90000]");
            assertThat(def.getType()).isEqualTo(AgentType.DETERMINISTIC); // GH-90000
        }

        @Test
        @DisplayName("still loads old flat-format YAML without agentSpecVersion [GH-90000]")
        void loadsOldFlatFormatNormally() throws IOException { // GH-90000
            String oldFormatYaml = """
                    id: agent.old.format
                    name: Old Format Agent
                    type: PROBABILISTIC
                    """;
            AgentDefinitionLoader loader = new AgentDefinitionLoader(); // GH-90000
            AgentDefinition def = loader.loadFromString(oldFormatYaml); // GH-90000

            assertThat(def.getId()).isEqualTo("agent.old.format [GH-90000]");
            assertThat(def.getType()).isEqualTo(AgentType.PROBABILISTIC); // GH-90000
        }
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private static Path writeYaml(Path dir, String name, String content) throws IOException { // GH-90000
        Path file = dir.resolve(name); // GH-90000
        Files.writeString(file, content); // GH-90000
        return file;
    }

    @Test
    @DisplayName("properly deserializes input and output contracts with AST definitions [GH-90000]")
    void deserializesIoContracts() throws IOException { // GH-90000
            String yaml = """
                    id: ast-agent
                    name: AST Agent
                    type: DETERMINISTIC
                    inputContract:
                      type: RequestEvent
                      format: JSON
                    outputContract:
                      type: ResponseEvent
                      format: JSON
                      uiAst:
                        component: Markdown
                        style: prose
                    """;
            AgentDefinition def = new AgentDefinitionLoader().loadFromString(yaml); // GH-90000

            assertThat(def.getInputContract()).isNotNull(); // GH-90000
            assertThat(def.getInputContract().typeName()).isEqualTo("RequestEvent [GH-90000]");

            assertThat(def.getOutputContract()).isNotNull(); // GH-90000
            assertThat(def.getOutputContract().uiAst()).isNotNull(); // GH-90000
            assertThat(def.getOutputContract().uiAst()).containsEntry("component", "Markdown"); // GH-90000
        }
    }
