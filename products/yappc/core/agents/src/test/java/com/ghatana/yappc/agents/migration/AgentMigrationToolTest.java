package com.ghatana.yappc.agents.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Integration tests for agent migration.
 *
 * Tests the end-to-end migration process from Java classes to YAML.
 *
 * @doc.pattern Test
 * @doc.purpose Integration tests for migration tool
 * @doc.layer test
 */
class AgentMigrationToolTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should migrate simple agent successfully [GH-90000]")
    void shouldMigrateSimpleAgent() throws IOException { // GH-90000
        // Setup test agent Java files
        Path srcDir = tempDir.resolve("src [GH-90000]");
        Files.createDirectories(srcDir); // GH-90000

        // Create mock JavaExpertAgent.java
        Files.writeString(srcDir.resolve("JavaExpertAgent.java [GH-90000]"), """
            public class JavaExpertAgent extends YAPPCAgentBase<JavaExpertInput, JavaExpertOutput> {
                public JavaExpertAgent(MemoryStore store, OutputGenerator gen) { // GH-90000
                    super("JavaExpertAgent", "expert.java", contract, gen, // GH-90000
        defaultEventPublisher()); // GH-90000
                }
            }
            """);

        // Create mock JavaExpertInput.java
        Files.writeString(srcDir.resolve("JavaExpertInput.java [GH-90000]"), """
            public record JavaExpertInput(String codeContext, String question) {} // GH-90000
            """);

        // Create mock JavaExpertOutput.java
        Files.writeString(srcDir.resolve("JavaExpertOutput.java [GH-90000]"), """
            public record JavaExpertOutput(String recommendation, java.util.List<String> suggestions) {} // GH-90000
            """);

        Path outputDir = tempDir.resolve("output/agents [GH-90000]");

        // Execute migration
        AgentMigrationTool tool = new AgentMigrationTool(); // GH-90000
        AgentMigrationTool.MigrationResult result = tool.migrateAgent("JavaExpertAgent", srcDir, outputDir); // GH-90000

        // Verify
        assertThat(result.agentId()).isEqualTo("expert.java [GH-90000]");
        assertThat(result.originalName()).isEqualTo("JavaExpertAgent [GH-90000]");
        assertThat(result.yamlFile()).exists(); // GH-90000
        assertThat(result.inputSchemaFile()).exists(); // GH-90000
        assertThat(result.outputSchemaFile()).exists(); // GH-90000

        // Verify YAML content
        String yamlContent = Files.readString(result.yamlFile()); // GH-90000
        assertThat(yamlContent).contains("id: expert.java [GH-90000]");
        assertThat(yamlContent).contains("type: llm [GH-90000]");

        // Verify input schema
        String inputSchema = Files.readString(result.inputSchemaFile()); // GH-90000
        assertThat(inputSchema).contains("codeContext [GH-90000]");
        assertThat(inputSchema).contains("question [GH-90000]");

        // Verify output schema
        String outputSchema = Files.readString(result.outputSchemaFile()); // GH-90000
        assertThat(outputSchema).contains("recommendation [GH-90000]");
        assertThat(outputSchema).contains("suggestions [GH-90000]");
    }

    @Test
    @DisplayName("Should infer correct agent ID from class name [GH-90000]")
    void shouldInferCorrectAgentId() { // GH-90000
        AgentMigrationTool tool = new AgentMigrationTool(); // GH-90000

        // Test ID generation
        assertThat(extractAgentId(tool, "JavaExpertAgent")).isEqualTo("expert.java [GH-90000]");
        assertThat(extractAgentId(tool, "CodeReviewerAgent")).isEqualTo("code.reviewer [GH-90000]");
        assertThat(extractAgentId(tool, "SecurityAuditAgent")).isEqualTo("security.audit [GH-90000]");
    }

    private String extractAgentId(AgentMigrationTool tool, String className) { // GH-90000
        return tool.generateAgentId(className); // GH-90000
    }
}
