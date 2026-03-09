package com.ghatana.virtualorg.framework.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of OrganizationalMemory.
 *
 * <p><b>Purpose</b><br>
 * Provides fast, in-memory storage for organizational memory entries.
 * Suitable for short-term memory and caching. Data is lost on restart.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * OrganizationalMemory memory = new InMemoryOrganizationalMemory();
 * memory.store(MemoryEntry.of("decisions", "Sprint Plan", "...", "PM"));
 * List<MemoryEntry> results = memory.search("sprint", 10);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Uses ConcurrentHashMap for storage.
 *
 * @see OrganizationalMemory
 * @see MemoryEntry
 * @doc.type class
 * @doc.purpose In-memory organizational memory implementation
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class InMemoryOrganizationalMemory implements OrganizationalMemory {
    
    private final Map<String, MemoryEntry> entries = new ConcurrentHashMap<>();
    
    @Override
    public void store(MemoryEntry entry) {
        entries.put(entry.getId(), entry);
    }
    
    @Override
    public Optional<MemoryEntry> retrieve(String id) {
        return Optional.ofNullable(entries.get(id));
    }
    
    @Override
    public List<MemoryEntry> search(String query, int limit) {
        String lowerQuery = query.toLowerCase();
        return entries.values().stream()
            .filter(e -> e.getTitle().toLowerCase().contains(lowerQuery) ||
                        e.getContent().toLowerCase().contains(lowerQuery) ||
                        e.getTags().toLowerCase().contains(lowerQuery))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<MemoryEntry> searchByCategory(String category, int limit) {
        return entries.values().stream()
            .filter(e -> e.getCategory().equalsIgnoreCase(category))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<MemoryEntry> searchByActor(String actor, int limit) {
        return entries.values().stream()
            .filter(e -> e.getActor().equalsIgnoreCase(actor))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<MemoryEntry> getRecent(int limit) {
        return entries.values().stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @Override
    public void clear() {
        entries.clear();
    }
    
    @Override
    public long getSize() {
        return entries.size();
    }
}
