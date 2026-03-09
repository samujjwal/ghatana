package com.ghatana.flashit.agent.service;

import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.dto.*;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI-powered moment classification into spheres.
 *
 * <p>Uses OpenAI chat completions to analyze moment content and determine
 * the best matching sphere from the user's available spheres.
 *
 * @doc.type class
 * @doc.purpose Classifies moments into spheres using LLM analysis
 * @doc.layer product
 * @doc.pattern Service
 */
public class ClassificationService {
    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);

    private final OpenAIClient client;
    private final String model;

    public ClassificationService(OpenAIClient client, AgentConfig config) {
        this.client = client;
        this.model = config.getOpenAiModel();
    }

    /**
     * Classify a moment into the best-matching sphere.
     *
     * @param request classification request with content and available spheres
     * @return classification response with sphere assignment and alternatives
     */
    public ClassificationResponse classify(ClassificationRequest request) {
        long start = System.currentTimeMillis();
        log.info("Classifying moment for user={}", request.userId());

        String sphereContext = request.availableSpheres().stream()
                .map(s -> String.format("- %s (id=%s, type=%s): %s",
                        s.name(), s.id(), s.type(),
                        s.description() != null ? s.description() : "No description"))
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                You are a personal context classifier. Given a user's moment (text, emotions, tags) \
                and their available spheres, determine which sphere the moment best belongs to.
                
                Respond in JSON format:
                {
                  "sphereId": "...",
                  "sphereName": "...",
                  "confidence": 0.0-1.0,
                  "reasoning": "...",
                  "alternatives": [{"sphereId":"...","sphereName":"...","confidence":0.0-1.0,"reasoning":"..."}]
                }
                """;

        String userPrompt = String.format("""
                Content: %s
                Content Type: %s
                Emotions: %s
                Tags: %s
                Intent: %s
                Transcript: %s
                
                Available Spheres:
                %s
                """,
                request.content(),
                request.contentType(),
                request.emotions() != null ? String.join(", ", request.emotions()) : "none",
                request.tags() != null ? String.join(", ", request.tags()) : "none",
                request.userIntent() != null ? request.userIntent() : "none",
                request.transcript() != null ? request.transcript() : "none",
                sphereContext);

        try {
            ChatCompletion completion = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .addSystemMessage(systemPrompt)
                            .addUserMessage(userPrompt)
                            .temperature(0.3)
                            .build()
            );

            String content = completion.choices().getFirst().message().content().orElse("{}");
            long elapsed = System.currentTimeMillis() - start;
            log.info("Classification completed in {}ms", elapsed);

            // Parse LLM JSON response
            var mapper = com.ghatana.flashit.agent.config.JsonConfig.objectMapper();
            var tree = mapper.readTree(content);

            String sphereId = tree.path("sphereId").asText("");
            String sphereName = tree.path("sphereName").asText("");
            double confidence = tree.path("confidence").asDouble(0.5);
            String reasoning = tree.path("reasoning").asText("");

            List<SphereSuggestion> alternatives = new ArrayList<>();
            if (tree.has("alternatives") && tree.get("alternatives").isArray()) {
                for (var alt : tree.get("alternatives")) {
                    alternatives.add(new SphereSuggestion(
                            alt.path("sphereId").asText(""),
                            alt.path("sphereName").asText(""),
                            alt.path("confidence").asDouble(0.0),
                            alt.path("reasoning").asText("")
                    ));
                }
            }

            return new ClassificationResponse(sphereId, sphereName, confidence, reasoning,
                    alternatives, elapsed, model);

        } catch (Exception e) {
            log.error("Classification failed for user={}", request.userId(), e);
            long elapsed = System.currentTimeMillis() - start;

            // Fallback: return first sphere with low confidence
            String fallbackId = "";
            String fallbackName = "Unknown";
            if (request.availableSpheres() != null && !request.availableSpheres().isEmpty()) {
                fallbackId = request.availableSpheres().getFirst().id();
                fallbackName = request.availableSpheres().getFirst().name();
            }
            return new ClassificationResponse(fallbackId, fallbackName, 0.1,
                    "Classification failed: " + e.getMessage(), List.of(), elapsed, model);
        }
    }

    /**
     * Get sphere suggestions for a moment without committing to one.
     *
     * @param request classification request
     * @return list of sphere suggestions ranked by confidence
     */
    public List<SphereSuggestion> suggestSpheres(ClassificationRequest request) {
        ClassificationResponse response = classify(request);
        List<SphereSuggestion> suggestions = new ArrayList<>();
        suggestions.add(new SphereSuggestion(response.sphereId(), response.sphereName(),
                response.confidence(), response.reasoning()));
        suggestions.addAll(response.alternatives());
        return suggestions;
    }
}
