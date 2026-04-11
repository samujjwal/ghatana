/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.memory.store.procedural;

import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link PatternEngine} using keyword hashing
 * and label indexing for O(1) lookups.
 *
 * <h2>Index Structure</h2>
 * <ul>
 *   <li><b>Keyword index</b>: normalized keyword → Set&lt;procedureId&gt;</li>
 *   <li><b>Label index</b>: "key=value" → Set&lt;procedureId&gt;</li>
 *   <li><b>Procedure store</b>: procedureId → EnhancedProcedure</li>
 * </ul>
 *
 * <p>Only procedures with confidence ≥ {@link #minConfidence} are indexed.
 *
 * @doc.type class
 * @doc.purpose In-memory reflex pattern engine
 * @doc.layer agent-memory
 * @doc.pattern Strategy / Index
 *
 * @since 2.4.0
 */
public class InMemoryPatternEngine implements PatternEngine {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPatternEngine.class);

    /** Minimum confidence for a procedure to be indexed. */
    private final double minConfidence;

    /** Minimum keyword overlap ratio for a partial match. */
    private final double minPartialMatchRatio;

    // ═══════════════════════════════════════════════════════════════════════════
    // Index structures (all concurrent for thread safety)
    // ═══════════════════════════════════════════════════════════════════════════

    /** keyword → set of procedure IDs */
    private final Map<String, Set<String>> keywordIndex = new ConcurrentHashMap<>();

    /** "labelKey=labelValue" → set of procedure IDs */
    private final Map<String, Set<String>> labelIndex = new ConcurrentHashMap<>();

    /** procedureId → procedure */
    private final Map<String, EnhancedProcedure> procedures = new ConcurrentHashMap<>();

    private volatile long lastRebuildTimeMs;

    /**
     * Creates a pattern engine with default thresholds.
     */
    public InMemoryPatternEngine() {
        this(0.7, 0.6);
    }

    /**
     * Creates a pattern engine with custom thresholds.
     *
     * @param minConfidence       minimum confidence for indexing (0.0-1.0)
     * @param minPartialMatchRatio minimum keyword overlap for partial match (0.0-1.0)
     */
    public InMemoryPatternEngine(double minConfidence, double minPartialMatchRatio) {
        this.minConfidence = minConfidence;
        this.minPartialMatchRatio = minPartialMatchRatio;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Match
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public Optional<MatchResult> match(@NotNull String situation, @NotNull Map<String, String> context) {
        Set<String> keywords = tokenize(situation);

        // 1. Try exact match: look for a procedure whose situation keywords fully match
        Optional<MatchResult> exact = tryExactMatch(keywords);
        if (exact.isPresent()) return exact;

        // 2. Try label match: find procedures matching context labels
        Optional<MatchResult> label = tryLabelMatch(context);
        if (label.isPresent()) return label;

        // 3. Try partial keyword match: best overlap above threshold
        return tryPartialMatch(keywords);
    }

    private Optional<MatchResult> tryExactMatch(Set<String> keywords) {
        // Find procedure IDs that appear in ALL keyword entries
        Map<String, Integer> hitCounts = new HashMap<>();
        for (String keyword : keywords) {
            Set<String> procs = keywordIndex.get(keyword);
            if (procs != null) {
                for (String procId : procs) {
                    hitCounts.merge(procId, 1, Integer::sum);
                }
            }
        }

        // An "exact" match means ALL input keywords map to the same procedure
        for (var entry : hitCounts.entrySet()) {
            if (entry.getValue() == keywords.size()) {
                EnhancedProcedure proc = procedures.get(entry.getKey());
                if (proc != null) {
                    return Optional.of(new MatchResult(
                            proc, 1.0, MatchType.EXACT, List.copyOf(keywords)));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<MatchResult> tryLabelMatch(Map<String, String> context) {
        if (context.isEmpty()) return Optional.empty();

        Map<String, Integer> hitCounts = new HashMap<>();
        for (var entry : context.entrySet()) {
            String labelKey = entry.getKey() + "=" + entry.getValue();
            Set<String> procs = labelIndex.get(labelKey);
            if (procs != null) {
                for (String procId : procs) {
                    hitCounts.merge(procId, 1, Integer::sum);
                }
            }
        }

        // Best label match: most matching labels
        return hitCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 0)
                .map(e -> {
                    EnhancedProcedure proc = procedures.get(e.getKey());
                    if (proc == null) return null;
                    double score = (double) e.getValue() / context.size();
                    return new MatchResult(proc, score, MatchType.LABEL, List.of());
                });
    }

    private Optional<MatchResult> tryPartialMatch(Set<String> keywords) {
        if (keywords.isEmpty()) return Optional.empty();

        Map<String, Integer> hitCounts = new HashMap<>();
        Map<String, List<String>> matchedKeywordsMap = new HashMap<>();

        for (String keyword : keywords) {
            Set<String> procs = keywordIndex.get(keyword);
            if (procs != null) {
                for (String procId : procs) {
                    hitCounts.merge(procId, 1, Integer::sum);
                    matchedKeywordsMap.computeIfAbsent(procId, k -> new ArrayList<>()).add(keyword);
                }
            }
        }

        // Find best partial match above threshold
        return hitCounts.entrySet().stream()
                .filter(e -> {
                    double ratio = (double) e.getValue() / keywords.size();
                    return ratio >= minPartialMatchRatio;
                })
                .max(Map.Entry.comparingByValue())
                .map(e -> {
                    EnhancedProcedure proc = procedures.get(e.getKey());
                    if (proc == null) return null;
                    double score = (double) e.getValue() / keywords.size();
                    List<String> matched = matchedKeywordsMap.getOrDefault(e.getKey(), List.of());
                    return new MatchResult(proc, score, MatchType.PARTIAL, matched);
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Index Management
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void index(@NotNull EnhancedProcedure procedure) {
        if (procedure.getConfidence() < minConfidence) {
            log.debug("Skipping low-confidence procedure {} (confidence={})",
                    procedure.getId(), procedure.getConfidence());
            return;
        }

        procedures.put(procedure.getId(), procedure);

        // Index keywords from situation
        for (String keyword : tokenize(procedure.getSituation())) {
            keywordIndex.computeIfAbsent(keyword, k -> ConcurrentHashMap.newKeySet())
                    .add(procedure.getId());
        }

        // Index labels
        for (var entry : procedure.getLabels().entrySet()) {
            String labelKey = entry.getKey() + "=" + entry.getValue();
            labelIndex.computeIfAbsent(labelKey, k -> ConcurrentHashMap.newKeySet())
                    .add(procedure.getId());
        }
    }

    @Override
    public void deindex(@NotNull String procedureId) {
        EnhancedProcedure removed = procedures.remove(procedureId);
        if (removed == null) return;

        // Remove from keyword index
        for (String keyword : tokenize(removed.getSituation())) {
            Set<String> procs = keywordIndex.get(keyword);
            if (procs != null) {
                procs.remove(procedureId);
                if (procs.isEmpty()) keywordIndex.remove(keyword);
            }
        }

        // Remove from label index
        for (var entry : removed.getLabels().entrySet()) {
            String labelKey = entry.getKey() + "=" + entry.getValue();
            Set<String> procs = labelIndex.get(labelKey);
            if (procs != null) {
                procs.remove(procedureId);
                if (procs.isEmpty()) labelIndex.remove(labelKey);
            }
        }
    }

    @Override
    @NotNull
    public Promise<Void> rebuild(@NotNull List<EnhancedProcedure> allProcedures) {
        long start = System.currentTimeMillis();

        // Clear all indices
        keywordIndex.clear();
        labelIndex.clear();
        procedures.clear();

        // Re-index everything
        for (EnhancedProcedure proc : allProcedures) {
            index(proc);
        }

        lastRebuildTimeMs = System.currentTimeMillis() - start;
        log.info("Pattern engine rebuilt: {} procedures indexed in {}ms",
                procedures.size(), lastRebuildTimeMs);

        return Promise.complete();
    }

    @Override
    @NotNull
    public IndexStats getStats() {
        return new IndexStats(
                procedures.size(),
                keywordIndex.size(),
                labelIndex.size(),
                lastRebuildTimeMs
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tokenization
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tokenizes a situation string into normalized keywords.
     * Removes stopwords, normalizes to lowercase, strips punctuation.
     */
    static Set<String> tokenize(@NotNull String text) {
        return Arrays.stream(text.toLowerCase().split("[\\s,.;:!?()\\[\\]{}\"']+"))
                .filter(w -> w.length() > 2)
                .filter(w -> !STOPWORDS.contains(w))
                .collect(Collectors.toSet());
    }

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all",
            "can", "has", "her", "was", "one", "our", "out", "its",
            "any", "had", "may", "who", "did", "get", "let", "say",
            "she", "too", "use", "his", "how", "man", "new", "now",
            "old", "see", "way", "day", "two", "been", "from",
            "have", "into", "just", "like", "long", "make", "many",
            "more", "most", "much", "must", "name", "need", "only",
            "over", "such", "take", "than", "them", "then", "they",
            "this", "that", "very", "when", "what", "with", "will",
            "each", "come", "some", "your"
    );
}
