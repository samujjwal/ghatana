package com.ghatana.appplatform.config.merge;

import com.ghatana.appplatform.config.domain.ConfigEntry;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.domain.ConfigValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConfigMerger}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for priority-based config merging
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ConfigMerger Tests")
class ConfigMergerTest {

    private final ConfigMerger merger = new ConfigMerger();

    private ConfigEntry entry(String key, String value, ConfigHierarchyLevel level, String levelId) {
        return new ConfigEntry("payments", key, value, level, levelId, "payments");
    }

    @Test
    @DisplayName("returns empty map for empty input")
    void emptyInput() {
        Map<String, ConfigValue> result = merger.merge(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("single entry is returned as-is")
    void singleEntry() {
        ConfigEntry e = entry("max_limit", "\"10000\"", ConfigHierarchyLevel.GLOBAL, "global");
        Map<String, ConfigValue> result = merger.merge(List.of(e));

        assertThat(result).containsKey("max_limit");
        assertThat(result.get("max_limit").value()).isEqualTo("\"10000\"");
        assertThat(result.get("max_limit").resolvedFromLevel()).isEqualTo(ConfigHierarchyLevel.GLOBAL);
    }

    @Test
    @DisplayName("TENANT overrides GLOBAL for the same key")
    void tenantOverridesGlobal() {
        ConfigEntry global = entry("max_limit", "\"10000\"", ConfigHierarchyLevel.GLOBAL, "global");
        ConfigEntry tenant = entry("max_limit", "\"50000\"", ConfigHierarchyLevel.TENANT, "tenant-abc");

        Map<String, ConfigValue> result = merger.merge(List.of(global, tenant));

        assertThat(result.get("max_limit").value()).isEqualTo("\"50000\"");
        assertThat(result.get("max_limit").resolvedFromLevel()).isEqualTo(ConfigHierarchyLevel.TENANT);
        assertThat(result.get("max_limit").resolvedFromLevelId()).isEqualTo("tenant-abc");
    }

    @Test
    @DisplayName("USER overrides TENANT overrides JURISDICTION overrides GLOBAL")
    void fullHierarchyPrecedence() {
        List<ConfigEntry> entries = List.of(
            entry("fee_rate", "\"0.01\"", ConfigHierarchyLevel.GLOBAL, "global"),
            entry("fee_rate", "\"0.008\"", ConfigHierarchyLevel.JURISDICTION, "NP"),
            entry("fee_rate", "\"0.005\"", ConfigHierarchyLevel.TENANT, "tenant-abc"),
            entry("fee_rate", "\"0.002\"", ConfigHierarchyLevel.USER, "user-xyz")
        );

        Map<String, ConfigValue> result = merger.merge(entries);

        assertThat(result.get("fee_rate").value()).isEqualTo("\"0.002\"");
        assertThat(result.get("fee_rate").resolvedFromLevel()).isEqualTo(ConfigHierarchyLevel.USER);
    }

    @Test
    @DisplayName("SESSION is the highest priority")
    void sessionIsHighestPriority() {
        List<ConfigEntry> entries = List.of(
            entry("debug_mode", "false", ConfigHierarchyLevel.GLOBAL, "global"),
            entry("debug_mode", "true", ConfigHierarchyLevel.SESSION, "session-999")
        );

        Map<String, ConfigValue> result = merger.merge(entries);

        assertThat(result.get("debug_mode").value()).isEqualTo("true");
        assertThat(result.get("debug_mode").resolvedFromLevel()).isEqualTo(ConfigHierarchyLevel.SESSION);
    }

    @Test
    @DisplayName("keys unique to a level are all present in merged result")
    void uniqueKeysFromMultipleLevels() {
        List<ConfigEntry> entries = List.of(
            entry("max_limit", "\"10000\"", ConfigHierarchyLevel.GLOBAL, "global"),
            entry("currency",  "\"NPR\"",   ConfigHierarchyLevel.JURISDICTION, "NP"),
            entry("fee_rate",  "\"0.005\"", ConfigHierarchyLevel.TENANT, "tenant-abc")
        );

        Map<String, ConfigValue> result = merger.merge(entries);

        assertThat(result).containsKeys("max_limit", "currency", "fee_rate");
    }

    @Test
    @DisplayName("merge is stable with entries supplied in reverse order")
    void stablesWithReverseInput() {
        ConfigEntry global = entry("timeout", "\"30\"", ConfigHierarchyLevel.GLOBAL, "global");
        ConfigEntry tenant = entry("timeout", "\"60\"", ConfigHierarchyLevel.TENANT, "tenant-abc");

        // Supply tenant first (high priority first), global second
        Map<String, ConfigValue> result = merger.merge(List.of(tenant, global));

        // TENANT still wins regardless of input order
        assertThat(result.get("timeout").value()).isEqualTo("\"60\"");
        assertThat(result.get("timeout").resolvedFromLevel()).isEqualTo(ConfigHierarchyLevel.TENANT);
    }
}
