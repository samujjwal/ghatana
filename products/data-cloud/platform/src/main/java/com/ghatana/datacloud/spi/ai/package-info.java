/**
 * AI capability SPIs for Data-Cloud plugins.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines optional AI/ML capabilities that storage plugins can implement to provide:
 * <ul>
 * <li>Predictive analytics (query performance, cost estimation)</li>
 * <li>Intelligent recommendations (indexes, schema optimization)</li>
 * <li>Anomaly detection (performance, security, data quality)</li>
 * <li>Explainability (query plans, AI decisions, provenance)</li>
 * </ul>
 *
 * <p>
 * <b>Design Principles</b><br>
 * <ol>
 * <li><b>Optional</b> - Capabilities are discovered via {@code instanceof}</li>
 * <li><b>Advisory</b> - AI outputs never mutate Tier-1 state autonomously</li>
 * <li><b>Auditable</b> - All decisions include confidence and rationale</li>
 * <li><b>Bounded</b> - Core orchestrates invocation, plugins execute</li>
 * </ol>
 *
 * <p>
 * <b>Discovery Pattern</b><br>
 * <pre>{@code
 * StoragePlugin plugin = registry.getPlugin("postgresql");
 *
 * // Check for prediction capability
 * if (plugin instanceof PredictionCapability predictor) {
 *     PredictionResult result = predictor.predict(request).getResult();
 *     if (result.isActionable()) {
 *         // Use prediction
 *     }
 * }
 *
 * // Check for recommendation capability
 * if (plugin instanceof RecommendationCapability recommender) {
 *     List<Recommendation> recs = recommender.recommend(context).getResult();
 * }
 * }</pre>
 *
 * <p>
 * <b>Safety Contract</b><br>
 * All AI capability implementations MUST:
 * <ul>
 * <li>Return confidence scores (0.0 to 1.0)</li>
 * <li>Include human-readable explanations</li>
 * <li>Log all outputs as learning signals</li>
 * <li>Never block deterministic operations</li>
 * <li>Handle failures gracefully (fallback to deterministic)</li>
 * </ul>
 *
 * <p>
 * <b>Capability Interfaces</b><br>
 * <ul>
 * <li>{@link com.ghatana.datacloud.spi.ai.PredictionCapability}</li>
 * <li>{@link com.ghatana.datacloud.spi.ai.RecommendationCapability}</li>
 * <li>{@link com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability}</li>
 * <li>{@link com.ghatana.datacloud.spi.ai.ExplanationCapability}</li>
 * </ul>
 *
 * @see com.ghatana.datacloud.spi.StoragePlugin
 * @see com.ghatana.datacloud.ai
 */
package com.ghatana.datacloud.spi.ai;

