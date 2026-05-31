package com.ghatana.datacloud.context;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical context record for the Context Plane.
 *
 * <p>F2: Represents context metadata including tenant ID, collection/entity scope,
 * source, provenance, freshness timestamp, trust score, semantic tags, and policy classification.
 *
 * @param tenantId           tenant identifier
 * @param collectionId       collection scope (optional, for collection-level context)
 * @param entityId           entity scope (optional, for entity-level context)
 * @param source             source of the context (e.g., "knowledge-graph", "rag", "manual")
 * @param provenance         provenance information (origin, derivation path)
 * @param freshnessTimestamp timestamp when the context was last updated
 * @param trustScore         trust score (0-100) indicating reliability
 * @param semanticTags       semantic tags for categorization and retrieval
 * @param policyClassification policy classification (e.g., "public", "confidential", "restricted")
 * @param metadata           additional metadata as key-value pairs
 *
 * @doc.type class
 * @doc.purpose Canonical context record for Context Plane
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ContextRecord(
    String tenantId,
    String collectionId,
    String entityId,
    String source,
    String provenance,
    Instant freshnessTimestamp,
    Integer trustScore,
    List<String> semanticTags,
    String policyClassification,
    Map<String, Object> metadata
) {

    /**
     * Validates the invariants of a {@link ContextRecord}.
     */
    public ContextRecord {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(source, "source must not be null");
        if (source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        Objects.requireNonNull(freshnessTimestamp, "freshnessTimestamp must not be null");
        Objects.requireNonNull(trustScore, "trustScore must not be null");
        if (trustScore < 0 || trustScore > 100) {
            throw new IllegalArgumentException("trustScore must be between 0 and 100");
        }
        Objects.requireNonNull(policyClassification, "policyClassification must not be null");
        
        collectionId = collectionId == null ? null : collectionId;
        entityId = entityId == null ? null : entityId;
        provenance = provenance == null ? "" : provenance;
        semanticTags = semanticTags == null
            ? List.of()
            : Collections.unmodifiableList(List.copyOf(semanticTags));
        metadata = metadata == null
            ? Map.of()
            : Collections.unmodifiableMap(Map.copyOf(metadata));
    }

    /**
     * Serialises this record to a plain {@link Map} suitable for JSON serialisation.
     *
     * @return an unmodifiable map representation of this record
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tenantId", tenantId);
        result.put("collectionId", collectionId);
        result.put("entityId", entityId);
        result.put("source", source);
        result.put("provenance", provenance);
        result.put("freshnessTimestamp", freshnessTimestamp.toString());
        result.put("trustScore", trustScore);
        result.put("semanticTags", semanticTags);
        result.put("policyClassification", policyClassification);
        result.put("metadata", metadata);
        return Collections.unmodifiableMap(result);
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Returns a builder for constructing a {@link ContextRecord}.
     *
     * @param tenantId the tenant identifier
     * @return a new builder
     */
    public static Builder builder(String tenantId) {
        return new Builder(tenantId);
    }

    /**
     * Builder for {@link ContextRecord}.
     *
     * @doc.type class
     * @doc.purpose Builder for ContextRecord
     * @doc.layer product
     * @doc.pattern Builder
     */
    public static final class Builder {

        private final String tenantId;
        private String collectionId;
        private String entityId;
        private String source;
        private String provenance;
        private Instant freshnessTimestamp = Instant.now();
        private Integer trustScore = 50;
        private List<String> semanticTags = List.of();
        private String policyClassification = "public";
        private Map<String, Object> metadata = Map.of();

        private Builder(String tenantId) {
            this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        }

        /** Sets the collection ID. */
        public Builder collectionId(String collectionId) {
            this.collectionId = collectionId;
            return this;
        }

        /** Sets the entity ID. */
        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        /** Sets the source. */
        public Builder source(String source) {
            this.source = Objects.requireNonNull(source, "source");
            return this;
        }

        /** Sets the provenance. */
        public Builder provenance(String provenance) {
            this.provenance = provenance;
            return this;
        }

        /** Sets the freshness timestamp. */
        public Builder freshnessTimestamp(Instant freshnessTimestamp) {
            this.freshnessTimestamp = Objects.requireNonNull(freshnessTimestamp, "freshnessTimestamp");
            return this;
        }

        /** Sets the trust score (0-100). */
        public Builder trustScore(Integer trustScore) {
            this.trustScore = Objects.requireNonNull(trustScore, "trustScore");
            return this;
        }

        /** Sets the semantic tags. */
        public Builder semanticTags(List<String> semanticTags) {
            this.semanticTags = semanticTags;
            return this;
        }

        /** Sets the policy classification. */
        public Builder policyClassification(String policyClassification) {
            this.policyClassification = Objects.requireNonNull(policyClassification, "policyClassification");
            return this;
        }

        /** Sets the metadata. */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /** Builds the {@link ContextRecord}, validating all invariants. */
        public ContextRecord build() {
            return new ContextRecord(
                tenantId, collectionId, entityId, source, provenance,
                freshnessTimestamp, trustScore, semanticTags, policyClassification, metadata);
        }
    }
}
