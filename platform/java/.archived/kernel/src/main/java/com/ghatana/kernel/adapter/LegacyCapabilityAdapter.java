package com.ghatana.kernel.adapter;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.annotation.KernelInternal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Compatibility adapter for legacy capability references.
 *
 * <p>This adapter provides backward compatibility during the migration from
 * duplicate capability abstractions to the canonical {@link KernelCapability} type.
 * It will be removed in a future version once all products have migrated.</p>
 *
 * <p><b>Deprecation Notice:</b> This adapter is marked for removal in version 2.0.0.
 * All products should migrate to using {@code com.ghatana.kernel.descriptor.KernelCapability}
 * directly.</p>
 *
 * @doc.type class
 * @doc.purpose Backward compatibility adapter for legacy capability references
 * @doc.layer adapter
 * @doc.pattern Adapter
 * @author Ghatana Kernel Team
 * @since 1.0.0
 * @deprecated Use {@link KernelCapability} directly. This adapter will be removed in 2.0.0.
 */
@Deprecated(since = "1.0.0", forRemoval = true)
@KernelInternal("Migration adapter only - use canonical KernelCapability")
public class LegacyCapabilityAdapter {

    private final Map<String, KernelCapability> legacyMappings;

    public LegacyCapabilityAdapter() {
        this.legacyMappings = new HashMap<>();
        initializeLegacyMappings();
    }

    /**
     * Initialize mappings from legacy capability IDs to canonical capabilities.
     */
    private void initializeLegacyMappings() {
        // Map legacy capability IDs to canonical capabilities
        legacyMappings.put("legacy.data.storage", KernelCapability.Core.DATA_STORAGE);
        legacyMappings.put("legacy.user.auth", KernelCapability.Core.USER_AUTHENTICATION);
        legacyMappings.put("legacy.api.framework", KernelCapability.Core.API_FRAMEWORK);
        legacyMappings.put("legacy.workflow", KernelCapability.Core.WORKFLOW_ENGINE);
        legacyMappings.put("legacy.event.processing", KernelCapability.Core.EVENT_PROCESSING);
        legacyMappings.put("legacy.ai.ml", KernelCapability.Core.AI_ML_FRAMEWORK);
        legacyMappings.put("legacy.observability", KernelCapability.Core.OBSERVABILITY_FRAMEWORK);
        legacyMappings.put("legacy.security", KernelCapability.Core.SECURITY_FRAMEWORK);
    }

    /**
     * Converts a legacy capability ID to the canonical capability.
     *
     * @param legacyCapabilityId the legacy capability identifier
     * @return optional containing the canonical capability if mapping exists
     */
    public Optional<KernelCapability> toCanonical(String legacyCapabilityId) {
        return Optional.ofNullable(legacyMappings.get(legacyCapabilityId));
    }

    /**
     * Checks if a legacy capability ID has a canonical mapping.
     *
     * @param legacyCapabilityId the legacy capability identifier
     * @return true if mapping exists
     */
    public boolean hasMapping(String legacyCapabilityId) {
        return legacyMappings.containsKey(legacyCapabilityId);
    }

    /**
     * Gets all legacy capability IDs that have mappings.
     *
     * @return map of legacy IDs to canonical capabilities
     */
    public Map<String, KernelCapability> getAllMappings() {
        return new HashMap<>(legacyMappings);
    }

    /**
     * Registers a custom legacy mapping.
     *
     * <p>This allows products to register their own legacy capability mappings
     * during migration.</p>
     *
     * @param legacyId the legacy capability identifier
     * @param canonical the canonical capability
     */
    public void registerMapping(String legacyId, KernelCapability canonical) {
        legacyMappings.put(legacyId, canonical);
    }
}
