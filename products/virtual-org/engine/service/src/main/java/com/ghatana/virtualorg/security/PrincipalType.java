package com.ghatana.virtualorg.security;

/**
 * Principal types in the virtual organization security system.
 *
 * <p><b>Purpose</b><br>
 * Categorizes actors (principals) in the system for authorization,
 * auditing, and security policy enforcement. Each type has different
 * trust levels, capabilities, and security requirements.
 *
 * <p><b>Architecture Role</b><br>
 * Value type used by {@link Principal} to distinguish between:
 * - AI agents (autonomous system actors)
 * - Human users (interactive human operators)
 * - Services (external system integrations)
 * - System operations (platform-level processes)
 *
 * <p><b>Trust Levels</b><br>
 * Each principal type has implicit trust level:
 * - <b>SYSTEM</b>: Highest trust - platform operations, no user context
 * - <b>SERVICE</b>: High trust - authenticated external services
 * - <b>AGENT</b>: Medium trust - autonomous agents with defined authority
 * - <b>USER</b>: Variable trust - human users with role-based permissions
 *
 * <p><b>Security Implications</b><br>
 * Type affects:
 * - Authentication requirements (API key, JWT, mTLS, etc.)
 * - Authorization scope (what resources can be accessed)
 * - Audit logging (different retention policies)
 * - Rate limiting (different quotas per type)
 * - Encryption requirements (different key types)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Create agent principal
 * Principal agent = Principal.of("agent-123", PrincipalType.AGENT);
 * 
 * // Create user principal
 * Principal user = Principal.of("user-456", PrincipalType.USER);
 * 
 * // Create system principal
 * Principal system = Principal.of("metrics-collector", PrincipalType.SYSTEM);
 * 
 * // Check principal type for authorization
 * if (principal.getType() == PrincipalType.SYSTEM) {
 *     // Allow elevated system operations
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable enum - thread-safe.
 *
 * @see Principal
 * @see SecureMessageChannel
 * @doc.type enum
 * @doc.purpose Principal type enumeration for security
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum PrincipalType {
    /**
     * An AI agent (autonomous system actor).
     * 
     * <p>Characteristics:
     * - Has defined role and authority
     * - Operates within organizational hierarchy
     * - Subject to decision-making constraints
     * - Authenticated via agent ID + authority token
     */
    AGENT,

    /**
     * A human user (interactive human operator).
     * 
     * <p>Characteristics:
     * - Has user account with credentials
     * - Subject to role-based access control (RBAC)
     * - Interactive sessions with timeout
     * - Authenticated via username/password or SSO
     */
    USER,

    /**
     * An external service or system.
     * 
     * <p>Characteristics:
     * - Third-party integration (GitHub, Jira, Slack, etc.)
     * - Service-to-service authentication
     * - API key or OAuth-based auth
     * - Rate-limited by service tier
     */
    SERVICE,

    /**
     * System-level operations (platform processes).
     * 
     * <p>Characteristics:
     * - Internal platform components
     * - Elevated privileges for infrastructure
     * - Metrics collection, health checks, migrations
     * - Trusted execution context
     */
    SYSTEM
}
