package com.ghatana.products.yappc.domain.agent;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prediction Agent for timeline forecasting and risk assessment.
 * <p>
 * Uses ensemble ML models to predict phase timelines, completion dates,
 * and risk scores for items in the DevSecOps platform.
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 *   <li>Timeline forecasting for phases and milestones</li>
 *   <li>Risk scoring and factor analysis</li>
 *   <li>Completion date prediction with confidence intervals</li>
 *   <li>Recommendation generation for risk mitigation</li>
 * </ul>
 * <p>
 * <b>Models:</b> XGBoost, Prophet, LSTM (ensemble)
 * <p>
 * <b>Latency SLA:</b> 500ms
 *
 * @doc.type class
 * @doc.purpose Timeline and risk prediction agent
 * @doc.layer product
 * @doc.pattern AIAgent
 */
public class PredictionAgent extends AbstractAIAgent<PredictionInput, PredictionOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(PredictionAgent.class);

    private static final String VERSION = "2.0.0";
    private static final String DESCRIPTION = "Timeline forecasting and risk assessment";
    private static final List<String> CAPABILITIES = List.of(
            "timeline-prediction",
            "risk-assessment",
            "phase-forecasting",
            "completion-estimation",
            "recommendation-generation"
    );
    private static final List<String> SUPPORTED_MODELS = List.of(
            "xgboost",
            "prophet",
            "lstm"
    );

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MLService mlService;
    private final FeatureExtractor featureExtractor;
    private final ModelRegistry modelRegistry;

    /**
     * Creates a new PredictionAgent.
     *
     * @param mlService        The ML service for predictions
     * @param featureExtractor The feature extractor
     * @param modelRegistry    The model registry
     * @param metricsCollector The metrics collector
     */
    public PredictionAgent(
            @NotNull MLService mlService,
            @NotNull FeatureExtractor featureExtractor,
            @NotNull ModelRegistry modelRegistry,
            @NotNull MetricsCollector metricsCollector
    ) {
        super(
                AgentName.PREDICTION_AGENT,
                VERSION,
                DESCRIPTION,
                CAPABILITIES,
                SUPPORTED_MODELS,
                metricsCollector
        );
        this.mlService = mlService;
        this.featureExtractor = featureExtractor;
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void validateInput(@NotNull PredictionInput input) {
        if (input.itemId() == null || input.itemId().isBlank()) {
            throw new IllegalArgumentException("Item ID cannot be empty");
        }
        if (input.horizonDays() <= 0) {
            throw new IllegalArgumentException("Horizon days must be positive");
        }
    }

    @Override
    protected Promise<ProcessResult<PredictionOutput>> processRequest(
            @NotNull PredictionInput input,
            @NotNull AIAgentContext context
    ) {
        LOG.debug("Generating predictions for item: {}", input.itemId());

        // Extract features from historical data
        return featureExtractor.extract(input)
                .then(features -> {
                    // Get the prediction model
                    return modelRegistry.getModel("phase-prediction-v3")
                            .then(model -> {
                                // Run ensemble predictions
                                return mlService.predictEnsemble(model, features, List.of("xgboost", "prophet", "lstm"))
                                        .map(predictions -> buildOutput(predictions, input, features));
                            });
                })
                .map(output -> ProcessResult.of(
                        output,
                        output.confidenceInterval().confidence()
                ));
    }

    private PredictionOutput buildOutput(
            EnsemblePredictions predictions,
            PredictionInput input,
            ExtractedFeatures features
    ) {
        // Calculate risk score
        PredictionOutput.RiskScore riskScore = calculateRiskScore(predictions, features);

        // Generate phase predictions
        List<PredictionOutput.PhasePrediction> phasePredictions = generatePhasePredictions(
                predictions,
                input.currentPhase()
        );

        // Generate risk factors
        List<PredictionOutput.RiskFactor> riskFactors = identifyRiskFactors(predictions, features);

        // Generate recommendations
        List<PredictionOutput.Recommendation> recommendations = generateRecommendations(
                riskScore,
                riskFactors,
                features
        );

        // Calculate completion date
        LocalDate completionDate = LocalDate.now().plusDays(predictions.estimatedDays());
        String completionDateStr = completionDate.format(DATE_FORMAT);

        return PredictionOutput.builder()
                .phasePredictions(phasePredictions)
                .estimatedCompletionDate(completionDateStr)
                .confidenceInterval(PredictionOutput.ConfidenceInterval.of95(
                        predictions.lowerBound(),
                        predictions.upperBound()
                ))
                .riskScore(riskScore)
                .riskFactors(riskFactors)
                .recommendations(recommendations)
                .similarHistoricalItems(input.similarItems())
                .build();
    }

    private PredictionOutput.RiskScore calculateRiskScore(
            EnsemblePredictions predictions,
            ExtractedFeatures features
    ) {
        // Multi-factor risk calculation
        double velocityRisk = assessVelocityRisk(features.velocity());
        double complexityRisk = assessComplexityRisk(features.complexity());
        double dependencyRisk = assessDependencyRisk(features.dependencyCount());
        double historicalRisk = assessHistoricalRisk(predictions.similarItemsSuccess());

        double overall = (velocityRisk + complexityRisk + dependencyRisk + historicalRisk) / 4.0;

        return new PredictionOutput.RiskScore(
                overall,
                new PredictionOutput.RiskScore.RiskBreakdown(
                        velocityRisk,
                        complexityRisk,
                        dependencyRisk,
                        historicalRisk
                )
        );
    }

    private double assessVelocityRisk(double velocity) {
        // Low velocity = higher risk
        if (velocity < 0.3) return 0.8;
        if (velocity < 0.5) return 0.6;
        if (velocity < 0.7) return 0.4;
        return 0.2;
    }

    private double assessComplexityRisk(double complexity) {
        // High complexity = higher risk
        if (complexity > 0.8) return 0.9;
        if (complexity > 0.6) return 0.7;
        if (complexity > 0.4) return 0.5;
        return 0.3;
    }

    private double assessDependencyRisk(int dependencyCount) {
        // More dependencies = higher risk
        if (dependencyCount > 10) return 0.8;
        if (dependencyCount > 5) return 0.6;
        if (dependencyCount > 2) return 0.4;
        return 0.2;
    }

    private double assessHistoricalRisk(double successRate) {
        // Lower success rate = higher risk
        return 1.0 - successRate;
    }

    private List<PredictionOutput.PhasePrediction> generatePhasePredictions(
            EnsemblePredictions predictions,
            String currentPhase
    ) {
        List<PredictionOutput.PhasePrediction> phasePredictions = new ArrayList<>();
        LocalDate startDate = LocalDate.now();

        String[] phases = {"planning", "design", "development", "testing", "security", "deployment"};
        int daysPerPhase = predictions.estimatedDays() / phases.length;

        boolean foundCurrentPhase = currentPhase == null;

        for (String phase : phases) {
            if (!foundCurrentPhase && phase.equals(currentPhase)) {
                foundCurrentPhase = true;
            }

            if (foundCurrentPhase) {
                LocalDate endDate = startDate.plusDays(daysPerPhase);
                phasePredictions.add(new PredictionOutput.PhasePrediction(
                        phase,
                        capitalizeFirst(phase),
                        startDate.format(DATE_FORMAT),
                        endDate.format(DATE_FORMAT),
                        daysPerPhase,
                        predictions.confidence(),
                        null,
                        null
                ));
                startDate = endDate;
            }
        }

        return phasePredictions;
    }

    private List<PredictionOutput.RiskFactor> identifyRiskFactors(
            EnsemblePredictions predictions,
            ExtractedFeatures features
    ) {
        List<PredictionOutput.RiskFactor> factors = new ArrayList<>();

        if (features.velocity() < 0.5) {
            factors.add(new PredictionOutput.RiskFactor(
                    "rf-velocity",
                    "Low Velocity",
                    "Team velocity is below target, may cause delays",
                    0.7,
                    0.8,
                    "Consider adding resources or reducing scope"
            ));
        }

        if (features.complexity() > 0.7) {
            factors.add(new PredictionOutput.RiskFactor(
                    "rf-complexity",
                    "High Complexity",
                    "Item has high technical complexity",
                    0.6,
                    0.7,
                    "Break down into smaller tasks, add senior review"
            ));
        }

        if (features.dependencyCount() > 5) {
            factors.add(new PredictionOutput.RiskFactor(
                    "rf-dependencies",
                    "Many Dependencies",
                    "Item has " + features.dependencyCount() + " dependencies that may block progress",
                    0.5,
                    0.6,
                    "Prioritize and track dependency resolution"
            ));
        }

        return factors;
    }

    private List<PredictionOutput.Recommendation> generateRecommendations(
            PredictionOutput.RiskScore riskScore,
            List<PredictionOutput.RiskFactor> riskFactors,
            ExtractedFeatures features
    ) {
        List<PredictionOutput.Recommendation> recommendations = new ArrayList<>();

        if (riskScore.overall() > 0.6) {
            recommendations.add(new PredictionOutput.Recommendation(
                    "rec-escalate",
                    "Escalate to Management",
                    "High risk score indicates potential delivery issues. Consider management review.",
                    PredictionOutput.Recommendation.RecommendationType.PROCESS,
                    PredictionOutput.Recommendation.Priority.HIGH,
                    0.3,
                    Map.of("action", "escalate")
            ));
        }

        if (features.velocity() < 0.5) {
            recommendations.add(new PredictionOutput.Recommendation(
                    "rec-resources",
                    "Add Resources",
                    "Consider adding team members to improve velocity",
                    PredictionOutput.Recommendation.RecommendationType.RESOURCE,
                    PredictionOutput.Recommendation.Priority.MEDIUM,
                    0.25,
                    Map.of("suggestedIncrease", 2)
            ));
        }

        if (features.dependencyCount() > 5) {
            recommendations.add(new PredictionOutput.Recommendation(
                    "rec-dependencies",
                    "Prioritize Dependencies",
                    "Create a dependency resolution plan to unblock critical paths",
                    PredictionOutput.Recommendation.RecommendationType.DEPENDENCY,
                    PredictionOutput.Recommendation.Priority.HIGH,
                    0.35,
                    Map.of("dependencies", features.dependencyCount())
            ));
        }

        return recommendations;
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Override
    protected Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck() {
        return mlService.healthCheck()
                .map(healthy -> Map.of(
                        "mlService", healthy ? AgentHealth.DependencyStatus.HEALTHY : AgentHealth.DependencyStatus.UNHEALTHY,
                        "featureExtractor", AgentHealth.DependencyStatus.HEALTHY,
                        "modelRegistry", AgentHealth.DependencyStatus.HEALTHY
                ));
    }

    // Service interfaces

    /**
     * ML Service for running predictions.
     */
    public interface MLService {
        Promise<EnsemblePredictions> predictEnsemble(
                Model model,
                ExtractedFeatures features,
                List<String> ensembleModels
        );

        Promise<Boolean> healthCheck();
    }

    /**
     * Feature extractor for preparing ML input.
     */
    public interface FeatureExtractor {
        Promise<ExtractedFeatures> extract(PredictionInput input);
    }

    /**
     * Model registry for loading trained models.
     */
    public interface ModelRegistry {
        Promise<Model> getModel(String modelName);
    }

    /**
     * A trained ML model.
     */
    public record Model(
            String name,
            String version,
            String type,
            Map<String, Object> parameters
    ) {}

    /**
     * Extracted features for ML prediction.
     */
    public record ExtractedFeatures(
            double velocity,
            double complexity,
            int dependencyCount,
            int dataPoints,
            Map<String, Double> numericalFeatures,
            Map<String, String> categoricalFeatures
    ) {}

    /**
     * Ensemble prediction results.
     */
    public record EnsemblePredictions(
            int estimatedDays,
            double lowerBound,
            double upperBound,
            double confidence,
            double similarItemsSuccess,
            Map<String, Double> modelContributions
    ) {}
}
