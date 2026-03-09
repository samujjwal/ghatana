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

/**
 * NLP analysis service for entity extraction, sentiment analysis, and mood detection.
 *
 * <p>Uses OpenAI chat completions to perform various NLP tasks on moment text.
 *
 * @doc.type class
 * @doc.purpose Performs NLP analysis (entities, sentiment, mood) on moment text
 * @doc.layer product
 * @doc.pattern Service
 */
public class NLPService {
    private static final Logger log = LoggerFactory.getLogger(NLPService.class);

    private final OpenAIClient client;
    private final String model;

    public NLPService(OpenAIClient client, AgentConfig config) {
        this.client = client;
        this.model = config.getOpenAiModel();
    }

    /**
     * Extract named entities from text.
     *
     * @param request NLP request with text
     * @return response with extracted entities
     */
    public NLPResponse extractEntities(NLPRequest request) {
        long start = System.currentTimeMillis();
        log.info("Extracting entities for momentId={}, user={}", request.momentId(), request.userId());

        String systemPrompt = """
                You are a named entity recognition system. Extract entities from the text.
                
                Respond in JSON format:
                {
                  "entities": [
                    {"text":"...","type":"PERSON|ORGANIZATION|LOCATION|DATE|EVENT|CONCEPT|EMOTION","confidence":0.0-1.0,"startOffset":0,"endOffset":0}
                  ]
                }
                
                Entity types: PERSON, ORGANIZATION, LOCATION, DATE, EVENT, CONCEPT, EMOTION.
                Only include entities with confidence >= 0.5.
                """;

        try {
            ChatCompletion completion = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .addSystemMessage(systemPrompt)
                            .addUserMessage(request.text())
                            .temperature(0.1)
                            .build()
            );

            String content = completion.choices().getFirst().message().content().orElse("{}");
            long elapsed = System.currentTimeMillis() - start;

            var mapper = com.ghatana.flashit.agent.config.JsonConfig.objectMapper();
            var tree = mapper.readTree(content);

            List<Entity> entities = new ArrayList<>();
            if (tree.has("entities") && tree.get("entities").isArray()) {
                for (var e : tree.get("entities")) {
                    entities.add(new Entity(
                            e.path("text").asText(""),
                            e.path("type").asText("CONCEPT"),
                            e.path("confidence").asDouble(0.5),
                            e.path("startOffset").asInt(0),
                            e.path("endOffset").asInt(0)
                    ));
                }
            }

            log.info("Entity extraction completed in {}ms, found={}", elapsed, entities.size());
            return new NLPResponse(request.momentId(), entities, null, null, elapsed, model);

        } catch (Exception e) {
            log.error("Entity extraction failed for momentId={}", request.momentId(), e);
            long elapsed = System.currentTimeMillis() - start;
            return new NLPResponse(request.momentId(), List.of(), null, null, elapsed, model);
        }
    }

    /**
     * Analyze sentiment of text.
     *
     * @param request NLP request with text
     * @return response with sentiment analysis
     */
    public NLPResponse analyzeSentiment(NLPRequest request) {
        long start = System.currentTimeMillis();
        log.info("Analyzing sentiment for momentId={}, user={}", request.momentId(), request.userId());

        String systemPrompt = """
                Analyze the sentiment of the following text.
                
                Respond in JSON format:
                {
                  "sentiment": {
                    "label": "positive|negative|neutral|mixed",
                    "score": -1.0 to 1.0,
                    "positive": 0.0-1.0,
                    "negative": 0.0-1.0,
                    "neutral": 0.0-1.0
                  }
                }
                """;

        try {
            ChatCompletion completion = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .addSystemMessage(systemPrompt)
                            .addUserMessage(request.text())
                            .temperature(0.1)
                            .build()
            );

            String content = completion.choices().getFirst().message().content().orElse("{}");
            long elapsed = System.currentTimeMillis() - start;

            var mapper = com.ghatana.flashit.agent.config.JsonConfig.objectMapper();
            var tree = mapper.readTree(content);

            SentimentResult sentiment = null;
            if (tree.has("sentiment")) {
                var s = tree.get("sentiment");
                sentiment = new SentimentResult(
                        s.path("label").asText("neutral"),
                        s.path("score").asDouble(0.0),
                        s.path("positive").asDouble(0.0),
                        s.path("negative").asDouble(0.0),
                        s.path("neutral").asDouble(1.0)
                );
            }

            log.info("Sentiment analysis completed in {}ms", elapsed);
            return new NLPResponse(request.momentId(), List.of(), sentiment, null, elapsed, model);

        } catch (Exception e) {
            log.error("Sentiment analysis failed for momentId={}", request.momentId(), e);
            long elapsed = System.currentTimeMillis() - start;
            return new NLPResponse(request.momentId(), List.of(),
                    new SentimentResult("neutral", 0.0, 0.0, 0.0, 1.0), null,
                    elapsed, model);
        }
    }

    /**
     * Detect mood from text.
     *
     * @param request NLP request with text
     * @return response with mood detection
     */
    public NLPResponse detectMood(NLPRequest request) {
        long start = System.currentTimeMillis();
        log.info("Detecting mood for momentId={}, user={}", request.momentId(), request.userId());

        String systemPrompt = """
                Detect the mood/emotional state expressed in the following text.
                
                Respond in JSON format:
                {
                  "mood": {
                    "primaryMood": "happy|sad|anxious|excited|calm|angry|hopeful|nostalgic|grateful|confused|determined|peaceful",
                    "confidence": 0.0-1.0,
                    "secondaryMoods": ["mood1", "mood2"],
                    "intensity": 0.0-1.0
                  }
                }
                """;

        try {
            ChatCompletion completion = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .addSystemMessage(systemPrompt)
                            .addUserMessage(request.text())
                            .temperature(0.2)
                            .build()
            );

            String content = completion.choices().getFirst().message().content().orElse("{}");
            long elapsed = System.currentTimeMillis() - start;

            var mapper = com.ghatana.flashit.agent.config.JsonConfig.objectMapper();
            var tree = mapper.readTree(content);

            MoodResult mood = null;
            if (tree.has("mood")) {
                var m = tree.get("mood");
                List<String> secondaryMoods = new ArrayList<>();
                if (m.has("secondaryMoods") && m.get("secondaryMoods").isArray()) {
                    m.get("secondaryMoods").forEach(n -> secondaryMoods.add(n.asText()));
                }
                mood = new MoodResult(
                        m.path("primaryMood").asText("neutral"),
                        m.path("confidence").asDouble(0.5),
                        secondaryMoods,
                        m.path("intensity").asDouble(0.5)
                );
            }

            log.info("Mood detection completed in {}ms", elapsed);
            return new NLPResponse(request.momentId(), List.of(), null, mood, elapsed, model);

        } catch (Exception e) {
            log.error("Mood detection failed for momentId={}", request.momentId(), e);
            long elapsed = System.currentTimeMillis() - start;
            return new NLPResponse(request.momentId(), List.of(), null,
                    new MoodResult("neutral", 0.5, List.of(), 0.5),
                    elapsed, model);
        }
    }
}
