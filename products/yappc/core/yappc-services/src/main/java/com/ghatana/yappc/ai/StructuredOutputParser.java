package com.ghatana.yappc.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.intent.*;
import com.ghatana.yappc.domain.shape.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Parses AI responses into structured domain objects
 * @doc.layer ai
 * @doc.pattern Parser
 */
public class StructuredOutputParser {
    
    private static final Logger log = LoggerFactory.getLogger(StructuredOutputParser.class);
    private static final ObjectMapper mapper = JsonMapper.getMapper();
    
    /**
     * Parses AI response into IntentSpec.
     * Expects JSON format with fields: productName, description, goals, personas, constraints
     */
    public static IntentSpec parseIntentSpec(String aiResponse, IntentInput input) {
        try {
            // Try to parse as JSON first
            JsonNode root = mapper.readTree(aiResponse);
            
            return IntentSpec.builder()
                    .id(UUID.randomUUID().toString())
                    .productName(extractString(root, "productName", "Unnamed Product"))
                    .description(extractString(root, "description", ""))
                    .goals(extractGoals(root.path("goals")))
                    .personas(extractPersonas(root.path("personas")))
                    .constraints(extractConstraints(root.path("constraints")))
                    .metadata(Map.of("source", "ai-parsed", "input_format", input.format()))
                    .createdAt(Instant.now())
                    .tenantId(input.tenantId())
                    .build();
                    
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI response as JSON, using fallback parsing", e);
            return parseFallback(aiResponse, input);
        }
    }
    
    /**
     * Parses AI response into IntentAnalysis.
     */
    public static IntentAnalysis parseIntentAnalysis(String aiResponse, String intentId) {
        try {
            JsonNode root = mapper.readTree(aiResponse);
            
            return IntentAnalysis.builder()
                    .intentId(intentId)
                    .feasible(extractBoolean(root, "feasible", true))
                    .risks(extractStringList(root.path("risks")))
                    .gaps(extractStringList(root.path("gaps")))
                    .assumptions(extractStringList(root.path("assumptions")))
                    .scores(extractScores(root.path("scores")))
                    .summary(extractString(root, "summary", aiResponse))
                    .build();
                    
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse analysis as JSON", e);
            return IntentAnalysis.builder()
                    .intentId(intentId)
                    .feasible(true)
                    .risks(List.of())
                    .gaps(List.of())
                    .assumptions(List.of())
                    .scores(Map.of())
                    .summary(aiResponse)
                    .build();
        }
    }
    
    /**
     * Parses AI response into ShapeSpec.
     */
    public static ShapeSpec parseShapeSpec(String aiResponse, String intentRef, String tenantId) {
        try {
            JsonNode root = mapper.readTree(aiResponse);
            
            return ShapeSpec.builder()
                    .id(UUID.randomUUID().toString())
                    .intentRef(intentRef)
                    .domainModel(extractDomainModel(root.path("domainModel")))
                    .workflows(extractWorkflows(root.path("workflows")))
                    .integrations(extractIntegrations(root.path("integrations")))
                    .architecture(extractArchitecture(root.path("architecture")))
                    .metadata(Map.of("source", "ai-generated"))
                    .createdAt(Instant.now())
                    .tenantId(tenantId)
                    .build();
                    
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse shape spec as JSON", e);
            return createDefaultShapeSpec(intentRef, tenantId);
        }
    }
    
    // Helper methods
    
    private static String extractString(JsonNode node, String field, String defaultValue) {
        return node.has(field) ? node.get(field).asText() : defaultValue;
    }
    
    private static boolean extractBoolean(JsonNode node, String field, boolean defaultValue) {
        return node.has(field) ? node.get(field).asBoolean() : defaultValue;
    }
    
    private static List<String> extractStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> result.add(item.asText()));
        }
        return result;
    }
    
    private static List<GoalSpec> extractGoals(JsonNode goalsNode) {
        List<GoalSpec> goals = new ArrayList<>();
        if (goalsNode.isArray()) {
            goalsNode.forEach(goalNode -> {
                goals.add(GoalSpec.builder()
                        .id(UUID.randomUUID().toString())
                        .description(extractString(goalNode, "description", ""))
                        .category(extractString(goalNode, "category", "general"))
                        .priority(goalNode.has("priority") ? goalNode.get("priority").asInt() : 1)
                        .successMetrics(extractStringList(goalNode.path("successMetrics")))
                        .build());
            });
        }
        return goals.isEmpty() ? List.of(createDefaultGoal()) : goals;
    }
    
    private static List<PersonaSpec> extractPersonas(JsonNode personasNode) {
        List<PersonaSpec> personas = new ArrayList<>();
        if (personasNode.isArray()) {
            personasNode.forEach(personaNode -> {
                personas.add(PersonaSpec.builder()
                        .id(UUID.randomUUID().toString())
                        .name(extractString(personaNode, "name", "User"))
                        .description(extractString(personaNode, "description", ""))
                        .needs(extractStringList(personaNode.path("needs")))
                        .painPoints(extractStringList(personaNode.path("painPoints")))
                        .attributes(new HashMap<>())
                        .build());
            });
        }
        return personas.isEmpty() ? List.of(createDefaultPersona()) : personas;
    }
    
    private static List<ConstraintSpec> extractConstraints(JsonNode constraintsNode) {
        List<ConstraintSpec> constraints = new ArrayList<>();
        if (constraintsNode.isArray()) {
            constraintsNode.forEach(constraintNode -> {
                constraints.add(ConstraintSpec.builder()
                        .id(UUID.randomUUID().toString())
                        .type(extractString(constraintNode, "type", "general"))
                        .description(extractString(constraintNode, "description", ""))
                        .severity(extractString(constraintNode, "severity", "medium"))
                        .details(new HashMap<>())
                        .build());
            });
        }
        return constraints;
    }
    
    private static Map<String, Double> extractScores(JsonNode scoresNode) {
        Map<String, Double> scores = new HashMap<>();
        if (scoresNode.isObject()) {
            scoresNode.fields().forEachRemaining(entry -> {
                scores.put(entry.getKey(), entry.getValue().asDouble());
            });
        }
        return scores;
    }
    
    private static DomainModel extractDomainModel(JsonNode node) {
        // Simplified extraction - can be enhanced
        return DomainModel.builder()
                .entities(List.of())
                .relationships(List.of())
                .boundedContexts(List.of())
                .build();
    }
    
    private static List<WorkflowSpec> extractWorkflows(JsonNode node) {
        return List.of();
    }
    
    private static List<IntegrationSpec> extractIntegrations(JsonNode node) {
        return List.of();
    }
    
    private static ArchitecturePattern extractArchitecture(JsonNode node) {
        return ArchitecturePattern.builder()
                .name(extractString(node, "name", "microservices"))
                .description(extractString(node, "description", ""))
                .components(List.of())
                .properties(new HashMap<>())
                .build();
    }
    
    // Fallback methods
    
    private static IntentSpec parseFallback(String text, IntentInput input) {
        return IntentSpec.builder()
                .id(UUID.randomUUID().toString())
                .productName("Product from: " + text.substring(0, Math.min(50, text.length())))
                .description(text)
                .goals(List.of(createDefaultGoal()))
                .personas(List.of(createDefaultPersona()))
                .constraints(List.of())
                .metadata(Map.of("source", "fallback-parse"))
                .createdAt(Instant.now())
                .tenantId(input.tenantId())
                .build();
    }
    
    private static GoalSpec createDefaultGoal() {
        return GoalSpec.builder()
                .id(UUID.randomUUID().toString())
                .description("Primary business goal")
                .category("business")
                .priority(1)
                .successMetrics(List.of())
                .build();
    }
    
    private static PersonaSpec createDefaultPersona() {
        return PersonaSpec.builder()
                .id(UUID.randomUUID().toString())
                .name("Primary User")
                .description("Main user persona")
                .needs(List.of())
                .painPoints(List.of())
                .attributes(new HashMap<>())
                .build();
    }
    
    private static ShapeSpec createDefaultShapeSpec(String intentRef, String tenantId) {
        return ShapeSpec.builder()
                .id(UUID.randomUUID().toString())
                .intentRef(intentRef)
                .domainModel(DomainModel.builder()
                        .entities(List.of())
                        .relationships(List.of())
                        .boundedContexts(List.of())
                        .build())
                .workflows(List.of())
                .integrations(List.of())
                .architecture(ArchitecturePattern.builder()
                        .name("microservices")
                        .description("Default microservices architecture")
                        .components(List.of())
                        .properties(new HashMap<>())
                        .build())
                .metadata(Map.of("source", "default"))
                .createdAt(Instant.now())
                .tenantId(tenantId)
                .build();
    }
}
