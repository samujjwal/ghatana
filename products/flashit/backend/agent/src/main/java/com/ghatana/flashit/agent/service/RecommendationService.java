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

import java.util.ArrayList;
import java.util.List;

/**
 * AI-powered recommendation engine for personalized moment suggestions.
 *
 * <p>Generates recommendations using multiple strategies:
 * <ul>
 *   <li><b>revisit</b> — Resurface forgotten moments worth revisiting</li>
 *   <li><b>connect</b> — Suggest connections between related moments</li>
 *   <li><b>habit</b> — Encourage positive recording habits</li>
 *   <li><b>wellbeing</b> — Mood-aware wellbeing suggestions</li>
 *   <li><b>explore</b> — Suggest new topics/spheres to explore</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Generates personalized AI recommendations from moment history
 * @doc.layer product
 * @doc.pattern Service
 */
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final OpenAIClient client;
    private final String model;

    public RecommendationService(OpenAIClient client, AgentConfig config) {
        this.client = client;
        this.model = config.getOpenAiModel();
    }

    /**
     * Generate personalized recommendations based on user's recent moments.
     *
     * @param request the recommendation request with user context and strategy preferences
     * @return ranked list of recommendations
     */
    public RecommendationResponse generateRecommendations(RecommendationRequest request) {
        long start = System.currentTimeMillis();
        log.info("Generating recommendations for user={}, strategies={}, limit={}",
                request.userId(), request.strategies(), request.limit());

        List<String> strategies = request.strategies() != null && !request.strategies().isEmpty()
                ? request.strategies()
                : List.of("revisit", "connect", "habit", "wellbeing", "explore");

        String momentsSummary = PromptUtils.buildMomentsSummary(
                request.recentMoments(), "No recent moments available.");

        String sphereContext = "";
        if (request.sphereIds() != null && !request.sphereIds().isEmpty()) {
            sphereContext = "\nFocus on spheres: " + String.join(", ", request.sphereIds());
        }

        String excludeContext = "";
        if (request.excludeIds() != null && !request.excludeIds().isEmpty()) {
            excludeContext = "\nExclude recommendations already shown (IDs): "
                    + String.join(", ", request.excludeIds());
        }

        String systemPrompt = buildSystemPrompt(strategies);
        String userPrompt = buildUserPrompt(momentsSummary, strategies, request.limit(),
                sphereContext, excludeContext);

        try {
            ChatCompletion completion = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .addSystemMessage(systemPrompt)
                            .addUserMessage(userPrompt)
                            .temperature(0.7)
                            .build()
            );

            String content = completion.choices().getFirst().message().content().orElse("{}");
            long elapsed = System.currentTimeMillis() - start;

            List<RecommendationItem> items = parseRecommendations(content);
            log.info("Generated {} recommendations in {}ms", items.size(), elapsed);

            return new RecommendationResponse(items, items.size(), strategies, elapsed, model);

        } catch (Exception e) {
            log.error("Recommendation generation failed for user={}", request.userId(), e);
            long elapsed = System.currentTimeMillis() - start;
            return new RecommendationResponse(List.of(), 0, strategies, elapsed, model);
        }
    }

    private String buildSystemPrompt(List<String> strategies) {
        return String.format("""
                You are a personal intelligence assistant that generates personalized recommendations
                based on the user's moment history. You produce smart, empathetic suggestions that
                help users reflect, connect ideas, and grow.
                
                Strategies to use: %s
                
                Strategy definitions:
                - revisit: Resurface moments the user may have forgotten but are worth revisiting
                - connect: Identify non-obvious connections between moments across different spheres
                - habit: Encourage positive recording and reflection habits
                - wellbeing: Provide mood-aware wellbeing suggestions based on emotional patterns
                - explore: Suggest new topics, spheres, or perspectives to explore
                
                Respond in JSON format:
                {
                  "recommendations": [
                    {
                      "type": "REVISIT|CONNECT|HABIT|WELLBEING|EXPLORE",
                      "strategy": "specific_strategy_name",
                      "title": "Short title",
                      "content": "Detailed recommendation text",
                      "score": 0.0-1.0,
                      "reasoning": "Why this recommendation is relevant",
                      "relatedMomentIds": ["id1", "id2"],
                      "actionUrl": "/moments/id or /spheres/id or null"
                    }
                  ]
                }
                
                Generate diverse, high-quality recommendations. Order by relevance score.
                """, String.join(", ", strategies));
    }

    private String buildUserPrompt(String momentsSummary, List<String> strategies, int limit,
                                    String sphereContext, String excludeContext) {
        return String.format("""
                User's recent moments:
                %s
                %s%s
                Generate up to %d recommendations using these strategies: %s
                Focus on actionable, specific suggestions.
                """, momentsSummary, sphereContext, excludeContext, limit, String.join(", ", strategies));
    }

    private List<RecommendationItem> parseRecommendations(String jsonContent) {
        List<RecommendationItem> items = new ArrayList<>();
        try {
            var mapper = JsonConfig.objectMapper();
            JsonNode tree = mapper.readTree(jsonContent);
            JsonNode recsNode = tree.has("recommendations") ? tree.get("recommendations") : tree;

            if (recsNode.isArray()) {
                for (JsonNode r : recsNode) {
                    List<String> relatedIds = new ArrayList<>();
                    if (r.has("relatedMomentIds") && r.get("relatedMomentIds").isArray()) {
                        r.get("relatedMomentIds").forEach(n -> relatedIds.add(n.asText()));
                    }

                    items.add(new RecommendationItem(
                            r.path("type").asText("EXPLORE"),
                            r.path("strategy").asText("general"),
                            r.path("title").asText(""),
                            r.path("content").asText(""),
                            r.path("score").asDouble(0.5),
                            r.path("reasoning").asText(""),
                            relatedIds,
                            r.path("actionUrl").asText(null)
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse recommendation JSON: {}", e.getMessage());
        }
        return items;
    }
}
