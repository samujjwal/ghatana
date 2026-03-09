package com.ghatana.platform.domain.agent.registry;

/**
 * {@code ResourceRequirements} declares compute and timing resources required
 * by an agent for optimal operation and safety constraints.
 *
 * <h2>Purpose</h2>
 * Enables resource management and scheduling by specifying:
 * <ul>
 *   <li>Concurrency limits for controlled scalability</li>
 *   <li>Memory requirements for capacity planning</li>
 *   <li>CPU resources for allocation decisions</li>
 *   <li>Operation timeouts for failure detection</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Provided by</b>: {@link AgentCapabilities}</li>
 *   <li><b>Consumed by</b>: Resource allocator, scheduler</li>
 *   <li><b>Validated by</b>: Deployment checker</li>
 *   <li><b>Monitored for</b>: Resource violation detection</li>
 * </ul>
 *
 * <h2>Resource Constraints</h2>
 *
 * <h3>Concurrency</h3>
 * {@code getMaxConcurrency()} limits simultaneous invocations:
 * <ul>
 *   <li>Controls thread pool sizing</li>
 *   <li>Prevents resource starvation</li>
 *   <li>Enforces back-pressure when exceeded</li>
 * </ul>
 * Typical values: 1 (single-threaded), 10-100 (moderate concurrency), 1000+ (highly parallel).
 *
 * <h3>Memory</h3>
 * {@code getMemoryMb()} specifies heap/buffer memory in megabytes:
 * <ul>
 *   <li>Used by JVM memory allocation logic</li>
 *   <li>Guides container/pod sizing in Kubernetes</li>
 *   <li>Prevents OOM killer termination</li>
 * </ul>
 * Example: 512MB for simple processors, 2048MB+ for complex analyzers.
 *
 * <h3>CPU</h3>
 * {@code getCpuUnits()} specifies CPU allocation units (millicores):
 * <ul>
 *   <li>100 = 0.1 CPU cores</li>
 *   <li>1000 = 1.0 CPU core</li>
 *   <li>Guides scheduling on multi-core systems</li>
 * </ul>
 *
 * <h3>Timeout</h3>
 * {@code getTimeoutMs()} specifies maximum operation duration in milliseconds:
 * <ul>
 *   <li>Prevents hung operations from blocking resources</li>
 *   <li>Enables automatic failure detection</li>
 *   <li>Should account for network latency and processing time</li>
 * </ul>
 * Example: 5000ms for quick operations, 60000ms+ for batch processing.
 *
 * <h2>Typical Implementations</h2>
 * {@code
 * // High-concurrency, memory-intensive agent
 * public class AnalyzerRequirements implements ResourceRequirements {
 *     public int getMaxConcurrency() { return 100; }
 *     public long getMemoryMb() { return 2048; }
 *     public int getCpuUnits() { return 2000; }  // 2 cores
 *     public long getTimeoutMs() { return 30000; }  // 30 sec
 * }
 *
 * // Single-threaded, lightweight agent
 * public class CoordinatorRequirements implements ResourceRequirements {
 *     public int getMaxConcurrency() { return 1; }
 *     public long getMemoryMb() { return 256; }
 *     public int getCpuUnits() { return 500; }  // 0.5 cores
 *     public long getTimeoutMs() { return 10000; }  // 10 sec
 * }
 * }
 *
 * <h2>Scheduling Implications</h2>
 * Orchestrator uses these requirements to:
 * <ol>
 *   <li>Validate deployment environment has sufficient resources</li>
 *   <li>Calculate thread pool sizes from maxConcurrency</li>
 *   <li>Set JVM heap to accommodate memoryMb</li>
 *   <li>Configure watchdog timers using timeoutMs</li>
 *   <li>Apply CPU affinity/cgroups constraints</li>
 * </ol>
 *
 * @see AgentCapabilities
 * @see ProcessingCharacteristics
 *
 * @doc.type interface
 * @doc.layer domain
 * @doc.purpose agent resource requirements declaration
 * @doc.pattern contract, capability-declaration, SPI
 * @doc.test-hints resource-allocation, concurrency-limiting, timeout-handling, memory-management, CPU-scheduling
 */
public interface ResourceRequirements {
    int getMaxConcurrency();
    long getMemoryMb();
    int getCpuUnits();
    long getTimeoutMs();
}
