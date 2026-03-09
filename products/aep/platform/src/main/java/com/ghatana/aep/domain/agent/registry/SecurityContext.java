package com.ghatana.aep.domain.agent.registry;

/**
 * {@code SecurityContext} provides minimal security information for agent
 * operations, enabling principal-based access control and audit logging.
 *
 * <h2>Purpose</h2>
 * Establishes identity and authentication context by providing:
 * <ul>
 *   <li>Principal identification (user, service, application)</li>
 *   <li>Foundation for access control decisions</li>
 *   <li>Audit trail association with actor</li>
 *   <li>Extensible contract for future security attributes</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Provided by</b>: Authentication layer, request handler</li>
 *   <li><b>Passed to</b>: Agent execution context, authorization interceptors</li>
 *   <li><b>Used by</b>: Agents for permission checks</li>
 *   <li><b>Logged by</b>: Audit system for compliance</li>
 * </ul>
 *
 * <h2>Principal Identification</h2>
 * {@code getPrincipal()} returns a security principal identifier, which could be:
 * <ul>
 *   <li><b>User ID</b>: "user:alice@example.com" - End user</li>
 *   <li><b>Service ID</b>: "service:analyzer-v2" - Microservice</li>
 *   <li><b>Application ID</b>: "app:dcmaar-backend" - Application</li>
 *   <li><b>Anonymous</b>: "anonymous:guest" - Unauthenticated requests</li>
 * </ul>
 * Principal format is implementation-specific but should be globally unique
 * within the security realm.
 *
 * <h2>Design Rationale</h2>
 * This is intentionally minimal contract living in the canonical core module
 * to minimize cross-module dependencies. Extended security information
 * (roles, permissions, tokens) should be added via:
 * <ul>
 *   <li>Wrapper interfaces in specific modules</li>
 *   <li>ThreadLocal context storage</li>
 *   <li>Request header injection</li>
 *   <li>External authorization systems</li>
 * </ul>
 *
 * <h2>Typical Usage Patterns</h2>
 * {@code
 * // Access control based on principal
 * public class SecureAnalyzer implements Agent {
 *     public Promise<Analysis> analyze(
 *         Event event,
 *         SecurityContext security) {
 *         String principal = security.getPrincipal();
 *         if (!isAuthorized(principal, event)) {
 *             return Promise.ofException(new AccessDeniedException());
 *         }
 *         return performAnalysis(event);
 *     }
 * }
 *
 * // Audit logging with actor information
 * public class AuditingAgent implements Agent {
 *     public Promise<Result> execute(Request req, SecurityContext sec) {
 *         String actor = sec.getPrincipal();
 *         Result result = executeRequest(req);
 *         auditLog.record(actor, req.getAction(), result.isSuccess());
 *         return Promise.of(result);
 *     }
 * }
 * }
 *
 * <h2>Multi-Tenancy Integration</h2>
 * SecurityContext complements {@link AgentExecutionContext#tenantId()}:
 * <ul>
 *   <li>SecurityContext identifies WHO is making the request</li>
 *   <li>AgentExecutionContext identifies WHICH tenant owns the resources</li>
 *   <li>Together they enforce tenant isolation and access control</li>
 * </ul>
 * Example: User "alice@tenant-a.com" can only access tenant-a resources.
 *
 * @see AgentExecutionContext
 *
 * @doc.type interface
 * @doc.layer domain
 * @doc.purpose security principal identification contract
 * @doc.pattern contract, security-context, minimal-interface
 * @doc.test-hints principal-identification, access-control, audit-logging, multi-tenancy-enforcement
 */
public interface SecurityContext {
    String getPrincipal();
}
