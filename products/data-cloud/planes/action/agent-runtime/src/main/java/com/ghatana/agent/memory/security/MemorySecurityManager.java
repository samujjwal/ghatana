package com.ghatana.agent.memory.security;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Controls access to memory operations based on data classification,
 * tenant isolation, and user permissions.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Tenant isolation — cannot read/write another tenant's memory</li>
 *   <li>Data classification — PII/PHI access requires elevated permissions</li>
 *   <li>Agent scope — agents can only access their own memory (unless shared)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Memory access control
 * @doc.layer agent-memory
  * @doc.pattern Manager
*/
public interface MemorySecurityManager {

    /**
     * Checks if the current context is authorized to read the given item.
     *
     * @param item The memory item to check
     * @param tenantId The tenant context
     * @param agentId The requesting agent
     * @return true if access is allowed
     */
    boolean canRead(@NotNull MemoryItem item, @NotNull String tenantId, @NotNull String agentId);

    /**
     * Checks if the current context is authorized to write the given item.
     *
     * @param item The memory item to write
     * @param tenantId The tenant context
     * @param agentId The requesting agent
     * @return true if write is allowed
     */
    boolean canWrite(@NotNull MemoryItem item, @NotNull String tenantId, @NotNull String agentId);

    /**
     * Authorizes semantic search and returns an enforceable search scope.
     *
     * @param request The requested search scope
     * @param tenantId The tenant context
     * @param agentId The requesting agent
     * @return authorized policy, or a denied policy with a reason
     */
    @NotNull MemorySearchPolicy authorizeSearch(
        @NotNull MemorySearchRequest request,
        @NotNull String tenantId,
        @NotNull String agentId);

    /**
     * Legacy boolean adapter. Prefer {@link #authorizeSearch(MemorySearchRequest, String, String)}
     * so callers can enforce the returned search scope instead of relying on a yes/no check.
     */
    @Deprecated(forRemoval = false)
    default boolean canSearch(@NotNull String tenantId, @NotNull String agentId) {
      return authorizeSearch(MemorySearchRequest.defaultRequest(), tenantId, agentId).allowed();
    }

    /**
     * Requested semantic search scope.
     */
    record MemorySearchRequest(
        @Nullable Set<MemoryItemType> requestedMemoryTiers,
        @Nullable Set<String> requestedClassifications,
        boolean includeSharedMemory,
        boolean requireRedaction,
        int requestedResultLimit,
        @Nullable Instant startTime,
        @Nullable Instant endTime
    ) {
      public MemorySearchRequest {
        requestedMemoryTiers = requestedMemoryTiers == null
            ? EnumSet.allOf(MemoryItemType.class)
            : EnumSet.copyOf(requestedMemoryTiers);
        requestedClassifications = requestedClassifications == null
            ? Set.of()
            : Set.copyOf(requestedClassifications);
        if (requestedResultLimit <= 0) {
          throw new IllegalArgumentException("requestedResultLimit must be positive");
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
          throw new IllegalArgumentException("startTime must be before endTime");
        }
      }

      @NotNull
      public static MemorySearchRequest defaultRequest() {
        return new MemorySearchRequest(
            EnumSet.allOf(MemoryItemType.class),
            Set.of(),
            false,
            true,
            25,
            null,
            null
        );
      }
    }

    /**
     * Enforceable search policy produced by authorization.
     */
    record MemorySearchPolicy(
        boolean allowed,
        @NotNull String tenantFilter,
        @NotNull Set<String> allowedAgentScopes,
        @NotNull Set<MemoryItemType> allowedMemoryTiers,
        @NotNull Set<String> allowedClassifications,
        @NotNull Set<String> redactionRequirements,
        @NotNull Duration maxRetention,
        int maxResultLimit,
        @Nullable String denialReason
    ) {
      public MemorySearchPolicy {
        Objects.requireNonNull(tenantFilter, "tenantFilter must not be null");
        allowedAgentScopes = Set.copyOf(allowedAgentScopes);
        allowedMemoryTiers = allowedMemoryTiers.isEmpty()
            ? EnumSet.noneOf(MemoryItemType.class)
            : EnumSet.copyOf(allowedMemoryTiers);
        allowedClassifications = Set.copyOf(allowedClassifications);
        redactionRequirements = Set.copyOf(redactionRequirements);
        Objects.requireNonNull(maxRetention, "maxRetention must not be null");
        if (maxResultLimit <= 0) {
          throw new IllegalArgumentException("maxResultLimit must be positive");
        }
        if (allowed && tenantFilter.isBlank()) {
          throw new IllegalArgumentException("allowed policy must include tenantFilter");
        }
      }

      @NotNull
      public static MemorySearchPolicy denied(@NotNull String reason) {
        return new MemorySearchPolicy(
            false,
            "",
            Set.of(),
            EnumSet.noneOf(MemoryItemType.class),
            Set.of(),
            Set.of(),
            Duration.ZERO,
            1,
            reason
        );
      }
    }
}
