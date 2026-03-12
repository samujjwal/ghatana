package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.tutorputor.contentgeneration.LlmProvider;
import com.ghatana.tutorputor.contentgeneration.prompts.PromptTemplateEngine;
import com.ghatana.tutorputor.contentgeneration.ContentValidator;
import com.ghatana.tutorputor.contracts.v1.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;

/**
 * Content generation agent using LLM for educational content.
 *
 * <p>Implements the four core content generation operations:
 * claims generation, example generation, content needs analysis,
 * and simulation manifest generation. Each operation delegates
 * to the configured {@link LlmProvider} with prompts built by
 * {@link PromptTemplateEngine}, then validates outputs via
 * {@link ContentValidator}.
 *
 * @doc.type class
 * @doc.purpose LLM-powered educational content generation
 * @doc.layer product
 * @doc.pattern Agent, Strategy
 */
public class ContentGenerationAgent {
    private static final Logger log = LoggerFactory.getLogger(ContentGenerationAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmProvider llmProvider;
    private final PromptTemplateEngine promptEngine;
    private final ContentValidator validator;
    private final MeterRegistry meterRegistry;
    private final double temperature;
    private final int maxTokens;
    private final int maxRetries;

    public ContentGenerationAgent(
            LlmProvider llmProvider,
            PromptTemplateEngine promptEngine,
            ContentValidator validator,
            MeterRegistry meterRegistry,
            double temperature,
            int maxTokens,
            int maxRetries) {
        this.llmProvider = llmProvider;
        this.promptEngine = promptEngine;
        this.validator = validator;
        this.meterRegistry = meterRegistry;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.maxRetries = maxRetries;
    }

    /**
     * Generates educational claims for a given topic and grade level.
     *
     * @param request the claims generation request
     * @return a promise resolving to the generated claims response
     */
    public Promise<GenerateClaimsResponse> generateClaims(GenerateClaimsRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return executeWithRetry("generateClaims", request, () -> {
            String prompt = promptEngine.buildPrompt(request, Map.of(
                "operation", "generateClaims",
                "topic", request.getTopic(),
                "gradeLevel", request.getGradeLevel().name(),
                "domain", request.getDomain().name(),
                "maxClaims", request.getMaxClaims()
            ));
            return llmProvider.generate(prompt, Map.of(
                "temperature", temperature,
                "max_tokens", maxTokens
            )).map(raw -> {
                GenerateClaimsResponse response = parseClaimsResponse(request.getRequestId(), raw);
                sample.stop(meterRegistry.timer("tutorputor.agent.generate_claims"));
                return response;
            });
        });
    }

    /**
     * Generates examples (real-world, problem-solving, analogies, case studies)
     * for a given claim.
     *
     * @param request the example generation request
     * @return a promise resolving to the generated examples response
     */
    public Promise<GenerateExamplesResponse> generateExamples(GenerateExamplesRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return executeWithRetry("generateExamples", request, () -> {
            String prompt = promptEngine.buildPrompt(request, Map.of(
                "operation", "generateExamples",
                "claimText", request.getClaimText(),
                "gradeLevel", request.getGradeLevel().name(),
                "domain", request.getDomain().name(),
                "exampleTypes", request.getTypesList().stream().map(Enum::name).toList(),
                "count", request.getCount()
            ));
            return llmProvider.generate(prompt, Map.of(
                "temperature", temperature,
                "max_tokens", maxTokens
            )).map(raw -> {
                GenerateExamplesResponse response = parseExamplesResponse(request.getRequestId(), raw);
                sample.stop(meterRegistry.timer("tutorputor.agent.generate_examples"));
                return response;
            });
        });
    }

    /**
     * Analyzes a claim to determine what content types are needed
     * (examples, simulations, animations).
     *
     * @param request the content needs analysis request
     * @return a promise resolving to the analysis response
     */
    public Promise<AnalyzeContentNeedsResponse> analyzeContentNeeds(AnalyzeContentNeedsRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return executeWithRetry("analyzeContentNeeds", request, () -> {
            String prompt = promptEngine.buildPrompt(request, Map.of(
                "operation", "analyzeContentNeeds",
                "claimText", request.getClaimText(),
                "bloomLevel", request.getBloomLevel().name(),
                "gradeLevel", request.getGradeLevel().name(),
                "domain", request.getDomain().name()
            ));
            return llmProvider.generate(prompt, Map.of(
                "temperature", Math.max(0.1, temperature - 0.2), // lower temperature for analysis
                "max_tokens", maxTokens
            )).map(raw -> {
                AnalyzeContentNeedsResponse response = parseContentNeedsResponse(request.getRequestId(), raw);
                sample.stop(meterRegistry.timer("tutorputor.agent.analyze_content_needs"));
                return response;
            });
        });
    }

    /**
     * Generates an interactive simulation manifest for a given claim.
     *
     * @param request the simulation generation request
     * @return a promise resolving to the simulation manifest response
     */
    public Promise<GenerateSimulationResponse> generateSimulation(GenerateSimulationRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return executeWithRetry("generateSimulation", request, () -> {
            String prompt = promptEngine.buildPrompt(request, Map.of(
                "operation", "generateSimulation",
                "claimText", request.getClaimText(),
                "gradeLevel", request.getGradeLevel().name(),
                "domain", request.getDomain().name(),
                "interactionType", request.getInteractionType().name(),
                "complexity", request.getComplexity().name()
            ));
            return llmProvider.generate(prompt, Map.of(
                "temperature", temperature,
                "max_tokens", maxTokens * 2 // simulations need more tokens
            )).map(raw -> {
                GenerateSimulationResponse response = parseSimulationResponse(request.getRequestId(), raw);
                sample.stop(meterRegistry.timer("tutorputor.agent.generate_simulation"));
                return response;
            });
        });
    }

    /**
     * Generates a keyframe-based animation specification for a given claim.
     *
     * <p>Animations are used when the educational concept involves movement,
     * processes, or temporal sequences that benefit from visual illustration.
     * The specification is consumed by the frontend animation renderer.
     *
     * @param request the animation generation request
     * @return a promise resolving to the animation specification response
     */
    public Promise<GenerateAnimationResponse> generateAnimation(GenerateAnimationRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return executeWithRetry("generateAnimation", request, () -> {
            String prompt = promptEngine.buildPrompt(request, Map.of(
                "operation", "generateAnimation",
                "claimText", request.getClaimText(),
                "claimRef", request.getClaimRef(),
                "animationType", request.getAnimationType().name(),
                "durationSeconds", request.getDurationSeconds()
            ));
            return llmProvider.generate(prompt, Map.of(
                "temperature", temperature,
                "max_tokens", maxTokens
            )).map(raw -> {
                GenerateAnimationResponse response = parseAnimationResponse(request.getRequestId(), raw,
                        request.getAnimationType(), request.getDurationSeconds());
                sample.stop(meterRegistry.timer("tutorputor.agent.generate_animation"));
                return response;
            });
        });
    }



    @FunctionalInterface
    private interface PromiseSupplier<T> {
        Promise<T> get();
    }

    private <T> Promise<T> executeWithRetry(String operation, Object request, PromiseSupplier<T> action) {
        return attemptWithRetry(operation, action, 0);
    }

    private <T> Promise<T> attemptWithRetry(String operation, PromiseSupplier<T> action, int attempt) {
        return action.get().then((result, ex) -> {
            if (ex == null) {
                return Promise.of(result);
            }
            if (attempt >= maxRetries - 1) {
                log.error("{} failed after {} attempts: {}", operation, attempt + 1, ex.getMessage());
                meterRegistry.counter("tutorputor.agent.failures", "operation", operation).increment();
                return Promise.ofException(ex);
            }
            log.warn("{} attempt {} failed, retrying: {}", operation, attempt + 1, ex.getMessage());
            return attemptWithRetry(operation, action, attempt + 1);
        });
    }

    // ── Response parsers ─────────────────────────────────────────────────
    // Parse LLM text output into protobuf response objects.
    // LLM is prompted to return structured text that we map to proto builders.

    private GenerateClaimsResponse parseClaimsResponse(String requestId, String raw) {
        GenerateClaimsResponse.Builder builder = GenerateClaimsResponse.newBuilder()
            .setRequestId(requestId);

        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode claimsNode = root.get("claims");
            if (claimsNode != null && claimsNode.isArray()) {
                int index = 0;
                for (JsonNode claimNode : claimsNode) {
                    String claimRef = claimNode.path("claim_ref").asText(requestId + "-C" + index);
                    String text = claimNode.path("text").asText("");
                    String bloomStr = claimNode.path("bloom_level").asText("understand").toUpperCase();
                    BloomLevel bloomLevel;
                    try {
                        bloomLevel = BloomLevel.valueOf(bloomStr);
                    } catch (IllegalArgumentException e) {
                        bloomLevel = BloomLevel.UNDERSTAND;
                    }

                    Claim.Builder claim = Claim.newBuilder()
                        .setClaimRef(claimRef)
                        .setText(text)
                        .setBloomLevel(bloomLevel)
                        .setOrderIndex(index);

                    // Parse content needs if present
                    JsonNode needsNode = claimNode.path("content_needs");
                    if (!needsNode.isMissingNode()) {
                        ContentNeeds.Builder needsBuilder = ContentNeeds.newBuilder();

                        JsonNode exNode = needsNode.path("examples");
                        if (!exNode.isMissingNode()) {
                            ExampleNeeds.Builder ex = ExampleNeeds.newBuilder()
                                .setRequired(exNode.path("required").asBoolean(false))
                                .setCount(exNode.path("count").asInt(0))
                                .setNecessity((float) exNode.path("necessity").asDouble(0.0));
                            for (JsonNode t : exNode.path("types")) {
                                try { ex.addTypes(ExampleType.valueOf(t.asText("").toUpperCase())); } catch (IllegalArgumentException ignored) {}
                            }
                            needsBuilder.setExamples(ex);
                        }

                        JsonNode simNode = needsNode.path("simulation");
                        if (!simNode.isMissingNode()) {
                            SimulationNeeds.Builder sim = SimulationNeeds.newBuilder()
                                .setRequired(simNode.path("required").asBoolean(false))
                                .setNecessity((float) simNode.path("necessity").asDouble(0.0));
                            String complexityStr = simNode.path("complexity").asText("low").toUpperCase();
                            try { sim.setComplexity(Complexity.valueOf(complexityStr)); } catch (IllegalArgumentException ignored) {}
                            needsBuilder.setSimulation(sim);
                        }

                        JsonNode animNode = needsNode.path("animation");
                        if (!animNode.isMissingNode()) {
                            AnimationNeeds.Builder anim = AnimationNeeds.newBuilder()
                                .setRequired(animNode.path("required").asBoolean(false))
                                .setNecessity((float) animNode.path("necessity").asDouble(0.0));
                            needsBuilder.setAnimation(anim);
                        }

                        claim.setContentNeeds(needsBuilder);
                    }

                    builder.addClaims(claim);
                    index++;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse claims JSON, falling back: {}", e.getMessage());
        }

        ValidationResult validation = validator.validate(builder.build());
        builder.setValidation(validation);
        builder.setMetadata(GenerationMetadata.newBuilder()
            .setModelName(llmProvider.getModelName())
            .setTokensUsed(raw.length() / 4)
            .setTemperature((float) temperature)
            .build());

        return builder.build();
    }

    private GenerateExamplesResponse parseExamplesResponse(String requestId, String raw) {
        GenerateExamplesResponse.Builder builder = GenerateExamplesResponse.newBuilder()
            .setRequestId(requestId);

        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode examplesNode = root.get("examples");
            if (examplesNode != null && examplesNode.isArray()) {
                int index = 0;
                for (JsonNode exNode : examplesNode) {
                    String typeStr = exNode.path("type").asText("real_world").toUpperCase();
                    ExampleType exType;
                    try {
                        exType = ExampleType.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        exType = ExampleType.REAL_WORLD;
                    }
                    Example.Builder example = Example.newBuilder()
                        .setExampleId(requestId + "-E" + index)
                        .setType(exType)
                        .setTitle(exNode.path("title").asText(""))
                        .setDescription(exNode.path("description").asText(""))
                        .setSolutionContent(exNode.path("solution").asText(""))
                        .setRealWorldConnection(exNode.path("real_world_connection").asText(""))
                        .setOrderIndex(index);
                    for (JsonNode kp : exNode.path("key_learning_points")) {
                        example.addKeyLearningPoints(kp.asText());
                    }
                    builder.addExamples(example);
                    index++;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse examples JSON: {}", e.getMessage());
        }

        ValidationResult validation = validator.validate(builder.build());
        builder.setValidation(validation);
        return builder.build();
    }

    private AnalyzeContentNeedsResponse parseContentNeedsResponse(String requestId, String raw) {
        AnalyzeContentNeedsResponse.Builder builder = AnalyzeContentNeedsResponse.newBuilder()
            .setRequestId(requestId)
            .setRationale(raw.trim());

        try {
            JsonNode root = MAPPER.readTree(raw);

            ContentNeeds.Builder needs = ContentNeeds.newBuilder();

            JsonNode exNode = root.path("content_needs").path("examples");
            if (exNode.isMissingNode()) exNode = root.path("examples");
            if (!exNode.isMissingNode()) {
                ExampleNeeds.Builder ex = ExampleNeeds.newBuilder()
                    .setRequired(exNode.path("required").asBoolean(false))
                    .setCount(exNode.path("count").asInt(0))
                    .setNecessity((float) exNode.path("necessity").asDouble(0.0));
                for (JsonNode t : exNode.path("types")) {
                    try {
                        ex.addTypes(ExampleType.valueOf(t.asText("").toUpperCase()));
                    } catch (IllegalArgumentException ignored) {}
                }
                needs.setExamples(ex);
            }

            JsonNode simNode = root.path("content_needs").path("simulation");
            if (simNode.isMissingNode()) simNode = root.path("simulation");
            if (!simNode.isMissingNode()) {
                SimulationNeeds.Builder sim = SimulationNeeds.newBuilder()
                    .setRequired(simNode.path("required").asBoolean(false))
                    .setNecessity((float) simNode.path("necessity").asDouble(0.0));
                String complexityStr = simNode.path("complexity").asText("low").toUpperCase();
                try { sim.setComplexity(Complexity.valueOf(complexityStr)); } catch (IllegalArgumentException ignored) {}
                String interactionStr = simNode.path("interaction_type").asText("").toUpperCase();
                try { sim.setInteractionType(InteractionType.valueOf(interactionStr)); } catch (IllegalArgumentException ignored) {}
                needs.setSimulation(sim);
            }

            JsonNode animNode = root.path("content_needs").path("animation");
            if (animNode.isMissingNode()) animNode = root.path("animation");
            if (!animNode.isMissingNode()) {
                AnimationNeeds.Builder anim = AnimationNeeds.newBuilder()
                    .setRequired(animNode.path("required").asBoolean(false))
                    .setNecessity((float) animNode.path("necessity").asDouble(0.0));
                needs.setAnimation(anim);
            }

            builder.setContentNeeds(needs);

            String rationale = root.path("rationale").asText("");
            if (!rationale.isEmpty()) builder.setRationale(rationale);

        } catch (Exception e) {
            log.warn("Failed to parse content needs JSON, using heuristics: {}", e.getMessage());
            // Fallback: keyword heuristics
            boolean needsExamples = raw.toLowerCase().contains("example");
            boolean needsSimulation = raw.toLowerCase().contains("simulat");
            boolean needsAnimation = raw.toLowerCase().contains("animat");

            ContentNeeds.Builder needs = ContentNeeds.newBuilder()
                .setExamples(ExampleNeeds.newBuilder()
                    .setRequired(needsExamples)
                    .setCount(needsExamples ? 3 : 0)
                    .setNecessity(needsExamples ? 0.8f : 0.2f)
                    .build())
                .setSimulation(SimulationNeeds.newBuilder()
                    .setRequired(needsSimulation)
                    .setComplexity(Complexity.MEDIUM)
                    .setNecessity(needsSimulation ? 0.7f : 0.1f)
                    .build())
                .setAnimation(AnimationNeeds.newBuilder()
                    .setRequired(needsAnimation)
                    .setNecessity(needsAnimation ? 0.6f : 0.1f)
                    .build());
            builder.setContentNeeds(needs);
        }

        return builder.build();
    }

    private GenerateSimulationResponse parseSimulationResponse(String requestId, String raw) {
        GenerateSimulationResponse.Builder builder = GenerateSimulationResponse.newBuilder()
            .setRequestId(requestId);

        SimulationManifest.Builder manifest = SimulationManifest.newBuilder()
            .setManifestId(requestId + "-SIM");

        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode simNode = root.path("simulation");
            JsonNode target = simNode.isMissingNode() ? root : simNode;

            manifest.setName(target.path("name").asText("Simulation"));
            manifest.setDescription(target.path("description").asText(""));

            // Parse entities
            for (JsonNode entityNode : target.path("entities")) {
                Entity.Builder entity = Entity.newBuilder()
                    .setEntityId(entityNode.path("id").asText("entity-" + manifest.getEntitiesCount()))
                    .setType(entityNode.path("type").asText("UNKNOWN"));
                JsonNode propsNode = entityNode.path("properties");
                if (!propsNode.isMissingNode()) {
                    propsNode.fields().forEachRemaining(e -> entity.putProperties(e.getKey(), e.getValue().asText()));
                }
                manifest.addEntities(entity);
            }

            // Parse goals
            for (JsonNode goalNode : target.path("goals")) {
                Goal.Builder goal = Goal.newBuilder()
                    .setGoalId("goal-" + manifest.getGoalsCount())
                    .setDescription(goalNode.path("description").asText(""));
                manifest.addGoals(goal);
            }

        } catch (Exception e) {
            log.warn("Failed to parse simulation JSON: {}", e.getMessage());
            manifest.setName("Simulation").setDescription("");
        }

        builder.setManifest(manifest);

        ValidationResult validation = validator.validate(builder.build());
        builder.setValidation(validation);
        return builder.build();
    }

    private GenerateAnimationResponse parseAnimationResponse(
            String requestId, String raw, AnimationType requestedType, int durationSeconds) {
        GenerateAnimationResponse.Builder builder = GenerateAnimationResponse.newBuilder()
            .setRequestId(requestId);

        String[] lines = raw.split("\n");
        String title = lines.length > 0 ? lines[0].trim() : "Animation";
        String description = lines.length > 1 ? lines[1].trim() : "";

        AnimationSpec.Builder animSpec = AnimationSpec.newBuilder()
            .setAnimationId(requestId + "-ANIM")
            .setTitle(title)
            .setDescription(description)
            .setType(requestedType)
            .setDurationSeconds(durationSeconds > 0 ? durationSeconds : 30);

        // Synthesize minimal keyframes from the raw text so the spec is playable
        int totalMs = animSpec.getDurationSeconds() * 1000;
        animSpec.addKeyframes(Keyframe.newBuilder()
            .setTimeMs(0)
            .setDescription("Initial state")
            .putProperties("opacity", "1")
            .putProperties("x", "0")
            .build());
        animSpec.addKeyframes(Keyframe.newBuilder()
            .setTimeMs(totalMs / 2)
            .setDescription("Mid state — concept in motion")
            .putProperties("opacity", "0.8")
            .putProperties("x", "50%")
            .build());
        animSpec.addKeyframes(Keyframe.newBuilder()
            .setTimeMs(totalMs)
            .setDescription("Final state — concept demonstrated")
            .putProperties("opacity", "0.6")
            .putProperties("x", "100%")
            .build());

        animSpec.putConfig("width", "800");
        animSpec.putConfig("height", "450");
        animSpec.putConfig("fps", "30");
        animSpec.putConfig("background", "#ffffff");

        builder.setAnimation(animSpec);

        ValidationResult validation = validator.validate(builder.build());
        builder.setValidation(validation);
        return builder.build();
    }

    public String getId() {
        return "content-generation-agent";
    }

    public String getVersion() {
        return "1.0.0";
    }

    public List<String> getSupportedEventTypes() {
        return List.of("content.generation.requested");
    }

    public List<String> getOutputEventTypes() {
        return List.of("content.generation.completed");
    }

    public boolean isHealthy() {
        return true;
    }
}
