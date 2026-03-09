package com.ghatana.virtualorg.framework.ontology;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The organizational ontology - a semantic vocabulary for the organization.
 *
 * <p><b>Purpose</b><br>
 * The Ontology provides a shared vocabulary that enables different agents
 * to communicate meaningfully. It defines concepts, their relationships,
 * and enables semantic reasoning.
 *
 * <p><b>Key Features</b><br>
 * - Hierarchical concept taxonomy (is-a relationships)
 * - Synonym resolution (multiple terms for same concept)
 * - Semantic search and matching
 * - Extensible by domain modules
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Ontology ontology = new Ontology();
 *
 * // Define concepts
 * ontology.define(Concept.builder("task", "Task")
 *     .description("A unit of work")
 *     .build());
 *
 * ontology.define(Concept.builder("code-review", "CodeReview")
 *     .parent("task")
 *     .synonyms("PR Review", "Pull Request Review")
 *     .build());
 *
 * // Resolve synonyms
 * Optional<Concept> concept = ontology.resolve("PR Review");
 * // Returns "CodeReview" concept
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Organizational semantic vocabulary
 * @doc.layer platform
 * @doc.pattern Registry
 */
public class Ontology {

    private static final Logger LOG = LoggerFactory.getLogger(Ontology.class);

    private final Map<String, Concept> concepts = new ConcurrentHashMap<>();
    private final Map<String, String> synonymIndex = new ConcurrentHashMap<>();

    /**
     * Creates an ontology with core organizational concepts.
     */
    public static Ontology withCoreConceptsAsync() {
        Ontology ontology = new Ontology();
        ontology.defineCoreConceptsSync();
        return ontology;
    }

    /**
     * Defines a new concept in the ontology.
     *
     * @param concept the concept to define
     * @return promise completing when definition is stored
     */
    public Promise<Void> define(Concept concept) {
        concepts.put(concept.id(), concept);

        // Index synonyms
        synonymIndex.put(concept.name().toLowerCase(), concept.id());
        for (String synonym : concept.synonyms()) {
            synonymIndex.put(synonym.toLowerCase(), concept.id());
        }

        LOG.debug("Defined concept: {} ({})", concept.name(), concept.id());
        return Promise.complete();
    }

    /**
     * Synchronous version of define.
     */
    public void defineSync(Concept concept) {
        define(concept);
    }

    /**
     * Resolves a term to a concept.
     *
     * @param term the term to resolve (name or synonym)
     * @return promise with optional concept
     */
    public Promise<Optional<Concept>> resolve(String term) {
        String conceptId = synonymIndex.get(term.toLowerCase());
        if (conceptId != null) {
            return Promise.of(Optional.ofNullable(concepts.get(conceptId)));
        }
        return Promise.of(Optional.empty());
    }

    /**
     * Gets a concept by ID.
     */
    public Promise<Optional<Concept>> get(String conceptId) {
        return Promise.of(Optional.ofNullable(concepts.get(conceptId)));
    }

    /**
     * Gets all sub-concepts of a parent.
     *
     * @param parentId the parent concept ID
     * @return promise with list of sub-concepts
     */
    public Promise<List<Concept>> getSubConcepts(String parentId) {
        return Promise.of(concepts.values().stream()
                .filter(c -> c.isSubConceptOf(parentId))
                .collect(Collectors.toList()));
    }

    /**
     * Gets the concept hierarchy (ancestors).
     *
     * @param conceptId the starting concept
     * @return promise with list of ancestors (from immediate parent to root)
     */
    public Promise<List<Concept>> getAncestors(String conceptId) {
        List<Concept> ancestors = new ArrayList<>();
        Concept current = concepts.get(conceptId);

        while (current != null && current.parentId().isPresent()) {
            Concept parent = concepts.get(current.parentId().get());
            if (parent != null) {
                ancestors.add(parent);
                current = parent;
            } else {
                break;
            }
        }

        return Promise.of(ancestors);
    }

    /**
     * Checks if a concept is a descendant of another.
     *
     * @param conceptId the concept to check
     * @param ancestorId the potential ancestor
     * @return promise with true if is descendant
     */
    public Promise<Boolean> isDescendantOf(String conceptId, String ancestorId) {
        return getAncestors(conceptId).map(ancestors ->
                ancestors.stream().anyMatch(a -> a.id().equals(ancestorId))
        );
    }

    /**
     * Semantic search for concepts.
     *
     * @param query the search query
     * @param limit maximum results
     * @return promise with matching concepts
     */
    public Promise<List<Concept>> search(String query, int limit) {
        String lowerQuery = query.toLowerCase();
        return Promise.of(concepts.values().stream()
                .filter(c -> c.matches(query) ||
                        c.description().toLowerCase().contains(lowerQuery))
                .limit(limit)
                .collect(Collectors.toList()));
    }

    /**
     * Gets all concepts.
     */
    public Promise<List<Concept>> getAll() {
        return Promise.of(new ArrayList<>(concepts.values()));
    }

    /**
     * Defines core organizational concepts.
     */
    private void defineCoreConceptsSync() {
        // Root concepts
        defineSync(Concept.builder("entity", "Entity")
                .description("Root concept for all organizational entities")
                .build());

        defineSync(Concept.builder("actor", "Actor")
                .description("An entity that can perform actions")
                .parent("entity")
                .build());

        defineSync(Concept.builder("artifact", "Artifact")
                .description("A tangible work product")
                .parent("entity")
                .build());

        defineSync(Concept.builder("activity", "Activity")
                .description("An action or series of actions")
                .parent("entity")
                .build());

        // Actor subtypes
        defineSync(Concept.builder("agent", "Agent")
                .description("An autonomous actor in the organization")
                .parent("actor")
                .synonyms("Worker", "Employee", "Resource")
                .build());

        defineSync(Concept.builder("department", "Department")
                .description("A collective actor (group of agents)")
                .parent("actor")
                .synonyms("Team", "Unit", "Division")
                .build());

        // Activity subtypes
        defineSync(Concept.builder("task", "Task")
                .description("A discrete unit of work")
                .parent("activity")
                .synonyms("Work Item", "Job", "Assignment")
                .build());

        defineSync(Concept.builder("workflow", "Workflow")
                .description("A sequence of related tasks")
                .parent("activity")
                .synonyms("Process", "Pipeline", "Flow")
                .build());

        defineSync(Concept.builder("decision", "Decision")
                .description("A choice between alternatives")
                .parent("activity")
                .build());

        // Common task types
        defineSync(Concept.builder("review", "Review")
                .description("An evaluation activity")
                .parent("task")
                .synonyms("Evaluation", "Assessment", "Inspection")
                .build());

        defineSync(Concept.builder("creation", "Creation")
                .description("A task that produces something new")
                .parent("task")
                .synonyms("Build", "Make", "Generate")
                .build());

        defineSync(Concept.builder("communication", "Communication")
                .description("Information exchange between actors")
                .parent("activity")
                .synonyms("Message", "Notification", "Announcement")
                .build());

        LOG.info("Core ontology loaded with {} concepts", concepts.size());
    }
}
