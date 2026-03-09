package com.ghatana.platform.domain.agent.registry;

/**
 * {@code ProcessingCharacteristics} defines processing traits that describe
 * how an agent handles work, including batch optimization, statefulness, and idempotency.
 *
 * <h2>Purpose</h2>
 * Enables optimization and orchestration decisions by declaring:
 * <ul>
 *   <li>Batch processing capability and optimal batch size</li>
 *   <li>Stateful operation requirements</li>
 *   <li>Idempotency guarantees for retry safety</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Returned by</b>: {@link AgentCapabilities}</li>
 *   <li><b>Used by</b>: Orchestrator for scheduling decisions</li>
 *   <li><b>Queried by</b>: Load balancer for request batching</li>
 *   <li><b>Checked by</b>: Retry logic for idempotency</li>
 * </ul>
 *
 * <h2>Key Characteristics</h2>
 *
 * <h3>Batch Optimization</h3>
 * {@code isBatchOptimized()} indicates if agent can efficiently process multiple
 * items together (vs. single-item processing). When true, orchestrator should batch requests.
 * {@code getPreferredBatchSize()} specifies ideal items per batch (e.g., 100, 1000).
 *
 * <h3>Statefulness</h3>
 * {@code isStateful()} indicates if agent maintains internal state across invocations:
 * <ul>
 *   <li><b>true</b>: Agent keeps state (cache, accumulated data). Requires stateful scheduling.</li>
 *   <li><b>false</b>: Agent is stateless. Can be scaled horizontally.</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * {@code isIdempotent()} indicates if operation produces same result on repeated execution:
 * <ul>
 *   <li><b>true</b>: Safe to retry on failure. No side effect concerns.</li>
 *   <li><b>false</b>: Retries may cause issues. Needs careful handling.</li>
 * </ul>
 *
 * <h2>Typical Implementations</h2>
 * {@code
 * // Batch-optimized, stateless, idempotent agent
 * public class AnalyzerCharacteristics implements ProcessingCharacteristics {
 *     public boolean isBatchOptimized() { return true; }
 *     public boolean isStateful() { return false; }
 *     public boolean isIdempotent() { return true; }
 *     public int getPreferredBatchSize() { return 1000; }
 * }
 *
 * // Single-item, stateful, non-idempotent agent
 * public class StatefulExecutorCharacteristics implements ProcessingCharacteristics {
 *     public boolean isBatchOptimized() { return false; }
 *     public boolean isStateful() { return true; }
 *     public boolean isIdempotent() { return false; }
 *     public int getPreferredBatchSize() { return 1; }
 * }
 * }
 *
 * <h2>Orchestration Implications</h2>
 * <table border="1">
 *   <tr>
 *     <th>Batch</th><th>Stateful</th><th>Idempotent</th><th>Implications</th>
 *   </tr>
 *   <tr>
 *     <td>true</td><td>false</td><td>true</td><td>Highly scalable, batch requests liberally</td>
 *   </tr>
 *   <tr>
 *     <td>true</td><td>false</td><td>false</td><td>Scalable but retry carefully</td>
 *   </tr>
 *   <tr>
 *     <td>false</td><td>true</td><td>true</td><td>Pin to instance, retries safe</td>
 *   </tr>
 *   <tr>
 *     <td>false</td><td>true</td><td>false</td><td>Pin to instance, no retries</td>
 *   </tr>
 * </table>
 *
 * @see AgentCapabilities
 *
 * @doc.type interface
 * @doc.layer domain
 * @doc.purpose agent processing characteristics declaration
 * @doc.pattern contract, capability-declaration, SPI
 * @doc.test-hints batch-optimization, statefulness-handling, idempotent-operations, retry-logic
 */
public interface ProcessingCharacteristics {
    boolean isBatchOptimized();
    boolean isStateful();
    boolean isIdempotent();
    int getPreferredBatchSize();
}
