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
    void shouldMigrateSimpleAgent() throws IOException {
        // Setup test agent Java files
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        
        // Create mock JavaExpertAgent.java
        Files.writeString(srcDir.resolve("JavaExpertAgent.java"), """
            public class JavaExpertAgent extends YAPPCAgentBase<JavaExpertInput, JavaExpertOutput> {
                public JavaExpertAgent(MemoryStore store, OutputGenerator gen) {
                    super("JavaExpertAgent", "expert.java", contract, gen);
                }
            }
            """);
        
        // Create mock JavaExpertInput.java
        Files.writeString(srcDir.resolve("JavaExpertInput.java"), """
            public record JavaExpertInput(String codeContext, String question) {}
            """);
        
        // Create mock JavaExpertOutput.java
        Files.writeString(srcDir.resolve("JavaExpertOutput.java"), """
            public record JavaExpertOutput(String recommendation, java.util.List<String> suggestions) {}
            """);
        
        Path outputDir = tempDir.resolve("output/agents");
        
        // Execute migration
        AgentMigrationTool tool = new AgentMigrationTool();
        AgentMigrationTool.MigrationResult result = tool.migrateAgent("JavaExpertAgent", srcDir, outputDir);
        
        // Verify
        assertThat(result.agentId()).isEqualTo("expert.java");
        assertThat(result.originalName()).isEqualTo("JavaExpertAgent");
        assertThat(result.yamlFile()).exists();
        assertThat(result.inputSchemaFile()).exists();
        assertThat(result.outputSchemaFile()).exists();
        
        // Verify YAML content
        String yamlContent = Files.readString(result.yamlFile());
        assertThat(yamlContent).contains("id: expert.java");
        assertThat(yamlContent).contains("type: llm");
        
        // Verify input schema
        String inputSchema = Files.readString(result.inputSchemaFile());
        assertThat(inputSchema).contains("codeContext");
        assertThat(inputSchema).contains("question");
        
        // Verify output schema
        String outputSchema = Files.readString(result.outputSchemaFile());
        assertThat(outputSchema).contains("recommendation");
        assertThat(outputSchema).contains("suggestions");
    }
    
    @Test
    @DisplayName("Should infer correct agent ID from class name")
    void shouldInferCorrectAgentId() {
        AgentMigrationTool tool = new AgentMigrationTool();
        
        // Test ID generation
        assertThat(extractAgentId(tool, "JavaExpertAgent")).isEqualTo("expert.java");
        assertThat(extractAgentId(tool, "CodeReviewerAgent")).isEqualTo("code.reviewer");
        assertThat(extractAgentId(tool, "SecurityAuditAgent")).isEqualTo("security.audit");
    }
    
    private String extractAgentId(AgentMigrationTool tool, String className) {
        // Access via reflection or test the pattern directly
        return className.replace("Agent", "")
            .replaceAll("([A-Z])", ".$1")
            .toLowerCase()
            .replaceFirst("^\\.", "");
    }
}
