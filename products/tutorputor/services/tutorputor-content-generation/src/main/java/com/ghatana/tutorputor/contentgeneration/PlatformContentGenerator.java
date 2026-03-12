package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.tutorputor.contentgeneration.domain.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Platform content generator implementation using LLMGateway.
 *
 * <p>Implements UnifiedContentGenerator using the platform's production-ready
 * LLMGateway with multi-provider routing, fallback, and comprehensive metrics.
 *
 * @doc.type class
 * @doc.purpose Platform-based content generation implementation
 * @doc.layer infrastructure
 * @doc.pattern Adapter, Gateway
 */
public class PlatformContentGenerator implements UnifiedContentGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformContentGenerator.class);

    private final LLMGateway llmGateway;
    private final ContentValidator validator;
    private final PromptTemplateEngine promptEngine;
    private final MetricsCollector metrics;

    public PlatformContentGenerator(
            LLMGateway llmGateway,
            ContentValidator validator,
            PromptTemplateEngine promptEngine,
            MetricsCollector metrics) {
        this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway required");
        this.validator = Objects.requireNonNull(validator, "validator required");
        this.promptEngine = Objects.requireNonNull(promptEngine, "promptEngine required");
        this.metrics = Objects.requireNonNull(metrics, "metrics required");
    }

    @Override
    public Promise<CompleteContentPackage> generateCompletePackage(ContentGenerationRequest request) {
        LOG.info("Generating complete content package for topic: {} in domain: {}", 
                request.getTopic(), request.getDomain());
        
        long startTime = System.currentTimeMillis();
        
        return generateClaims(new ClaimsRequest(
                request.getTopic(), request.getGradeLevel(), 
                request.getDomain().name(), request.getMaxClaims()))
            .then(claimsResponse -> {
                LOG.debug("Generated {} claims", claimsResponse.getClaims().size());
                
                List<LearningClaim> claims = claimsResponse.getClaims();
                
                // Generate all content types in parallel
                Promise<ExamplesResponse> examplesPromise = claims.isEmpty() 
                    ? Promise.of(new ExamplesResponse(Collections.emptyList(), new ValidationResult(true, 1.0, Collections.emptyList())))
                    : generateExamples(new ExamplesRequest(
                        claims, request.getGradeLevel(), request.getDomain().name(), request.getMaxExamples()));
                
                Promise<SimulationResponse> simulationsPromise = claims.isEmpty()
                    ? Promise.of(new SimulationResponse(Collections.emptyList(), new ValidationResult(true, 1.0, Collections.emptyList())))
                    : generateSimulation(new SimulationRequest(
                        claims, request.getGradeLevel(), request.getDomain().name(), request.getMaxSimulations()));
                
                Promise<AnimationResponse> animationsPromise = claims.isEmpty()
                    ? Promise.of(new AnimationResponse(Collections.emptyList(), new ValidationResult(true, 1.0, Collections.emptyList())))
                    : generateAnimation(new AnimationRequest(
                        claims, request.getGradeLevel(), request.getDomain().name(), request.getMaxAnimations()));
                
                Promise<AssessmentResponse> assessmentsPromise = claims.isEmpty()
                    ? Promise.of(new AssessmentResponse(Collections.emptyList(), new ValidationResult(true, 1.0, Collections.emptyList())))
                    : generateAssessment(new AssessmentRequest(
                        claims, request.getGradeLevel(), request.getDomain().name(), request.getMaxAssessments()));
                
                return Promise.all(examplesPromise, simulationsPromise, animationsPromise, assessmentsPromise)
                    .map(results -> {
                        ExamplesResponse examples = (ExamplesResponse) results[0];
                        SimulationResponse simulations = (SimulationResponse) results[1];
                        AnimationResponse animations = (AnimationResponse) results[2];
                        AssessmentResponse assessments = (AssessmentResponse) results[3];
                        
                        long totalTime = System.currentTimeMillis() - startTime;
                        metrics.recordTimer("content.generation.complete_package", totalTime,
                            "topic", request.getTopic(),
                            "domain", request.getDomain().name());
                        
                        return new CompleteContentPackage(
                            claims,
                            Collections.emptyList(), // evidence
                            examples.getExamples(),
                            simulations.getSimulations(),
                            animations.getAnimations(),
                            assessments.getAssessments(),
                            generateQualityReport(claims, examples, simulations, animations, assessments, totalTime)
                        );
                    });
            });
    }

    @Override
    public Promise<ClaimsResponse> generateClaims(ClaimsRequest request) {
        LOG.debug("Generating claims for topic: {}", request.getTopic());
        long startTime = System.currentTimeMillis();
        
        return Promise.ofBlocking(() -> {
            try {
                String prompt = promptEngine.buildClaimsPrompt(
                    request.getTopic(), request.getGradeLevel(), request.getDomain(), request.getMaxClaims());
                
                CompletionRequest completionRequest = CompletionRequest.builder()
                    .prompt(prompt)
                    .model("gpt-4")
                    .temperature(0.7)
                    .maxTokens(2000)
                    .build();
                
                CompletionResult result = llmGateway.complete(completionRequest).get();
                
                List<LearningClaim> claims = parseClaimsFromText(result.getText(), request);
                
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordTimer("content.generation.claims", duration,
                    "topic", request.getTopic(),
                    "count", String.valueOf(claims.size()));
                
                return new ClaimsResponse(claims, validator.validateClaims(claims));
                
            } catch (Exception e) {
                LOG.error("Failed to generate claims", e);
                metrics.incrementCounter("content.generation.claims.errors");
                throw new RuntimeException("Claims generation failed", e);
            }
        });
    }

    @Override
    public Promise<ExamplesResponse> generateExamples(ExamplesRequest request) {
        LOG.debug("Generating examples for {} claims", request.getClaims().size());
        long startTime = System.currentTimeMillis();
        
        return Promise.ofBlocking(() -> {
            try {
                List<ContentExample> examples = new ArrayList<>();
                
                for (LearningClaim claim : request.getClaims()) {
                    String prompt = promptEngine.buildExamplePrompt(
                        claim.getText(), request.getGradeLevel(), request.getDomain());
                    
                    CompletionRequest completionRequest = CompletionRequest.builder()
                        .prompt(prompt)
                        .model("gpt-4")
                        .temperature(0.8)
                        .maxTokens(1500)
                        .build();
                    
                    CompletionResult result = llmGateway.complete(completionRequest).get();
                    
                    ContentExample example = parseExampleFromText(result.getText(), claim);
                    examples.add(example);
                }
                
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordTimer("content.generation.examples", duration,
                    "count", String.valueOf(examples.size()));
                
                return new ExamplesResponse(examples, validator.validateExamples(examples));
                
            } catch (Exception e) {
                LOG.error("Failed to generate examples", e);
                metrics.incrementCounter("content.generation.examples.errors");
                throw new RuntimeException("Examples generation failed", e);
            }
        });
    }

    @Override
    public Promise<SimulationResponse> generateSimulation(SimulationRequest request) {
        LOG.debug("Generating simulations for {} claims", request.getClaims().size());
        long startTime = System.currentTimeMillis();
        
        return Promise.ofBlocking(() -> {
            try {
                List<SimulationManifest> simulations = new ArrayList<>();
                
                for (LearningClaim claim : request.getClaims()) {
                    String prompt = promptEngine.buildSimulationPrompt(
                        claim.getText(), request.getGradeLevel(), request.getDomain());
                    
                    CompletionRequest completionRequest = CompletionRequest.builder()
                        .prompt(prompt)
                        .model("gpt-4")
                        .temperature(0.6)
                        .maxTokens(1000)
                        .build();
                    
                    CompletionResult result = llmGateway.complete(completionRequest).get();
                    
                    SimulationManifest simulation = parseSimulationFromText(result.getText(), claim);
                    simulations.add(simulation);
                }
                
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordTimer("content.generation.simulations", duration,
                    "count", String.valueOf(simulations.size()));
                
                return new SimulationResponse(simulations, validator.validateSimulations(simulations));
                
            } catch (Exception e) {
                LOG.error("Failed to generate simulations", e);
                metrics.incrementCounter("content.generation.simulations.errors");
                throw new RuntimeException("Simulation generation failed", e);
            }
        });
    }

    @Override
    public Promise<AnimationResponse> generateAnimation(AnimationRequest request) {
        LOG.debug("Generating animations for {} claims", request.getClaims().size());
        long startTime = System.currentTimeMillis();
        
        return Promise.ofBlocking(() -> {
            try {
                List<AnimationConfig> animations = new ArrayList<>();
                
                for (LearningClaim claim : request.getClaims()) {
                    String prompt = promptEngine.buildAnimationPrompt(
                        claim.getText(), request.getGradeLevel(), request.getDomain());
                    
                    CompletionRequest completionRequest = CompletionRequest.builder()
                        .prompt(prompt)
                        .model("gpt-4")
                        .temperature(0.7)
                        .maxTokens(1200)
                        .build();
                    
                    CompletionResult result = llmGateway.complete(completionRequest).get();
                    
                    AnimationConfig animation = parseAnimationFromText(result.getText(), claim);
                    animations.add(animation);
                }
                
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordTimer("content.generation.animations", duration,
                    "count", String.valueOf(animations.size()));
                
                return new AnimationResponse(animations, validator.validateAnimations(animations));
                
            } catch (Exception e) {
                LOG.error("Failed to generate animations", e);
                metrics.incrementCounter("content.generation.animations.errors");
                throw new RuntimeException("Animation generation failed", e);
            }
        });
    }

    @Override
    public Promise<AssessmentResponse> generateAssessment(AssessmentRequest request) {
        LOG.debug("Generating assessments for {} claims", request.getClaims().size());
        long startTime = System.currentTimeMillis();
        
        return Promise.ofBlocking(() -> {
            try {
                List<AssessmentItem> assessments = new ArrayList<>();
                
                for (LearningClaim claim : request.getClaims()) {
                    String prompt = promptEngine.buildAssessmentPrompt(
                        claim.getText(), request.getGradeLevel(), request.getDomain());
                    
                    CompletionRequest completionRequest = CompletionRequest.builder()
                        .prompt(prompt)
                        .model("gpt-4")
                        .temperature(0.5)
                        .maxTokens(800)
                        .build();
                    
                    CompletionResult result = llmGateway.complete(completionRequest).get();
                    
                    AssessmentItem assessment = parseAssessmentFromText(result.getText(), claim);
                    assessments.add(assessment);
                }
                
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordTimer("content.generation.assessments", duration,
                    "count", String.valueOf(assessments.size()));
                
                return new AssessmentResponse(assessments, validator.validateAssessments(assessments));
                
            } catch (Exception e) {
                LOG.error("Failed to generate assessments", e);
                metrics.incrementCounter("content.generation.assessments.errors");
                throw new RuntimeException("Assessment generation failed", e);
            }
        });
    }

    @Override
    public Promise<ValidationResult> validateContent(Object content, ContentType contentType) {
        return Promise.ofBlocking(() -> {
            switch (contentType) {
                case CLAIMS:
                    return validator.validateClaims((List<LearningClaim>) content);
                case EXAMPLES:
                    return validator.validateExamples((List<ContentExample>) content);
                case SIMULATION:
                    return validator.validateSimulations((List<SimulationManifest>) content);
                case ANIMATION:
                    return validator.validateAnimations((List<AnimationConfig>) content);
                case ASSESSMENT:
                    return validator.validateAssessments((List<AssessmentItem>) content);
                default:
                    return new ValidationResult(false, 0.0, List.of("Unknown content type: " + contentType));
            }
        });
    }

    @Override
    public Promise<Void> updateConfiguration(String tenantId, Map<String, Object> configuration) {
        LOG.info("Updating configuration for tenant: {}", tenantId);
        // Configuration would be stored and applied to LLM routing
        return Promise.of(null);
    }

    @Override
    public Promise<Map<String, Object>> getConfiguration(String tenantId) {
        return Promise.of(Map.of(
            "defaultModel", "gpt-4",
            "temperature", 0.7,
            "maxTokens", 2000,
            "availableProviders", llmGateway.getAvailableProviders()
        ));
    }

    @Override
    public Promise<List<String>> getSupportedModels() {
        return Promise.of(llmGateway.getAvailableProviders());
    }

    // Helper methods for parsing LLM responses
    private List<LearningClaim> parseClaimsFromText(String text, ClaimsRequest request) {
        // Simplified parsing - production would use structured JSON parsing
        String claimText = text.trim();
        if (claimText.length() > 100) {
            claimText = claimText.substring(0, 100);
        }
        
        return List.of(
            new LearningClaim(
                UUID.randomUUID().toString(),
                claimText,
                request.getGradeLevel(),
                request.getDomain(),
                Map.of("source", "llm", "timestamp", System.currentTimeMillis())
            )
        );
    }

    private ContentExample parseExampleFromText(String text, LearningClaim claim) {
        return new ContentExample(
            UUID.randomUUID().toString(),
            text.trim(),
            claim.getId(),
            "WORKED_EXAMPLE",
            Map.of("source", "llm", "timestamp", System.currentTimeMillis())
        );
    }

    private SimulationManifest parseSimulationFromText(String text, LearningClaim claim) {
        return new SimulationManifest(
            UUID.randomUUID().toString(),
            claim.getId(),
            text.trim(),
            Map.of("source", "llm", "timestamp", System.currentTimeMillis())
        );
    }

    private AnimationConfig parseAnimationFromText(String text, LearningClaim claim) {
        return new AnimationConfig(
            UUID.randomUUID().toString(),
            claim.getId(),
            text.trim(),
            Collections.emptyList(),
            Map.of("source", "llm", "timestamp", System.currentTimeMillis())
        );
    }

    private AssessmentItem parseAssessmentFromText(String text, LearningClaim claim) {
        return new AssessmentItem(
            UUID.randomUUID().toString(),
            claim.getId(),
            text.trim(),
            "MULTIPLE_CHOICE",
            List.of("A", "B", "C", "D"),
            "A",
            Map.of("source", "llm", "timestamp", System.currentTimeMillis())
        );
    }

    private QualityReport generateQualityReport(
            List<LearningClaim> claims,
            ExamplesResponse examples,
            SimulationResponse simulations,
            AnimationResponse animations,
            AssessmentResponse assessments,
            long totalTimeMs) {
        
        double overallScore = calculateOverallQualityScore(
            examples.getValidation(),
            simulations.getValidation(),
            animations.getValidation(),
            assessments.getValidation()
        );
        
        List<String> issues = new ArrayList<>();
        issues.addAll(examples.getValidation().getIssues());
        issues.addAll(simulations.getValidation().getIssues());
        issues.addAll(animations.getValidation().getIssues());
        issues.addAll(assessments.getValidation().getIssues());
        
        return new QualityReport(
            overallScore,
            issues,
            Map.of(
                "claimsCount", claims.size(),
                "examplesCount", examples.getExamples().size(),
                "simulationsCount", simulations.getSimulations().size(),
                "animationsCount", animations.getAnimations().size(),
                "assessmentsCount", assessments.getAssessments().size(),
                "totalGenerationTimeMs", totalTimeMs,
                "timestamp", System.currentTimeMillis()
            )
        );
    }

    private double calculateOverallQualityScore(ValidationResult... validations) {
        if (validations.length == 0) return 0.0;
        
        double totalScore = 0.0;
        for (ValidationResult validation : validations) {
            totalScore += validation.getConfidence();
        }
        
        return totalScore / validations.length;
    }
}
