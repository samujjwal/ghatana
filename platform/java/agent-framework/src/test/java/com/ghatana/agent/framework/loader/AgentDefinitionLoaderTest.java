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
@DisplayName("AgentDefinitionLoader")
class AgentDefinitionLoaderTest {

    // =========================================================================
    //  loadFromString()
    // =========================================================================

    @Nested
    @DisplayName("loadFromString()")
    class LoadFromStringTests {

        @Test
        @DisplayName("parses a minimal valid agent YAML string")
        void minimalValidYaml() throws IOException {
            String yaml = """
                    id: agent-001
                    name: My Agent
                    type: DETERMINISTIC
                    """;
            AgentDefinitionLoader loader = new AgentDefinitionLoader();

            AgentDefinition def = loader.loadFromString(yaml);

            assertThat(def.getId()).isEqualTo("agent-001");
            assertThat(def.getName()).isEqualTo("My Agent");
            assertThat(def.getType()).isEqualTo(AgentType.DETERMINISTIC);
        }

        @Test
        @DisplayName("parses all optional fields when present")
        void allOptionalFieldsParsed() throws IOException {
            String yaml = """
                    id: agent-full
                    name: Full Agent
                    type: PROBABILISTIC
                    version: 2.1.0
                    description: A fully specified agent
                    subtype: CLASSIFIER
                    """;
            AgentDefinition def = new AgentDefinitionLoader().loadFromString(yaml);

            assertThat(def.getVersion()).isEqualTo("2.1.0");
            assertThat(def.getDescription()).isEqualTo("A fully specified agent");
            assertThat(def.getSubtype()).isEqualTo("CLASSIFIER");
        }

        @Test
        @DisplayName("substitutes {{ varName }} placeholders from context")
        void placeholdersAreSubstituted() throws IOException {
            String yaml = """
                    id: {{ agentId }}
                    name: {{ agentName }}
                    type: DETERMINISTIC
                    """;
            TemplateContext ctx = TemplateContext.builder()
                    .put("agentId", "injected-001")
                    .put("agentName", "Injected Agent")
                    .build();
            AgentDefinition def = new AgentDefinitionLoader(ctx).loadFromString(yaml);

            assertThat(def.getId()).isEqualTo("injected-001");
            assertThat(def.getName()).isEqualTo("Injected Agent");
        }

        @Test
        @DisplayName("throws ISE when 'id' field is missing")
        void missingIdThrowsIse() {
            String yaml = """
                    name: My Agent
                    type: DETERMINISTIC
                    """;
            assertThatThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("throws ISE when 'name' field is missing")
        void missingNameThrowsIse() {
            String yaml = """
                    id: agent-001
                    type: DETERMINISTIC
                    """;
            assertThatThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("throws ISE when 'type' field is missing")
        void missingTypeThrowsIse() {
            String yaml = """
                    id: agent-001
                    name: My Agent
                    """;
            assertThatThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("type");
        }

        @Test
        @DisplayName("throws ISE for unrecognised agent type value")
        void unknownTypeThrowsIse() {
            String yaml = """
                    id: agent-001
                    name: My Agent
                    type: UNKNOWN_TYPE_XYZ
                    """;
            assertThatThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("UNKNOWN_TYPE_XYZ");
        }

        @Test
        @DisplayName("type is case-insensitive ('deterministic' → DETERMINISTIC)")
        void typeIsCaseInsensitive() throws IOException {
            String yaml = """
                    id: agent-001
                    name: My Agent
                    type: deterministic
                    """;
            AgentDefinition def = new AgentDefinitionLoader().loadFromString(yaml);
            assertThat(def.getType()).isEqualTo(AgentType.DETERMINISTIC);
        }

        @Test
        @DisplayName("throws ISE when template variable is undefined")
        void undefinedTemplateVariableThrowsIse() {
            String yaml = """
                    id: {{ undefinedVar }}
                    name: My Agent
                    type: DETERMINISTIC
                    """;
            assertThatThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("undefinedVar");
        }
    }

    // =========================================================================
    //  load(Path)
    // =========================================================================

    @Nested
    @DisplayName("load(Path)")
    class LoadFromFileTests {

        @TempDir
        Path dir;

        @Test
        @DisplayName("loads a valid YAML file from the filesystem")
        void loadsYamlFile() throws IOException {
            Path file = writeYaml(dir, "agent.yaml", """
                    id: file-agent
                    name: File Agent
                    type: HYBRID
                    """);
            AgentDefinition def = new AgentDefinitionLoader().load(file);

            assertThat(def.getId()).isEqualTo("file-agent");
            assertThat(def.getType()).isEqualTo(AgentType.HYBRID);
        }

        @Test
        @DisplayName("resolves extends chain during load")
        void loadsFileWithInheritance() throws IOException {
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
            AgentDefinition def = new AgentDefinitionLoader().load(child);

            assertThat(def.getId()).isEqualTo("child-agent");
            assertThat(def.getName()).isEqualTo("ChildName");
            assertThat(def.getType()).isEqualTo(AgentType.DETERMINISTIC); // inherited
        }
    }

    // =========================================================================
    //  loadFromDirectory()
    // =========================================================================

    @Nested
    @DisplayName("loadFromDirectory()")
    class LoadFromDirectoryTests {

        @TempDir
        Path dir;

        @Test
        @DisplayName("loads all valid *.yaml files in a directory")
        void loadsAllYamlFiles() throws IOException {
            writeYaml(dir, "agent1.yaml", "id: a1\nname: A1\ntype: DETERMINISTIC\n");
            writeYaml(dir, "agent2.yaml", "id: a2\nname: A2\ntype: ADAPTIVE\n");
            writeYaml(dir, "agent3.yaml", "id: a3\nname: A3\ntype: COMPOSITE\n");

            List<AgentDefinition> defs = new AgentDefinitionLoader().loadFromDirectory(dir);

            assertThat(defs).hasSize(3)
                    .extracting(AgentDefinition::getId)
                    .containsExactlyInAnyOrder("a1", "a2", "a3");
        }

        @Test
        @DisplayName("skips non-YAML files")
        void skipsNonYamlFiles() throws IOException {
            writeYaml(dir, "agent.yaml", "id: a1\nname: A1\ntype: DETERMINISTIC\n");
            Files.writeString(dir.resolve("README.md"), "# readme");
            Files.writeString(dir.resolve("data.json"), "{}");

            List<AgentDefinition> defs = new AgentDefinitionLoader().loadFromDirectory(dir);
            assertThat(defs).hasSize(1);
        }

        @Test
        @DisplayName("returns empty list for empty directory")
        void emptyDirectoryReturnsEmptyList() throws IOException {
            List<AgentDefinition> defs = new AgentDefinitionLoader().loadFromDirectory(dir);
            assertThat(defs).isEmpty();
        }

        @Test
        @DisplayName("skips invalid YAML files and returns rest")
        void invalidFilesSkippedOthersLoaded() throws IOException {
            writeYaml(dir, "valid.yaml", "id: v1\nname: Valid\ntype: DETERMINISTIC\n");
            writeYaml(dir, "invalid.yaml", "id: \nname: \ntype: \n"); // missing required

            List<AgentDefinition> defs = new AgentDefinitionLoader().loadFromDirectory(dir);
            assertThat(defs).hasSize(1);
            assertThat(defs.get(0).getId()).isEqualTo("v1");
        }

        @Test
        @DisplayName("throws IOException when path is not a directory")
        void throwsWhenNotDirectory() throws IOException {
            Path file = writeYaml(dir, "file.yaml", "id: x\nname: X\ntype: DETERMINISTIC\n");
            assertThatIOException().isThrownBy(() -> new AgentDefinitionLoader().loadFromDirectory(file));
        }
    }

    // =========================================================================
    //  All AgentType values parse correctly
    // =========================================================================

    @Nested
    @DisplayName("AgentType parsing")
    class AgentTypeParsingTests {

        @Test
        @DisplayName("all declared AgentType enum values are loadable")
        void allAgentTypesAreLoadable() {
            for (AgentType type : AgentType.values()) {
                String yaml = String.format(
                        "id: agent-%s\nname: Agent %s\ntype: %s\n",
                        type.name().toLowerCase(), type.name(), type.name());
                assertThatNoException()
                        .as("AgentType." + type)
                        .isThrownBy(() -> new AgentDefinitionLoader().loadFromString(yaml));
            }
        }
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private static Path writeYaml(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
