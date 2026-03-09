package com.ghatana.agent.framework.memory;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory event-sourced implementation of MemoryStore.
 * 
 * <p>Uses event sourcing pattern:
 * <ul>
 *   <li>All writes append events to an immutable event log</li>
 *   <li>Materialized views (episodes, facts, policies, preferences) for fast reads</li>
 *   <li>Events are the source of truth - views can be rebuilt from events</li>
 * </ul>
 * 
 * <p>Thread-safe implementation using ConcurrentHashMap for materialized views.
 * 
 * @doc.type class
 * @doc.purpose In-memory event-sourced memory store
 * @doc.layer framework
 * @doc.pattern Repository, Event Sourcing
 * @doc.gaa.memory episodic|semantic|procedural|preference
 */
public class EventLogMemoryStore implements MemoryStore {
    
    private static final Logger log = LoggerFactory.getLogger(EventLogMemoryStore.class);
    
    // Event log (source of truth)
    private final List<MemoryEvent> eventLog = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong eventCounter = new AtomicLong(0);
    
    // Materialized views (for fast queries)
    private final Map<String, Episode> episodes = new ConcurrentHashMap<>();
    private final Map<String, Fact> facts = new ConcurrentHashMap<>();
    private final Map<String, Policy> policies = new ConcurrentHashMap<>();
    private final Map<String, Preference> preferences = new ConcurrentHashMap<>();
    
    // Indexes
    private final Map<String, Set<String>> episodesByAgent = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> factsByAgent = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> policiesByAgent = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> preferencesByNamespace = new ConcurrentHashMap<>();
    
    @Override
    @NotNull
    public Promise<Episode> storeEpisode(@NotNull Episode episode) {
        try {
            String episodeId = episode.getId() != null ? episode.getId() : generateId("episode");
            Episode stored = Episode.builder()
                    .id(episodeId)
                    .agentId(episode.getAgentId())
                    .turnId(episode.getTurnId())
                    .timestamp(episode.getTimestamp())
                    .input(episode.getInput())
                    .output(episode.getOutput())
                    .action(episode.getAction())
                    .context(episode.getContext())
                    .tags(episode.getTags())
                    .reward(episode.getReward())
                    .embedding(episode.getEmbedding())
                    .build();
            
            synchronized (eventLog) {
                episodes.put(episodeId, stored);
                
                // Append event to event log
                eventLog.add(new MemoryEvent(
                        generateEventId(),
                        "EPISODE_STORED",
                        Instant.now(),
                        stored.getAgentId(),
                        Map.of("episodeId", episodeId, "input", episode.getInput())));
            }
            
            // Update indexes
            episodesByAgent.computeIfAbsent(stored.getAgentId(), k -> ConcurrentHashMap.newKeySet())
                    .add(episodeId);
            
            log.debug("Stored episode: {} for agent: {}", episodeId, stored.getAgentId());
            return Promise.of(stored);
            
        } catch (Exception e) {
            log.error("Failed to store episode", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<List<Episode>> queryEpisodes(@NotNull MemoryFilter filter, int limit) {
        try {
            Set<String> agentEpisodes = episodesByAgent.getOrDefault(filter.getAgentId(), Set.of());
            
            List<Episode> results = agentEpisodes.stream()
                    .map(episodes::get)
                    .filter(Objects::nonNull)
                    .filter(e -> matchesFilter(e, filter))
                    .sorted(Comparator.comparing(Episode::getTimestamp).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
            
            log.debug("Queried {} episodes for agent: {} with filter: {}", 
                    results.size(), filter.getAgentId(), filter);
            return Promise.of(results);
            
        } catch (Exception e) {
            log.error("Failed to query episodes", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<List<Episode>> searchEpisodes(@NotNull String query, int limit) {
        try {
            List<Episode> results = episodes.values().stream()
                    .filter(e -> e.getInput().toLowerCase().contains(query.toLowerCase()) ||
                            (e.getOutput() != null && e.getOutput().toLowerCase().contains(query.toLowerCase())))
                    .sorted(Comparator.comparing(Episode::getTimestamp).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
            
            log.debug("Searched episodes: found {} results for query: {}", results.size(), query);
            return Promise.of(results);
            
        } catch (Exception e) {
            log.error("Failed to search episodes", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<Fact> storeFact(@NotNull Fact fact) {
        try {
            String factId = fact.getId() != null ? fact.getId() : generateId("fact");
            Fact stored = Fact.builder()
                    .id(factId)
                    .agentId(fact.getAgentId())
                    .subject(fact.getSubject())
                    .predicate(fact.getPredicate())
                    .object(fact.getObject())
                    .learnedAt(fact.getLearnedAt())
                    .confidence(fact.getConfidence())
                    .source(fact.getSource())
                    .metadata(fact.getMetadata())
                    .build();
            
            synchronized (eventLog) {
                facts.put(factId, stored);
                
                // Append event
                eventLog.add(new MemoryEvent(
                        generateEventId(),
                        "FACT_STORED",
                        Instant.now(),
                        stored.getAgentId(),
                        Map.of("factId", factId, "subject", fact.getSubject(), "predicate", fact.getPredicate())));
            }
            
            // Update indexes
            factsByAgent.computeIfAbsent(stored.getAgentId(), k -> ConcurrentHashMap.newKeySet())
                    .add(factId);
            
            log.debug("Stored fact: {} for agent: {}", factId, stored.getAgentId());
            return Promise.of(stored);
            
        } catch (Exception e) {
            log.error("Failed to store fact", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<List<Fact>> queryFacts(@Nullable String subject, 
                                          @NotNull String predicate, 
                                          @Nullable String object) {
        try {
            List<Fact> results = facts.values().stream()
                    .filter(f -> (subject == null || f.getSubject().equals(subject)))
                    .filter(f -> f.getPredicate().equals(predicate))
                    .filter(f -> (object == null || f.getObject().equals(object)))
                    .sorted(Comparator.comparing(Fact::getConfidence).reversed())
                    .collect(Collectors.toList());
            
            log.debug("Queried {} facts for predicate: {}", results.size(), predicate);
            return Promise.of(results);
            
        } catch (Exception e) {
            log.error("Failed to query facts", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<List<Fact>> searchFacts(@NotNull String concept, int limit) {
        try {
            List<Fact> results = facts.values().stream()
                    .filter(f -> f.getSubject().contains(concept) || f.getObject().contains(concept))
                    .sorted(Comparator.comparing(Fact::getConfidence).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
            
            log.debug("Searched facts: found {} results for concept: {}", results.size(), concept);
            return Promise.of(results);
            
        } catch (Exception e) {
            log.error("Failed to search facts", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<Policy> storePolicy(@NotNull Policy policy) {
        try {
            String policyId = policy.getId() != null ? policy.getId() : generateId("policy");
            Policy stored = Policy.builder()
                    .id(policyId)
                    .agentId(policy.getAgentId())
                    .situation(policy.getSituation())
                    .action(policy.getAction())
                    .confidence(policy.getConfidence())
                    .learnedAt(policy.getLearnedAt())
                    .lastUsedAt(policy.getLastUsedAt())
                    .useCount(policy.getUseCount())
                    .learnedFromEpisodes(policy.getLearnedFromEpisodes())
                    .version(policy.getVersion())
                    .metadata(policy.getMetadata())
                    .build();
            
            synchronized (eventLog) {
                policies.put(policyId, stored);
                
                // Append event
                eventLog.add(new MemoryEvent(
                        generateEventId(),
                        "POLICY_STORED",
                        Instant.now(),
                        stored.getAgentId(),
                        Map.of("policyId", policyId, "situation", policy.getSituation())));
            }
            
            // Update indexes
            policiesByAgent.computeIfAbsent(stored.getAgentId(), k -> ConcurrentHashMap.newKeySet())
                    .add(policyId);
            
            log.debug("Stored policy: {} for agent: {}", policyId, stored.getAgentId());
            return Promise.of(stored);
            
        } catch (Exception e) {
            log.error("Failed to store policy", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<List<Policy>> queryPolicies(@NotNull String situation, double minConfidence) {
        try {
            List<Policy> results = policies.values().stream()
                    .filter(p -> p.getSituation().contains(situation) || situation.contains(p.getSituation()))
                    .filter(p -> p.getConfidence() >= minConfidence)
                    .sorted(Comparator.comparing(Policy::getConfidence).reversed())
                    .collect(Collectors.toList());
            
            log.debug("Queried {} policies for situation: {} with minConfidence: {}", 
                    results.size(), situation, minConfidence);
            return Promise.of(results);
            
        } catch (Exception e) {
            log.error("Failed to query policies", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<Policy> getPolicy(@NotNull String policyId) {
        try {
            Policy policy = policies.get(policyId);
            if (policy == null) {
                return Promise.ofException(
                        new IllegalArgumentException("Policy not found: " + policyId));
            }
            return Promise.of(policy);
            
        } catch (Exception e) {
            log.error("Failed to get policy: {}", policyId, e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<Preference> storePreference(@NotNull Preference preference) {
        try {
            String key = preference.getKey();
            Preference stored = Preference.builder()
                    .key(preference.getKey())
                    .value(preference.getValue())
                    .namespace(preference.getNamespace())
                    .agentId(preference.getAgentId())
                    .setAt(preference.getSetAt())
                    .build();
            
            synchronized (eventLog) {
                preferences.put(key, stored);
                
                // Append event
                eventLog.add(new MemoryEvent(
                        generateEventId(),
                        "PREFERENCE_STORED",
                        Instant.now(),
                        stored.getAgentId(),
                        Map.of("key", key)));
            }
            
            // Update indexes
            preferencesByNamespace.computeIfAbsent(stored.getNamespace(), k -> ConcurrentHashMap.newKeySet())
                    .add(key);
            
            log.debug("Stored preference: {} for agent: {}", key, stored.getAgentId());
            return Promise.of(stored);
            
        } catch (Exception e) {
            log.error("Failed to store preference", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<String> getPreference(@NotNull String key) {
        try {
            Optional<Preference> pref = Optional.ofNullable(preferences.get(key));
            return Promise.of(pref.map(Preference::getValue).orElse(null));
            
        } catch (Exception e) {
            log.error("Failed to get preference: {}", key, e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<Map<String, String>> getPreferences(@NotNull String namespace) {
        try {
            Set<String> namespacePrefs = preferencesByNamespace.getOrDefault(namespace, Set.of());
            
            Map<String, String> results = namespacePrefs.stream()
                    .map(preferences::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Preference::getKey, Preference::getValue));
            
            log.debug("Retrieved {} preferences for namespace: {}", results.size(), namespace);
            return Promise.of(results);
            
        } catch (Exception e) {
            log.error("Failed to get preferences for namespace: {}", namespace, e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<GovernanceResult> applyGovernance(@NotNull GovernancePolicy policy) {
        try {
            int redacted = 0;
            int deleted = 0;
            
            // For this in-memory implementation, simulate governance
            // In real implementation, would apply retention and redaction rules
            log.info("Applied governance policy (simulated), redacted: {}, deleted: {}", 
                    redacted, deleted);
            
            return Promise.of(new GovernanceResult(0, redacted, deleted, 0));
            
        } catch (Exception e) {
            log.error("Failed to apply governance", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<Integer> clearMemory() {
        try {
            int totalRecords;
            synchronized (eventLog) {
                totalRecords = episodes.size() + facts.size() + policies.size() + preferences.size();
                episodes.clear();
                facts.clear();
                policies.clear();
                preferences.clear();
                episodesByAgent.clear();
                factsByAgent.clear();
                policiesByAgent.clear();
                preferencesByNamespace.clear();
                eventLog.clear();
                eventCounter.set(0);
            }
            
            log.info("Cleared all memory ({} records)", totalRecords);
            return Promise.of(totalRecords);
            
        } catch (Exception e) {
            log.error("Failed to clear memory", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    @NotNull
    public Promise<MemoryStats> getStats() {
        try {
            MemoryStats stats = new MemoryStats(
                    episodes.size(),
                    facts.size(),
                    policies.size(),
                    preferences.size(),
                    eventLog.size() * 1024L  // Rough estimate
            );
            return Promise.of(stats);
            
        } catch (Exception e) {
            log.error("Failed to get stats", e);
            return Promise.ofException(e);
        }
    }
    
    // Helper methods
    
    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString();
    }
    
    private String generateEventId() {
        return "evt-" + eventCounter.incrementAndGet();
    }
    
    private boolean matchesFilter(Episode episode, MemoryFilter filter) {
        if (filter.getStartTime() != null && episode.getTimestamp().isBefore(filter.getStartTime())) {
            return false;
        }
        if (filter.getEndTime() != null && episode.getTimestamp().isAfter(filter.getEndTime())) {
            return false;
        }
        
        // Check tags
        if (filter.getTags() != null && !filter.getTags().isEmpty()) {
            return episode.getTags().stream().anyMatch(filter.getTags()::contains);
        }
        
        return true;
    }
    
    /**
     * Internal event record for event sourcing.
     * 
     * @param eventId Event ID
     * @param eventType Event type (EPISODE_STORED, FACT_STORED, etc.)
     * @param timestamp When event occurred
     * @param agentId Which agent created the event
     * @param payload Event data
     */
    private record MemoryEvent(
            String eventId,
            String eventType,
            Instant timestamp,
            String agentId,
            Map<String, Object> payload
    ) {}
}
