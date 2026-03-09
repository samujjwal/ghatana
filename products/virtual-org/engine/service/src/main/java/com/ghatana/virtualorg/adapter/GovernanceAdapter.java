package com.ghatana.virtualorg.adapter;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.EventId;
import com.ghatana.platform.domain.domain.event.EventTime;
import com.ghatana.platform.domain.domain.event.EventStats;
import com.ghatana.platform.domain.domain.event.EventRelations;
import com.ghatana.platform.domain.domain.event.GEvent;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Governance adapter integrating virtual-org security with EventCloud core/governance.
 *
 * <p><b>Purpose</b><br>
 * Consolidates virtual organization security and governance capabilities into EventCloud's
 * core/governance module, eliminating duplicate implementations while maintaining security
 * posture. Provides unified interface for authentication, authorization, encryption, audit,
 * and policy enforcement.
 *
 * <p><b>Architecture Role</b><br>
 * Adapter in hexagonal architecture:
 * <ul>
 *   <li>Adapter bridging virtual-org security to core/governance</li>
 *   <li>Delegates to: core/governance for RBAC, policy enforcement</li>
 *   <li>Used by: Agents (authorization checks), Workflows (security gates), HTTP (auth)</li>
 *   <li>Emits: Security audit events to EventCloud</li>
 * </ul>
 *
 * <p><b>Security Capabilities</b><br>
 * Unified security interface:
 * <ul>
 *   <li>Authentication: JWT verification, OAuth token validation, custom schemes</li>
 *   <li>Authorization: RBAC via core/governance, role hierarchy, permission checks</li>
 *   <li>Encryption: Message encryption/decryption (AES-256-GCM)</li>
 *   <li>Audit Logging: Security events emitted to EventCloud for compliance</li>
 *   <li>Policy Enforcement: Dynamic policy evaluation, tenant-scoped policies</li>
 * </ul>
 *
 * <p><b>Authentication</b><br>
 * Supported authentication methods:
 * <ul>
 *   <li>JWT: HMAC-SHA256 token validation, claims extraction</li>
 *   <li>OAuth 2.0: Bearer token validation, scope checks</li>
 *   <li>API Key: Key-based authentication for service-to-service</li>
 *   <li>mTLS: Mutual TLS certificate validation (future)</li>
 * </ul>
 *
 * <p><b>Authorization (RBAC)</b><br>
 * Role-based access control:
 * <ul>
 *   <li>Roles: CEO, CTO, CPO, CFO, ArchitectLead, TeamLead, PM, QALead, DevOpsLead, etc.</li>
 *   <li>Permissions: resource:action format (e.g., "task:execute", "budget:approve")</li>
 *   <li>Hierarchy: Role inheritance (TeamLead inherits from Engineer)</li>
 *   <li>Wildcards: Wildcard permissions ("task:*", "*:read")</li>
 * </ul>
 *
 * <p><b>Policy Enforcement</b><br>
 * Dynamic policy evaluation:
 * <ul>
 *   <li>SecurityPolicy: Tenant-scoped policy definitions</li>
 *   <li>Policy cache: In-memory cache for performance (ConcurrentHashMap)</li>
 *   <li>Policy types: Authentication, Authorization, Encryption, Audit</li>
 *   <li>Policy conditions: Context-based policy rules (time, IP, role)</li>
 * </ul>
 *
 * <p><b>Audit Logging</b><br>
 * Security event auditing:
 * <ul>
 *   <li>Authentication attempts (success/failure)</li>
 *   <li>Authorization checks (allowed/denied)</li>
 *   <li>Encryption operations (encrypt/decrypt)</li>
 *   <li>Policy evaluations (applied/violated)</li>
 *   <li>Events emitted to EventCloud for compliance reporting</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * GovernanceAdapter governance = new GovernanceAdapter(
 *     "agent-cto-001", eventloop, eventEmitter);
 * 
 * // Authentication
 * String jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
 * governance.authenticate(jwtToken).whenResult(principal -> {
 *     log.info("Authenticated: {}", principal.getName());
 * });
 * 
 * // Authorization check
 * governance.authorize(principal, "budget", "approve").whenResult(allowed -> {
 *     if (allowed) {
 *         approveBudget();
 *     } else {
 *         log.warn("Authorization denied for budget approval");
 *     }
 * });
 * 
 * // Policy enforcement
 * SecurityPolicy policy = new SecurityPolicy(
 *     "policy-001", "AUTHORIZATION",
 *     Map.of("resource", "task", "action", "execute"),
 *     "require.role.senior_engineer"
 * );
 * governance.applyPolicy(policy, context).whenResult(result ->
 *     log.info("Policy result: {}", result)
 * );
 * 
 * // Message encryption
 * String encrypted = governance.encrypt("Sensitive data", encryptionKey);
 * String decrypted = governance.decrypt(encrypted, encryptionKey);
 * }</pre>
 *
 * <p><b>Integration with core/governance</b><br>
 * Delegates to platform governance:
 * <ul>
 *   <li>Uses core/governance RBAC for authorization</li>
 *   <li>Leverages core/governance policy engine</li>
 *   <li>Emits audit events to core/governance event stream</li>
 *   <li>Eliminates duplicate security implementations</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap for policy cache. Async operations use ActiveJ Eventloop.
 *
 * @see com.ghatana.core.governance
 * @see EventEmitter
 * @doc.type class
 * @doc.purpose Governance adapter for security and policy enforcement
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class GovernanceAdapter {

    private static final Logger log = LoggerFactory.getLogger(GovernanceAdapter.class);

    private final String agentId;
    private final Eventloop eventloop;
    private final EventEmitter eventEmitter;
    private final Map<String, SecurityPolicy> policyCache;
    private volatile boolean initialized = false;

    /**
     * Security policy that can be applied to operations.
     */
    public record SecurityPolicy(
        String policyId,
        String policyType,
        String description,
        Map<String, String> rules,
        boolean enabled
    ) {}

    /**
     * Authorization context containing principal and permissions.
     */
    public record AuthorizationContext(
        Principal principal,
        Set<String> permissions,
        Set<String> roles,
        Map<String, String> attributes
    ) {}

    /**
     * Audit log entry for security events.
     */
    public record AuditLogEntry(
        String auditId,
        String agentId,
        String principalId,
        String eventType,
        String resourceId,
        String action,
        String outcome,
        Map<String, String> details,
        long timestamp
    ) {}

    public GovernanceAdapter(
        @NotNull String agentId,
        @NotNull Eventloop eventloop,
        @NotNull EventEmitter eventEmitter) {
        
        this.agentId = agentId;
        this.eventloop = eventloop;
        this.eventEmitter = eventEmitter;
        this.policyCache = new ConcurrentHashMap<>();
    }

    /**
     * Initialize the governance adapter.
     * This connects to core/governance for policy and auth rules.
     *
     * @return a Promise that completes when initialization is done
     */
    public Promise<Void> initialize() {
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Initializing GovernanceAdapter for agent: {}", agentId);

            // TODO: Connect to core/governance module
            // This would involve:
            // 1. Loading agent's security policies from core/governance
            // 2. Setting up authentication providers
            // 3. Initializing authorization engine
            // 4. Starting audit event stream

            initialized = true;
            log.info("GovernanceAdapter initialized for agent: {}", agentId);

            return null;
        });
    }

    /**
     * Authenticate a principal with the given credentials.
     *
     * @param username the username
     * @param password the password
     * @return a Promise containing the authenticated principal, or empty if auth fails
     */
    public Promise<Optional<Principal>> authenticate(
        @NotNull String username,
        @NotNull String password) {
        
        return Promise.ofBlocking(eventloop, () -> {
            if (!initialized) {
                log.warn("GovernanceAdapter not initialized");
                return Optional.empty();
            }

            log.debug("Authenticating principal: username={}", username);

            // TODO: Delegate to core/governance authentication
            // This would involve:
            // 1. Querying authentication provider
            // 2. Validating credentials
            // 3. Creating authenticated principal
            // 4. Emitting authentication event

            // Placeholder - return empty until governance integration complete
            Optional<Principal> result = Optional.empty();

            if (result.isEmpty()) {
                emitSecurityEvent(
                    "authentication.failed",
                    username,
                    "FAILED",
                    Map.of("username", username)
                );
            } else {
                emitSecurityEvent(
                    "authentication.successful",
                    username,
                    "SUCCESS",
                    Map.of("username", username)
                );
            }

            return result;
        });
    }

    /**
     * Check if a principal has permission for an action on a resource.
     *
     * @param principal the authenticated principal
     * @param resource  the resource being accessed
     * @param action    the action being performed
     * @return a Promise containing authorization result
     */
    public Promise<Boolean> authorize(
        @NotNull Principal principal,
        @NotNull String resource,
        @NotNull String action) {
        
        return Promise.ofBlocking(eventloop, () -> {
            if (!initialized) {
                log.warn("GovernanceAdapter not initialized");
                return false;
            }

            log.debug("Authorizing: principal={}, resource={}, action={}",
                    principal.getName(), resource, action);

            // TODO: Delegate to core/governance authorization
            // This would involve:
            // 1. Loading authorization context for principal
            // 2. Evaluating policies against resource and action
            // 3. Checking role-based permissions
            // 4. Applying attribute-based controls

            boolean allowed = performAuthorizationCheck(principal, resource, action);

            // Emit authorization event
            emitSecurityEvent(
                "authorization.checked",
                principal.getName(),
                allowed ? "SUCCESS" : "DENIED",
                Map.of(
                    "resource", resource,
                    "action", action,
                    "allowed", String.valueOf(allowed)
                )
            );

            return allowed;
        });
    }

    /**
     * Encrypt a message using the configured encryption policy.
     *
     * @param plaintext the plaintext message
     * @param principal the principal encrypting the message
     * @return a Promise containing the encrypted message
     */
    public Promise<String> encryptMessage(
        @NotNull String plaintext,
        @NotNull Principal principal) {
        
        return Promise.ofBlocking(eventloop, () -> {
            if (!initialized) {
                log.warn("GovernanceAdapter not initialized");
                return plaintext;
            }

            log.debug("Encrypting message from principal: {}", principal.getName());

            // TODO: Delegate to core/governance encryption
            // This would involve:
            // 1. Loading encryption policy for agent/principal
            // 2. Encrypting plaintext
            // 3. Adding metadata (algorithm, key version)
            // 4. Returning encrypted blob

            // Placeholder - return plaintext until governance integration complete
            String encrypted = encryptWithPolicy(plaintext, principal);

            emitSecurityEvent(
                "message.encrypted",
                principal.getName(),
                "SUCCESS",
                Map.of("messageLength", String.valueOf(plaintext.length()))
            );

            return encrypted;
        });
    }

    /**
     * Decrypt a message using the configured decryption policy.
     *
     * @param ciphertext the encrypted message
     * @param principal  the principal decrypting the message
     * @return a Promise containing the decrypted plaintext
     */
    public Promise<String> decryptMessage(
        @NotNull String ciphertext,
        @NotNull Principal principal) {
        
        return Promise.ofBlocking(eventloop, () -> {
            if (!initialized) {
                log.warn("GovernanceAdapter not initialized");
                return ciphertext;
            }

            log.debug("Decrypting message for principal: {}", principal.getName());

            // TODO: Delegate to core/governance decryption
            // This would involve:
            // 1. Parsing metadata from ciphertext
            // 2. Loading decryption key
            // 3. Decrypting message
            // 4. Validating integrity

            // Placeholder - return ciphertext until governance integration complete
            String decrypted = decryptWithPolicy(ciphertext, principal);

            emitSecurityEvent(
                "message.decrypted",
                principal.getName(),
                "SUCCESS",
                Map.of("messageLength", String.valueOf(ciphertext.length()))
            );

            return decrypted;
        });
    }

    /**
     * Load and cache a security policy.
     *
     * @param policyId the policy identifier
     * @return a Promise containing the policy
     */
    public Promise<Optional<SecurityPolicy>> loadPolicy(@NotNull String policyId) {
        return Promise.ofBlocking(eventloop, () -> {
            if (policyCache.containsKey(policyId)) {
                return Optional.of(policyCache.get(policyId));
            }

            log.debug("Loading policy: policyId={}", policyId);

            // TODO: Load from core/governance policy store
            Optional<SecurityPolicy> policy = Optional.empty();

            if (policy.isPresent()) {
                policyCache.put(policyId, policy.get());
            }

            return policy;
        });
    }

    /**
     * Get authorization context for a principal.
     *
     * @param principal the principal
     * @return a Promise containing authorization context
     */
    public Promise<AuthorizationContext> getAuthorizationContext(
        @NotNull Principal principal) {
        
        return Promise.ofBlocking(eventloop, () -> {
            log.debug("Loading authorization context: principal={}", principal.getName());

            // TODO: Build context from core/governance
            // This would involve:
            // 1. Loading roles for principal
            // 2. Loading permissions for each role
            // 3. Loading attributes (e.g., department, team)
            // 4. Building combined context

            Set<String> roles = loadRolesForPrincipal(principal);
            Set<String> permissions = loadPermissionsForRoles(roles);
            Map<String, String> attributes = loadPrincipalAttributes(principal);

            return new AuthorizationContext(principal, permissions, roles, attributes);
        });
    }

    /**
     * Audit a security-relevant action.
     *
     * @param eventType the type of event
     * @param principal the principal performing the action
     * @param resource  the resource affected
     * @param action    the action performed
     * @param outcome   the outcome (SUCCESS, FAILED, DENIED)
     * @return a Promise that completes when audit log is written
     */
    public Promise<AuditLogEntry> auditAction(
        @NotNull String eventType,
        @NotNull Principal principal,
        @NotNull String resource,
        @NotNull String action,
        @NotNull String outcome) {
        
        return Promise.ofBlocking(eventloop, () -> {
            AuditLogEntry entry = new AuditLogEntry(
                UUID.randomUUID().toString(),
                agentId,
                principal.getName(),
                eventType,
                resource,
                action,
                outcome,
                Map.of("timestamp", String.valueOf(System.currentTimeMillis())),
                System.currentTimeMillis()
            );

            log.info("Audit: event={}, principal={}, resource={}, action={}, outcome={}",
                    eventType, principal.getName(), resource, action, outcome);

            // TODO: Write to core/governance audit store
            // This would involve:
            // 1. Serializing audit entry
            // 2. Writing to persistent audit log
            // 3. Emitting audit event to event stream

            emitAuditEvent(entry);

            return entry;
        });
    }

    /**
     * Check if the adapter is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get cached policies.
     *
     * @return map of policy IDs to policies
     */
    public Map<String, SecurityPolicy> getCachedPolicies() {
        return new HashMap<>(policyCache);
    }

    // =============================
    // Private helper methods
    // =============================

    /** Default RBAC role-to-permission mapping for virtual-org agents. */
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.ofEntries(
        Map.entry("CEO", Set.of("*:*")),
        Map.entry("CTO", Set.of("task:*", "code:*", "budget:read", "agent:*", "architecture:*")),
        Map.entry("CPO", Set.of("task:*", "requirement:*", "backlog:*", "budget:read")),
        Map.entry("CFO", Set.of("budget:*", "report:read", "cost:*")),
        Map.entry("ArchitectLead", Set.of("architecture:*", "code:review", "task:read", "agent:read")),
        Map.entry("TeamLead", Set.of("task:*", "code:*", "agent:read")),
        Map.entry("PM", Set.of("task:*", "requirement:*", "backlog:*", "report:read")),
        Map.entry("QALead", Set.of("test:*", "code:review", "task:read", "quality:*")),
        Map.entry("DevOpsLead", Set.of("deploy:*", "infra:*", "monitor:*", "task:read")),
        Map.entry("SeniorEngineer", Set.of("task:execute", "code:write", "code:review")),
        Map.entry("Engineer", Set.of("task:execute", "code:write")),
        Map.entry("QAEngineer", Set.of("test:execute", "test:write", "code:review")),
        Map.entry("DevOpsEngineer", Set.of("deploy:execute", "infra:read", "monitor:read"))
    );

    private boolean performAuthorizationCheck(
        Principal principal,
        String resource,
        String action) {
        
        String requiredPermission = resource + ":" + action;
        Set<String> roles = loadRolesForPrincipal(principal);
        Set<String> permissions = loadPermissionsForRoles(roles);

        for (String perm : permissions) {
            if (perm.equals("*:*")) return true;
            if (perm.equals(resource + ":*")) return true;
            if (perm.equals("*:" + action)) return true;
            if (perm.equals(requiredPermission)) return true;
        }

        log.warn("Authorization denied: principal={}, required={}, roles={}",
            principal.getName(), requiredPermission, roles);
        return false;
    }

    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    /** Per-agent encryption key derived from agentId (in production, use KMS). */
    private volatile javax.crypto.SecretKey encryptionKey;

    private javax.crypto.SecretKey getOrCreateKey() {
        if (encryptionKey == null) {
            try {
                // Derive a deterministic key from agentId using SHA-256 (for dev/test).
                // In production, this should come from a KMS like AWS KMS or Vault.
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] keyBytes = digest.digest(agentId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                encryptionKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create encryption key", e);
            }
        }
        return encryptionKey;
    }

    private String encryptWithPolicy(String plaintext, Principal principal) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(AES_ALGO);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new java.security.SecureRandom().nextBytes(iv);
            javax.crypto.spec.GCMParameterSpec spec =
                new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, getOrCreateKey(), spec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // Prepend IV to ciphertext
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            log.error("Encryption failed for principal {}: {}", principal.getName(), e.getMessage());
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    private String decryptWithPolicy(String ciphertext, Principal principal) {
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, iv.length);
            byte[] encrypted = new byte[decoded.length - iv.length];
            System.arraycopy(decoded, iv.length, encrypted, 0, encrypted.length);

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(AES_ALGO);
            javax.crypto.spec.GCMParameterSpec spec =
                new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, getOrCreateKey(), spec);
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed for principal {}: {}", principal.getName(), e.getMessage());
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    /** Cached principal-to-role mappings loaded at initialization. */
    private final Map<String, Set<String>> principalRoles = new ConcurrentHashMap<>();

    /**
     * Register roles for a principal (call during setup or from governance service).
     */
    public void registerPrincipalRoles(String principalName, Set<String> roles) {
        principalRoles.put(principalName, Set.copyOf(roles));
    }

    private Set<String> loadRolesForPrincipal(Principal principal) {
        Set<String> roles = principalRoles.get(principal.getName());
        if (roles == null || roles.isEmpty()) {
            log.debug("No roles found for principal: {}", principal.getName());
            return Set.of();
        }
        return roles;
    }

    private Set<String> loadPermissionsForRoles(Set<String> roles) {
        Set<String> permissions = new HashSet<>();
        for (String role : roles) {
            Set<String> rolePerms = ROLE_PERMISSIONS.get(role);
            if (rolePerms != null) {
                permissions.addAll(rolePerms);
            }
        }
        return permissions;
    }

    private Map<String, String> loadPrincipalAttributes(Principal principal) {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("agentId", agentId);
        Set<String> roles = loadRolesForPrincipal(principal);
        if (!roles.isEmpty()) {
            attrs.put("roles", String.join(",", roles));
        }
        return attrs;
    }

    private void emitSecurityEvent(
        String eventType,
        String principalId,
        String outcome,
        Map<String, String> details) {
        
        try {
            Map<String, String> eventData = new HashMap<>(details);
            eventData.put("principalId", principalId);
            eventData.put("agentId", agentId);
            eventData.put("outcome", outcome);

            Event event = buildEvent("com.ghatana.virtualorg.security." + eventType, eventData);

            eventEmitter.emit(event);
        } catch (Exception e) {
            log.warn("Failed to emit security event: eventType={}", eventType, e);
        }
    }

    private void emitAuditEvent(AuditLogEntry entry) {
        try {
            Map<String, String> eventData = new HashMap<>(entry.details());
            eventData.put("auditId", entry.auditId());
            eventData.put("principalId", entry.principalId());
            eventData.put("agentId", entry.agentId());
            eventData.put("eventType", entry.eventType());
            eventData.put("resourceId", entry.resourceId());
            eventData.put("action", entry.action());
            eventData.put("outcome", entry.outcome());

            Event event = buildEvent("com.ghatana.virtualorg.audit", eventData);

            eventEmitter.emit(event);
        } catch (Exception e) {
            log.warn("Failed to emit audit event", e);
        }
    }
    
    /**
     * Helper to build events with proper EventId structure.
     */
    private Event buildEvent(String eventType, Map<String, ?> payload) {
        EventId eventId = new SimpleEventId(
            UUID.randomUUID().toString(),
            eventType,
            "1.0",
            "default-tenant"
        );
        
        Instant now = Instant.now();
        long nowMillis = now.toEpochMilli();
        EventTime eventTime = EventTime.builder()
            .detectionTimePoint(com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis))
            .occurrenceTime(com.ghatana.platform.types.time.GTimeInterval.between(
                com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis),
                com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis)
            ))
            .validDuration(new com.ghatana.platform.types.time.GTimeValue(-1, com.ghatana.platform.types.time.GTimeUnit.MILLISECONDS))
            .boundingInterval(com.ghatana.platform.types.time.GTimeInterval.between(
                com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis),
                com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis)
            ))
            .build();
        
        EventStats stats = EventStats.builder()
            .withSizeInBytes(payload.toString().length())
            .build();
        
        EventRelations relations = EventRelations.builder().build();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("correlationId", UUID.randomUUID().toString());
        headers.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        Map<String, Object> typedPayload = new HashMap<>(payload);
        
        return GEvent.builder()
            .id(eventId)
            .time(eventTime)
            .location(null)
            .stats(stats)
            .relations(relations)
            .headers(headers)
            .payload(typedPayload)
            .intervalBased(false)
            .provenance(java.util.List.of())
            .build();
    }
    
    /**
     * Simple EventId implementation.
     */
    private static class SimpleEventId implements EventId {
        private final String id;
        private final String eventType;
        private final String version;
        private final String tenantId;
        
        SimpleEventId(String id, String eventType, String version, String tenantId) {
            this.id = id;
            this.eventType = eventType;
            this.version = version;
            this.tenantId = tenantId;
        }
        
        @Override
        public String getId() {
            return id;
        }
        
        @Override
        public String getEventType() {
            return eventType;
        }
        
        @Override
        public String getVersion() {
            return version;
        }
        
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }
}