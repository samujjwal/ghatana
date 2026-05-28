package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.yappc.api.AdminFeatureFlagController;
import com.ghatana.yappc.api.PhasePacket;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class PhaseFeatureFlagProvider {

    private static final Logger log = LoggerFactory.getLogger(PhaseFeatureFlagProvider.class);

    private final DataCloudClient dataCloudClient;

    PhaseFeatureFlagProvider(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient");
    }

    Promise<Map<String, Object>> enrichProjectStateWithTenantFlags(String tenantId, Map<String, Object> projectState) {
        return queryEnabledTenantFeatureFlags(tenantId)
                .map(result -> {
                    Map<String, Object> state = new HashMap<>(projectState);
                    List<String> tenantFlags = result.flags();
                    if (!tenantFlags.isEmpty()) {
                        LinkedHashSet<String> merged = new LinkedHashSet<>();
                        addFlagValues(merged, state.get("enabledPhaseFlags"));
                        addFlagValues(merged, state.get("featureFlags"));
                        addFlagValues(merged, state.get("entitlements"));
                        addFlagValues(merged, tenantFlags);
                        state.put("featureFlags", List.copyOf(merged));
                        state.put("featureFlagsSource", "project+tenant");
                    }
                    if (result.degraded()) {
                        state.put("featureFlagsDegraded", true);
                        state.put("featureFlagsDegradedReason", result.degradedReason());
                    }
                    return Map.copyOf(state);
                });
    }

    Set<String> determineEnabledFlags(Map<String, Object> projectState, PhasePacket.TenantTier tenantTier) {
        try {
            LinkedHashSet<String> flags = new LinkedHashSet<>();
            addFlagValues(flags, projectState.get("enabledPhaseFlags"));
            addFlagValues(flags, projectState.get("featureFlags"));
            addFlagValues(flags, projectState.get("entitlements"));

            if (tenantTier == PhasePacket.TenantTier.ENTERPRISE) {
                flags.add("phase.report.export");
                flags.add("phase.governance.configure");
            }
            if (tenantTier == PhasePacket.TenantTier.PRO || tenantTier == PhasePacket.TenantTier.ENTERPRISE) {
                flags.add("phase.advance");
            }
            return Set.copyOf(flags);
        } catch (Exception exception) {
            log.error("Error determining enabled flags", exception);
            return Set.of();
        }
    }

        private Promise<TenantFeatureFlagsResult> queryEnabledTenantFeatureFlags(String tenantId) {
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("enabled", true))
                .limit(500)
                .build();
        return dataCloudClient.query(tenantId, AdminFeatureFlagController.FLAG_COLLECTION, query)
                .map(records -> records.stream()
                        .map(record -> record.data().get("key"))
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(flag -> !flag.isBlank())
                        .distinct()
                .toList())
            .map(flags -> new TenantFeatureFlagsResult(flags, false, null))
                .then((flags, error) -> {
                    if (error == null) {
                        return Promise.of(flags);
                    }
                    log.error("DataCloud query failed for tenant feature flags: tenantId={}", tenantId, error);
                return Promise.of(new TenantFeatureFlagsResult(
                    List.of(),
                    true,
                    "FEATURE_FLAG_DEPENDENCY_UNAVAILABLE"
                ));
                });
    }

        private record TenantFeatureFlagsResult(
            List<String> flags,
            boolean degraded,
            String degradedReason
        ) {}

    private static void addFlagValues(Set<String> flags, Object value) {
        if (value instanceof String text) {
            for (String flag : text.split(",")) {
                if (!flag.isBlank()) {
                    flags.add(flag.trim());
                }
            }
            return;
        }
        if (value instanceof Collection<?> values) {
            values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::trim)
                    .filter(flag -> !flag.isBlank())
                    .forEach(flags::add);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, enabled) -> {
                if (key instanceof String flag && Boolean.TRUE.equals(enabled)) {
                    flags.add(flag);
                }
            });
        }
    }
}