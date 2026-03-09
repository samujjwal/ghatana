package com.ghatana.agent.memory.store.semantic;

import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.Validity;
import com.ghatana.agent.memory.model.ValidityStatus;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.fact.FactVersion;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Manages the semantic memory tier — facts with versioning, conflict detection,
 * and confidence-based truth maintenance.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Fact de-duplication (subject + predicate + object triple matching)</li>
 *   <li>Version management (append new version, keep history)</li>
 *   <li>Conflict detection (same subject-predicate, different object)</li>
 *   <li>Confidence decay and validity tracking</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Semantic memory management
 * @doc.layer agent-memory
 */
public class SemanticMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(SemanticMemoryManager.class);

    private final MemoryPlane memoryPlane;

    public SemanticMemoryManager(@NotNull MemoryPlane memoryPlane) {
        this.memoryPlane = Objects.requireNonNull(memoryPlane, "memoryPlane");
    }

    /**
     * Stores a fact with de-duplication. If an existing fact with the same
     * (subject, predicate, object) triple exists, a new version is created.
     *
     * @param fact The fact to store
     * @return The stored (or versioned) fact
     */
    @NotNull
    public Promise<EnhancedFact> storeOrVersion(@NotNull EnhancedFact fact) {
        // Query for existing facts with same triple
        MemoryQuery query = MemoryQuery.builder()
                .limit(10)
                .build();

        return memoryPlane.queryFacts(query)
                .then(existingFacts -> {
                    Optional<EnhancedFact> existing = existingFacts.stream()
                            .filter(f -> tripleMatches(f, fact))
                            .findFirst();

                    if (existing.isPresent()) {
                        EnhancedFact old = existing.get();
                        if (old.getObject().equals(fact.getObject())) {
                            // Same triple — bump confidence
                            log.debug("Reinforcing existing fact: {}", old.getId());
                            EnhancedFact reinforced = EnhancedFact.builder()
                                    .id(old.getId())
                                    .agentId(old.getAgentId())
                                    .subject(old.getSubject())
                                    .predicate(old.getPredicate())
                                    .object(old.getObject())
                                    .confidence(Math.min(1.0, old.getConfidence() + 0.05))
                                    .provenance(fact.getProvenance())
                                    .validity(old.getValidity())
                                    .versionHistory(old.getVersionHistory())
                                    .createdAt(old.getCreatedAt())
                                    .updatedAt(Instant.now())
                                    .build();
                            return memoryPlane.storeFact(reinforced);
                        } else {
                            // Conflict: same subject-predicate, different object
                            log.info("Conflict detected for ({}, {}) — old='{}' vs new='{}'",
                                    fact.getSubject(), fact.getPredicate(),
                                    old.getObject(), fact.getObject());

                            // Create a new version
                            FactVersion version = FactVersion.builder()
                                    .version(old.getVersion() + 1)
                                    .content(fact.getObject())
                                    .changedAt(Instant.now())
                                    .changedBy(fact.getProvenance() != null ? fact.getProvenance().getSource() : "unknown")
                                    .changeReason("Conflict: updated object value")
                                    .build();

                            List<FactVersion> versions = new ArrayList<>(old.getVersionHistory());
                            versions.add(version);

                            // Highest confidence wins
                            String winningObject = fact.getConfidence() >= old.getConfidence()
                                    ? fact.getObject()
                                    : old.getObject();

                            EnhancedFact updated = EnhancedFact.builder()
                                    .id(old.getId())
                                    .agentId(old.getAgentId())
                                    .subject(old.getSubject())
                                    .predicate(old.getPredicate())
                                    .object(winningObject)
                                    .confidence(Math.max(old.getConfidence(), fact.getConfidence()))
                                    .provenance(fact.getProvenance())
                                    .validity(old.getValidity())
                                    .versionHistory(versions)
                                    .createdAt(old.getCreatedAt())
                                    .updatedAt(Instant.now())
                                    .build();
                            return memoryPlane.storeFact(updated);
                        }
                    }

                    // No existing fact — store new
                    return memoryPlane.storeFact(fact);
                });
    }

    /**
     * Invalidates a fact by marking its validity as DISPUTED.
     */
    @NotNull
    public Promise<EnhancedFact> invalidate(@NotNull String factId, @NotNull String reason) {
        log.info("Invalidating fact {}: {}", factId, reason);
        // In a full impl: load fact, set validity to DISPUTED, save
        return Promise.of(null);
    }

    private boolean tripleMatches(EnhancedFact a, EnhancedFact b) {
        return a.getSubject().equals(b.getSubject())
                && a.getPredicate().equals(b.getPredicate());
    }
}
