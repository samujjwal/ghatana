package com.ghatana.tutorputor.contentgeneration.generators;

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
 * @doc.type class
 * @doc.purpose Orchestrate the helper generators into a complete package
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

    public ComprehensiveContentGenerator(
            ClaimGenerator claimGenerator,
            EvidenceGenerator evidenceGenerator,
            ExampleGenerator exampleGenerator,
            SimulationGenerator simulationGenerator,
            AnimationGenerator animationGenerator,
            AssessmentGenerator assessmentGenerator,
            Object llmGateway,
            Object embeddingService,
            Object qualityValidator
    ) {
        this.claimGenerator = claimGenerator;
        this.evidenceGenerator = evidenceGenerator;
        this.exampleGenerator = exampleGenerator;
        this.simulationGenerator = simulationGenerator;
        this.animationGenerator = animationGenerator;
        this.assessmentGenerator = assessmentGenerator;
    }

    public Promise<CompleteContentPackage> generateCompleteContent(ContentGenerationRequest request) {
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

                                                            return new CompleteContentPackage(
                                                                    claims,
                                                                    evidence,
                                                                    examples,
                                                                    simulations,
                                                                    animations,
                                                                    assessments,
                                                                    qualityReport,
                                                                    0L
                                                            );
                                                        }))))));
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
