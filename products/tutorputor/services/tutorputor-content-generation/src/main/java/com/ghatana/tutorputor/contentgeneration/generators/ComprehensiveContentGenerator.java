package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.tutorputor.agent.ContentQualityValidator;
import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.tutorputor.explorer.model.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @doc.type class
 * @doc.purpose Comprehensive content generation orchestrator using existing platform services
 * @doc.layer product
 * @doc.pattern Orchestrator, Facade
 */
public class ComprehensiveContentGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(ComprehensiveContentGenerator.class);
    
    private final ClaimGenerator claimGenerator;
    private final EvidenceGenerator evidenceGenerator;
    private final ExampleGenerator exampleGenerator;
    private final SimulationGenerator simulationGenerator;
    private final AnimationGenerator animationGenerator;
    private final AssessmentGenerator assessmentGenerator;
    private final LLMGateway llmGateway;
    private final EmbeddingService embeddingService;
    private final ContentQualityValidator qualityValidator;
    
    public ComprehensiveContentGenerator(
            ClaimGenerator claimGenerator,
            EvidenceGenerator evidenceGenerator,
            ExampleGenerator exampleGenerator,
            SimulationGenerator simulationGenerator,
            AnimationGenerator animationGenerator,
            AssessmentGenerator assessmentGenerator,
            LLMGateway llmGateway,
            EmbeddingService embeddingService,
            ContentQualityValidator qualityValidator) {
        this.claimGenerator = claimGenerator;
        this.evidenceGenerator = evidenceGenerator;
        this.exampleGenerator = exampleGenerator;
        this.simulationGenerator = simulationGenerator;
        this.animationGenerator = animationGenerator;
        this.assessmentGenerator = assessmentGenerator;
        this.llmGateway = llmGateway;
        this.embeddingService = embeddingService;
        this.qualityValidator = qualityValidator;
    }
    
    public Promise<CompleteContentPackage> generateCompleteContent(ContentGenerationRequest request) {
        LOG.info("Generating complete content package for topic: {}", request.getTopic());
        
        return Promise.ofBlocking(() -> {
            long startTime = System.currentTimeMillis();
            
            // 1. Generate claims using LLMGateway
            List<LearningClaim> claims = claimGenerator.generateClaims(request).get();
            LOG.debug("Generated {} claims", claims.size());
            
            // 2. Generate evidence for each claim
            List<LearningEvidence> evidence = evidenceGenerator.generateEvidence(claims, request).get();
            LOG.debug("Generated {} evidence items", evidence.size());
            
            // 3. Generate examples using LLMGateway
            List<ContentExample> examples = exampleGenerator.generateExamples(claims, request).get();
            LOG.debug("Generated {} examples", examples.size());
            
            // 4. Generate simulations
            List<SimulationManifest> simulations = simulationGenerator.generateSimulations(claims, request).get();
            LOG.debug("Generated {} simulations", simulations.size());
            
            // 5. Generate animations using LLMGateway
            List<AnimationConfig> animations = animationGenerator.generateAnimations(claims, request).get();
            LOG.debug("Generated {} animations", animations.size());
            
            // 6. Generate assessments
            List<AssessmentItem> assessments = assessmentGenerator.generateAssessments(claims, request).get();
            LOG.debug("Generated {} assessments", assessments.size());
            
            // 7. Quality validation
            QualityReport qualityReport = validateCompletePackage(
                claims, evidence, examples, simulations, animations, assessments
            ).get();
            
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("Complete content package generated in {}ms with quality score {}", 
                duration, qualityReport.getOverallScore());
            
            return CompleteContentPackage.builder()
                .claims(claims)
                .evidence(evidence)
                .examples(examples)
                .simulations(simulations)
                .animations(animations)
                .assessments(assessments)
                .qualityReport(qualityReport)
                .generationDurationMs(duration)
                .build();
        });
    }
    
    private Promise<QualityReport> validateCompletePackage(
            List<LearningClaim> claims,
            List<LearningEvidence> evidence,
            List<ContentExample> examples,
            List<SimulationManifest> simulations,
            List<AnimationConfig> animations,
            List<AssessmentItem> assessments) {
        
        return Promise.ofBlocking(() -> {
            double totalScore = 0.0;
            int itemCount = 0;
            
            // Validate each component
            for (ContentExample example : examples) {
                ContentQualityValidator.ValidationResult result = 
                    qualityValidator.validate(example, null).get();
                totalScore += result.score();
                itemCount++;
            }
            
            double overallScore = itemCount > 0 ? totalScore / itemCount : 0.0;
            boolean passed = overallScore >= 0.7;
            
            return new QualityReport(passed, overallScore, List.of());
        });
    }
}
