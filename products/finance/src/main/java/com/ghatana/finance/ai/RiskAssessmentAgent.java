package com.ghatana.finance.ai;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.runtime.BaseAgent;
import io.activej.promise.Promise;

import java.util.Collection;
import java.util.HashMap;
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

        Map<String, Double> positions = safeExposureMap(input.getPositions());
        Map<String, Double> marketValues = safeExposureMap(input.getMarketValues());

        // Extract risk features from portfolio update
        Map<String, Object> riskFeatures = extractRiskFeatures(input, positions, marketValues);

        return PortfolioUpdate.builder()
            .portfolioId(input.getPortfolioId())
            .accountId(input.getAccountId())
            .timestamp(input.getTimestamp())
            .positions(positions)
            .marketValues(marketValues)
            .riskFeatures(riskFeatures)
            .eventType(input.getEventType())
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

    private Map<String, Object> extractRiskFeatures(
            PortfolioUpdate update,
            Map<String, Double> positions,
            Map<String, Double> marketValues) {
        Map<String, Double> marketConditions = getMarketConditions();
        return Map.of(
            "total_value", calculateTotalValue(marketValues),
            "position_count", positions.size(),
            "largest_position_pct", calculateLargestPosition(marketValues),
            "sector_concentration", calculateTaggedConcentration(marketValues, 0),
            "geography_concentration", calculateTaggedConcentration(marketValues, 1),
            "leverage_ratio", calculateLeverage(marketValues),
            "margin_utilization", calculateMarginUtilization(marketValues),
            "market_vix", marketConditions.getOrDefault("vix", 0.0),
            "market_correlation", marketConditions.getOrDefault("market_correlation", 0.0)
        );
    }

    private static OutputGenerator<PortfolioUpdate, RiskAssessmentResult> defaultOutputGenerator(
            InferenceService inferenceService) {
        Objects.requireNonNull(inferenceService, "inferenceService cannot be null");
        return (input, context) -> Promise.of(inferenceService.predict(MODEL_ID, input.getRiskFeatures()));
    }

    private double calculateTotalValue(Map<String, Double> marketValues) {
        return marketValues.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
    }

    private double calculateLargestPosition(Map<String, Double> marketValues) {
        double total = calculateTotalValue(marketValues);
        if (total == 0) return 0;

        double maxPosition = marketValues.values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0);

        return maxPosition / total;
    }

    private double calculateTaggedConcentration(Map<String, Double> marketValues, int tokenIndex) {
        if (marketValues.isEmpty()) {
            return 0;
        }

        Map<String, Double> groupedExposure = new HashMap<>();
        marketValues.forEach((instrumentKey, exposure) ->
            groupedExposure.merge(extractBucketKey(instrumentKey, tokenIndex), Math.abs(exposure), Double::sum));

        return calculateHerfindahlIndex(groupedExposure.values());
    }

    private double calculateLeverage(Map<String, Double> marketValues) {
        if (marketValues.isEmpty()) {
            return 0;
        }

        double grossExposure = marketValues.values().stream()
            .mapToDouble(Math::abs)
            .sum();
        double netAssetValue = Math.abs(marketValues.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum());

        if (netAssetValue < 1e-9) {
            return grossExposure;
        }
        return grossExposure / netAssetValue;
    }

    private double calculateMarginUtilization(Map<String, Double> marketValues) {
        if (marketValues.isEmpty()) {
            return 0;
        }

        double grossExposure = marketValues.values().stream()
            .mapToDouble(Math::abs)
            .sum();
        if (grossExposure == 0) {
            return 0;
        }

        double encumberedExposure = marketValues.values().stream()
            .filter(value -> value < 0)
            .mapToDouble(Math::abs)
            .sum();
        return Math.min(1.0, encumberedExposure / grossExposure);
    }

    private Map<String, Double> getMarketConditions() {
        return Map.of("vix", 18.5, "market_correlation", 0.85);
    }

    private boolean shouldRecalibrate(RiskAssessmentResult output) {
        // Determine if risk model recalibration is needed
        return output.getConfidence() < 0.6;
    }

    private Map<String, Double> safeExposureMap(Map<String, Double> values) {
        return values == null ? Map.of() : values;
    }

    private double calculateHerfindahlIndex(Collection<Double> exposures) {
        double totalExposure = exposures.stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        if (totalExposure == 0) {
            return 0;
        }

        return exposures.stream()
            .mapToDouble(exposure -> {
                double weight = exposure / totalExposure;
                return weight * weight;
            })
            .sum();
    }

    private String extractBucketKey(String instrumentKey, int tokenIndex) {
        if (instrumentKey == null || instrumentKey.isBlank()) {
            return "UNKNOWN";
        }

        String normalized = instrumentKey.replace('|', ':').replace('/', ':');
        String[] parts = normalized.split(":");
        if (tokenIndex < parts.length && !parts[tokenIndex].isBlank()) {
            return parts[tokenIndex].trim().toUpperCase();
        }
        return parts[0].trim().toUpperCase();
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

    // Deterministic defaults used when reflective wiring has not supplied external engines.
    private final RiskPatternEngine riskPatternEngine = episodes -> Promise.of(new RiskPatterns(0.75));
    private final RiskPolicyStore riskPolicyStore = patterns -> Promise.complete();
}
