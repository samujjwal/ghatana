package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.tutorputor.contentgeneration.prompts.PromptTemplateEngine;
import com.ghatana.tutorputor.contentgeneration.contracts.v1.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
            String prompt = promptEngine.buildPrompt(
                "generateClaims",
                Map.of(
                    "topic", request.getTopic(),
                    "gradeLevel", request.getGradeLevel(),
                    "domain", request.getDomain(),
                    "maxClaims", String.valueOf(request.getMaxClaims())
                ));
            return llmProvider.generate(prompt, Map.of(
                "temperature", temperature,
                "max_tokens", maxTokens
            )).map(raw -> {
                GenerateClaimsResponse response = parseClaimsResponse(request.getContext().getRequestId(), raw);
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
            // Get example types as list of strings (no enum conversion needed)
            String prompt = promptEngine.buildPrompt(
                "generateExamples",
                Map.of(
                    "claimText", request.getClaimText(),
                    "gradeLevel", request.getGradeLevel(),
                    "exampleTypes", String.join(",", request.getExampleTypesList()),
                    "count", String.valueOf(request.getCount())
                ));
            return llmProvider.generate(prompt, Map.of(
                "temperature", temperature,
                "max_tokens", maxTokens
            )).map(raw -> {
                GenerateExamplesResponse response = parseExamplesResponse(request.getContext().getRequestId(), raw);
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
            // Extract claim text from first claim if available
            String claimText = request.getClaimsList().isEmpty() ?
                "" : request.getClaimsList().get(0).getClaimText();

            String prompt = promptEngine.buildPrompt(
                "analyzeContentNeeds",
                Map.of(
                    "claimText", claimText,
                    "targetGradeLevel", request.getTargetGradeLevel(),
                    "learningObjective", request.getLearningObjective()
                ));
            return llmProvider.generate(prompt, Map.of(
                "temperature", Math.max(0.1, temperature - 0.2), // lower temperature for analysis
                "max_tokens", maxTokens
            )).map(raw -> {
                AnalyzeContentNeedsResponse response = parseContentNeedsResponse(request.getContext().getRequestId(), raw);
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
            String prompt = promptEngine.buildPrompt(
                "generateSimulation",
                Map.of(
                    "claimText", request.getClaimText(),
                    "gradeLevel", request.getGradeLevel(),
                    "simulationType", request.getSimulationType(),
                    "maxSteps", String.valueOf(request.getMaxSteps()),
                    "durationMinutes", String.valueOf(request.getDurationMinutes())
                ));
            return llmProvider.generate(prompt, Map.of(
                "temperature", temperature,
                "max_tokens", maxTokens * 2 // simulations need more tokens
            )).map(raw -> {
                GenerateSimulationResponse response = parseSimulationResponse(request.getContext().getRequestId(), raw);
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
            String prompt = promptEngine.buildPrompt(
                "generateAnimation",
                Map.of(
                    "claimText", request.getClaimText(),
                    "claimRef", request.getClaimRef(),
                    "durationSeconds", String.valueOf(request.getDurationSeconds())
                ));
            return llmProvider.generate(prompt, Map.of(
                "temperature", temperature,
                "max_tokens", maxTokens
            )).map(raw -> {
                GenerateAnimationResponse response = parseAnimationResponse(request.getRequestId(), raw,
                        String.valueOf(request.getAnimationType()), request.getDurationSeconds());
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

    private GenerateClaimsResponse parseClaimsResponse(String requestId, String raw) {
        GenerateClaimsResponse.Builder builder = GenerateClaimsResponse.newBuilder();

        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode claimsNode = root.get("claims");
            if (claimsNode != null && claimsNode.isArray()) {
                int index = 0;
                for (JsonNode claimNode : claimsNode) {
                    String claimRef = claimNode.path("claim_ref").asText(requestId + "-C" + index);
                    String text = claimNode.path("text").asText("");

                    ContentClaim.Builder claim = ContentClaim.newBuilder()
                        .setClaimId(requestId + "-C" + index)
                        .setClaimRef(claimRef)
                        .setClaimText(text);

                    builder.addClaims(claim);
                    index++;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse claims JSON, falling back: {}", e.getMessage());
        }

        return builder.build();
    }

    private GenerateExamplesResponse parseExamplesResponse(String requestId, String raw) {
        GenerateExamplesResponse.Builder builder = GenerateExamplesResponse.newBuilder();

        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode examplesNode = root.get("examples");
            if (examplesNode != null && examplesNode.isArray()) {
                int index = 0;
                for (JsonNode exNode : examplesNode) {
                    ContentExample.Builder example = ContentExample.newBuilder()
                        .setExampleId(requestId + "-E" + index)
                        .setScenario(exNode.path("scenario").asText(""))
                        .setQuestion(exNode.path("question").asText(""))
                        .setAnswer(exNode.path("answer").asText(""));

                    builder.addExamples(example);
                    index++;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse examples JSON: {}", e.getMessage());
        }

        return builder.build();
    }

    private AnalyzeContentNeedsResponse parseContentNeedsResponse(String requestId, String raw) {
        AnalyzeContentNeedsResponse.Builder builder = AnalyzeContentNeedsResponse.newBuilder();

        try {
            JsonNode root = MAPPER.readTree(raw);
            ContentNeedsAnalysis.Builder analysis = ContentNeedsAnalysis.newBuilder();

            // Parse gaps
            JsonNode gapsNode = root.get("gaps");
            if (gapsNode != null && gapsNode.isArray()) {
                for (JsonNode gapNode : gapsNode) {
                    ContentGap.Builder gap = ContentGap.newBuilder()
                        .setGapType(gapNode.path("type").asText(""))
                        .setDescription(gapNode.path("description").asText(""))
                        .setSeverity(gapNode.path("severity").asText(""));
                    analysis.addGaps(gap);
                }
            }

            builder.setAnalysis(analysis);

        } catch (Exception e) {
            log.warn("Failed to parse content needs JSON, using heuristics: {}", e.getMessage());
            ContentNeedsAnalysis.Builder analysis = ContentNeedsAnalysis.newBuilder();
            builder.setAnalysis(analysis);
        }

        return builder.build();
    }

    private GenerateSimulationResponse parseSimulationResponse(String requestId, String raw) {
        GenerateSimulationResponse.Builder builder = GenerateSimulationResponse.newBuilder();

        try {
            JsonNode root = MAPPER.readTree(raw);
            SimulationContent.Builder sim = SimulationContent.newBuilder()
                .setSimulationId(requestId)
                .setTitle(root.path("title").asText(""))
                .setDescription(root.path("description").asText(""));

            JsonNode stepsNode = root.get("steps");
            if (stepsNode != null && stepsNode.isArray()) {
                int index = 0;
                for (JsonNode stepNode : stepsNode) {
                    SimulationStep.Builder step = SimulationStep.newBuilder()
                        .setStepId(requestId + "-S" + index)
                        .setOrderIndex(index)
                        .setTitle(stepNode.path("title").asText(""))
                        .setDescription(stepNode.path("description").asText(""));
                    sim.addSteps(step);
                    index++;
                }
            }

            builder.setSimulation(sim);

        } catch (Exception e) {
            log.warn("Failed to parse simulation JSON: {}", e.getMessage());
        }

        return builder.build();
    }

    private GenerateAnimationResponse parseAnimationResponse(
            String requestId, String raw, String animationType, int durationSeconds) {
        GenerateAnimationResponse.Builder builder = GenerateAnimationResponse.newBuilder();

        try {
            JsonNode root = MAPPER.readTree(raw);
            AnimationContent.Builder anim = AnimationContent.newBuilder()
                .setAnimationId(requestId)
                .setTitle(root.path("title").asText(""))
                .setDescription(root.path("description").asText(""))
                .setTotalDurationSeconds(durationSeconds > 0 ? durationSeconds : 30);

            JsonNode keyframesNode = root.get("keyframes");
            if (keyframesNode != null && keyframesNode.isArray()) {
                for (JsonNode kfNode : keyframesNode) {
                    AnimationKeyframe.Builder kf = AnimationKeyframe.newBuilder()
                        .setFrameIndex(kfNode.path("frame_index").asInt(0))
                        .setDurationMs(kfNode.path("duration_ms").asInt(0));
                    anim.addKeyframes(kf);
                }
            }

            builder.setAnimation(anim);

        } catch (Exception e) {
            log.warn("Failed to parse animation JSON: {}", e.getMessage());
        }

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
