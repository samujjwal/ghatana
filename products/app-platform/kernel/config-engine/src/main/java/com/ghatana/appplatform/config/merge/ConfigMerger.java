package com.ghatana.appplatform.config.merge;

import com.ghatana.appplatform.config.domain.ConfigEntry;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.domain.ConfigValue;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges a list of {@link ConfigEntry} values from multiple hierarchy levels into
 * a single resolved map, where higher-priority levels win.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Sort entries by {@link ConfigHierarchyLevel#priority()} ascending (GLOBAL first).
 *   <li>Iterate, placing each entry into the result map — later (higher-priority) entries
 *       overwrite earlier ones for the same key.
 * </ol>
 *
 * <p>This produces the simplest correct merge: the value with the highest-priority
 * level always wins. Deep JSON merge of object values is intentionally deferred to
 * a future story when product requirements demand it.
 *
 * @doc.type class
 * @doc.purpose Priority-based merger of multi-level config entries
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ConfigMerger {

    /**
     * Merges a list of entries into a resolved config map.
     *
     * <p>Entries may be supplied in any order; they are sorted internally.
     * The returned map is ordered by key (insertion order from the merge pass).
     *
     * @param entries entries from all relevant hierarchy levels
     * @return a map from config key to its resolved {@link ConfigValue}
     */
    public Map<String, ConfigValue> merge(List<ConfigEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }

        // Sort ascending by priority (lowest first) so higher-priority entries
        // overwrite lower-priority ones in the map
        List<ConfigEntry> sorted = entries.stream()
            .sorted(Comparator.comparingInt(e -> e.level().priority()))
            .toList();

        Map<String, ConfigValue> result = new LinkedHashMap<>();
        for (ConfigEntry entry : sorted) {
            result.put(entry.key(), new ConfigValue(
                entry.key(),
                entry.value(),
                entry.level(),
                entry.levelId()));
        }

        return result;
    }
}
