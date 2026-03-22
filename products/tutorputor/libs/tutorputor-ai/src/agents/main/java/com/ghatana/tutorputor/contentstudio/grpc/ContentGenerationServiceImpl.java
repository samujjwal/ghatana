package com.ghatana.tutorputor.contentstudio.grpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import io.activej.promise.Promise;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Production-ready implementation of the ContentGenerationService gRPC service.
 * 
 * <p>This service provides AI-powered educational content generation including:
 * <ul>
 *   <li>Learning claims generation with Bloom's taxonomy alignment</li>
 *   <li>Content needs analysis for pedagogical completeness</li>
 *   <li>Example generation with real-world connections</li>
 *   <li>Simulation manifest generation for interactive experiences</li>
 *   <li>Content validation against educational standards</li>
 *   <li>Content enhancement with engagement improvements</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose AI-powered educational content generation service
 * @doc.layer product
 * @doc.pattern Service
 */
public class ContentGenerationServiceImpl extends ContentGenerationServiceGrpc.ContentGenerationServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ContentGenerationServiceImpl.class);
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();
    
    private final LLMGateway llmGateway;
    private final Executor executor;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter claimsGeneratedCounter;
    private final Counter examplesGeneratedCounter;
    private final Counter simulationsGeneratedCounter;
    private final Counter validationsPerformedCounter;
    private final Counter enhancementsPerformedCounter;
    private final Timer generationTimer;
    private final Counter llmErrorCounter;

    /**
     * Creates a new ContentGenerationServiceImpl.
     *
     * @param llmGateway the LLM gateway for AI completions
     * @param executor the executor for async operations
     * @param meterRegistry the metrics registry
     */
    public ContentGenerationServiceImpl(LLMGateway llmGateway, Executor executor, MeterRegistry meterRegistry) {
        this.llmGateway = llmGateway;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.claimsGeneratedCounter = Counter.builder("tutorputor.content.claims.generated")
            .description("Number of learning claims generated")
            .register(meterRegistry);
        this.examplesGeneratedCounter = Counter.builder("tutorputor.content.examples.generated")
            .description("Number of examples generated")
            .register(meterRegistry);
        this.simulationsGeneratedCounter = Counter.builder("tutorputor.content.simulations.generated")
            .description("Number of simulations generated")
            .register(meterRegistry);
        this.validationsPerformedCounter = Counter.builder("tutorputor.content.validations.performed")
            .description("Number of content validations performed")
            .register(meterRegistry);
        this.enhancementsPerformedCounter = Counter.builder("tutorputor.content.enhancements.performed")
            .description("Number of content enhancements performed")
            .register(meterRegistry);
        this.generationTimer = Timer.builder("tutorputor.content.generation.duration")
            .description("Content generation duration")
            .register(meterRegistry);
        this.llmErrorCounter = Counter.builder("tutorputor.content.llm.errors")
            .description("Number of LLM errors during content generation")
            .register(meterRegistry);
    }

    // =========================================================================
    // Generate Claims
    // =========================================================================

    @Override
    public void generateClaims(GenerateClaimsRequest request, StreamObserver<GenerateClaimsResponse> responseObserver) {
        Instant start = Instant.now();
        String requestId = request.getRequestId().isEmpty() ? UUID.randomUUID().toString() : request.getRequestId();
        
        LOG.info("Generating claims for topic='{}', gradeLevel='{}', domain='{}', maxClaims={}, requestId={}",
            request.getTopic(), request.getGradeLevel(), request.getDomain(), request.getMaxClaims(), requestId);
        
        String prompt = buildClaimsPrompt(request);
        
        CompletionRequest completionRequest = CompletionRequest.builder()
            .prompt(prompt)
            .maxTokens(2000)
            .temperature(0.7)
            .build();

        Promise<CompletionResult> promise = llmGateway.complete(completionRequest);
        promise.whenComplete((result, error) -> executor.execute(() -> {
            if (error != null) {
                LOG.error("Error generating claims for requestId={}", requestId, error);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to generate claims: " + error.getMessage())
                    .asRuntimeException());
                return;
            }

            if (result == null || result.getText() == null || result.getText().isBlank()) {
                LOG.error("LLM returned empty response for claims generation, requestId={}", requestId);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("LLM returned empty response")
                    .asRuntimeException());
                return;
            }

            try {
                List<LearningClaim> claims = parseClaimsResponse(result.getText());

                GenerateClaimsResponse grpcResponse = GenerateClaimsResponse.newBuilder()
                    .setRequestId(requestId)
                    .addAllClaims(claims)
                    .setValidation(ValidationResult.newBuilder()
                        .setValid(true)
                        .setConfidenceScore(0.85)
                        .build())
                    .setMetadata(buildMetadata(result, start, 0.7, prompt))
                    .build();

                claimsGeneratedCounter.increment(claims.size());
                generationTimer.record(Duration.between(start, Instant.now()));

                LOG.info("Generated {} claims for requestId={}", claims.size(), requestId);
                responseObserver.onNext(grpcResponse);
                responseObserver.onCompleted();
            } catch (Exception e) {
                LOG.error("Error building claims response for requestId={}", requestId, e);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to generate claims: " + e.getMessage())
                    .asRuntimeException());
            }
        }));
    }

    private String buildClaimsPrompt(GenerateClaimsRequest request) {
        int maxClaims = request.getMaxClaims() > 0 ? request.getMaxClaims() : 5;
        
        return String.format("""
            You are an expert educational content designer creating learning claims.
            
            ## Task
            Generate %d distinct, testable learning claims for the topic: "%s"
            
            ## Requirements
            1. Each claim must be a single, verifiable statement of what a learner will know or be able to do
            2. Claims must progress through Bloom's Taxonomy levels (REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE)
            3. Language must be appropriate for %s students
            4. Claims must be specific to %s domain
            5. Each claim must be independently assessable
            
            ## Output Format (JSON only, no markdown)
            {
              "claims": [
                {
                  "claim_ref": "C1",
                  "text": "The learner can [specific observable action]",
                  "bloom_level": "UNDERSTAND"
                }
              ]
            }
            
            Generate exactly %d claims with diverse Bloom's levels.
            """,
            maxClaims,
            request.getTopic(),
            request.getGradeLevel(),
            request.getDomain(),
            maxClaims
        );
    }

    private List<LearningClaim> parseClaimsResponse(String llmResponse) {
        List<LearningClaim> claims = new ArrayList<>();
        
        try {
            String json = extractJson(llmResponse);
            JsonNode root = MAPPER.readTree(json);
            JsonNode claimsNode = root.get("claims");
            
            if (claimsNode != null && claimsNode.isArray()) {
                int index = 0;
                for (JsonNode claimNode : claimsNode) {
                    LearningClaim.Builder builder = LearningClaim.newBuilder()
                        .setClaimRef(getTextOrDefault(claimNode, "claim_ref", "C" + (index + 1)))
                        .setText(getTextOrDefault(claimNode, "text", ""))
                        .setBloomLevel(getTextOrDefault(claimNode, "bloom_level", "UNDERSTAND"))
                        .setOrderIndex(index);
                    
                    claims.add(builder.build());
                    index++;
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to parse claims from LLM response", e);
        }
        
        return claims;
    }

    // =========================================================================
    // Analyze Content Needs
    // =========================================================================

    @Override
    public void analyzeContentNeeds(AnalyzeContentNeedsRequest request, StreamObserver<AnalyzeContentNeedsResponse> responseObserver) {
        Instant start = Instant.now();
        String requestId = request.getRequestId().isEmpty() ? UUID.randomUUID().toString() : request.getRequestId();
        
        LOG.info("Analyzing content needs for claim, bloomLevel='{}', domain='{}', requestId={}",
            request.getBloomLevel(), request.getDomain(), requestId);
        
        String prompt = buildContentNeedsPrompt(request);
        
        CompletionRequest completionRequest = CompletionRequest.builder()
            .prompt(prompt)
            .maxTokens(1500)
            .temperature(0.6)
            .build();

        Promise<CompletionResult> promise = llmGateway.complete(completionRequest);
        promise.whenComplete((result, error) -> executor.execute(() -> {
            if (error != null) {
                LOG.error("Error analyzing content needs for requestId={}", requestId, error);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to analyze content needs: " + error.getMessage())
                    .asRuntimeException());
                return;
            }

            if (result == null || result.getText() == null || result.getText().isBlank()) {
                LOG.error("LLM returned empty response for content needs analysis, requestId={}", requestId);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("LLM returned empty response")
                    .asRuntimeException());
                return;
            }

            try {
                ContentNeeds needs = parseContentNeedsResponse(result.getText());

                AnalyzeContentNeedsResponse grpcResponse = AnalyzeContentNeedsResponse.newBuilder()
                    .setRequestId(requestId)
                    .setContentNeeds(needs)
                    .setMetadata(buildMetadata(result, start, 0.6, prompt))
                    .build();

                generationTimer.record(Duration.between(start, Instant.now()));

                LOG.info("Analyzed content needs for requestId={}", requestId);
                responseObserver.onNext(grpcResponse);
                responseObserver.onCompleted();
            } catch (Exception e) {
                LOG.error("Error building content needs response for requestId={}", requestId, e);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to analyze content needs: " + e.getMessage())
                    .asRuntimeException());
            }
        }));
    }

    private String buildContentNeedsPrompt(AnalyzeContentNeedsRequest request) {
        return String.format("""
            You are an instructional design expert analyzing what types of supporting content a learning claim requires.
            
            ## Learning Claim
            "%s"
            
            ## Claim Characteristics
            - Bloom's Level: %s
            - Grade Level: %s
            - Domain: %s
            
            ## Task
            Analyze what types of supporting content would best help learners master this claim.
            
            ## Output Format (JSON only, no markdown)
            {
              "examples": {
                "required": true,
                "types": ["REAL_WORLD", "PROBLEM_SOLVING"],
                "count": 2,
                "necessity": 0.9,
                "rationale": "Why examples are needed"
              },
              "simulation": {
                "required": true,
                "interaction_type": "PARAMETER_EXPLORATION",
                "complexity": "MEDIUM",
                "necessity": 0.8,
                "rationale": "Why simulation is needed"
              },
              "animation": {
                "required": false,
                "animation_type": "TWO_D",
                "duration_seconds": 30,
                "necessity": 0.3,
                "rationale": "Why animation is/isn't needed"
              }
            }
            """,
            request.getClaimText(),
            request.getBloomLevel(),
            request.getGradeLevel(),
            request.getDomain()
        );
    }

    private ContentNeeds parseContentNeedsResponse(String llmResponse) {
        ContentNeeds.Builder builder = ContentNeeds.newBuilder();
        
        try {
            String json = extractJson(llmResponse);
            JsonNode root = MAPPER.readTree(json);
            
            // Parse examples needs
            JsonNode examplesNode = root.get("examples");
            if (examplesNode != null) {
                ExampleNeeds.Builder exampleBuilder = ExampleNeeds.newBuilder()
                    .setRequired(getBoolOrDefault(examplesNode, "required", false))
                    .setCount(getIntOrDefault(examplesNode, "count", 2))
                    .setNecessity(getDoubleOrDefault(examplesNode, "necessity", 0.8))
                    .setRationale(getTextOrDefault(examplesNode, "rationale", ""));
                
                JsonNode typesNode = examplesNode.get("types");
                if (typesNode != null && typesNode.isArray()) {
                    for (JsonNode typeNode : typesNode) {
                        exampleBuilder.addTypes(typeNode.asText());
                    }
                }
                
                builder.setExamples(exampleBuilder.build());
            }
            
            // Parse simulation needs
            JsonNode simNode = root.get("simulation");
            if (simNode != null) {
                builder.setSimulation(SimulationNeeds.newBuilder()
                    .setRequired(getBoolOrDefault(simNode, "required", false))
                    .setInteractionType(getTextOrDefault(simNode, "interaction_type", "PARAMETER_EXPLORATION"))
                    .setComplexity(getTextOrDefault(simNode, "complexity", "MEDIUM"))
                    .setNecessity(getDoubleOrDefault(simNode, "necessity", 0.7))
                    .setRationale(getTextOrDefault(simNode, "rationale", ""))
                    .build());
            }
            
            // Parse animation needs
            JsonNode animNode = root.get("animation");
            if (animNode != null) {
                builder.setAnimation(AnimationNeeds.newBuilder()
                    .setRequired(getBoolOrDefault(animNode, "required", false))
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
    // Generate Examples
    // =========================================================================

    @Override
    public void generateExamples(GenerateExamplesRequest request, StreamObserver<GenerateExamplesResponse> responseObserver) {
        Instant start = Instant.now();
        String requestId = request.getRequestId().isEmpty() ? UUID.randomUUID().toString() : request.getRequestId();
        
        LOG.info("Generating examples for claimRef='{}', types={}, count={}, requestId={}",
            request.getClaimRef(), request.getExampleTypesList(), request.getCount(), requestId);
        
        String prompt = buildExamplesPrompt(request);
        
        CompletionRequest completionRequest = CompletionRequest.builder()
            .prompt(prompt)
            .maxTokens(2500)
            .temperature(0.7)
            .build();

        Promise<CompletionResult> promise = llmGateway.complete(completionRequest);
        promise.whenComplete((result, error) -> executor.execute(() -> {
            if (error != null) {
                LOG.error("Error generating examples for requestId={}", requestId, error);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to generate examples: " + error.getMessage())
                    .asRuntimeException());
                return;
            }

            if (result == null || result.getText() == null || result.getText().isBlank()) {
                LOG.error("LLM returned empty response for examples generation, requestId={}", requestId);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("LLM returned empty response")
                    .asRuntimeException());
                return;
            }

            try {
                List<Example> examples = parseExamplesResponse(result.getText());

                GenerateExamplesResponse grpcResponse = GenerateExamplesResponse.newBuilder()
                    .setRequestId(requestId)
                    .addAllExamples(examples)
                    .setMetadata(buildMetadata(result, start, 0.7, prompt))
                    .build();

                examplesGeneratedCounter.increment(examples.size());
                generationTimer.record(Duration.between(start, Instant.now()));

                LOG.info("Generated {} examples for requestId={}", examples.size(), requestId);
                responseObserver.onNext(grpcResponse);
                responseObserver.onCompleted();
            } catch (Exception e) {
                LOG.error("Error building examples response for requestId={}", requestId, e);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to generate examples: " + e.getMessage())
                    .asRuntimeException());
            }
        }));
    }

    private String buildExamplesPrompt(GenerateExamplesRequest request) {
        int count = request.getCount() > 0 ? request.getCount() : 2;
        String types = request.getExampleTypesCount() > 0 
            ? String.join(", ", request.getExampleTypesList())
            : "REAL_WORLD, PROBLEM_SOLVING";
        
        return String.format("""
            You are an expert educational content creator generating examples for a learning claim.
            
            ## Learning Claim
            Reference: %s
            Claim: "%s"
            
            ## Target Audience
            - Grade Level: %s
            - Domain: %s
            
            ## Requirements
            Generate %d examples of the following types: %s
            
            Each example must:
            1. Directly support understanding the claim
            2. Be age-appropriate and culturally sensitive
            3. Include clear learning points
            4. Connect to real-world applications
            
            ## Output Format (JSON only, no markdown)
            {
              "examples": [
                {
                  "example_id": "EX1",
                  "type": "REAL_WORLD",
                  "title": "Descriptive title",
                  "description": "Brief overview",
                  "content": "The main example content with full explanation",
                  "tags": ["physics", "motion"],
                  "relevance_score": 0.9
                }
              ]
            }
            """,
            request.getClaimRef(),
            request.getClaimText(),
            request.getGradeLevel(),
            request.getDomain(),
            count,
            types
        );
    }

    private List<Example> parseExamplesResponse(String llmResponse) {
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
                        .setContent(getTextOrDefault(exNode, "content", ""))
                        .setRelevanceScore(getDoubleOrDefault(exNode, "relevance_score", 0.8));
                    
                    JsonNode tagsNode = exNode.get("tags");
                    if (tagsNode != null && tagsNode.isArray()) {
                        for (JsonNode tag : tagsNode) {
                            builder.addTags(tag.asText());
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
    // Generate Simulation
    // =========================================================================

    @Override
    public void generateSimulation(GenerateSimulationRequest request, StreamObserver<GenerateSimulationResponse> responseObserver) {
        Instant start = Instant.now();
        String requestId = request.getRequestId().isEmpty() ? UUID.randomUUID().toString() : request.getRequestId();
        
        LOG.info("Generating simulation for claimRef='{}', interactionType='{}', complexity='{}', requestId={}",
            request.getClaimRef(), request.getInteractionType(), request.getComplexity(), requestId);
        
        String prompt = buildSimulationPrompt(request);
        
        CompletionRequest completionRequest = CompletionRequest.builder()
            .prompt(prompt)
            .maxTokens(3000)
            .temperature(0.6)
            .build();

        Promise<CompletionResult> promise = llmGateway.complete(completionRequest);
        promise.whenComplete((result, error) -> executor.execute(() -> {
            if (error != null) {
                LOG.error("Error generating simulation for requestId={}", requestId, error);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to generate simulation: " + error.getMessage())
                    .asRuntimeException());
                return;
            }

            if (result == null || result.getText() == null || result.getText().isBlank()) {
                LOG.error("LLM returned empty response for simulation generation, requestId={}", requestId);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("LLM returned empty response")
                    .asRuntimeException());
                return;
            }

            try {
                SimulationManifest manifest = parseSimulationResponse(result.getText(), request);

                GenerateSimulationResponse grpcResponse = GenerateSimulationResponse.newBuilder()
                    .setRequestId(requestId)
                    .setManifest(manifest)
                    .setMetadata(buildMetadata(result, start, 0.6, prompt))
                    .build();

                simulationsGeneratedCounter.increment();
                generationTimer.record(Duration.between(start, Instant.now()));

                LOG.info("Generated simulation for requestId={}", requestId);
                responseObserver.onNext(grpcResponse);
                responseObserver.onCompleted();
            } catch (Exception e) {
                LOG.error("Error building simulation response for requestId={}", requestId, e);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to generate simulation: " + e.getMessage())
                    .asRuntimeException());
            }
        }));
    }

    private String buildSimulationPrompt(GenerateSimulationRequest request) {
        return String.format("""
            You are an expert simulation designer creating an interactive physics/science simulation.
            
            ## Learning Claim
            Reference: %s
            Claim: "%s"
            
            ## Simulation Requirements
            - Grade Level: %s
            - Domain: %s
            - Interaction Type: %s
            - Complexity: %s
            
            ## Available Entity Types
            BALL, BOX, PLATFORM, RAMP, SPRING, PENDULUM, PULLEY, WALL, LEVER, WHEEL
            
            ## Task
            Design a simulation manifest that helps learners explore and understand the claim.
            
            ## Output Format (JSON only, no markdown)
            {
              "manifest": {
                "title": "Simulation title",
                "description": "What the simulation demonstrates",
                "domain": "%s",
                "entities": [
                  {
                    "id": "ball1",
                    "label": "Ball",
                    "entity_type": "BALL",
                    "visual": "{\\"color\\": \\"#3B82F6\\", \\"radius\\": 20}",
                    "position": {"x": 100, "y": 50, "z": 0}
                  }
                ],
                "steps": [
                  {
                    "id": "step1",
                    "description": "Initial observation",
                    "duration": 3000,
                    "actions": ["observe initial state"]
                  }
                ],
                "keyframes": [
                  {
                    "id": "kf1",
                    "time_ms": 0,
                    "state": "{\\"phase\\": \\"initial\\"}"
                  }
                ],
                "domain_config": "{\\"gravity\\": 9.8, \\"friction\\": 0.1}"
              }
            }
            """,
            request.getClaimRef(),
            request.getClaimText(),
            request.getGradeLevel(),
            request.getDomain(),
            request.getInteractionType().isEmpty() ? "PARAMETER_EXPLORATION" : request.getInteractionType(),
            request.getComplexity().isEmpty() ? "MEDIUM" : request.getComplexity(),
            request.getDomain()
        );
    }

    private SimulationManifest parseSimulationResponse(String llmResponse, GenerateSimulationRequest request) {
        SimulationManifest.Builder builder = SimulationManifest.newBuilder()
            .setManifestId(UUID.randomUUID().toString())
            .setVersion("1.0")
            .setDomain(request.getDomain());
        
        try {
            String json = extractJson(llmResponse);
            JsonNode root = MAPPER.readTree(json);
            JsonNode manifestNode = root.get("manifest");
            
            if (manifestNode != null) {
                builder.setTitle(getTextOrDefault(manifestNode, "title", "Simulation"))
                       .setDescription(getTextOrDefault(manifestNode, "description", ""))
                       .setDomainConfig(getTextOrDefault(manifestNode, "domain_config", "{}"));
                
                // Parse entities
                JsonNode entitiesNode = manifestNode.get("entities");
                if (entitiesNode != null && entitiesNode.isArray()) {
                    for (JsonNode entityNode : entitiesNode) {
                        Entity.Builder entityBuilder = Entity.newBuilder()
                            .setId(getTextOrDefault(entityNode, "id", "entity"))
                            .setLabel(getTextOrDefault(entityNode, "label", "Entity"))
                            .setEntityType(getTextOrDefault(entityNode, "entity_type", "BALL"))
                            .setVisual(getTextOrDefault(entityNode, "visual", "{}"));
                        
                        JsonNode posNode = entityNode.get("position");
                        if (posNode != null) {
                            entityBuilder.setPosition(Position.newBuilder()
                                .setX(getDoubleOrDefault(posNode, "x", 0))
                                .setY(getDoubleOrDefault(posNode, "y", 0))
                                .setZ(getDoubleOrDefault(posNode, "z", 0))
                                .build());
                        }
                        
                        builder.addEntities(entityBuilder.build());
                    }
                }
                
                // Parse steps
                JsonNode stepsNode = manifestNode.get("steps");
                if (stepsNode != null && stepsNode.isArray()) {
                    for (JsonNode stepNode : stepsNode) {
                        Step.Builder stepBuilder = Step.newBuilder()
                            .setId(getTextOrDefault(stepNode, "id", "step"))
                            .setDescription(getTextOrDefault(stepNode, "description", ""))
                            .setDuration(getIntOrDefault(stepNode, "duration", 1000));
                        
                        JsonNode actionsNode = stepNode.get("actions");
                        if (actionsNode != null && actionsNode.isArray()) {
                            for (JsonNode action : actionsNode) {
                                stepBuilder.addActions(action.asText());
                            }
                        }
                        
                        builder.addSteps(stepBuilder.build());
                    }
                }
                
                // Parse keyframes
                JsonNode keyframesNode = manifestNode.get("keyframes");
                if (keyframesNode != null && keyframesNode.isArray()) {
                    for (JsonNode kfNode : keyframesNode) {
                        builder.addKeyframes(Keyframe.newBuilder()
                            .setId(getTextOrDefault(kfNode, "id", "kf"))
                            .setTimeMs(getIntOrDefault(kfNode, "time_ms", 0))
                            .setState(getTextOrDefault(kfNode, "state", "{}"))
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
    // Validate Content
    // =========================================================================

    @Override
    public void validateContent(ValidateContentRequest request, StreamObserver<ValidateContentResponse> responseObserver) {
        Instant start = Instant.now();
        String requestId = request.getRequestId().isEmpty() ? UUID.randomUUID().toString() : request.getRequestId();
        
        LOG.info("Validating content for experienceId='{}', domain='{}', requestId={}",
            request.getExperienceId(), request.getDomain(), requestId);
        
        String prompt = buildValidationPrompt(request);
        
        CompletionRequest completionRequest = CompletionRequest.builder()
            .prompt(prompt)
            .maxTokens(2000)
            .temperature(0.3) // Lower temperature for more consistent validation
            .build();

        Promise<CompletionResult> promise = llmGateway.complete(completionRequest);
        promise.whenComplete((result, error) -> executor.execute(() -> {
            if (error != null) {
                LOG.error("Error validating content for requestId={}", requestId, error);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to validate content: " + error.getMessage())
                    .asRuntimeException());
                return;
            }

            if (result == null || result.getText() == null || result.getText().isBlank()) {
                LOG.error("LLM returned empty response for content validation, requestId={}", requestId);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("LLM returned empty response")
                    .asRuntimeException());
                return;
            }

            try {
                GenerationMetadata metadata = buildMetadata(result, start, 0.3, prompt);
                ValidateContentResponse grpcResponse = parseValidationResponse(result.getText(), requestId, metadata);

                validationsPerformedCounter.increment();
                generationTimer.record(Duration.between(start, Instant.now()));

                LOG.info("Validated content for requestId={}, status={}", requestId, grpcResponse.getStatus());
                responseObserver.onNext(grpcResponse);
                responseObserver.onCompleted();
            } catch (Exception e) {
                LOG.error("Error building validation response for requestId={}", requestId, e);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to validate content: " + e.getMessage())
                    .asRuntimeException());
            }
        }));
    }

    private String buildValidationPrompt(ValidateContentRequest request) {
        String claims = String.join("\n- ", request.getClaimTextsList());
        
        return String.format("""
            You are an educational content quality assessor validating learning content.
            
            ## Content to Validate
            - Title: %s
            - Description: %s
            - Domain: %s
            - Claims:
            - %s
            
            ## Validation Dimensions
            1. **Educational** (0-100): Learning objectives clarity, Bloom's alignment, scaffolding
            2. **Experiential** (0-100): Engagement, interactivity, real-world relevance
            3. **Safety** (0-100): Age-appropriateness, no harmful content, accessibility
            4. **Technical** (0-100): Simulation accuracy, rendering feasibility
            5. **Accessibility** (0-100): Multiple modalities, clear language, inclusive design
            
            ## Task
            Evaluate the content across all dimensions and identify any issues.
            
            ## Output Format (JSON only, no markdown)
            {
              "status": "valid",
              "overall_score": 85,
              "can_publish": true,
              "dimension_scores": {
                "educational": 90,
                "experiential": 85,
                "safety": 95,
                "technical": 80,
                "accessibility": 75
              },
              "issues": [
                {
                  "issue_id": "I1",
                  "dimension": "accessibility",
                  "severity": "warning",
                  "message": "Consider adding alt text for visual elements",
                  "suggestion": "Add descriptive alt text to all images and diagrams"
                }
              ]
            }
            
            Status should be: "valid" (>80), "warning" (60-80), or "invalid" (<60)
            """,
            request.getTitle(),
            request.getDescription(),
            request.getDomain(),
            claims
        );
    }

    private ValidateContentResponse parseValidationResponse(String llmResponse, String requestId,
                                                            GenerationMetadata metadata) {
        ValidateContentResponse.Builder builder = ValidateContentResponse.newBuilder()
            .setRequestId(requestId);
        
        try {
            String json = extractJson(llmResponse);
            JsonNode root = MAPPER.readTree(json);
            
            builder.setStatus(getTextOrDefault(root, "status", "valid"))
                   .setOverallScore(getIntOrDefault(root, "overall_score", 80))
                   .setCanPublish(getBoolOrDefault(root, "can_publish", true));
            
            // Parse dimension scores
            JsonNode scoresNode = root.get("dimension_scores");
            if (scoresNode != null) {
                Iterator<Map.Entry<String, JsonNode>> fields = scoresNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    builder.putDimensionScores(field.getKey(), field.getValue().asInt());
                }
            }
            
            // Parse issues
            JsonNode issuesNode = root.get("issues");
            if (issuesNode != null && issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    builder.addIssues(ValidationIssue.newBuilder()
                        .setIssueId(getTextOrDefault(issueNode, "issue_id", "I" + (builder.getIssuesCount() + 1)))
                        .setDimension(getTextOrDefault(issueNode, "dimension", "general"))
                        .setSeverity(getTextOrDefault(issueNode, "severity", "info"))
                        .setMessage(getTextOrDefault(issueNode, "message", ""))
                        .setSuggestion(getTextOrDefault(issueNode, "suggestion", ""))
                        .build());
                }
            }
            
            builder.setIssueCount(builder.getIssuesCount());
            
        } catch (Exception e) {
            LOG.error("Failed to parse validation response from LLM", e);
            builder.setStatus("error")
                   .setOverallScore(0)
                   .setCanPublish(false);
        }
        
        builder.setMetadata(metadata);
        return builder.build();
    }

    // =========================================================================
    // Enhance Content
    // =========================================================================

    @Override
    public void enhanceContent(EnhanceContentRequest request, StreamObserver<EnhanceContentResponse> responseObserver) {
        Instant start = Instant.now();
        String requestId = request.getRequestId().isEmpty() ? UUID.randomUUID().toString() : request.getRequestId();
        
        LOG.info("Enhancing content for experienceId='{}', types={}, requestId={}",
            request.getExperienceId(), request.getEnhancementTypesList(), requestId);
        
        String prompt = buildEnhancementPrompt(request);
        
        CompletionRequest completionRequest = CompletionRequest.builder()
            .prompt(prompt)
            .maxTokens(2500)
            .temperature(0.7)
            .build();

        Promise<CompletionResult> promise = llmGateway.complete(completionRequest);
        promise.whenComplete((result, error) -> executor.execute(() -> {
            if (error != null) {
                LOG.error("Error enhancing content for requestId={}", requestId, error);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to enhance content: " + error.getMessage())
                    .asRuntimeException());
                return;
            }

            if (result == null || result.getText() == null || result.getText().isBlank()) {
                LOG.error("LLM returned empty response for content enhancement, requestId={}", requestId);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("LLM returned empty response")
                    .asRuntimeException());
                return;
            }

            try {
                GenerationMetadata metadata = buildMetadata(result, start, 0.7, prompt);
                EnhanceContentResponse grpcResponse = parseEnhancementResponse(result.getText(), requestId, metadata);

                enhancementsPerformedCounter.increment();
                generationTimer.record(Duration.between(start, Instant.now()));

                LOG.info("Enhanced content with {} suggestions for requestId={}",
                    grpcResponse.getEnhancementCount(), requestId);
                responseObserver.onNext(grpcResponse);
                responseObserver.onCompleted();
            } catch (Exception e) {
                LOG.error("Error building enhancement response for requestId={}", requestId, e);
                llmErrorCounter.increment();
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to enhance content: " + e.getMessage())
                    .asRuntimeException());
            }
        }));
    }

    private String buildEnhancementPrompt(EnhanceContentRequest request) {
        String claims = String.join("\n- ", request.getClaimTextsList());
        String types = request.getEnhancementTypesCount() > 0 
            ? String.join(", ", request.getEnhancementTypesList())
            : "engagement, real_world, assessment, scaffolding";
        
        return String.format("""
            You are an educational content enhancement specialist.
            
            ## Content to Enhance
            - Title: %s
            - Description: %s
            - Domain: %s
            - Grade Level: %s
            - Claims:
            - %s
            
            ## Enhancement Types Requested: %s
            
            ## Enhancement Categories
            - **engagement**: Gamification, interactive elements, curiosity hooks
            - **real_world**: Real-world applications, current events, career connections
            - **assessment**: Formative assessments, self-check questions, reflection prompts
            - **scaffolding**: Prerequisites, hints, alternative explanations, worked examples
            
            ## Task
            Generate specific, actionable enhancement suggestions.
            
            ## Output Format (JSON only, no markdown)
            {
              "enhancements": [
                {
                  "enhancement_id": "E1",
                  "type": "engagement",
                  "title": "Add Progress Badges",
                  "description": "Introduce achievement badges for completing sections",
                  "rationale": "Gamification increases motivation and completion rates",
                  "confidence": 0.9,
                  "implementation": "{\\"badge_types\\": [\\"explorer\\", \\"master\\"], \\"triggers\\": [\\"section_complete\\", \\"quiz_perfect\\"]}"
                }
              ],
              "overall_confidence": 0.85
            }
            """,
            request.getTitle(),
            request.getDescription(),
            request.getDomain(),
            request.getGradeLevel(),
            claims,
            types
        );
    }

    private EnhanceContentResponse parseEnhancementResponse(String llmResponse, String requestId,
                                                            GenerationMetadata metadata) {
        EnhanceContentResponse.Builder builder = EnhanceContentResponse.newBuilder()
            .setRequestId(requestId);
        
        try {
            String json = extractJson(llmResponse);
            JsonNode root = MAPPER.readTree(json);
            
            builder.setOverallConfidence(getDoubleOrDefault(root, "overall_confidence", 0.8));
            
            JsonNode enhancementsNode = root.get("enhancements");
            if (enhancementsNode != null && enhancementsNode.isArray()) {
                for (JsonNode enhNode : enhancementsNode) {
                    builder.addEnhancements(Enhancement.newBuilder()
                        .setEnhancementId(getTextOrDefault(enhNode, "enhancement_id", "E" + (builder.getEnhancementsCount() + 1)))
                        .setType(getTextOrDefault(enhNode, "type", "general"))
                        .setTitle(getTextOrDefault(enhNode, "title", "Enhancement"))
                        .setDescription(getTextOrDefault(enhNode, "description", ""))
                        .setRationale(getTextOrDefault(enhNode, "rationale", ""))
                        .setConfidence(getDoubleOrDefault(enhNode, "confidence", 0.8))
                        .setImplementation(getTextOrDefault(enhNode, "implementation", "{}"))
                        .build());
                }
            }
            
            builder.setEnhancementCount(builder.getEnhancementsCount());
            
        } catch (Exception e) {
            LOG.error("Failed to parse enhancement response from LLM", e);
            builder.setOverallConfidence(0.0)
                   .setEnhancementCount(0);
        }
        
        builder.setMetadata(metadata);
        return builder.build();
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private GenerationMetadata buildMetadata(
            CompletionResult response,
            Instant start,
            double temperature,
            String prompt
    ) {
        String promptHash = prompt != null ? Integer.toHexString(prompt.hashCode()) : "";

        return GenerationMetadata.newBuilder()
            .setModelName(response != null && response.getModelUsed() != null ? response.getModelUsed() : "unknown")
            .setTokensUsed(response != null ? response.getTokensUsed() : 0)
            .setGenerationTimeMs(Duration.between(start, Instant.now()).toMillis())
            .setTemperature(temperature)
            .setPromptHash(promptHash)
            .setTimestamp(Instant.now().toString())
            .build();
    }

    private String extractJson(String response) {
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

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }

    private int getIntOrDefault(JsonNode node, String field, int defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asInt();
        }
        return defaultValue;
    }

    private double getDoubleOrDefault(JsonNode node, String field, double defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asDouble();
        }
        return defaultValue;
    }

    private boolean getBoolOrDefault(JsonNode node, String field, boolean defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asBoolean();
        }
        return defaultValue;
    }
}
