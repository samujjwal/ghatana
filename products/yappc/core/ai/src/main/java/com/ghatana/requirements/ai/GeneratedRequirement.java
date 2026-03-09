package com.ghatana.requirements.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single AI-generated requirement.
 *
 * <p>
 * <b>Purpose</b><br>
 * Encapsulates a generated requirement with its metadata including type,
 * priority, acceptance criteria, source information, and confidence score.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * GeneratedRequirement requirement = GeneratedRequirement.builder()
 *     .description("User can login with email and password")
 *     .type(RequirementType.FUNCTIONAL)
 *     .priority("high")
 *     .addAcceptanceCriteria("GIVEN user has valid credentials")
 *     .addAcceptanceCriteria("WHEN user submits login form")
 *     .addAcceptanceCriteria("THEN user is authenticated")
 *     .confidence(0.95)
 *     .build();
 * }</pre>
 *
 * @see RequirementType
 * @see RequirementGenerationResponse
 * @doc.type class
 * @doc.purpose Single generated requirement representation
 * @doc.layer product
 * @doc.pattern DTO
 */
public class GeneratedRequirement {

    private final String id;
    private final String description;
    private final RequirementType type;
    private final String priority;
    private final List<String> acceptanceCriteria;
    private final String source;
    private final double confidence;
    private final Instant createdAt;
    private final String parentFeature;

    private GeneratedRequirement(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.description = Objects.requireNonNull(builder.description, "description is required");
        this.type = Objects.requireNonNull(builder.type, "type is required");
        this.priority = builder.priority != null ? builder.priority : "medium";
        this.acceptanceCriteria = Collections.unmodifiableList(new ArrayList<>(builder.acceptanceCriteria));
        this.source = builder.source != null ? builder.source : "ai-generated";
        this.confidence = builder.confidence;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.parentFeature = builder.parentFeature;

        validateConfidence(this.confidence);
    }

    private void validateConfidence(double confidence) {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0, got: " + confidence);
        }
    }

    /**
     * Gets unique requirement identifier.
     *
     * @return requirement ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets requirement description text.
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets requirement type.
     *
     * @return requirement type
     */
    public RequirementType getType() {
        return type;
    }

    /**
     * Gets priority level (high, medium, low).
     *
     * @return priority
     */
    public String getPriority() {
        return priority;
    }

    /**
     * Gets list of acceptance criteria (Given-When-Then format).
     *
     * @return unmodifiable list of acceptance criteria
     */
    public List<String> getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    /**
     * Gets source of requirement generation.
     *
     * @return source (e.g., "ai-generated", "user-input")
     */
    public String getSource() {
        return source;
    }

    /**
     * Gets confidence score of generation quality.
     *
     * @return confidence between 0.0 and 1.0
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Gets creation timestamp.
     *
     * @return created timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets parent feature this requirement belongs to.
     *
     * @return parent feature description
     */
    public String getParentFeature() {
        return parentFeature;
    }

    /**
     * Checks if this requirement has high confidence (>= 0.8).
     *
     * @return true if high confidence
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    /**
     * Checks if acceptance criteria are included.
     *
     * @return true if criteria present
     */
    public boolean hasAcceptanceCriteria() {
        return !acceptanceCriteria.isEmpty();
    }

    /**
     * Creates builder for GeneratedRequirement.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for GeneratedRequirement.
     */
    public static class Builder {

        private String id;
        private String description;
        private RequirementType type;
        private String priority;
        private List<String> acceptanceCriteria = new ArrayList<>();
        private String source;
        private double confidence = 1.0;
        private Instant createdAt;
        private String parentFeature;

        /**
         * Sets requirement ID.
         *
         * @param id unique identifier
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets requirement description.
         *
         * @param description requirement text
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets requirement type.
         *
         * @param type requirement type
         * @return this builder
         */
        public Builder type(RequirementType type) {
            this.type = type;
            return this;
        }

        /**
         * Sets priority level.
         *
         * @param priority priority (high, medium, low)
         * @return this builder
         */
        public Builder priority(String priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets acceptance criteria list.
         *
         * @param acceptanceCriteria list of criteria
         * @return this builder
         */
        public Builder acceptanceCriteria(List<String> acceptanceCriteria) {
            this.acceptanceCriteria = acceptanceCriteria != null ? acceptanceCriteria : new ArrayList<>();
            return this;
        }

        /**
         * Adds single acceptance criterion.
         *
         * @param criterion criterion text
         * @return this builder
         */
        public Builder addAcceptanceCriteria(String criterion) {
            if (criterion != null && !criterion.trim().isEmpty()) {
                this.acceptanceCriteria.add(criterion);
            }
            return this;
        }

        /**
         * Sets source identifier.
         *
         * @param source source of generation
         * @return this builder
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * Sets confidence score.
         *
         * @param confidence score between 0.0 and 1.0
         * @return this builder
         */
        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        /**
         * Sets creation timestamp.
         *
         * @param createdAt timestamp
         * @return this builder
         */
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Sets parent feature.
         *
         * @param parentFeature feature description
         * @return this builder
         */
        public Builder parentFeature(String parentFeature) {
            this.parentFeature = parentFeature;
            return this;
        }

        /**
         * Builds GeneratedRequirement instance.
         *
         * @return new requirement instance
         * @throws NullPointerException if required fields are null
         * @throws IllegalArgumentException if confidence out of range
         */
        public GeneratedRequirement build() {
            return new GeneratedRequirement(this);
        }
    }

    @Override
    public String toString() {
        return "GeneratedRequirement{"
                + "id='" + id + '\''
                + ", type=" + type
                + ", priority='" + priority + '\''
                + ", confidence=" + confidence
                + ", criteriaCount=" + acceptanceCriteria.size()
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GeneratedRequirement that = (GeneratedRequirement) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
