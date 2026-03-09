package com.ghatana.pipeline.registry.repository;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.pipeline.registry.model.Pattern;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of PatternRepository.
 *
 * <p>
 * <b>Purpose</b><br>
 * Development and testing repository that stores patterns in-memory using
 * concurrent hash maps. Suitable for single-node scenarios or testing.
 *
 * <p>
 * <b>Storage</b><br>
 * Patterns stored with composite key: "{tenantId}:{patternId}" Status index:
 * "{tenantId}:{status}" → Set of pattern IDs
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Uses ConcurrentHashMap for thread-safe access across eventloop threads.
 *
 * <p>
 * <b>Production Note</b><br>
 * Replace with database-backed repository (PostgreSQL + JDBI) for production.
 *
 * @doc.type class
 * @doc.purpose In-memory pattern repository implementation
 * @doc.layer product
 * @doc.pattern Repository Implementation
 */
@Slf4j
public class InMemoryPatternRepository implements PatternRepository {

    private final Map<String, Pattern> patterns = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> statusIndex = new ConcurrentHashMap<>();

    /**
     * Generates composite key for tenant-scoped pattern storage.
     *
     * @param tenantId the tenant ID
     * @param patternId the pattern ID
     * @return composite key string
     */
    private String compositeKey(TenantId tenantId, String patternId) {
        return tenantId.toString() + ":" + patternId;
    }

    @Override
    public Promise<Pattern> save(Pattern pattern) {
        String key = compositeKey(pattern.getTenantId(), pattern.getId());
        patterns.put(key, pattern);

        // Update status index
        String statusKey = pattern.getTenantId().toString() + ":" + pattern.getStatus();
        statusIndex.computeIfAbsent(statusKey, k -> new ConcurrentHashMap<>())
                .put(pattern.getId(), 1);

        log.debug("Pattern saved: {} ({})", pattern.getName(), pattern.getId());
        return Promise.of(pattern);
    }

    @Override
    public Promise<Pattern> update(Pattern pattern) {
        String key = compositeKey(pattern.getTenantId(), pattern.getId());

        // Get old status to update index
        Pattern oldPattern = patterns.get(key);
        if (oldPattern != null && !oldPattern.getStatus().equals(pattern.getStatus())) {
            String oldStatusKey = pattern.getTenantId().toString() + ":" + oldPattern.getStatus();
            statusIndex.getOrDefault(oldStatusKey, new ConcurrentHashMap<>())
                    .remove(pattern.getId());
        }

        // Save updated pattern
        patterns.put(key, pattern);

        // Update status index with new status
        String statusKey = pattern.getTenantId().toString() + ":" + pattern.getStatus();
        statusIndex.computeIfAbsent(statusKey, k -> new ConcurrentHashMap<>())
                .put(pattern.getId(), 1);

        log.debug("Pattern updated: {} ({})", pattern.getName(), pattern.getId());
        return Promise.of(pattern);
    }

    @Override
    public Promise<Void> delete(String id) {
        // Find and remove pattern from all maps
        patterns.entrySet()
                .stream()
                .filter(e -> e.getValue().getId().equals(id))
                .findFirst()
                .ifPresent(e -> {
                    Pattern pattern = e.getValue();
                    patterns.remove(e.getKey());

                    // Remove from status index
                    String statusKey = pattern.getTenantId().toString() + ":" + pattern.getStatus();
                    statusIndex.getOrDefault(statusKey, new ConcurrentHashMap<>())
                            .remove(pattern.getId());

                    log.debug("Pattern deleted: {} ({})", pattern.getName(), id);
                });

        return Promise.complete();
    }

    @Override
    public Promise<Optional<Pattern>> findByIdAndTenant(String id, TenantId tenantId) {
        String key = compositeKey(tenantId, id);
        Optional<Pattern> result = Optional.ofNullable(patterns.get(key));

        log.debug("Pattern lookup: {} - {}", id, result.isPresent() ? "found" : "not found");
        return Promise.of(result);
    }

    @Override
    public Promise<List<Pattern>> findByTenant(TenantId tenantId, String status) {
        List<Pattern> results = patterns.values()
                .stream()
                .filter(p -> p.getTenantId().equals(tenantId))
                .filter(p -> status == null || status.equals(p.getStatus()))
                .collect(Collectors.toList());

        log.debug("Pattern query: tenant={}, status={}, count={}",
                tenantId.toString(), status, results.size());
        return Promise.of(results);
    }
}
