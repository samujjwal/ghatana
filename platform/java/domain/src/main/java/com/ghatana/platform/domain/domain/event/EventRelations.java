package com.ghatana.platform.domain.domain.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable semantic relationships between events for correlation and causation tracking.
 * 
 * <p>
 * Encapsulates relationships between events and other entities (causal, hierarchical,
 * temporal, etc.). EventRelations enable building event graphs for root cause analysis,
 * dependency tracking, and understanding event flow. Implemented as an immutable map
 * from RelationType to sets of EventIds for efficient graph navigation.
 * </p>
 *
 * <h2>Relation Types Supported</h2>
 * <p>
 * Events can have multiple relation types (via {@link RelationType}):
 * <ul>
 *   <li><b>Causal</b>: CAUSES, CAUSED_BY - event that triggered or was triggered by this event</li>
 *   <li><b>Hierarchical</b>: PARENT_OF, CHILD_OF - event composition relationships</li>
 *   <li><b>Temporal</b>: FOLLOWS, PRECEDES - ordering and sequencing relationships</li>
 *   <li><b>Reference</b>: REFERENCES, REFERENCES_BY - data dependencies and references</li>
 *   <li><b>Equivalence</b>: DUPLICATE_OF, DERIVED_FROM - similarity and transformation</li>
 *   <li><b>Response</b>: RESPONSE_TO, RETRY_OF - reactive relationships</li>
 *   <li><b>Correction</b>: CORRECTION_OF, ROLLBACK_OF - fix/undo relationships</li>
 * </ul>
 * </p>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * Event relations are critical for graph-based processing:
 * <ul>
 *   <li><b>Correlation</b>: Relates events by causation and dependency chains</li>
 *   <li><b>Root Cause Analysis</b>: Builds causal graphs for incident investigation</li>
 *   <li><b>Impact Analysis</b>: Traverses event graphs to find downstream impacts</li>
 *   <li><b>Pattern Detection</b>: Matches complex event patterns across related events</li>
 *   <li><b>Event Enrichment</b>: Adds semantic relationships during event processing</li>
 *   <li><b>SLA Tracking</b>: Tracks impacts and dependencies for SLA calculations</li>
 * </ul>
 * </p>
 *
 * <h2>Immutability & Thread Safety</h2>
 * <p>
 * Relations are immutable and completely thread-safe:
 * <ul>
 *   <li>All internal collections are unmodifiable (Collections.unmodifiableMap/Set)</li>
 *   <li>Copy-on-construct ensures original builder data doesn't leak</li>
 *   <li>Safe to share across concurrent processing threads without synchronization</li>
 *   <li>Created only via builder, not directly instantiated</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Build event relations
 * EventRelations relations = builder()
 *     .addRelation(RelationType.CAUSES, eventId1)
 *     .addRelation(RelationType.CAUSES, eventId2)
 *     .addRelation(RelationType.CAUSED_BY, causedByEventId)
 *     .build();
 *
 * // Query relations
 * if (relations.hasRelation(RelationType.CAUSES)) {
 *   Set<EventId> causes = relations.getRelatedEvents(RelationType.CAUSES);
 *   for (EventId cause : causes) {
 *     // Process cause event
 *   }
 * }
 *
 * // Get all relation types
 * Set<RelationType> types = relations.getRelationTypes();
 * Map<RelationType, Set<EventId>> allRelations = relations.getAllRelations();
 *
 * // Graph traversal for RCA
 * Stack<EventId> toVisit = new Stack<>();
 * toVisit.push(rootCauseEvent);
 * while (!toVisit.empty()) {
 *   EventId eventId = toVisit.pop();
 *   Event event = eventStore.get(eventId);
 *   for (EventId parent : event.getRelations().getRelatedEvents(RelationType.CAUSED_BY)) {
 *     toVisit.push(parent);  // Visit parent causes
 *   }
 * }
 * }</pre>
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose immutable semantic relationships for event graphs and correlation
 * @doc.pattern immutable collection (unmodifiable map of sets, copy-on-construct)
 * @doc.test-hints verify immutability, test graph traversal, validate relationship queries, check thread safety
 *
 * @see RelationType (semantic relationship types)
 * @see EventId (events referenced in relations)
 * @see Event (events contain EventRelations)
 */
public final class EventRelations {
    private final Map<RelationType, Set<EventId>> relations;

    private EventRelations(Builder builder) {
        // Create an immutable copy of the relations map
        Map<RelationType, Set<EventId>> tempMap = new HashMap<>();
        builder.relations.forEach((type, ids) -> 
            tempMap.put(type, Collections.unmodifiableSet(new HashSet<>(ids)))
        );
        this.relations = Collections.unmodifiableMap(tempMap);
    }

    /**
     * Gets all relation types that exist for this event.
     *
     * @return An immutable set of relation types
     */
    public Set<RelationType> getRelationTypes() {
        return relations.keySet();
    }

    /**
     * Gets all event IDs for a specific relation type.
     *
     * @param type The relation type
     * @return An immutable set of event IDs, or an empty set if none exist
     */
    public Set<EventId> getRelatedEvents(RelationType type) {
        return relations.getOrDefault(type, Collections.emptySet());
    }

    /**
     * Checks if this event has any relations of the specified type.
     *
     * @param type The relation type to check
     * @return true if the event has at least one relation of the specified type
     */
    public boolean hasRelation(RelationType type) {
        return relations.containsKey(type) && !relations.get(type).isEmpty();
    }

    /**
     * Gets all relations as an immutable map.
     *
     * @return An immutable map of relation types to sets of event IDs
     */
    public Map<RelationType, Set<EventId>> getAllRelations() {
        return relations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventRelations that = (EventRelations) o;
        return relations.equals(that.relations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relations);
    }

    @Override
    public String toString() {
        return "EventRelations{" +
               "relations=" + relations +
               "}";
    }

    /**
     * Creates a new builder for constructing EventRelations objects.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an empty EventRelations instance.
     *
     * @return An empty EventRelations instance
     */
    public static EventRelations empty() {
        return builder().build();
    }

    /**
     * Builder for creating EventRelations instances.
     */
    public static final class Builder {
        private final Map<RelationType, Set<EventId>> relations = new HashMap<>();

        private Builder() { }

        /**
         * Adds a relation to another event.
         *
         * @param type The type of relation
         * @param eventId The ID of the related event
         * @return This builder
         */
        public Builder addRelation(RelationType type, EventId eventId) {
            relations.computeIfAbsent(type, k -> new HashSet<>()).add(eventId);
            return this;
        }

        /**
         * Adds multiple relations of the same type.
         *
         * @param type The type of relation
         * @param eventIds The IDs of the related events
         * @return This builder
         */
        public Builder addRelations(RelationType type, Iterable<EventId> eventIds) {
            eventIds.forEach(id -> addRelation(type, id));
            return this;
        }

        /**
         * Adds all relations from another EventRelations instance.
         *
         * @param other The other EventRelations instance
         * @return This builder
         */
        public Builder addAll(EventRelations other) {
            other.relations.forEach((type, ids) -> 
                relations.computeIfAbsent(type, k -> new HashSet<>()).addAll(ids)
            );
            return this;
        }

        /**
         * Builds a new EventRelations instance with the current configuration.
         *
         * @return A new EventRelations instance
         */
        public EventRelations build() {
            return new EventRelations(this);
        }
    }
}
