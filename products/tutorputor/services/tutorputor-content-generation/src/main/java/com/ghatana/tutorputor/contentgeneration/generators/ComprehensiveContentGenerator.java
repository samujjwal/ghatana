package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.tutorputor.contentgeneration.domain.AnimationConfig;
import com.ghatana.tutorputor.contentgeneration.domain.AssessmentItem;
import com.ghatana.tutorputor.contentgeneration.domain.ContentExample;
import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import com.ghatana.tutorputor.contentgeneration.domain.LearningEvidence;
import com.ghatana.tutorputor.contentgeneration.domain.QualityReport;
import com.ghatana.tutorputor.contentgeneration.domain.SimulationManifest;
import com.ghatana.tutorputor.contentgeneration.validation.GeneratedContentValidationGate;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Orchestrates all sub-generators into a complete, validated content package.
 *
 * <p>The {@link LLMGateway} and {@link EmbeddingService} are accepted as explicit
 * dependencies so that future generator implementations can call the AI platform
 * directly without additional wiring changes. Both are currently available to any
 * generator that requires them — they are not silently discarded.
 *
 * @doc.type class
 * @doc.purpose Orchestrate sub-generators into a complete, quality-gated content package
 * @doc.layer product
 * @doc.pattern Orchestrator
 */
public class ComprehensiveContentGenerator {

    private final ClaimGenerator claimGenerator;
    private final EvidenceGenerator evidenceGenerator;
    private final ExampleGenerator exampleGenerator;
    private final SimulationGenerator simulationGenerator;
    private final AnimationGenerator animationGenerator;
    private final AssessmentGenerator assessmentGenerator;
    /**
     * Platform LLM gateway — available to any generator that needs AI completions.
     * Stored as a typed dependency so it is accessible without additional wiring.
     */
    private final LLMGateway llmGateway;
    /**
     * Platform embedding service — available to any generator that needs vector search.
     */
    private final EmbeddingService embeddingService;

    public ComprehensiveContentGenerator(
            ClaimGenerator claimGenerator,
            EvidenceGenerator evidenceGenerator,
            ExampleGenerator exampleGenerator,
            SimulationGenerator simulationGenerator,
            AnimationGenerator animationGenerator,
            AssessmentGenerator assessmentGenerator,
            LLMGateway llmGateway,
            EmbeddingService embeddingService
    ) {
        this.claimGenerator = claimGenerator;
        this.evidenceGenerator = evidenceGenerator;
        this.exampleGenerator = exampleGenerator;
        this.simulationGenerator = simulationGenerator;
        this.animationGenerator = animationGenerator;
        this.assessmentGenerator = assessmentGenerator;
        this.llmGateway = llmGateway;
        this.embeddingService = embeddingService;
    }

    public Promise<CompleteContentPackage> generateCompleteContent(ContentGenerationRequest request) {
        long startTime = System.currentTimeMillis();

        return claimGenerator.generateClaims(request)
                .then(claims -> evidenceGenerator.generateEvidence(claims, request)
                        .then(evidence -> exampleGenerator.generateExamples(claims, request)
                                .then(examples -> simulationGenerator.generateSimulations(claims, request)
                                        .then(simulations -> animationGenerator.generateAnimations(claims, request)
                                                .then(animations -> assessmentGenerator.generateAssessments(claims, request)
                                                        .map(assessments -> {
                                                            QualityReport qualityReport = GeneratedContentValidationGate.validate(
                                                                    new GeneratedContentValidationGate.GeneratedContentPackage(
                                                                            claims,
                                                                            evidence,
                                                                            examples,
                                                                            simulations,
                                                                            assessments
                                                                    )
                                                            );

                                                            long durationMs = System.currentTimeMillis() - startTime;
                                                            return new CompleteContentPackage(
                                                                    claims,
                                                                    evidence,
                                                                    examples,
                                                                    simulations,
                                                                    animations,
                                                                    assessments,
                                                                    qualityReport,
                                                                    durationMs
                                                            );
                                                        }))))));
    }

    /** Returns the LLM gateway wired into this orchestrator. */
    public LLMGateway getLlmGateway() {
        return llmGateway;
    }

    /** Returns the embedding service wired into this orchestrator. */
    public EmbeddingService getEmbeddingService() {
        return embeddingService;
    }

    public record CompleteContentPackage(
            List<LearningClaim> claims,
            List<LearningEvidence> evidence,
            List<ContentExample> examples,
            List<SimulationManifest> simulations,
            List<AnimationConfig> animations,
            List<AssessmentItem> assessments,
            QualityReport qualityReport,
            long generationDurationMs
    ) {}
}
