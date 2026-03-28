package com.ghatana.finance.ai;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.runtime.BaseAgent;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * GAA-powered portfolio risk assessment agent following the full GAA lifecycle.
 *
 * <p>Extends {@link BaseAgent} from {@code platform/java/agent-framework}. Implements
 * real-time portfolio risk analysis with adaptive learning capabilities.</p>
 *
 * <p>The agent analyzes portfolio updates to assess:
 * <ul>
 *   <li>Market risk (VaR, expected shortfall)</li>
 *   <li>Credit risk (counterparty exposure)</li>
 *   <li>Operational risk (settlement failures)</li>
 *   <li>Liquidity risk (position sizing)</li>
 *   <li>Concentration risk (sector/geography limits)</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose GAA risk assessment agent — portfolio risk analysis with adaptive learning
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class RiskAssessmentAgent extends BaseAgent<PortfolioUpdate, RiskAssessmentResult> {

    private static final String AGENT_ID = "risk-assessment-agent";
    private static final String MODEL_ID = "risk-assessment-v1";

    private final ModelRegistry modelRegistry;
    private final InferenceService inferenceService;
    private final RiskAlertService riskAlertService;

    /**
     * Creates a new risk assessment agent.
     *
     * @param modelRegistry the model registry for risk models
     * @param inferenceService the inference service for predictions
     * @param riskAlertService the risk alert service for notifications
     */
    public RiskAssessmentAgent(
            ModelRegistry modelRegistry,
            InferenceService inferenceService,
            RiskAlertService riskAlertService) {
        this(defaultOutputGenerator(inferenceService), modelRegistry, inferenceService, riskAlertService);
    }

    /**
     * Creates a new risk assessment agent.
     *
     * @param outputGenerator the output generator for reasoning
     * @param modelRegistry the model registry for risk models
     * @param inferenceService the inference service for predictions
     * @param riskAlertService the risk alert service for notifications
     */
    public RiskAssessmentAgent(
            OutputGenerator<PortfolioUpdate, RiskAssessmentResult> outputGenerator,
            ModelRegistry modelRegistry,
            InferenceService inferenceService,
            RiskAlertService riskAlertService) {
        super(AGENT_ID, outputGenerator);
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry cannot be null");
        this.inferenceService = Objects.requireNonNull(inferenceService, "inferenceService cannot be null");
        this.riskAlertService = Objects.requireNonNull(riskAlertService, "riskAlertService cannot be null");
    }

    // ==================== GAA Lifecycle ====================

    /**
     * Phase 1: PERCEIVE - Ingest portfolio update, extract risk features.
     *
     * <p>Processes portfolio updates and extracts risk-relevant features:
     * <ul>
     *   <li>Position changes and concentrations</li>
     *   <li>Market value fluctuations</li>
     *   <li>Correlation with market indices</li>
     *   <li>Margin and collateral utilization</li>
     * </ul></p>
     *
     * @param input the raw portfolio update
     * @param context the agent context
     * @return enriched portfolio update ready for risk analysis
     */
    @Override
    protected PortfolioUpdate perceive(PortfolioUpdate input, AgentContext context) {
        if (!"portfolio.updated".equals(input.getEventType())) {
            return null; // Skip non-portfolio events
        }

        // Extract risk features from portfolio update
        Map<String, Object> riskFeatures = extractRiskFeatures(input);

        return PortfolioUpdate.builder()
            .portfolioId(input.getPortfolioId())
            .accountId(input.getAccountId())
            .timestamp(input.getTimestamp())
            .positions(input.getPositions())
            .marketValues(input.getMarketValues())
            .riskFeatures(riskFeatures)
            .build();
    }

    /**
     * Phase 3: ACT - Publish risk update if threshold exceeded.
     *
     * <p>If any risk metric exceeds the configured threshold, publishes:
     * <ul>
     *   <li>Risk limit breach alert</li>
     *   <li>Updated risk dashboard</li>
     *   <li>Regulatory risk report entry</li>
     * </ul></p>
     *
     * @param output the risk assessment result
     * @param context the agent context
     * @return promise of the final output
     */
    @Override
    protected Promise<RiskAssessmentResult> act(RiskAssessmentResult output, AgentContext context) {
        if (output == null) {
            return Promise.of(RiskAssessmentResult.skip());
        }

        // Publish risk updates regardless of threshold (for dashboard)
        return riskAlertService.publishRiskUpdate(
            RiskUpdate.builder()
                .updateId("RISK-" + output.getPortfolioId() + "-" + Instant.now().toEpochMilli())
                .portfolioId(output.getPortfolioId())
                .var95(output.getVar95())
                .var99(output.getVar99())
                .expectedShortfall(output.getExpectedShortfall())
                .beta(output.getBeta())
                .concentrationRisk(output.getConcentrationRisk())
                .liquidityRisk(output.getLiquidityRisk())
                .creditRisk(output.getCreditRisk())
                .overallRiskScore(output.getOverallRiskScore())
                .timestamp(Instant.now())
                .build()
        ).map(v -> output);
    }

    /**
     * Phase 4: CAPTURE - Record risk assessment episode for learning.
     *
     * <p>Stores the complete risk assessment episode including:
     * <ul>
     *   <li>Portfolio composition at time of assessment</li>
     *   <li>Market conditions</li>
     *   <li>Risk model outputs</li>
     *   <li>Any limit breaches</li>
     * </ul></p>
     *
     * @param input the portfolio update
     * @param output the risk assessment result
     * @param context the agent context
     * @return promise completing when capture is done
     */
    @Override
    protected Promise<Void> capture(PortfolioUpdate input, RiskAssessmentResult output, AgentContext context) {
        Episode episode = Episode.builder()
            .agentId(getAgentId())
            .input(input.getPortfolioId())
            .output(output.toString())
            .timestamp(Instant.now())
            .build();

        return context.getMemoryStore().storeEpisode(episode).toVoid();
    }

    /**
     * Phase 5: REFLECT - Async learning and pattern extraction.
     *
     * <p>Background risk model improvement:
     * <ol>
     *   <li>Analyze recent risk assessments for accuracy</li>
     *   <li>Update risk factor correlations</li>
     *   <li>Recalibrate VaR models if needed</li>
     *   <li>Adjust stress testing scenarios</li>
     * </ol></p>
     *
     * @param input the portfolio update
     * @param output the risk assessment result
     * @param context the agent context
     * @return promise completing when reflection is done
     */
    @Override
    protected Promise<Void> reflect(PortfolioUpdate input, RiskAssessmentResult output, AgentContext context) {
        return context.getMemoryStore().queryEpisodes(MemoryFilter.builder().agentId(getAgentId()).build(), 50)
            .then(episodes -> riskPatternEngine.extractPatterns(episodes))
            .then(patterns -> {
                if (patterns.getConfidence() > 0.7) {
                    return riskPolicyStore.mergePatterns(patterns);
                }
                return Promise.complete();
            })
            .then($ -> {
                // Recalibrate models if accuracy is degrading
                if (shouldRecalibrate(output)) {
                    return modelRegistry.triggerRetraining(MODEL_ID);
                }
                return Promise.complete();
            })
            .toVoid();
    }

    // ==================== Private Methods ====================

    private Map<String, Object> extractRiskFeatures(PortfolioUpdate update) {
        @SuppressWarnings("unchecked")
        Map<String, Object> marketConditions = (Map<String, Object>) getMarketConditions();
        return Map.of(
            "total_value", calculateTotalValue(update),
            "position_count", update.getPositions().size(),
            "largest_position_pct", calculateLargestPosition(update),
            "sector_concentration", calculateSectorConcentration(update),
            "geography_concentration", calculateGeographyConcentration(update),
            "leverage_ratio", calculateLeverage(update),
            "margin_utilization", calculateMarginUtilization(update),
            "market_vix", marketConditions.get("vix"),
            "market_correlation", marketConditions.get("market_correlation")
        );
    }

    private static OutputGenerator<PortfolioUpdate, RiskAssessmentResult> defaultOutputGenerator(
            InferenceService inferenceService) {
        Objects.requireNonNull(inferenceService, "inferenceService cannot be null");
        return (input, context) -> Promise.of(inferenceService.predict(MODEL_ID, input.getRiskFeatures()));
    }

    private double calculateTotalValue(PortfolioUpdate update) {
        return update.getMarketValues().values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
    }

    private double calculateLargestPosition(PortfolioUpdate update) {
        double total = calculateTotalValue(update);
        if (total == 0) return 0;

        double maxPosition = update.getMarketValues().values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0);

        return maxPosition / total;
    }

    private double calculateSectorConcentration(PortfolioUpdate update) {
        // Calculate Herfindahl index for sector concentration
        return 0.15; // Placeholder
    }

    private double calculateGeographyConcentration(PortfolioUpdate update) {
        // Calculate geography concentration risk
        return 0.20; // Placeholder
    }

    private double calculateLeverage(PortfolioUpdate update) {
        // Calculate leverage ratio
        return 1.0; // Placeholder
    }

    private double calculateMarginUtilization(PortfolioUpdate update) {
        // Calculate margin utilization percentage
        return 0.5; // Placeholder
    }

    private Object getMarketConditions() {
        // Get current market volatility, correlations, etc.
        return Map.of("vix", 18.5, "market_correlation", 0.85);
    }

    private boolean shouldRecalibrate(RiskAssessmentResult output) {
        // Determine if risk model recalibration is needed
        return output.getConfidence() < 0.6;
    }

    // ==================== Inner Types ====================

    /**
     * Model registry for risk models.
     */
    public interface ModelRegistry {
        Promise<Void> triggerRetraining(String modelId);
    }

    /**
     * Inference service for risk predictions.
     */
    public interface InferenceService {
        RiskAssessmentResult predict(String modelId, Map<String, Object> features);
    }

    /**
     * Risk alert service for notifications.
     */
    public interface RiskAlertService {
        Promise<Void> publishRiskUpdate(RiskUpdate update);
    }

    /**
     * Risk pattern extraction engine.
     */
    public interface RiskPatternEngine {
        Promise<RiskPatterns> extractPatterns(java.util.List<Episode> episodes);
    }

    /**
     * Risk policy store.
     */
    public interface RiskPolicyStore {
        Promise<Void> mergePatterns(RiskPatterns patterns);
    }

    // Placeholder implementations for reflection
    private final RiskPatternEngine riskPatternEngine = episodes -> Promise.of(new RiskPatterns(0.75));
    private final RiskPolicyStore riskPolicyStore = patterns -> Promise.complete();
}

/**
 * Portfolio update event.
 */
class PortfolioUpdate {
    private final String portfolioId;
    private final String accountId;
    private final Instant timestamp;
    private final Map<String, Double> positions;
    private final Map<String, Double> marketValues;
    private final Map<String, Object> riskFeatures;
    private final String eventType;

    private PortfolioUpdate(Builder builder) {
        this.portfolioId = builder.portfolioId;
        this.accountId = builder.accountId;
        this.timestamp = builder.timestamp;
        this.positions = builder.positions;
        this.marketValues = builder.marketValues;
        this.riskFeatures = builder.riskFeatures;
        this.eventType = builder.eventType;
    }

    public String getPortfolioId() { return portfolioId; }
    public String getAccountId() { return accountId; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Double> getPositions() { return positions; }
    public Map<String, Double> getMarketValues() { return marketValues; }
    public Map<String, Object> getRiskFeatures() { return riskFeatures; }
    public String getEventType() { return eventType; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String portfolioId;
        private String accountId;
        private Instant timestamp;
        private Map<String, Double> positions;
        private Map<String, Double> marketValues;
        private Map<String, Object> riskFeatures;
        private String eventType;

        public Builder portfolioId(String portfolioId) {
            this.portfolioId = portfolioId;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder positions(Map<String, Double> positions) {
            this.positions = positions;
            return this;
        }

        public Builder marketValues(Map<String, Double> marketValues) {
            this.marketValues = marketValues;
            return this;
        }

        public Builder riskFeatures(Map<String, Object> riskFeatures) {
            this.riskFeatures = riskFeatures;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public PortfolioUpdate build() {
            return new PortfolioUpdate(this);
        }
    }
}

/**
 * Risk assessment result.
 */
class RiskAssessmentResult {
    private final String portfolioId;
    private final double var95;
    private final double var99;
    private final double expectedShortfall;
    private final double beta;
    private final double concentrationRisk;
    private final double liquidityRisk;
    private final double creditRisk;
    private final double overallRiskScore;
    private final double confidence;
    private final boolean skipped;

    private RiskAssessmentResult(String portfolioId, double var95, double var99, double expectedShortfall,
                                 double beta, double concentrationRisk, double liquidityRisk, double creditRisk,
                                 double overallRiskScore, double confidence, boolean skipped) {
        this.portfolioId = portfolioId;
        this.var95 = var95;
        this.var99 = var99;
        this.expectedShortfall = expectedShortfall;
        this.beta = beta;
        this.concentrationRisk = concentrationRisk;
        this.liquidityRisk = liquidityRisk;
        this.creditRisk = creditRisk;
        this.overallRiskScore = overallRiskScore;
        this.confidence = confidence;
        this.skipped = skipped;
    }

    public static RiskAssessmentResult skip() {
        return new RiskAssessmentResult(null, 0, 0, 0, 0, 0, 0, 0, 0, 0, true);
    }

    public static RiskAssessmentResult create(String portfolioId, double var95, double var99,
                                              double expectedShortfall, double beta, double concentrationRisk,
                                              double liquidityRisk, double creditRisk, double overallRiskScore,
                                              double confidence) {
        return new RiskAssessmentResult(portfolioId, var95, var99, expectedShortfall, beta,
            concentrationRisk, liquidityRisk, creditRisk, overallRiskScore, confidence, false);
    }

    public String getPortfolioId() { return portfolioId; }
    public double getVar95() { return var95; }
    public double getVar99() { return var99; }
    public double getExpectedShortfall() { return expectedShortfall; }
    public double getBeta() { return beta; }
    public double getConcentrationRisk() { return concentrationRisk; }
    public double getLiquidityRisk() { return liquidityRisk; }
    public double getCreditRisk() { return creditRisk; }
    public double getOverallRiskScore() { return overallRiskScore; }
    public double getConfidence() { return confidence; }
    public boolean isSkipped() { return skipped; }
}

/**
 * Risk update for alerts.
 */
class RiskUpdate {
    private final String updateId;
    private final String portfolioId;
    private final double var95;
    private final double var99;
    private final double expectedShortfall;
    private final double beta;
    private final double concentrationRisk;
    private final double liquidityRisk;
    private final double creditRisk;
    private final double overallRiskScore;
    private final Instant timestamp;

    private RiskUpdate(Builder builder) {
        this.updateId = builder.updateId;
        this.portfolioId = builder.portfolioId;
        this.var95 = builder.var95;
        this.var99 = builder.var99;
        this.expectedShortfall = builder.expectedShortfall;
        this.beta = builder.beta;
        this.concentrationRisk = builder.concentrationRisk;
        this.liquidityRisk = builder.liquidityRisk;
        this.creditRisk = builder.creditRisk;
        this.overallRiskScore = builder.overallRiskScore;
        this.timestamp = builder.timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String updateId;
        private String portfolioId;
        private double var95;
        private double var99;
        private double expectedShortfall;
        private double beta;
        private double concentrationRisk;
        private double liquidityRisk;
        private double creditRisk;
        private double overallRiskScore;
        private Instant timestamp;

        public Builder updateId(String updateId) { this.updateId = updateId; return this; }
        public Builder portfolioId(String portfolioId) { this.portfolioId = portfolioId; return this; }
        public Builder var95(double var95) { this.var95 = var95; return this; }
        public Builder var99(double var99) { this.var99 = var99; return this; }
        public Builder expectedShortfall(double expectedShortfall) { this.expectedShortfall = expectedShortfall; return this; }
        public Builder beta(double beta) { this.beta = beta; return this; }
        public Builder concentrationRisk(double concentrationRisk) { this.concentrationRisk = concentrationRisk; return this; }
        public Builder liquidityRisk(double liquidityRisk) { this.liquidityRisk = liquidityRisk; return this; }
        public Builder creditRisk(double creditRisk) { this.creditRisk = creditRisk; return this; }
        public Builder overallRiskScore(double overallRiskScore) { this.overallRiskScore = overallRiskScore; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public RiskUpdate build() {
            return new RiskUpdate(this);
        }
    }
}

/**
 * Risk learning episode.
 */
class RiskEpisode {
    private final String agentId;
    private final String portfolioId;
    private final Map<String, Object> inputFeatures;
    private final RiskAssessmentResult outputResult;
    private final Object marketConditions;
    private final Instant timestamp;

    private RiskEpisode(Builder builder) {
        this.agentId = builder.agentId;
        this.portfolioId = builder.portfolioId;
        this.inputFeatures = builder.inputFeatures;
        this.outputResult = builder.outputResult;
        this.marketConditions = builder.marketConditions;
        this.timestamp = builder.timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentId;
        private String portfolioId;
        private Map<String, Object> inputFeatures;
        private RiskAssessmentResult outputResult;
        private Object marketConditions;
        private Instant timestamp;

        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder portfolioId(String portfolioId) { this.portfolioId = portfolioId; return this; }
        public Builder inputFeatures(Map<String, Object> inputFeatures) { this.inputFeatures = inputFeatures; return this; }
        public Builder outputResult(RiskAssessmentResult outputResult) { this.outputResult = outputResult; return this; }
        public Builder marketConditions(Object marketConditions) { this.marketConditions = marketConditions; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public RiskEpisode build() {
            return new RiskEpisode(this);
        }
    }
}

/**
 * Risk patterns from learning.
 */
class RiskPatterns {
    private final double confidence;

    public RiskPatterns(double confidence) {
        this.confidence = confidence;
    }

    public double getConfidence() {
        return confidence;
    }
}
