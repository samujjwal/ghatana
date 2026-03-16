package com.ghatana.appplatform.sanctions.adapter;

import com.ghatana.appplatform.sanctions.domain.SanctionsEntry;
import com.ghatana.appplatform.sanctions.domain.SanctionsListType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @doc.type    Adapter (Secondary)
 * @doc.purpose In-memory trie for fast prefix-based candidate filtering (D14-001 performance).
 *              Reduces the number of full name-matching evaluations from O(N) to O(candidates)
 *              by indexing normalized name prefixes of length {@value #PREFIX_LENGTH}.
 *              Not a replacement for full fuzzy matching — only a pre-filter.
 * @doc.layer   Infrastructure Adapter
 * @doc.pattern Trie (prefix index) + Hexagonal Architecture secondary adapter
 */
public class InMemorySanctionsTrie {

    private static final int PREFIX_LENGTH = 3;

    /** prefix → set of entry IDs that start with that prefix. */
    private final Map<String, Set<String>> index = new ConcurrentHashMap<>();
    /** entryId → SanctionsEntry for O(1) lookup. */
    private final Map<String, SanctionsEntry> entries = new ConcurrentHashMap<>();

    /** Clear the trie and rebuild from a fresh list (called during atomic swap). */
    public void rebuild(List<SanctionsEntry> newEntries) {
        index.clear();
        entries.clear();
        for (var entry : newEntries) {
            entries.put(entry.entryId(), entry);
            indexName(entry.entryId(), entry.primaryName());
            for (var alias : entry.aliases()) {
                indexName(entry.entryId(), alias);
            }
        }
    }

    /**
     * Return candidate entries whose primary name / alias starts with a prefix
     * derived from the normalized query. If fewer than 3 characters long, returns all entries.
     */
    public List<SanctionsEntry> candidates(String normalizedQuery) {
        if (normalizedQuery.length() < PREFIX_LENGTH) {
            return List.copyOf(entries.values());
        }
        String prefix = normalizedQuery.substring(0, PREFIX_LENGTH);
        Set<String> ids = index.getOrDefault(prefix, Set.of());
        var result = new ArrayList<SanctionsEntry>(ids.size());
        for (var id : ids) {
            SanctionsEntry e = entries.get(id);
            if (e != null) result.add(e);
        }
        return result;
    }

    public int size() {
        return entries.size();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void indexName(String entryId, String name) {
        if (name == null || name.length() < PREFIX_LENGTH) return;
        String normalized = normalizeForIndex(name);
        if (normalized.length() < PREFIX_LENGTH) return;
        String prefix = normalized.substring(0, PREFIX_LENGTH);
        index.computeIfAbsent(prefix, k -> ConcurrentHashMap.newKeySet()).add(entryId);
        // Also index each individual token for multi-word names
        for (var token : normalized.split("\\s+")) {
            if (token.length() >= PREFIX_LENGTH) {
                String tokenPrefix = token.substring(0, PREFIX_LENGTH);
                index.computeIfAbsent(tokenPrefix, k -> ConcurrentHashMap.newKeySet()).add(entryId);
            }
        }
    }

    private String normalizeForIndex(String name) {
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", "")
                .trim();
    }
}
