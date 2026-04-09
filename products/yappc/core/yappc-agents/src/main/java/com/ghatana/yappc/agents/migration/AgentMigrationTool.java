package com.ghatana.yappc.agents.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ghatana.platform.core.exception.ServiceException;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Agent Migration Tool - Converts Java-based agents to YAML configurations.
 *
 * This tool analyzes existing Java agent classes (Agent, Input, Output, Generator)
 * and generates equivalent YAML configuration files with JSON schemas.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AgentMigrationTool tool = new AgentMigrationTool();
 * tool.migrateAgent("JavaExpertAgent",
 *     Path.of("src/main/java/..."),
 *     Path.of("src/main/resources/agents/"));
 * }</pre>
 *
 * <p><b>Migration Output:</b>
 * <ul>
 *   <li>{@code agent-name.yaml} - Agent definition</li>
 *   <li>{@code agent-name-input.json} - Input schema</li>
 *   <li>{@code agent-name-output.json} - Output schema</li>
 *   <li>{@code prompts/agent-name.txt} - LLM prompt template (if applicable)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.pattern Tool
 * @doc.purpose Migrate Java agents to YAML configuration
 * @doc.layer migration
 */
public class AgentMigrationTool {
    private static final Logger log = LoggerFactory.getLogger(AgentMigrationTool.class);

    private final JavaParser parser;
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    public AgentMigrationTool() {
        this.parser = new JavaParser();
        this.jsonMapper = new ObjectMapper();
        this.yamlMapper = new ObjectMapper();
    }

    /**
     * Migrate a complete agent (Agent + Input + Output classes).
     *
     * @param agentName The agent class name (e.g., "JavaExpertAgent")
     * @param sourceDir Directory containing Java source files
     * @param outputDir Directory for generated YAML/JSON files
     * @return Migration result with file paths
     */
    public MigrationResult migrateAgent(String agentName, Path sourceDir, Path outputDir) {
        log.info("Starting migration for agent: {}", agentName);

        try {
            // Find the Java files
            Path agentFile = findFile(sourceDir, agentName + ".java");
            Path inputFile = findFile(sourceDir, agentName.replace("Agent", "Input") + ".java");
            Path outputFile = findFile(sourceDir, agentName.replace("Agent", "Output") + ".java");

            if (agentFile == null) {
                throw new MigrationException("Agent file not found: " + agentName);
            }

            // Parse the classes
            AgentAnalysis analysis = analyzeAgent(agentFile, inputFile, outputFile);

            // Generate YAML configuration
            String agentId = generateAgentId(agentName);
            Path yamlFile = outputDir.resolve(agentId + ".yaml");
            String yamlContent = generateYamlConfig(analysis, agentId);
            writeFile(yamlFile, yamlContent);

            // Generate input schema
            Path inputSchemaFile = outputDir.getParent().resolve("schemas").resolve(agentId + "-input.json");
            String inputSchema = generateInputSchema(analysis);
            writeFile(inputSchemaFile, inputSchema);

            // Generate output schema
            Path outputSchemaFile = outputDir.getParent().resolve("schemas").resolve(agentId + "-output.json");
            String outputSchema = generateOutputSchema(analysis);
            writeFile(outputSchemaFile, outputSchema);

            // Generate prompt template (if LLM-based)
            Path promptFile = outputDir.getParent().resolve("prompts").resolve(agentId + ".txt");
            String promptTemplate = generatePromptTemplate(analysis);
            if (promptTemplate != null) {
                writeFile(promptFile, promptTemplate);
            }

            log.info("Successfully migrated agent {} to YAML", agentName);

            return new MigrationResult(
                agentName,
                agentId,
                yamlFile,
                inputSchemaFile,
                outputSchemaFile,
                promptTemplate != null ? promptFile : null,
                analysis.getEstimatedLinesSaved()
            );

        } catch (Exception e) {
            log.error("Migration failed for agent: {}", agentName, e);
            throw new MigrationException("Migration failed for " + agentName, e);
        }
    }

    /**
     * Analyze an agent class to extract configuration.
     */
    private AgentAnalysis analyzeAgent(Path agentFile, Path inputFile, Path outputFile) throws IOException {
        AgentAnalysis analysis = new AgentAnalysis();

        // Parse agent class
        CompilationUnit agentCu = parser.parse(agentFile).getResult().orElseThrow();
        ClassOrInterfaceDeclaration agentClass = agentCu.findFirst(ClassOrInterfaceDeclaration.class)
            .orElseThrow(() -> new MigrationException("No class found in " + agentFile));

        analysis.setAgentName(agentClass.getNameAsString());

        // Extract agent ID from constructor or fields
        agentClass.getConstructors().forEach(ctor -> {
            ctor.getParameters().forEach(param -> {
                if (param.getTypeAsString().contains("String") &&
                    (param.getNameAsString().contains("id") || param.getNameAsString().contains("name"))) {
                    analysis.setAgentId(extractStringLiteral(ctor));
                }
            });
        });

        // Parse input record
        if (inputFile != null && Files.exists(inputFile)) {
            CompilationUnit inputCu = parser.parse(inputFile).getResult().orElseThrow();
            inputCu.findFirst(RecordDeclaration.class).ifPresent(record -> {
                analysis.setInputClassName(record.getNameAsString());
                record.getParameters().forEach(param -> {
                    analysis.addInputField(param.getNameAsString(), param.getTypeAsString());
                });
            });
        }

        // Parse output record
        if (outputFile != null && Files.exists(outputFile)) {
            CompilationUnit outputCu = parser.parse(outputFile).getResult().orElseThrow();
            outputCu.findFirst(RecordDeclaration.class).ifPresent(record -> {
                analysis.setOutputClassName(record.getNameAsString());
                record.getParameters().forEach(param -> {
                    analysis.addOutputField(param.getNameAsString(), param.getTypeAsString());
                });
            });
        }

        // Infer generator type from class structure
        if (agentClass.toString().contains("LLM") || agentClass.toString().contains("llm")) {
            analysis.setGeneratorType("llm");
        } else if (agentClass.toString().contains("Rule") || agentClass.toString().contains("rule")) {
            analysis.setGeneratorType("rule_based");
        } else if (agentClass.toString().contains("Template") || agentClass.toString().contains("template")) {
            analysis.setGeneratorType("template");
        } else {
            analysis.setGeneratorType("llm"); // Default
        }

        return analysis;
    }

    /**
     * Generate YAML configuration from analysis.
     */
    private String generateYamlConfig(AgentAnalysis analysis, String agentId) {
        StringBuilder yaml = new StringBuilder();

        yaml.append("# ").append(analysis.getAgentName()).append(" - Schema-Driven Agent Definition\n");
        yaml.append("# Auto-generated by AgentMigrationTool\n");
        yaml.append("# Original: ").append(analysis.getAgentName()).append(".java\n\n");

        yaml.append("agent:\n");
        yaml.append("  id: ").append(agentId).append("\n");
        yaml.append("  name: \"").append(toDisplayName(analysis.getAgentName())).append("\"\n");
        yaml.append("  description: \"Auto-migrated from ").append(analysis.getAgentName()).append("\"\n");
        yaml.append("  version: \"1.0.0\"\n\n");

        // Tags and capabilities (inferred)
        yaml.append("  # Categorization\n");
        yaml.append("  tags: [").append(inferTags(analysis)).append("]\n");
        yaml.append("  capabilities: [").append(inferCapabilities(analysis)).append("]\n\n");

        // Schemas
        yaml.append("  # Schema References\n");
        yaml.append("  input_schema: schemas/").append(agentId).append("-input.json\n");
        yaml.append("  output_schema: schemas/").append(agentId).append("-output.json\n\n");

        // Generator
        yaml.append("  # Generator Configuration\n");
        yaml.append("  generator:\n");
        yaml.append("    type: ").append(analysis.getGeneratorType()).append("\n");
        if ("llm".equals(analysis.getGeneratorType())) {
            yaml.append("    prompt_template: prompts/").append(agentId).append(".txt\n");
            yaml.append("    model: gpt-4\n");
            yaml.append("    temperature: 0.7\n");
            yaml.append("    max_tokens: 2000\n");
            yaml.append("    max_retries: 3\n");
        }
        yaml.append("\n");

        // Cache
        yaml.append("  # Caching\n");
        yaml.append("  cache:\n");
        yaml.append("    enabled: false\n");
        yaml.append("    ttl: 3600\n");
        yaml.append("    key_fields: []\n");

        return yaml.toString();
    }

    /**
     * Generate JSON Schema for input.
     */
    private String generateInputSchema(AgentAnalysis analysis) throws IOException {
        ObjectNode schema = jsonMapper.createObjectNode();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");
        schema.put("$id", "https://yappc.ghatana.com/schemas/" + generateAgentId(analysis.getAgentName()) + "-input.json");
        schema.put("title", analysis.getInputClassName());
        schema.put("description", "Input for " + analysis.getAgentName());
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        List<String> required = new ArrayList<>();

        for (Map.Entry<String, String> field : analysis.getInputFields().entrySet()) {
            String fieldName = field.getKey();
            String fieldType = field.getValue();

            ObjectNode fieldSchema = jsonMapper.createObjectNode();
            fieldSchema.put("description", fieldName + " field");

            // Map Java types to JSON Schema types
            if (fieldType.contains("String")) {
                fieldSchema.put("type", "string");
                fieldSchema.put("minLength", 1);
            } else if (fieldType.contains("int") || fieldType.contains("Integer")) {
                fieldSchema.put("type", "integer");
            } else if (fieldType.contains("double") || fieldType.contains("Double")) {
                fieldSchema.put("type", "number");
            } else if (fieldType.contains("boolean") || fieldType.contains("Boolean")) {
                fieldSchema.put("type", "boolean");
            } else if (fieldType.contains("List") || fieldType.contains("Collection")) {
                fieldSchema.put("type", "array");
            } else if (fieldType.contains("Map")) {
                fieldSchema.put("type", "object");
            } else {
                fieldSchema.put("type", "object");
            }

            properties.set(fieldName, fieldSchema);

            // Assume required if field name doesn't contain "optional"
            if (!fieldType.contains("Optional")) {
                required.add(fieldName);
            }
        }

        if (!required.isEmpty()) {
            ArrayNode requiredArray = schema.putArray("required");
            required.forEach(requiredArray::add);
        }

        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
    }

    /**
     * Generate JSON Schema for output.
     */
    private String generateOutputSchema(AgentAnalysis analysis) throws IOException {
        ObjectNode schema = jsonMapper.createObjectNode();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");
        schema.put("$id", "https://yappc.ghatana.com/schemas/" + generateAgentId(analysis.getAgentName()) + "-output.json");
        schema.put("title", analysis.getOutputClassName());
        schema.put("description", "Output from " + analysis.getAgentName());
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        for (Map.Entry<String, String> field : analysis.getOutputFields().entrySet()) {
            String fieldName = field.getKey();
            String fieldType = field.getValue();

            ObjectNode fieldSchema = jsonMapper.createObjectNode();
            fieldSchema.put("description", fieldName + " field");

            if (fieldType.contains("String")) {
                fieldSchema.put("type", "string");
            } else if (fieldType.contains("int") || fieldType.contains("Integer")) {
                fieldSchema.put("type", "integer");
            } else if (fieldType.contains("double") || fieldType.contains("Double")) {
                fieldSchema.put("type", "number");
            } else if (fieldType.contains("boolean") || fieldType.contains("Boolean")) {
                fieldSchema.put("type", "boolean");
            } else if (fieldType.contains("List") || fieldType.contains("Collection")) {
                fieldSchema.put("type", "array");
            } else {
                fieldSchema.put("type", "object");
            }

            properties.set(fieldName, fieldSchema);
        }

        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
    }

    /**
     * Generate a basic LLM prompt template.
     */
    private String generatePromptTemplate(AgentAnalysis analysis) {
        if (!"llm".equals(analysis.getGeneratorType())) {
            return null;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are ").append(toDisplayName(analysis.getAgentName())).append(".\n\n");
        prompt.append("Your task is to analyze the following input and provide recommendations.\n\n");

        prompt.append("## Input Fields:\n");
        for (String fieldName : analysis.getInputFields().keySet()) {
            prompt.append("- ").append(fieldName).append("\n");
        }
        prompt.append("\n");

        prompt.append("## Expected Output:\n");
        for (String fieldName : analysis.getOutputFields().keySet()) {
            prompt.append("- ").append(fieldName).append("\n");
        }
        prompt.append("\n");

        prompt.append("## Input:\n");
        prompt.append("${input}\n\n");

        prompt.append("Please provide your analysis in a structured format.");

        return prompt.toString();
    }

    // Helper methods

    private Path findFile(Path dir, String fileName) throws IOException {
        try (var files = Files.walk(dir)) {
            return files.filter(f -> f.getFileName().toString().equals(fileName))
                .findFirst()
                .orElse(null);
        }
    }

    private void writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String generateAgentId(String agentName) {
        // Convert JavaExpertAgent -> expert.java
        String base = agentName.replace("Agent", "");
        base = base.replaceAll("([A-Z])", ".$1").toLowerCase();
        base = base.startsWith(".") ? base.substring(1) : base;
        return base;
    }

    private String toDisplayName(String className) {
        // JavaExpertAgent -> Java Expert
        String base = className.replace("Agent", "");
        return base.replaceAll("([A-Z])", " $1").trim();
    }

    private String inferTags(AgentAnalysis analysis) {
        List<String> tags = new ArrayList<>();
        String name = analysis.getAgentName().toLowerCase();

        if (name.contains("java")) tags.add("java");
        if (name.contains("code")) tags.add("code-review");
        if (name.contains("test")) tags.add("testing");
        if (name.contains("security")) tags.add("security");
        if (name.contains("arch")) tags.add("architecture");
        if (name.contains("deploy")) tags.add("deployment");

        if (tags.isEmpty()) tags.add("general");

        return String.join(", ", tags.stream().map(t -> "\"" + t + "\"").toList());
    }

    private String inferCapabilities(AgentAnalysis analysis) {
        List<String> caps = new ArrayList<>();
        String name = analysis.getAgentName().toLowerCase();

        if (name.contains("expert")) caps.add("expert-analysis");
        if (name.contains("review")) caps.add("code-review");
        if (name.contains("generate")) caps.add("code-generation");
        if (name.contains("test")) caps.add("test-generation");

        if (caps.isEmpty()) caps.add("analysis");

        return String.join(", ", caps.stream().map(c -> "\"" + c + "\"").toList());
    }

    private String extractStringLiteral(com.github.javaparser.ast.body.ConstructorDeclaration ctor) {
        return ctor.findAll(com.github.javaparser.ast.expr.StringLiteralExpr.class)
            .stream()
            .findFirst()
            .map(lit -> lit.getValue())
            .orElse("unknown");
    }

    /**
     * Result of a migration operation.
     */
    public record MigrationResult(
        String originalName,
        String agentId,
        Path yamlFile,
        Path inputSchemaFile,
        Path outputSchemaFile,
        Path promptFile,
        int linesSaved
    ) {}

    /**
     * Analysis of an agent class.
     */
    private static class AgentAnalysis {
        private String agentName;
        private String agentId;
        private String inputClassName;
        private String outputClassName;
        private String generatorType;
        private final Map<String, String> inputFields = new LinkedHashMap<>();
        private final Map<String, String> outputFields = new LinkedHashMap<>();

        void setAgentName(String name) { this.agentName = name; }
        void setAgentId(String id) { this.agentId = id; }
        void setInputClassName(String name) { this.inputClassName = name; }
        void setOutputClassName(String name) { this.outputClassName = name; }
        void setGeneratorType(String type) { this.generatorType = type; }
        void addInputField(String name, String type) { inputFields.put(name, type); }
        void addOutputField(String name, String type) { outputFields.put(name, type); }

        String getAgentName() { return agentName; }
        String getAgentId() { return agentId; }
        String getInputClassName() { return inputClassName; }
        String getOutputClassName() { return outputClassName; }
        String getGeneratorType() { return generatorType; }
        Map<String, String> getInputFields() { return inputFields; }
        Map<String, String> getOutputFields() { return outputFields; }

        int getEstimatedLinesSaved() {
            // Rough estimate: ~200 lines per agent class set
            return 200;
        }
    }

    /**
     * Migration exception.
     */
    public static class MigrationException extends ServiceException {
        public MigrationException(String message) { super(message); }
        public MigrationException(String message, Throwable cause) { super(message, cause); }
    }
}
