package com.ghatana.virtualorg.framework.ontology;

import java.util.*;

/**
 * Represents a concept in the organizational ontology.
 *
 * <p><b>Purpose</b><br>
 * Concepts are the building blocks of the organizational vocabulary.
 * They define what terms mean in the context of the organization,
 * enabling semantic interoperability between diverse agents.
 *
 * <p><b>Example Concepts</b><br>
 * - "Task" (parent of "CodeReview", "BugFix", "Feature")
 * - "Role" (parent of "Engineer", "Manager", "Architect")
 * - "Artifact" (parent of "PullRequest", "Document", "Deployment")
 *
 * @doc.type record
 * @doc.purpose Ontological concept definition
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record Concept(
        String id,
        String name,
        String description,
        Optional<String> parentId,
        Set<String> synonyms,
        Map<String, String> properties,
        Set<String> relationships
) {

    /**
     * Compact constructor with defaults.
     */
    public Concept {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Concept ID must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Concept name must not be blank");
        }
        description = description != null ? description : "";
        parentId = parentId != null ? parentId : Optional.empty();
        synonyms = synonyms != null ? Set.copyOf(synonyms) : Set.of();
        properties = properties != null ? Map.copyOf(properties) : Map.of();
        relationships = relationships != null ? Set.copyOf(relationships) : Set.of();
    }

    /**
     * Creates a new concept builder.
     */
    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    /**
     * Checks if this concept is a sub-concept of another.
     */
    public boolean isSubConceptOf(String parentConceptId) {
        return parentId.map(p -> p.equals(parentConceptId)).orElse(false);
    }

    /**
     * Checks if a term matches this concept (name or synonym).
     */
    public boolean matches(String term) {
        String lower = term.toLowerCase();
        return name.toLowerCase().equals(lower) ||
                synonyms.stream().anyMatch(s -> s.toLowerCase().equals(lower));
    }

    /**
     * Builder for Concept.
     */
    public static class Builder {
        private final String id;
        private final String name;
        private String description = "";
        private String parentId;
        private Set<String> synonyms = new HashSet<>();
        private Map<String, String> properties = new HashMap<>();
        private Set<String> relationships = new HashSet<>();

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder description(String desc) {
            this.description = desc;
            return this;
        }

        public Builder parent(String parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder synonym(String synonym) {
            this.synonyms.add(synonym);
            return this;
        }

        public Builder synonyms(String... syns) {
            this.synonyms.addAll(Arrays.asList(syns));
            return this;
        }

        public Builder property(String key, String value) {
            this.properties.put(key, value);
            return this;
        }

        public Builder relationship(String relatedConceptId) {
            this.relationships.add(relatedConceptId);
            return this;
        }

        public Concept build() {
            return new Concept(
                    id, name, description,
                    Optional.ofNullable(parentId),
                    synonyms, properties, relationships
            );
        }
    }
}
