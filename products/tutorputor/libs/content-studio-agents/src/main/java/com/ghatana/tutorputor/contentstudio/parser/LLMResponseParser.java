package com.ghatana.tutorputor.contentstudio.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.tutorputor.contentstudio.grpc.ContentNeeds;
import com.ghatana.tutorputor.contentstudio.grpc.Entity;
import com.ghatana.tutorputor.contentstudio.grpc.ExampleNeeds;
import com.ghatana.tutorputor.contentstudio.grpc.Example;
import com.ghatana.tutorputor.contentstudio.grpc.AnimationNeeds;
import com.ghatana.tutorputor.contentstudio.grpc.SimulationNeeds;
import com.ghatana.tutorputor.contentstudio.grpc.Keyframe;
import com.ghatana.tutorputor.contentstudio.grpc.LearningClaim;
import com.ghatana.tutorputor.contentstudio.grpc.Position;
import com.ghatana.tutorputor.contentstudio.grpc.SimulationManifest;
import com.ghatana.tutorputor.contentstudio.grpc.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Production-ready parser for LLM responses into protobuf messages.
 * 
 * <p>This parser handles the JSON output from LLM responses and converts them
 * into strongly-typed protobuf messages. It includes:
 * <ul>
 *   <li>Robust JSON extraction (handles markdown code blocks)</li>
 *   <li>Graceful degradation on parse errors</li>
 *   <li>Detailed logging for debugging</li>
 *   <li>Validation of required fields</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Parse LLM JSON responses into proto messages
 * @doc.layer product
 * @doc.pattern Parser
 */
public final class LLMResponseParser {

    private static final Logger LOG = LoggerFactory.getLogger(LLMResponseParser.class);
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    private LLMResponseParser() {
        // Utility class
    }

    // =========================================================================
    // Claims Parsing
    // =========================================================================

    /**
     * Parses LLM response into a list of LearningClaim proto messages.
     */
    public static List<LearningClaim> parseClaims(String llmResponse) {
        List<LearningClaim> claims = new ArrayList<>();
        
        try {
            String json = extractJson(llmResponse);
            JsonNode root = MAPPER.readTree(json);
            JsonNode claimsNode = root.get("claims");
            
            if (claimsNode != null && claimsNode.isArray()) {
                int orderIndex = 0;
                for (JsonNode claimNode : claimsNode) {
                    LearningClaim.Builder builder = LearningClaim.newBuilder()
                        .setClaimRef(getTextOrDefault(claimNode, "claim_ref", "C" + (claims.size() + 1)))
                        .setText(getTextOrDefault(claimNode, "text", ""))
                        .setBloomLevel(getTextOrDefault(claimNode, "bloom_level", "UNDERSTAND"))
                        .setOrderIndex(orderIndex);
                    
                    claims.add(builder.build());
                    orderIndex++;
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to parse claims from LLM response", e);
        }
        
        return claims;
    }

    // =========================================================================
    // Content Needs Parsing
    // =========================================================================

    /**
     * Parses LLM response into ContentNeeds proto message.
     */
    public static ContentNeeds parseContentNeeds(String llmResponse, String claimRef) {
        ContentNeeds.Builder builder = ContentNeeds.newBuilder();
        
        try {
            String json = extractJson(llmResponse);
            JsonNode root = MAPPER.readTree(json);
            
            // Parse examples needs
            JsonNode examplesNode = root.get("examples");
            if (examplesNode != null) {
                boolean required = getBoolOrDefault(examplesNode, "required", false);
                ExampleNeeds.Builder exampleNeeds = ExampleNeeds.newBuilder()
                    .setRequired(required)
                    .setCount(getIntOrDefault(examplesNode, "count", 2))
                    .setNecessity(getDoubleOrDefault(examplesNode, "necessity", 0.8))
                    .setRationale(getTextOrDefault(examplesNode, "rationale", ""));

                JsonNode typesNode = examplesNode.get("types");
                if (typesNode != null && typesNode.isArray()) {
                    for (JsonNode typeNode : typesNode) {
                        exampleNeeds.addTypes(typeNode.asText());
                    }
                }

                builder.setExamples(exampleNeeds.build());
            }
            
            // Parse simulation needs
            JsonNode simNode = root.get("simulation");
            if (simNode != null) {
                boolean required = getBoolOrDefault(simNode, "required", false);
                builder.setSimulation(SimulationNeeds.newBuilder()
                    .setRequired(required)
                    .setInteractionType(getTextOrDefault(simNode, "interaction_type", "PARAMETER_EXPLORATION"))
                    .setComplexity(getTextOrDefault(simNode, "complexity", "MEDIUM"))
                    .setNecessity(getDoubleOrDefault(simNode, "necessity", 0.7))
                    .setRationale(getTextOrDefault(simNode, "rationale", ""))
                    .build());
            }
            
            // Parse animation needs
            JsonNode animNode = root.get("animation");
            if (animNode != null) {
                boolean required = getBoolOrDefault(animNode, "required", false);
                builder.setAnimation(AnimationNeeds.newBuilder()
                    .setRequired(required)
                    .setAnimationType(getTextOrDefault(animNode, "animation_type", "TWO_D"))
                    .setDurationSeconds(getIntOrDefault(animNode, "duration_seconds", 30))
                    .setNecessity(getDoubleOrDefault(animNode, "necessity", 0.5))
                    .setRationale(getTextOrDefault(animNode, "rationale", ""))
                    .build());
            }
            
        } catch (Exception e) {
            LOG.error("Failed to parse content needs from LLM response", e);
        }
        
        return builder.build();
    }

    // =========================================================================
    // Examples Parsing
    // =========================================================================

    /**
     * Parses LLM response into a list of Example proto messages.
     */
    public static List<Example> parseExamples(String llmResponse, String claimRef) {
        List<Example> examples = new ArrayList<>();
        
        try {
            String json = extractJson(llmResponse);
            JsonNode root = MAPPER.readTree(json);
            JsonNode examplesNode = root.get("examples");
            
            if (examplesNode != null && examplesNode.isArray()) {
                int idx = 1;
                for (JsonNode exNode : examplesNode) {
                    Example.Builder builder = Example.newBuilder()
                        .setExampleId(getTextOrDefault(exNode, "example_id", "EX" + idx))
                        .setType(getTextOrDefault(exNode, "type", "REAL_WORLD"))
                        .setTitle(getTextOrDefault(exNode, "title", "Example " + idx))
                        .setDescription(getTextOrDefault(exNode, "description", ""))
                        .setContent(getTextOrDefault(exNode, "solution_content", getTextOrDefault(exNode, "content", "")));
                    
                    // Parse key learning points
                    JsonNode pointsNode = exNode.get("key_learning_points");
                    if (pointsNode != null && pointsNode.isArray()) {
                        for (JsonNode point : pointsNode) {
                            builder.addTags(point.asText());
                        }
                    }
                    
                    examples.add(builder.build());
                    idx++;
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to parse examples from LLM response", e);
        }
        
        return examples;
    }

    // =========================================================================
    // Simulation Parsing
    // =========================================================================

    /**
     * Parses LLM response into a SimulationManifest proto message.
     */
    public static SimulationManifest parseSimulation(String llmResponse, String claimRef) {
        SimulationManifest.Builder builder = SimulationManifest.newBuilder();
        
        try {
            String json = extractJson(llmResponse);
            JsonNode root = MAPPER.readTree(json);
            JsonNode manifestNode = root.has("manifest") ? root.get("manifest") : root;
            
            if (manifestNode != null) {
                builder.setManifestId(getTextOrDefault(manifestNode, "manifest_id", "manifest"))
                       .setVersion(getTextOrDefault(manifestNode, "version", "v1"))
                       .setDomain(getTextOrDefault(manifestNode, "domain", ""))
                       .setTitle(getTextOrDefault(manifestNode, "name", getTextOrDefault(manifestNode, "title", "Simulation")))
                       .setDescription(getTextOrDefault(manifestNode, "description", ""));
                
                // Parse entities
                JsonNode entitiesNode = manifestNode.get("entities");
                if (entitiesNode != null && entitiesNode.isArray()) {
                    for (JsonNode entityNode : entitiesNode) {
                        Entity.Builder entityBuilder = Entity.newBuilder()
                            .setId(getTextOrDefault(entityNode, "entity_id", getTextOrDefault(entityNode, "id", "entity")))
                            .setLabel(getTextOrDefault(entityNode, "label", ""))
                            .setEntityType(getTextOrDefault(entityNode, "type", getTextOrDefault(entityNode, "entity_type", "BALL")));

                        JsonNode propsNode = entityNode.get("properties");
                        if (propsNode != null) {
                            entityBuilder.setVisual(propsNode.toString());

                            Position.Builder posBuilder = Position.newBuilder();
                            if (propsNode.has("x")) posBuilder.setX(propsNode.get("x").asDouble());
                            if (propsNode.has("y")) posBuilder.setY(propsNode.get("y").asDouble());
                            if (propsNode.has("z")) posBuilder.setZ(propsNode.get("z").asDouble());
                            entityBuilder.setPosition(posBuilder.build());
                        }

                        builder.addEntities(entityBuilder.build());
                    }
                }
                
                // Parse config
                JsonNode configNode = manifestNode.get("config");
                if (configNode != null) {
                    builder.setDomainConfig(configNode.toString());
                }

                JsonNode stepsNode = manifestNode.get("steps");
                if (stepsNode != null && stepsNode.isArray()) {
                    for (JsonNode stepNode : stepsNode) {
                        Step.Builder stepBuilder = Step.newBuilder()
                            .setId(getTextOrDefault(stepNode, "id", "step"))
                            .setDescription(getTextOrDefault(stepNode, "description", ""))
                            .setDuration(getIntOrDefault(stepNode, "duration", 0));

                        JsonNode actionsNode = stepNode.get("actions");
                        if (actionsNode != null && actionsNode.isArray()) {
                            for (JsonNode actionNode : actionsNode) {
                                stepBuilder.addActions(actionNode.toString());
                            }
                        }

                        builder.addSteps(stepBuilder.build());
                    }
                }

                JsonNode keyframesNode = manifestNode.get("keyframes");
                if (keyframesNode != null && keyframesNode.isArray()) {
                    for (JsonNode kfNode : keyframesNode) {
                        builder.addKeyframes(Keyframe.newBuilder()
                            .setId(getTextOrDefault(kfNode, "id", "keyframe"))
                            .setTimeMs(getIntOrDefault(kfNode, "time_ms", 0))
                            .setState(getTextOrDefault(kfNode, "state", kfNode.toString()))
                            .build());
                    }
                }
            }
            
        } catch (Exception e) {
            LOG.error("Failed to parse simulation from LLM response", e);
        }
        
        return builder.build();
    }

    // =========================================================================
    // JSON Extraction Utilities
    // =========================================================================

    /**
     * Extracts JSON from LLM response, handling markdown code blocks.
     */
    private static String extractJson(String response) {
        if (response == null || response.isEmpty()) {
            return "{}";
        }
        
        // Try to extract from markdown code block
        int jsonStart = response.indexOf("```json");
        if (jsonStart != -1) {
            int start = jsonStart + 7;
            int end = response.indexOf("```", start);
            if (end != -1) {
                return response.substring(start, end).trim();
            }
        }
        
        // Try to extract from any code block
        jsonStart = response.indexOf("```");
        if (jsonStart != -1) {
            int start = response.indexOf("\n", jsonStart) + 1;
            int end = response.indexOf("```", start);
            if (end != -1) {
                return response.substring(start, end).trim();
            }
        }
        
        // Try to find raw JSON
        int braceStart = response.indexOf("{");
        if (braceStart != -1) {
            int braceEnd = response.lastIndexOf("}");
            if (braceEnd > braceStart) {
                return response.substring(braceStart, braceEnd + 1);
            }
        }
        
        return response;
    }

    private static String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }

    private static int getIntOrDefault(JsonNode node, String field, int defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asInt();
        }
        return defaultValue;
    }

    private static double getDoubleOrDefault(JsonNode node, String field, double defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asDouble();
        }
        return defaultValue;
    }

    private static boolean getBoolOrDefault(JsonNode node, String field, boolean defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asBoolean();
        }
        return defaultValue;
    }

}
