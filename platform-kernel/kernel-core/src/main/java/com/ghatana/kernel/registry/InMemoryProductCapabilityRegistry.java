package com.ghatana.kernel.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ProductCapabilityRegistry}.
 *
 * <p>Thread-safe in-memory storage for product capabilities and tenant/workspace
 * capability assignments. Suitable for development and testing. Production use
 * should use a database-backed implementation.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory product capability registry implementation (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Registry
 */
public final class InMemoryProductCapabilityRegistry implements ProductCapabilityRegistry {

    private final Map<String, String> capabilityDescriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> tenantCapabilities = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<String>>> workspaceCapabilities = new ConcurrentHashMap<>();

    private static String workspaceKey(String tenantId, String workspaceId) {
        return tenantId + ":" + workspaceId;
    }

    @Override
    public void registerCapability(String capabilityKey, String description) {
        Objects.requireNonNull(capabilityKey, "capabilityKey must not be null");
        Objects.requireNonNull(description, "description must not be null");
        if (capabilityKey.isBlank()) {
            throw new IllegalArgumentException("capabilityKey cannot be blank");
        }
        capabilityDescriptions.put(capabilityKey, description);
    }

    @Override
    public boolean isCapabilityEnabled(String tenantId, String workspaceId, String capabilityKey) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(capabilityKey, "capabilityKey must not be null");

        if (!capabilityDescriptions.containsKey(capabilityKey)) {
            return false;
        }

        Map<String, Set<String>> workspaceMap = workspaceCapabilities.get(tenantId);
        if (workspaceMap != null) {
            Set<String> enabled = workspaceMap.get(workspaceId);
            if (enabled != null && enabled.contains(capabilityKey)) {
                return true;
            }
        }

        // Fallback to tenant-level capabilities
        Set<String> tenantEnabled = tenantCapabilities.get(tenantId);
        return tenantEnabled != null && tenantEnabled.contains(capabilityKey);
    }

    @Override
    public void enableCapability(String tenantId, String workspaceId, String capabilityKey) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(capabilityKey, "capabilityKey must not be null");

        if (!capabilityDescriptions.containsKey(capabilityKey)) {
            throw new IllegalArgumentException("Capability not registered: " + capabilityKey);
        }

        String key = workspaceKey(tenantId, workspaceId);
        workspaceCapabilities.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(workspaceId, k -> ConcurrentHashMap.newKeySet())
            .add(capabilityKey);
    }

    @Override
    public void disableCapability(String tenantId, String workspaceId, String capabilityKey) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(capabilityKey, "capabilityKey must not be null");

        String key = workspaceKey(tenantId, workspaceId);
        Map<String, Set<String>> workspaceMap = workspaceCapabilities.get(tenantId);
        if (workspaceMap != null) {
            Set<String> enabled = workspaceMap.get(workspaceId);
            if (enabled != null) {
                enabled.remove(capabilityKey);
            }
        }
    }

    @Override
    public Set<String> getRegisteredCapabilities() {
        return Collections.unmodifiableSet(new HashSet<>(capabilityDescriptions.keySet()));
    }

    @Override
    public Set<String> getEnabledCapabilities(String tenantId, String workspaceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");

        Set<String> result = new HashSet<>();

        // Add workspace-level capabilities
        Map<String, Set<String>> workspaceMap = workspaceCapabilities.get(tenantId);
        if (workspaceMap != null) {
            Set<String> workspaceEnabled = workspaceMap.get(workspaceId);
            if (workspaceEnabled != null) {
                result.addAll(workspaceEnabled);
            }
        }

        // Add tenant-level capabilities
        Set<String> tenantEnabled = tenantCapabilities.get(tenantId);
        if (tenantEnabled != null) {
            result.addAll(tenantEnabled);
        }

        return Collections.unmodifiableSet(result);
    }

    @Override
    public boolean isCapabilityRegistered(String capabilityKey) {
        Objects.requireNonNull(capabilityKey, "capabilityKey must not be null");
        return capabilityDescriptions.containsKey(capabilityKey);
    }

    @Override
    public String getCapabilityDescription(String capabilityKey) {
        Objects.requireNonNull(capabilityKey, "capabilityKey must not be null");
        return capabilityDescriptions.getOrDefault(capabilityKey, "");
    }

    /**
     * Enable a capability at the tenant level (applies to all workspaces in tenant).
     *
     * @param tenantId tenant identifier
     * @param capabilityKey capability key to enable
     */
    public void enableTenantCapability(String tenantId, String capabilityKey) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(capabilityKey, "capabilityKey must not be null");

        if (!capabilityDescriptions.containsKey(capabilityKey)) {
            throw new IllegalArgumentException("Capability not registered: " + capabilityKey);
        }

        tenantCapabilities.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet())
            .add(capabilityKey);
    }

    /**
     * Disable a capability at the tenant level.
     *
     * @param tenantId tenant identifier
     * @param capabilityKey capability key to disable
     */
    public void disableTenantCapability(String tenantId, String capabilityKey) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(capabilityKey, "capabilityKey must not be null");

        Set<String> tenantEnabled = tenantCapabilities.get(tenantId);
        if (tenantEnabled != null) {
            tenantEnabled.remove(capabilityKey);
        }
    }
}
