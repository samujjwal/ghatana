package com.ghatana.platform.core.exception;

/**
 * Exception thrown when registry operations fail.
 *
 * <h2>Purpose</h2>
 * Signals errors during lookup, registration, or updates in any registry:
 * <ul>
 *   <li>Agent registry (agents not found or registration fails)</li>
 *   <li>Service registry (service endpoint lookup fails)</li>
 *   <li>Operator catalog (operator not found or version mismatch)</li>
 *   <li>Schema registry (schema lookup or versioning fails)</li>
 *   <li>Function registry (UDF not found)</li>
 * </ul>
 *
 * <h2>When to Use</h2>
 * <table>
 *   <tr>
 *     <th>Scenario</th>
 *     <th>Exception Type</th>
 *   </tr>
 *   <tr>
 *     <td>Agent not found by ID</td>
 *     <td>✅ RegistryException</td>
 *   </tr>
 *   <tr>
 *     <td>Resource not found (404)</td>
 *     <td>ResourceNotFoundException</td>
 *   </tr>
 *   <tr>
 *     <td>Registry backend is down</td>
 *     <td>✅ RegistryException (SERVICE_UNAVAILABLE)</td>
 *   </tr>
 *   <tr>
 *     <td>Schema version mismatch</td>
 *     <td>SchemaEvolutionException</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * {@code
 * // 1. Agent lookup failure
 * Agent agent = agentRegistry.findById(agentId);
 * if (agent == null) {
 *     throw new RegistryException(
 *         "Agent not found in registry: " + agentId
 *     );
 * }
 *
 * // 2. Registry connection failure
 * try {
 *     operatorCatalog.getOperator(operatorId, version);
 * } catch (CatalogException ce) {
 *     throw new RegistryException(
 *         "Failed to query operator catalog",
 *         ce
 *     );
 * }
 *
 * // 3. Batch lookup with fallback
 * List<Agent> agents = new ArrayList<>();
 * for (String agentId : agentIds) {
 *     try {
 *         agents.add(agentRegistry.findByIdStrict(agentId));
 *     } catch (NotFoundException nfe) {
 *         throw new RegistryException(
 *             "Batch lookup failed: agent " + agentId + " not found",
 *             nfe
 *         );
 *     }
 * }
 *
 * // 4. Registry service unavailable
 * throw new RegistryException(
 *     "Operator catalog service is unavailable - retry later"
 * );
 *
 * // 5. Version mismatch in registry
 * throw new RegistryException(
 *     ErrorCode.SERVICE_UNAVAILABLE,
 *     "Operator version 2 not found - available versions: 1, 3"
 * );
 * }
 *
 * <h2>Common Registries</h2>
 * <ul>
 *   <li><b>AgentRegistry</b>: Manages agent discovery and lookup</li>
 *   <li><b>OperatorCatalog</b>: Central operator registry with versioning</li>
 *   <li><b>SchemaRegistry</b>: Event/message schema storage</li>
 *   <li><b>ServiceRegistry</b>: Service endpoint discovery (mTLS, gRPC)</li>
 *   <li><b>FunctionRegistry</b>: User-defined functions (UDFs)</li>
 * </ul>
 *
 * <h2>Caught By</h2>
 * <ul>
 *   <li>HTTP exception handlers → HTTP 503 Service Unavailable (temp) or 404 Not Found</li>
 *   <li>Retry policies (for temporary unavailability)</li>
 *   <li>Service startup/initialization (register in registry)</li>
 *   <li>Runtime lookup handlers</li>
 * </ul>
 *
 * <h2>Common Error Messages</h2>
 * <ul>
 *   <li>"Agent 'fraud-detector-v2' not found in registry"</li>
 *   <li>"Operator catalog service unavailable - check cluster health"</li>
 *   <li>"Schema 'Event' version 3 not found (available: 1, 2)"</li>
 *   <li>"Service endpoint lookup failed for 'event-processor:8443'"</li>
 *   <li>"UDF 'custom_hash' not registered - register before use"</li>
 * </ul>
 *
 * <h2>Recovery Strategies</h2>
 * <ul>
 *   <li><b>Temporary Failure:</b> Retry with exponential backoff (registry service down)</li>
 *   <li><b>Not Found:</b> Check registry for available items, add if missing</li>
 *   <li><b>Version Mismatch:</b> Update to matching version or register new version</li>
 *   <li><b>Caching:</b> Use local cache as fallback (with TTL)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Thread-safe after construction (immutable state).
 *
 * <h2>Design Pattern: Registry Lookup</h2>
 * <pre>{@code
 * public Agent getAgentOrThrow(String agentId) {
 *     Agent agent = registry.findById(agentId);  // Returns null if not found
 *     if (agent == null) {
 *         throw new RegistryException(
 *             "Agent not registered: " + agentId
 *         );
 *     }
 *     return agent;
 * }
 *
 * // Or optional-based approach
 * public Agent getAgent(String agentId) {
 *     return registry.findByIdOpt(agentId)
 *         .orElseThrow(() -> new RegistryException(
 *             "Agent not found: " + agentId
 *         ));
 * }
 * }</pre>
 *
 * @see BaseException Parent exception class
 * @see ResourceNotFoundException For 404 scenarios
 * @doc.type exception
 * @doc.layer core
 * @doc.purpose exception for registry lookup and operation failures
 * @doc.pattern domain-exception service-discovery registry-pattern
 */
public class RegistryException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a RegistryException with an error message.
     *
     * <p>Use when a registry operation fails (lookup, not found, registration failed).
     * The error code defaults to SERVICE_UNAVAILABLE (503).
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * throw new RegistryException("Agent 'fraud-detector-v2' not found in registry");
     * }</pre>
     *
     * @param message description of registry operation failure (never null)
     */
    public RegistryException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }

    /**
     * Constructs a RegistryException with message and root cause.
     *
     * <p>Use when catching a lower-level exception (IOException, SQLException)
     * from the registry backend and converting to RegistryException.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * try {
     *     operatorCatalog.lookup(operatorId);
     * } catch (CatalogException ce) {
     *     throw new RegistryException(
     *         "Operator catalog lookup failed",
     *         ce  // Preserves original stack trace
     *     );
     * }
     * }</pre>
     *
     * @param message description of registry failure (never null)
     * @param cause the underlying exception (never null)
     */
    public RegistryException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }

    /**
     * Constructs a RegistryException from a root cause only.
     *
     * <p>Use when the cause exception's message already explains the registry failure.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * try {
     *     schemaRegistry.getSchema(schemaId, version);
     * } catch (SchemaRegistryException sre) {
     *     throw new RegistryException(sre);
     * }
     * }</pre>
     *
     * @param cause the underlying exception (never null)
     */
    public RegistryException(Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, cause);
    }
    
    /**
     * Constructs a RegistryException with custom error code and message.
     *
     * <p>Use when you need fine-grained error codes for different registry scenarios
     * (not found vs service down vs permission denied).
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * throw new RegistryException(
     *     ErrorCode.UNAUTHORIZED,
     *     "Cannot access agent registry - insufficient permissions"
     * );
     * }</pre>
     *
     * @param errorCode specific error code for this registry problem (never null)
     * @param message description of registry operation failure (never null)
     */
    public RegistryException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
