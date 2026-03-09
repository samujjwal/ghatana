package com.ghatana.flashit.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.config.JsonConfig;
import com.ghatana.flashit.agent.dto.*;
import com.ghatana.flashit.agent.util.PromptUtils;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Intelligence accumulation service for cross-session learning.
 *
 * <p>Computes and maintains a long-term knowledge profile for each user by
 * analyzing their moment history across sessions. Tracks:
 * <ul>
 *   <li>Evolving topic interests and their trends</li>
 *   <li>Recurring entities and their importance</li>
 *   <li>Emotional patterns and wellbeing trajectory</li>
 *   <li>Activity patterns (time, frequency, sphere distribution)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Builds evolving knowledge profiles from cross-session moment analysis
 * @doc.layer product
 * @doc.pattern Service
 */
public class IntelligenceAccumulationService {
    private static final Logger log = LoggerFactory.getLogger(IntelligenceAccumulationService.class);

    private final OpenAIClient client;
    private final String model;

    public IntelligenceAccumulationService(OpenAIClient client, AgentConfig config) {
        this.client = client;
        this.model = config.getOpenAiModel();
    }

    /**
     * Compute or update the user's knowledge profile from their moment history.
     *
     * @param request accumulation request with moments and existing profile data
     * @return updated knowledge profile with topics, entities, and patterns
     */
    public IntelligenceAccumulationResponse computeProfile(IntelligenceAccumulationRequest request) {
        long start = System.currentTimeMillis();
        log.info("Computing intelligence profile for user={}, moments={}, profileV={}",
                request.userId(),
                request.moments() != null ? request.moments().size() : 0,
                request.profileVersion());

        String momentsSummary = PromptUtils.buildMomentsSummary(request.moments());
        String existingContext = buildExistingContext(request);

        String systemPrompt = """
                You are a personal intelligence engine that builds a comprehensive knowledge profile
                by analyzing a user's moment history. You track evolving interests, recurring entities,
                emotional patterns, and activity rhythms.
                
                Respond in JSON format:
                {
                  "topTopics": [
                    {"topic": "name", "weight": 0.0-1.0, "momentCount": N, "trend": "rising|stable|declining"}
                  ],
                  "topEntities": [
                    {"entity": "name", "entityType": "person|place|org|concept", "weight": 0.0-1.0, "mentionCount": N}
                  ],
                  "emotionProfile": {
                    "joy": 0.3, "sadness": 0.1, "excitement": 0.2, ...
                  },
                  "activityPattern": {
                    "peakHours": [9, 14, 21],
                    "mostActiveDay": "Monday",
                    "averageMomentsPerDay": 3.5,
                    "sphereDistribution": {"work": 0.4, "personal": 0.3, "health": 0.3}
                  },
                  "newInsights": [
                    "Insight about trend or pattern...",
                    ...
                  ]
                }
                
                Order topics and entities by weight (descending). Limit to top 20 each.
                Provide 2-5 new insights about patterns or changes.
                """;

        String userPrompt = String.format("""
                %s
                
                User's recent moments:
                %s
                
                Compute the updated knowledge profile. Identify trends and evolution.
                """, existingContext, momentsSummary);

        try {
            ChatCompletion completion = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .addSystemMessage(systemPrompt)
                            .addUserMessage(userPrompt)
                            .temperature(0.4)
                            .build()
            );

            String content = completion.choices().getFirst().message().content().orElse("{}");
            long elapsed = System.currentTimeMillis() - start;

            return parseProfileResponse(content, request.userId(),
                    request.profileVersion() + 1, elapsed);

        } catch (Exception e) {
            log.error("Intelligence accumulation failed for user={}", request.userId(), e);
            long elapsed = System.currentTimeMillis() - start;
            return new IntelligenceAccumulationResponse(
                    request.userId(), List.of(), List.of(), Map.of(), Map.of(),
                    List.of(), request.profileVersion(), elapsed, model);
        }
    }

    private String buildExistingContext(IntelligenceAccumulationRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.existingTopics() != null && !request.existingTopics().isEmpty()) {
            sb.append("Previously known topics: ")
                    .append(String.join(", ", request.existingTopics())).append("\n");
        }
        if (request.existingEntities() != null && !request.existingEntities().isEmpty()) {
            sb.append("Previously known entities: ")
                    .append(String.join(", ", request.existingEntities())).append("\n");
        }
        if (request.profileVersion() > 0) {
            sb.append("This is profile version ").append(request.profileVersion())
                    .append(" — look for changes and trends.\n");
        }
        return sb.isEmpty() ? "This is the first profile computation." : sb.toString();
    }

    private IntelligenceAccumulationResponse parseProfileResponse(
            String jsonContent, String userId, int newVersion, long elapsed) {
        try {
            var mapper = JsonConfig.objectMapper();
            JsonNode tree = mapper.readTree(jsonContent);

            List<TopicWeight> topics = new ArrayList<>();
            if (tree.has("topTopics") && tree.get("topTopics").isArray()) {
                for (JsonNode t : tree.get("topTopics")) {
                    topics.add(new TopicWeight(
                            t.path("topic").asText(""),
                            t.path("weight").asDouble(0.5),
                            t.path("momentCount").asInt(1),
                            t.path("trend").asText("stable")
                    ));
                }
            }

            List<EntityWeight> entities = new ArrayList<>();
            if (tree.has("topEntities") && tree.get("topEntities").isArray()) {
                for (JsonNode e : tree.get("topEntities")) {
                    entities.add(new EntityWeight(
                            e.path("entity").asText(""),
                            e.path("entityType").asText("concept"),
                            e.path("weight").asDouble(0.5),
                            e.path("mentionCount").asInt(1)
                    ));
                }
            }

            Map<String, Double> emotionProfile = new LinkedHashMap<>();
            if (tree.has("emotionProfile") && tree.get("emotionProfile").isObject()) {
                tree.get("emotionProfile").fields().forEachRemaining(
                        entry -> emotionProfile.put(entry.getKey(), entry.getValue().asDouble(0.0)));
            }

            Map<String, Object> activityPattern = new LinkedHashMap<>();
            if (tree.has("activityPattern") && tree.get("activityPattern").isObject()) {
                JsonNode ap = tree.get("activityPattern");
                if (ap.has("peakHours")) {
                    List<Integer> hours = new ArrayList<>();
                    ap.get("peakHours").forEach(h -> hours.add(h.asInt()));
                    activityPattern.put("peakHours", hours);
                }
                if (ap.has("mostActiveDay")) {
                    activityPattern.put("mostActiveDay", ap.get("mostActiveDay").asText());
                }
                if (ap.has("averageMomentsPerDay")) {
                    activityPattern.put("averageMomentsPerDay", ap.get("averageMomentsPerDay").asDouble());
                }
                if (ap.has("sphereDistribution") && ap.get("sphereDistribution").isObject()) {
                    Map<String, Double> dist = new LinkedHashMap<>();
                    ap.get("sphereDistribution").fields().forEachRemaining(
                            e -> dist.put(e.getKey(), e.getValue().asDouble(0.0)));
                    activityPattern.put("sphereDistribution", dist);
                }
            }

            List<String> newInsights = new ArrayList<>();
            if (tree.has("newInsights") && tree.get("newInsights").isArray()) {
                tree.get("newInsights").forEach(n -> newInsights.add(n.asText()));
            }

            log.info("Profile computed: {} topics, {} entities, {} insights in {}ms",
                    topics.size(), entities.size(), newInsights.size(), elapsed);

            return new IntelligenceAccumulationResponse(
                    userId, topics, entities, emotionProfile, activityPattern,
                    newInsights, newVersion, elapsed, model);

        } catch (Exception e) {
            log.warn("Failed to parse intelligence profile JSON: {}", e.getMessage());
            return new IntelligenceAccumulationResponse(
                    userId, List.of(), List.of(), Map.of(), Map.of(),
                    List.of(), newVersion, elapsed, model);
        }
    }
}
