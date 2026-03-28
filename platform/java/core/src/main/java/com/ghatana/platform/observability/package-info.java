/**
 * Platform observability abstractions: metrics collection and no-op implementations.
 *
 * <h2>Logging Pattern Conventions (OBS-002)</h2>
 *
 * <p>All services and platform modules must follow these logging patterns to ensure
 * consistent, diagnosable, and aggregatable log output:</p>
 *
 * <h3>Logger Declaration</h3>
 * <pre>{@code
 * // Always use SLF4J — never Log4j or java.util.logging directly:
 * private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
 * }</pre>
 *
 * <h3>Log Level Guidelines</h3>
 * <ul>
 * <li>{@code ERROR} — Exceptions or failures that require immediate action. Always include
 *     the exception as the last argument.</li>
 * <li>{@code WARN} — Unexpected state that is recoverable (e.g., fallback activated,
 *     soft validation failure).</li>
 * <li>{@code INFO} — Important lifecycle events (startup, shutdown, config loaded,
 *     significant business events). Keep to &lt;5 lines per request.</li>
 * <li>{@code DEBUG} — Detailed flow for diagnosing issues. Not emitted in production.</li>
 * <li>{@code TRACE} — Ultra-verbose data dumps. Disabled by default in all environments.</li>
 * </ul>
 *
 * <h3>Parameterized Messages (Never Concatenate)</h3>
 * <pre>{@code
 * // Correct — lazy evaluation, no allocation when level is disabled:
 * logger.info("Processing event {} for tenant {}", eventId, tenantId);
 * logger.error("Failed to process event {}", eventId, exception);
 *
 * // Incorrect — eager string concatenation even when DEBUG is off:
 * logger.debug("Processing: " + eventId + " " + tenantId);
 * }</pre>
 *
 * <h3>Security: Never Log Sensitive Data</h3>
 * <ul>
 * <li>Do not log JWT tokens, passwords, API keys, or PII.</li>
 * <li>Log IDs (userId, tenantId, requestId) — not the actual values of sensitive fields.</li>
 * </ul>
 *
 * <h3>Correlation IDs</h3>
 * <p>Propagate requestId / traceId through log statements in request-handling code:</p>
 * <pre>{@code
 * logger.info("Request {} completed in {}ms [tenant={}]", requestId, durationMs, tenantId);
 * }</pre>
 */
package com.ghatana.platform.observability;
