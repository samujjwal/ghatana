/**
 * Feedback Loop Module - Continuous learning through outcome observation.
 *
 * <p>This module provides the feedback infrastructure that enables the AI brain
 * to learn from outcomes and improve over time. It captures feedback signals from
 * various sources, processes them into learning signals, and coordinates with
 * the pattern registry and model trainers.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.client.feedback.FeedbackEvent} - Captured feedback with outcome information</li>
 *   <li>{@link com.ghatana.datacloud.client.feedback.FeedbackCollector} - Aggregates feedback from multiple sources</li>
 *   <li>{@link com.ghatana.datacloud.client.feedback.FeedbackProcessor} - Transforms feedback into learning signals</li>
 *   <li>{@link com.ghatana.datacloud.client.feedback.LearningLoop} - Orchestrates the continuous learning cycle</li>
 *   <li>{@link com.ghatana.datacloud.client.feedback.OutcomeTracker} - Tracks predictions to actual outcomes</li>
 * </ul>
 *
 * <h2>Feedback Types</h2>
 * <table>
 *   <tr><th>Type</th><th>Source</th><th>Use Case</th></tr>
 *   <tr><td>EXPLICIT</td><td>User actions</td><td>Thumbs up/down, corrections</td></tr>
 *   <tr><td>IMPLICIT</td><td>System observation</td><td>Click-through, dwell time</td></tr>
 *   <tr><td>OUTCOME</td><td>Event resolution</td><td>Prediction accuracy, alert validity</td></tr>
 *   <tr><td>OPERATIONAL</td><td>System metrics</td><td>Latency, resource usage</td></tr>
 * </table>
 *
 * <h2>Learning Loop Cycle</h2>
 * <pre>
 *     ┌─────────────┐
 *     │   Observe   │ ◄── System outputs, predictions
 *     └──────┬──────┘
 *            │
 *     ┌──────▼──────┐
 *     │   Collect   │ ◄── Feedback events aggregated
 *     └──────┬──────┘
 *            │
 *     ┌──────▼──────┐
 *     │   Process   │ ◄── Transform to learning signals
 *     └──────┬──────┘
 *            │
 *     ┌──────▼──────┐
 *     │    Learn    │ ◄── Update models/patterns
 *     └──────┬──────┘
 *            │
 *     ┌──────▼──────┐
 *     │    Apply    │ ──► Improved predictions
 *     └─────────────┘
 * </pre>
 *
 * @doc.type package
 * @doc.purpose Continuous learning through feedback collection and processing
 * @doc.layer core
 * @doc.pattern Observer, Event Sourcing
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see com.ghatana.datacloud.client.LearningSignal
 * @see com.ghatana.datacloud.client.LearningSignalStore
 */
@org.jetbrains.annotations.ApiStatus.Experimental
package com.ghatana.datacloud.client.feedback;
