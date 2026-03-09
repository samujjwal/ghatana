/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.pattern;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default in-memory implementation of the PatternCatalog.
 *
 * <p>Provides full pattern management functionality with thread-safe
 * concurrent access. In production, this would be backed by a
 * persistent store.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Thread-safe concurrent storage</li>
 *   <li>Multi-tenant isolation</li>
 *   <li>Version history tracking</li>
 *   <li>Flexible search capabilities</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default pattern catalog implementation
 * @doc.layer core
 * @doc.pattern Repository, Registry
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public class DefaultPatternCatalog implements PatternCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPatternCatalog.class);

    // Storage: tenantId -> patternId -> PatternRecord
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, PatternRecord>> storage;

    // Version history: tenantId -> patternId -> List<PatternRecord>
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, List<PatternRecord>>> history;

    /**
     * Creates a new pattern catalog.
     */
    public DefaultPatternCatalog() {
        this.storage = new ConcurrentHashMap<>();
        this.history = new ConcurrentHashMap<>();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<PatternRecord> register(PatternRecord pattern) {
        return Promise.of(pattern)
                .map(p -> {
                    String tenantId = p.getTenantId() != null ? p.getTenantId() : "default";
                    String patternId = p.getId() != null ? p.getId() : generateId();

                    PatternRecord toStore = p.toBuilder()
                            .id(patternId)
                            .tenantId(tenantId)
                            .version(PatternVersion.initial(p.getObservationCount()))
                            .status(PatternRecord.PatternStatus.DRAFT)
                            .discoveredAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    getTenantStorage(tenantId).put(patternId, toStore);
                    addToHistory(tenantId, patternId, toStore);

                    LOG.info("Registered pattern {} for tenant {}", patternId, tenantId);
                    return toStore;
                });
    }

    @Override
    public Promise<PatternRecord> update(PatternRecord pattern, String changelog) {
        return Promise.of(pattern)
                .map(p -> {
                    String tenantId = p.getTenantId() != null ? p.getTenantId() : "default";
                    String patternId = p.getId();

                    PatternRecord existing = getTenantStorage(tenantId).get(patternId);
                    if (existing == null) {
                        throw new IllegalArgumentException("Pattern not found: " + patternId);
                    }

                    // Determine version increment based on changes
                    PatternVersion newVersion = determineNewVersion(existing, p, changelog);

                    PatternRecord updated = p.toBuilder()
                            .version(newVersion)
                            .updatedAt(Instant.now())
                            .build();

                    getTenantStorage(tenantId).put(patternId, updated);
                    addToHistory(tenantId, patternId, updated);

                    LOG.info("Updated pattern {} to version {}", patternId, newVersion.getVersionString());
                    return updated;
                });
    }

    @Override
    public Promise<Optional<PatternRecord>> get(String patternId, String tenantId) {
        return Promise.of(Optional.ofNullable(
                getTenantStorage(tenantId).get(patternId)));
    }

    @Override
    public Promise<Optional<PatternRecord>> getVersion(String patternId, String version, String tenantId) {
        return Promise.of(
                getTenantHistory(tenantId)
                        .getOrDefault(patternId, List.of())
                        .stream()
                        .filter(p -> p.getVersion().getVersionString().equals(version))
                        .findFirst());
    }

    @Override
    public Promise<Boolean> delete(String patternId, String tenantId) {
        return archive(patternId, tenantId).map(p -> true);
    }

    @Override
    public Promise<Boolean> purge(String patternId, String tenantId) {
        return Promise.of(patternId)
                .map(id -> {
                    PatternRecord removed = getTenantStorage(tenantId).remove(id);
                    getTenantHistory(tenantId).remove(id);

                    if (removed != null) {
                        LOG.info("Purged pattern {} from tenant {}", id, tenantId);
                    }
                    return removed != null;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Search Operations
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<PatternSearchResult> search(PatternQuery query) {
        return Promise.of(query)
                .map(q -> {
                    long startTime = System.currentTimeMillis();
                    String tenantId = q.getTenantId() != null ? q.getTenantId() : "default";

                    Stream<PatternRecord> stream = getTenantStorage(tenantId).values().stream();

                    // Apply filters
                    if (q.getTextQuery() != null && !q.getTextQuery().isBlank()) {
                        String searchText = q.getTextQuery().toLowerCase();
                        stream = stream.filter(p ->
                                (p.getName() != null && p.getName().toLowerCase().contains(searchText)) ||
                                        (p.getDescription() != null && p.getDescription().toLowerCase().contains(searchText)));
                    }

                    if (!q.getTypes().isEmpty()) {
                        stream = stream.filter(p -> q.getTypes().contains(p.getType()));
                    }

                    if (!q.getTags().isEmpty()) {
                        stream = stream.filter(p ->
                                p.getTags().stream().anyMatch(q.getTags()::contains));
                    }

                    if (!q.getStatuses().isEmpty()) {
                        stream = stream.filter(p -> q.getStatuses().contains(p.getStatus()));
                    }

                    if (q.getMinConfidence() > 0) {
                        stream = stream.filter(p -> p.getConfidence() >= q.getMinConfidence());
                    }

                    if (q.getMinPrecision() > 0) {
                        stream = stream.filter(p -> p.getPrecision() >= q.getMinPrecision());
                    }

                    if (q.getCreatedAfter() != null) {
                        stream = stream.filter(p -> p.getDiscoveredAt().isAfter(q.getCreatedAfter()));
                    }

                    if (q.getCreatedBefore() != null) {
                        stream = stream.filter(p -> p.getDiscoveredAt().isBefore(q.getCreatedBefore()));
                    }

                    // Sort
                    Comparator<PatternRecord> comparator = getComparator(q.getSortBy());
                    if (!q.isAscending()) {
                        comparator = comparator.reversed();
                    }

                    List<PatternRecord> allMatches = stream.sorted(comparator).collect(Collectors.toList());
                    int totalCount = allMatches.size();

                    // Paginate
                    List<PatternRecord> page = allMatches.stream()
                            .skip(q.getOffset())
                            .limit(q.getLimit())
                            .collect(Collectors.toList());

                    long searchTime = System.currentTimeMillis() - startTime;

                    return PatternSearchResult.builder()
                            .patterns(page)
                            .totalCount(totalCount)
                            .searchTimeMs(searchTime)
                            .hasMore(q.getOffset() + page.size() < totalCount)
                            .build();
                });
    }

    @Override
    public Promise<List<PatternRecord>> findByType(PatternType type, String tenantId) {
        return Promise.of(
                getTenantStorage(tenantId).values().stream()
                        .filter(p -> p.getType() == type)
                        .filter(p -> p.getStatus() == PatternRecord.PatternStatus.ACTIVE)
                        .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<PatternRecord>> findByTags(List<String> tags, String tenantId) {
        return Promise.of(
                getTenantStorage(tenantId).values().stream()
                        .filter(p -> p.getTags().stream().anyMatch(tags::contains))
                        .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<PatternRecord>> findSimilar(float[] embedding, int k, String tenantId) {
        return Promise.of(embedding)
                .map(emb ->
                        getTenantStorage(tenantId).values().stream()
                                .filter(p -> p.getEmbedding() != null)
                                .map(p -> new ScoredPattern(p, cosineSimilarity(emb, p.getEmbedding())))
                                .sorted(Comparator.comparingDouble(ScoredPattern::score).reversed())
                                .limit(k)
                                .map(ScoredPattern::pattern)
                                .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<PatternRecord>> listActive(String tenantId, int limit) {
        return Promise.of(
                getTenantStorage(tenantId).values().stream()
                        .filter(p -> p.getStatus() == PatternRecord.PatternStatus.ACTIVE)
                        .sorted(Comparator.comparingDouble(PatternRecord::getConfidence).reversed())
                        .limit(limit)
                        .collect(Collectors.toList()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle Management
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<PatternRecord> activate(String patternId, String tenantId) {
        return updateStatus(patternId, tenantId, PatternRecord.PatternStatus.ACTIVE);
    }

    @Override
    public Promise<PatternRecord> deprecate(String patternId, String reason, String tenantId) {
        return get(patternId, tenantId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Pattern not found: " + patternId));
                    }

                    PatternRecord updated = opt.get().toBuilder()
                            .status(PatternRecord.PatternStatus.DEPRECATED)
                            .updatedAt(Instant.now())
                            .metadata(Map.of("deprecationReason", reason))
                            .build();

                    getTenantStorage(tenantId).put(patternId, updated);
                    LOG.info("Deprecated pattern {}: {}", patternId, reason);
                    return Promise.of(updated);
                });
    }

    @Override
    public Promise<PatternRecord> archive(String patternId, String tenantId) {
        return updateStatus(patternId, tenantId, PatternRecord.PatternStatus.ARCHIVED);
    }

    @Override
    public Promise<List<PatternRecord>> findNeedingRevalidation(String tenantId) {
        return Promise.of(
                getTenantStorage(tenantId).values().stream()
                        .filter(p -> p.getStatus() == PatternRecord.PatternStatus.ACTIVE)
                        .filter(PatternRecord::needsRevalidation)
                        .collect(Collectors.toList()));
    }

    @Override
    public Promise<Integer> expireStale(String tenantId) {
        return Promise.of(tenantId)
                .map(tid -> {
                    int expired = 0;
                    for (PatternRecord pattern : getTenantStorage(tid).values()) {
                        if (pattern.getStatus() == PatternRecord.PatternStatus.ACTIVE
                                && pattern.needsRevalidation()) {

                            PatternRecord updated = pattern.toBuilder()
                                    .expired(true)
                                    .status(PatternRecord.PatternStatus.DEPRECATED)
                                    .updatedAt(Instant.now())
                                    .build();

                            getTenantStorage(tid).put(pattern.getId(), updated);
                            expired++;
                        }
                    }

                    if (expired > 0) {
                        LOG.info("Expired {} stale patterns for tenant {}", expired, tid);
                    }
                    return expired;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Statistics
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<CatalogStats> getStats(String tenantId) {
        return Promise.of(tenantId)
                .map(tid -> {
                    List<PatternRecord> patterns = new ArrayList<>(getTenantStorage(tid).values());

                    Map<PatternType, Integer> byType = new EnumMap<>(PatternType.class);
                    int active = 0;
                    int deprecated = 0;
                    float totalConfidence = 0;
                    float totalPrecision = 0;
                    int needingRevalidation = 0;

                    for (PatternRecord p : patterns) {
                        byType.merge(p.getType(), 1, Integer::sum);

                        if (p.getStatus() == PatternRecord.PatternStatus.ACTIVE) active++;
                        if (p.getStatus() == PatternRecord.PatternStatus.DEPRECATED) deprecated++;

                        totalConfidence += p.getConfidence();
                        totalPrecision += p.getPrecision();

                        if (p.needsRevalidation()) needingRevalidation++;
                    }

                    return CatalogStats.builder()
                            .totalPatterns(patterns.size())
                            .activePatterns(active)
                            .deprecatedPatterns(deprecated)
                            .byType(byType)
                            .avgConfidence(patterns.isEmpty() ? 0 : totalConfidence / patterns.size())
                            .avgPrecision(patterns.isEmpty() ? 0 : totalPrecision / patterns.size())
                            .needingRevalidation(needingRevalidation)
                            .build();
                });
    }

    @Override
    public Promise<PatternMetrics> getMetrics(String patternId, String tenantId) {
        return get(patternId, tenantId)
                .map(opt -> opt.map(p -> PatternMetrics.builder()
                                .patternId(patternId)
                                .totalMatches(p.getObservationCount())
                                .truePositives(p.getMatchSuccessCount())
                                .falsePositives(p.getFalsePositiveCount())
                                .falseNegatives(p.getFalseNegativeCount())
                                .precision(p.getPrecision())
                                .recall(p.getRecall())
                                .f1Score(p.getF1Score())
                                .avgMatchScore(p.getConfidence())
                                .lastMatchedAt(p.getLastMatchedAt())
                                .matchesPerHour(0f) // Would need time-series data
                                .build())
                        .orElse(null));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private ConcurrentHashMap<String, PatternRecord> getTenantStorage(String tenantId) {
        String key = tenantId != null ? tenantId : "default";
        return storage.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
    }

    private ConcurrentHashMap<String, List<PatternRecord>> getTenantHistory(String tenantId) {
        String key = tenantId != null ? tenantId : "default";
        return history.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
    }

    private void addToHistory(String tenantId, String patternId, PatternRecord pattern) {
        getTenantHistory(tenantId)
                .computeIfAbsent(patternId, k -> new ArrayList<>())
                .add(pattern);
    }

    private String generateId() {
        return "pat-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private PatternVersion determineNewVersion(PatternRecord existing, PatternRecord updated, String changelog) {
        PatternVersion current = existing.getVersion();
        float confidenceDelta = updated.getConfidence() - existing.getConfidence();

        // Determine if this is a major, minor, or patch change
        // Major: type changed or significant signature change
        if (existing.getType() != updated.getType()) {
            return current.incrementMajor(changelog, updated.getObservationCount(), confidenceDelta);
        }

        // Minor: conditions changed or significant stat improvement
        if (!existing.getConditions().equals(updated.getConditions())
                || Math.abs(confidenceDelta) > 0.1) {
            return current.incrementMinor(changelog, updated.getObservationCount(), confidenceDelta);
        }

        // Patch: small adjustments
        return current.incrementPatch(changelog, confidenceDelta);
    }

    private Promise<PatternRecord> updateStatus(String patternId, String tenantId, PatternRecord.PatternStatus status) {
        return get(patternId, tenantId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Pattern not found: " + patternId));
                    }

                    PatternRecord updated = opt.get().toBuilder()
                            .status(status)
                            .updatedAt(Instant.now())
                            .build();

                    getTenantStorage(tenantId).put(patternId, updated);
                    LOG.info("Updated pattern {} status to {}", patternId, status);
                    return Promise.of(updated);
                });
    }

    private Comparator<PatternRecord> getComparator(String sortBy) {
        return switch (sortBy) {
            case "name" -> Comparator.comparing(PatternRecord::getName, Comparator.nullsLast(String::compareTo));
            case "createdAt" -> Comparator.comparing(PatternRecord::getDiscoveredAt);
            case "updatedAt" -> Comparator.comparing(PatternRecord::getUpdatedAt);
            case "observationCount" -> Comparator.comparingLong(PatternRecord::getObservationCount);
            case "precision" -> Comparator.comparingDouble(PatternRecord::getPrecision);
            default -> Comparator.comparingDouble(PatternRecord::getConfidence);
        };
    }

    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;

        float dot = 0, magA = 0, magB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            magA += a[i] * a[i];
            magB += b[i] * b[i];
        }

        float denominator = (float) (Math.sqrt(magA) * Math.sqrt(magB));
        return denominator > 0 ? dot / denominator : 0;
    }

    private record ScoredPattern(PatternRecord pattern, float score) {}
}
