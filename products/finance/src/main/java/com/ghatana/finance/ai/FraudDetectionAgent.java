package com.ghatana.finance.ai;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.runtime.BaseAgent;
import io.activej.promise.Promise;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * GAA-powered fraud detection agent following the PERCEIVE → REASON → ACT → CAPTURE → REFLECT lifecycle.
 *
 * <p>Extends {@link BaseAgent} from {@code platform/java/agent-framework}. Runs on ActiveJ Eventloop.
 * Uses {@link Promise} for all async operations — CompletableFuture is BANNED.</p>
 *
 * <p>The agent analyzes trade events in real-time to detect suspicious patterns indicative of:
 * <ul>
 *   <li>Market manipulation (pump and dump, layering, spoofing)</li>
 *   <li>Insider trading (unusual pre-announcement trading)</li>
 *   <li>Account takeover (unusual trading patterns)</li>
 *   <li>Money laundering (structuring, rapid movement)</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose GAA fraud detection agent — real-time trade fraud analysis with learning
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class FraudDetectionAgent extends BaseAgent<TradeEvent, FraudDetectionResult> {

    private static final String AGENT_ID = "fraud-detection-agent";

    private final ModelRegistry modelRegistry;
    private final InferenceService inferenceService;
    private final AlertService alertService;

    /**
     * Creates a new fraud detection agent.
     *
     * @param outputGenerator the output generator for reasoning
     * @param modelRegistry the model registry for fraud detection models
     * @param inferenceService the inference service for predictions
     * @param alertService the alert service for notifications
     */
    public FraudDetectionAgent(
            OutputGenerator<TradeEvent, FraudDetectionResult> outputGenerator,
            ModelRegistry modelRegistry,
            InferenceService inferenceService,
            AlertService alertService) {
        super(AGENT_ID, outputGenerator);
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry cannot be null");
        this.inferenceService = Objects.requireNonNull(inferenceService, "inferenceService cannot be null");
        this.alertService = Objects.requireNonNull(alertService, "alertService cannot be null");
    }

    // ==================== GAA Lifecycle ====================

    /**
     * Phase 1: PERCEIVE - Ingest trade event, extract features.
     *
     * <p>Validates the trade event and enriches it with context:
     * <ul>
     *   <li>Historical trading patterns for the account</li>
     *   <li>Market conditions at trade time</li>
     *   <li>Related entity information</li>
     * </ul></p>
     *
     * @param input the raw trade event
     * @param context the agent context
     * @return enriched trade event ready for analysis
     */
    @Override
    protected TradeEvent perceive(TradeEvent input, AgentContext context) {
        // Skip if not a trade event
        if (!"trade.executed".equals(input.getEventType())) {
            return null; // Will cause the turn to return empty result
        }

        // Enrich with historical context
        Map<String, Object> features = extractFeatures(input);

        return TradeEvent.builder()
            .tradeId(input.getTradeId())
            .accountId(input.getAccountId())
            .symbol(input.getSymbol())
            .quantity(input.getQuantity())
            .price(input.getPrice())
            .timestamp(input.getTimestamp())
            .market(input.getMarket())
            .features(features)
            .build();
    }

    /**
     * Phase 3: ACT - Publish alert if suspicious.
     *
     * <p>If the fraud confidence exceeds the threshold, publishes an alert to:
     * <ul>
     *   <li>Real-time monitoring dashboard</li>
     *   <li>Compliance team notification</li>
     *   <li>Audit trail for regulatory review</li>
     * </ul></p>
     *
     * @param output the reasoning result
     * @param context the agent context
     * @return promise of the final output
     */
    @Override
    protected Promise<FraudDetectionResult> act(FraudDetectionResult output, AgentContext context) {
        if (output == null) {
            return Promise.of(FraudDetectionResult.skip());
        }

        if (output.isSuspicious()) {
            return alertService.publishAlert(
                Alert.builder()
                    .alertId("FRAUD-" + output.getTradeId() + "-" + Instant.now().toEpochMilli())
                    .tradeId(output.getTradeId())
                    .accountId(output.getAccountId())
                    .fraudType(output.getFraudType())
                    .confidence(output.getConfidence())
                    .timestamp(Instant.now())
                    .build()
            ).map(v -> output);
        }

        return Promise.of(output);
    }

    /**
     * Phase 4: CAPTURE - Record episode for learning.
     *
     * <p>Stores the complete fraud detection episode including:
     * <ul>
     *   <li>Trade event details</li>
     *   <li>Extracted features</li>
     *   <li>Fraud assessment result</li>
     *   <li>Alert action taken</li>
     * </ul></p>
     *
     * @param input the trade event
     * @param output the detection result
     * @param context the agent context
     * @return promise completing when capture is done
     */
    @Override
    protected Promise<Void> capture(TradeEvent input, FraudDetectionResult output, AgentContext context) {
        // Store in memory for learning
        Episode episode = Episode.builder()
            .agentId(getAgentId())
            .input(input.getTradeId())
            .output(output.toString())
            .timestamp(Instant.now())
            .build();

        return context.getMemoryStore().storeEpisode(episode).toVoid();
    }

    /**
     * Phase 5: REFLECT - Async, fire-and-forget — extract patterns from recent episodes.
     *
     * <p>Background learning process:
     * <ol>
     *   <li>Retrieve recent episodes from memory</li>
     *   <li>Extract fraud patterns using ML models</li>
     *   <li>Update fraud detection policies</li>
     *   <li>Retrain models if confidence is low</li>
     * </ol></p>
     *
     * @param input the trade event
     * @param output the detection result
     * @param context the agent context
     * @return promise completing when reflection is done
     */
    @Override
    protected Promise<Void> reflect(TradeEvent input, FraudDetectionResult output, AgentContext context) {
        // Fire-and-forget async reflection
        return context.getMemoryStore().queryEpisodes(MemoryFilter.builder().agentId(getAgentId()).build(), 100)
            .then(episodes -> patternEngine.extractPatterns(episodes))
            .then(patterns -> {
                // Only update if we have high confidence patterns
                if (patterns.getConfidence() > 0.7) {
                    return policyStore.mergePatterns(patterns);
                }
                return Promise.complete();
            })
            .then($ -> {
                // Trigger model retraining if needed
                if (shouldRetrain(output)) {
                    return modelRegistry.triggerRetraining("fraud-detector-v2");
                }
                return Promise.complete();
            })
            .toVoid();
    }

    // ==================== Private Methods ====================

    private Map<String, Object> extractFeatures(TradeEvent event) {
        return Map.of(
            "price_deviation", calculatePriceDeviation(event),
            "volume_anomaly", calculateVolumeAnomaly(event),
            "time_pattern", analyzeTimePattern(event),
            "account_history", getAccountHistory(event.getAccountId()),
            "market_correlation", getMarketCorrelation(event),
            "velocity_score", calculateVelocityScore(event)
        );
    }

    private double calculatePriceDeviation(TradeEvent event) {
        // Calculate deviation from market price
        return Math.abs(event.getPrice() - event.getMarketPrice()) / event.getMarketPrice();
    }

    private double calculateVolumeAnomaly(TradeEvent event) {
        // Calculate volume anomaly score
        return event.getQuantity() / getAverageVolume(event.getSymbol());
    }

    private String analyzeTimePattern(TradeEvent event) {
        // Analyze if trade occurs during suspicious hours
        int hour = event.getTimestamp().atZone(ZoneOffset.UTC).getHour();
        return (hour < 9 || hour > 16) ? "after_hours" : "market_hours";
    }

    private Object getAccountHistory(String accountId) {
        // Retrieve account trading history
        return Map.of("account_id", accountId);
    }

    private double getMarketCorrelation(TradeEvent event) {
        // Calculate correlation with market movements
        return 0.5; // Placeholder
    }

    private double calculateVelocityScore(TradeEvent event) {
        // Calculate trading velocity (trades per minute)
        return 1.0; // Placeholder
    }

    private double getAverageVolume(String symbol) {
        // Get average volume for symbol
        return 1000.0; // Placeholder
    }

    private boolean shouldRetrain(FraudDetectionResult output) {
        // Determine if model retraining is needed
        return output.getConfidence() < 0.5 && output.isSuspicious();
    }

    // ==================== Inner Types ====================

    /**
     * Model registry for fraud detection models.
     */
    public interface ModelRegistry {
        Promise<Void> triggerRetraining(String modelId);
    }

    /**
     * Inference service for fraud predictions.
     */
    public interface InferenceService {
        FraudDetectionResult predict(String modelId, Map<String, Object> features);
    }

    /**
     * Alert service for notifications.
     */
    public interface AlertService {
        Promise<Void> publishAlert(Alert alert);
    }

    /**
     * Pattern extraction engine.
     */
    public interface PatternEngine {
        Promise<Patterns> extractPatterns(java.util.List<Episode> episodes);
    }

    /**
     * Policy store for fraud detection rules.
     */
    public interface PolicyStore {
        Promise<Void> mergePatterns(Patterns patterns);
    }

    // Placeholder instances for reflection
    private final PatternEngine patternEngine = episodes -> Promise.of(new Patterns(0.8));
    private final PolicyStore policyStore = patterns -> Promise.complete();
}
