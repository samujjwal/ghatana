package com.ghatana.flashit.agent.service;

import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.dto.*;
import com.ghatana.flashit.agent.util.PromptUtils;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI reflection service for generating insights, patterns, and connections.
 *
 * <p>Analyzes collections of moments to surface patterns, themes, action items,
 * and connections between experiences using LLM-powered analysis.
 *
 * @doc.type class
 * @doc.purpose Generates AI-powered reflections and insights from moment collections
 * @doc.layer product
 * @doc.pattern Service
 */
public class ReflectionService {
    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

    private final OpenAIClient client;
    private final String model;

    public ReflectionService(OpenAIClient client, AgentConfig config) {
        this.client = client;
        this.model = config.getOpenAiModel();
    }

    /**
     * Generate insights from a collection of moments.
     *
     * @param request reflection request with moments
     * @return reflection response with summary, insights, patterns
     */
    public ReflectionResponse generateInsights(ReflectionRequest request) {
        return reflect(request, "insights");
    }

    /**
     * Detect patterns across moments.
     *
     * @param request reflection request with moments
     * @return reflection response focused on patterns
     */
    public ReflectionResponse detectPatterns(ReflectionRequest request) {
        return reflect(request, "patterns");
    }

    /**
     * Find connections between moments.
     *
     * @param request reflection request with moments
     * @return reflection response focused on connections
     */
    public ReflectionResponse findConnections(ReflectionRequest request) {
        return reflect(request, "connections");
    }

    private ReflectionResponse reflect(ReflectionRequest request, String focusType) {
        long start = System.currentTimeMillis();
        log.info("Generating {} reflection for user={}, sphereId={}, moments={}",
                focusType, request.userId(), request.sphereId(), request.moments().size());

        String momentsSummary = PromptUtils.buildMomentsSummary(request.moments());

        String systemPrompt = String.format("""
                You are a personal reflection assistant. Analyze the user's moments and generate %s.
                
                Respond in JSON format:
                {
                  "summary": "A concise 2-3 sentence summary of the moments",
                  "insights": ["insight1", "insight2", ...],
                  "patterns": [{"pattern":"...","frequency":N,"confidence":0.0-1.0,"examples":["..."]}],
                  "connections": [{"momentId":"...","relationship":"...","confidence":0.0-1.0}],
                  "themes": ["theme1", "theme2", ...],
                  "actionItems": ["action1", "action2", ...]
                }
                
                Focus especially on %s. Be empathetic and constructive.
                """, focusType, focusType);

        String userPrompt = String.format("""
                User's moments from sphere %s:
                Time range: %s
                
                %s
                """,
                request.sphereId(),
                request.timeRange() != null ? request.timeRange() : "recent",
                momentsSummary);

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
            log.info("Reflection ({}) completed in {}ms", focusType, elapsed);

            var mapper = com.ghatana.flashit.agent.config.JsonConfig.objectMapper();
            var tree = mapper.readTree(content);

            String summary = tree.path("summary").asText("");

            List<String> insights = new ArrayList<>();
            if (tree.has("insights") && tree.get("insights").isArray()) {
                tree.get("insights").forEach(n -> insights.add(n.asText()));
            }

            List<PatternInfo> patterns = new ArrayList<>();
            if (tree.has("patterns") && tree.get("patterns").isArray()) {
                for (var p : tree.get("patterns")) {
                    List<String> examples = new ArrayList<>();
                    if (p.has("examples") && p.get("examples").isArray()) {
                        p.get("examples").forEach(e -> examples.add(e.asText()));
                    }
                    patterns.add(new PatternInfo(
                            p.path("pattern").asText(""),
                            p.path("frequency").asInt(1),
                            p.path("confidence").asDouble(0.5),
                            examples
                    ));
                }
            }

            List<ConnectionInfo> connections = new ArrayList<>();
            if (tree.has("connections") && tree.get("connections").isArray()) {
                for (var c : tree.get("connections")) {
                    connections.add(new ConnectionInfo(
                            c.path("momentId").asText(""),
                            c.path("relationship").asText(""),
                            c.path("confidence").asDouble(0.5)
                    ));
                }
            }

            List<String> themes = new ArrayList<>();
            if (tree.has("themes") && tree.get("themes").isArray()) {
                tree.get("themes").forEach(n -> themes.add(n.asText()));
            }

            List<String> actionItems = new ArrayList<>();
            if (tree.has("actionItems") && tree.get("actionItems").isArray()) {
                tree.get("actionItems").forEach(n -> actionItems.add(n.asText()));
            }

            return new ReflectionResponse(summary, insights, patterns, connections,
                    themes, actionItems, elapsed, model);

        } catch (Exception e) {
            log.error("Reflection ({}) failed for user={}", focusType, request.userId(), e);
            long elapsed = System.currentTimeMillis() - start;
            return new ReflectionResponse(
                    "Reflection generation failed: " + e.getMessage(),
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    elapsed, model);
        }
    }
}
