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
    @DisplayName("Should migrate simple agent successfully")
    void shouldMigrateSimpleAgent() throws IOException { // GH-90000
        // Setup test agent Java files
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir); // GH-90000

        // Create mock JavaExpertAgent.java
        Files.writeString(srcDir.resolve("JavaExpertAgent.java"), """
            public class JavaExpertAgent extends YAPPCAgentBase<JavaExpertInput, JavaExpertOutput> {
                public JavaExpertAgent(MemoryStore store, OutputGenerator gen) { // GH-90000
                    super("JavaExpertAgent", "expert.java", contract, gen, // GH-90000
        defaultEventPublisher()); // GH-90000
                }
            }
            """);

        // Create mock JavaExpertInput.java
        Files.writeString(srcDir.resolve("JavaExpertInput.java"), """
            public record JavaExpertInput(String codeContext, String question) {} // GH-90000
            """);

        // Create mock JavaExpertOutput.java
        Files.writeString(srcDir.resolve("JavaExpertOutput.java"), """
            public record JavaExpertOutput(String recommendation, java.util.List<String> suggestions) {} // GH-90000
            """);

        Path outputDir = tempDir.resolve("output/agents");

        // Execute migration
        AgentMigrationTool tool = new AgentMigrationTool(); // GH-90000
        AgentMigrationTool.MigrationResult result = tool.migrateAgent("JavaExpertAgent", srcDir, outputDir); // GH-90000

        // Verify
        assertThat(result.agentId()).isEqualTo("expert.java");
        assertThat(result.originalName()).isEqualTo("JavaExpertAgent");
        assertThat(result.yamlFile()).exists(); // GH-90000
        assertThat(result.inputSchemaFile()).exists(); // GH-90000
        assertThat(result.outputSchemaFile()).exists(); // GH-90000

        // Verify YAML content
        String yamlContent = Files.readString(result.yamlFile()); // GH-90000
        assertThat(yamlContent).contains("id: expert.java");
        assertThat(yamlContent).contains("type: llm");

        // Verify input schema
        String inputSchema = Files.readString(result.inputSchemaFile()); // GH-90000
        assertThat(inputSchema).contains("codeContext");
        assertThat(inputSchema).contains("question");

        // Verify output schema
        String outputSchema = Files.readString(result.outputSchemaFile()); // GH-90000
        assertThat(outputSchema).contains("recommendation");
        assertThat(outputSchema).contains("suggestions");
    }

    @Test
    @DisplayName("Should infer correct agent ID from class name")
    void shouldInferCorrectAgentId() { // GH-90000
        AgentMigrationTool tool = new AgentMigrationTool(); // GH-90000

        // Test ID generation
        assertThat(extractAgentId(tool, "JavaExpertAgent")).isEqualTo("expert.java");
        assertThat(extractAgentId(tool, "CodeReviewerAgent")).isEqualTo("code.reviewer");
        assertThat(extractAgentId(tool, "SecurityAuditAgent")).isEqualTo("security.audit");
    }

    private String extractAgentId(AgentMigrationTool tool, String className) { // GH-90000
        return tool.generateAgentId(className); // GH-90000
    }
}
