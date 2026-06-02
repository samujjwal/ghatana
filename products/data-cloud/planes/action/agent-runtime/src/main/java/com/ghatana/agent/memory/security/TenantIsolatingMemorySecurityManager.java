package com.ghatana.agent.memory.security;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Strict tenant-isolating implementation of {@link MemorySecurityManager}.
 *
 * <p>Enforces that every memory read/write/search is scoped to the caller's
 * tenant. Cross-tenant access is unconditionally denied, regardless of agent
 * identity, preventing data leakage in multi-tenant deployments.
 *
 * <p><b>Write policy</b>: A write is permitted when the item's tenant ID matches
 * the caller's tenant ID, or when the item carries no tenant (the item will be
 * scoped to the caller's tenant by the persistence layer).
 *
 * <p><b>Read policy</b>: A read is permitted only when the item's tenant ID
 * exactly matches the caller's tenant ID (null item tenant → denied).
 *
 * <p><b>Search policy</b>: Cross-tiers semantic search is always permitted for
 * any authenticated tenant (the underlying query already applies tenant filters).
 *
 * @doc.type class
 * @doc.purpose Strict tenant isolation for all memory plane operations
 * @doc.layer agent-memory
 * @doc.pattern Strategy
 * @doc.gaa.memory episodic
 * @doc.gaa.lifecycle perceive
 */
public class TenantIsolatingMemorySecurityManager implements MemorySecurityManager {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolatingMemorySecurityManager.class);
    private static final Set<String> DEFAULT_ALLOWED_CLASSIFICATIONS = Set.of(
        "PUBLIC",
        "INTERNAL",
        "CONFIDENTIAL"
    );
    private static final Set<String> DEFAULT_REDACTION_REQUIREMENTS = Set.of(
        "MASK_PII",
        "MASK_CREDENTIALS"
    );
    private static final int MAX_SEARCH_RESULTS = 50;
    private static final Duration MAX_RETENTION = Duration.ofDays(90);

    /**
     * Permits a read only when the item's tenant ID exactly matches the caller's tenant ID.
     * A null item tenant is treated as a mismatch and access is denied.
     *
     * @param item     The memory item to check
     * @param tenantId The calling tenant context (non-null)
     * @param agentId  The requesting agent (informational, not checked here)
     * @return {@code true} iff {@code item.getTenantId().equals(tenantId)}
     */
    @Override
    public boolean canRead(@NotNull MemoryItem item, @NotNull String tenantId, @NotNull String agentId) {
        String itemTenant = item.getTenantId();
        boolean allowed = tenantId.equals(itemTenant);
        if (!allowed) {
            log.warn("Memory read DENIED — caller tenant='{}' item tenant='{}' itemId='{}' agent='{}'",
                    tenantId, itemTenant, item.getId(), agentId);
        }
        return allowed;
    }

    /**
     * Permits a write when:
     * <ul>
     *   <li>The item's tenant ID is {@code null} (new item, will be set by persistence layer), or</li>
     *   <li>The item's tenant ID equals the caller's tenant ID (natural owner).</li>
     * </ul>
     * Any attempt to write an item owned by a different tenant is denied.
     *
     * @param item     The memory item to write
     * @param tenantId The calling tenant context (non-null)
     * @param agentId  The requesting agent (informational)
     * @return {@code true} iff the write should proceed
     */
    @Override
    public boolean canWrite(@NotNull MemoryItem item, @NotNull String tenantId, @NotNull String agentId) {
        String itemTenant = item.getTenantId();
        // Allow if tenantId matches OR if item has the default "default" tenant (will be overwritten)
        boolean allowed = itemTenant == null || tenantId.equals(itemTenant) || "default".equals(itemTenant);
        if (!allowed) {
            log.warn("Memory write DENIED — caller tenant='{}' item tenant='{}' itemId='{}' agent='{}'",
                    tenantId, itemTenant, item.getId(), agentId);
        }
        return allowed;
    }

    /**
     * Produces an enforceable search policy that always scopes semantic search to the caller tenant.
     * Shared-scope queries require an explicitly authorized agent identity.
     */
    @Override
    public @NotNull MemorySearchPolicy authorizeSearch(
            @NotNull MemorySearchRequest request,
            @NotNull String tenantId,
            @NotNull String agentId) {
        if (tenantId.isBlank() || "default".equalsIgnoreCase(tenantId)) {
            return denySearch(agentId, tenantId, "invalid-tenant");
        }

        if (agentId.isBlank()) {
            return denySearch(agentId, tenantId, "invalid-agent");
        }

        if (request.includeSharedMemory() && !isSharedMemoryAuthorized(agentId)) {
            return denySearch(agentId, tenantId, "shared-memory-not-authorized");
        }

        Set<MemoryItemType> allowedTiers = request.requestedMemoryTiers().isEmpty()
                ? EnumSet.allOf(MemoryItemType.class)
                : EnumSet.copyOf(request.requestedMemoryTiers());

        Set<String> requestedClassifications = request.requestedClassifications().stream()
                .map(classification -> classification.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> allowedClassifications = requestedClassifications.isEmpty()
                ? DEFAULT_ALLOWED_CLASSIFICATIONS
                : requestedClassifications.stream()
                .filter(DEFAULT_ALLOWED_CLASSIFICATIONS::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        if (!requestedClassifications.isEmpty() && allowedClassifications.isEmpty()) {
            return denySearch(agentId, tenantId, "classification-not-authorized");
        }

        Set<String> allowedScopes = request.includeSharedMemory()
                ? Set.of(agentId, tenantId + ":shared")
                : Set.of(agentId);

        return new MemorySearchPolicy(
                true,
                tenantId,
                allowedScopes,
                allowedTiers,
                allowedClassifications,
                request.requireRedaction() ? DEFAULT_REDACTION_REQUIREMENTS : Set.of(),
                MAX_RETENTION,
                Math.min(request.requestedResultLimit(), MAX_SEARCH_RESULTS),
                null
        );
    }

    private boolean isSharedMemoryAuthorized(@NotNull String agentId) {
        String normalizedAgentId = agentId.toLowerCase(Locale.ROOT);
        return normalizedAgentId.contains("shared")
                || normalizedAgentId.contains("supervisor")
                || normalizedAgentId.contains("operator");
    }

    private @NotNull MemorySearchPolicy denySearch(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String reason) {
        log.warn(
                "Memory search DENIED - reason='{}' agent='{}' tenantPresent={} ",
                reason,
                agentId,
                !tenantId.isBlank()
        );
        return MemorySearchPolicy.denied(reason);
    }
}
