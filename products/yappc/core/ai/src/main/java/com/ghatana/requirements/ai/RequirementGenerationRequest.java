package com.ghatana.requirements.ai;

import java.util.List;
import java.util.Objects;

/**
 * Request for generating requirements from a feature description.
 *
 * <p>
 * <b>Purpose</b><br>
 * Encapsulates all parameters needed to generate requirements using AI.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable value object - safe for concurrent use.
 *
 * @doc.type record
 * @doc.purpose Request for AI-powered requirements generation
 * @doc.layer application
 * @doc.pattern Value Object (immutable with builder)
 */
public class RequirementGenerationRequest {

    private final String featureDescription;
    private final String personaId;
    private final Integer count;
    private final RequirementType type;
    private final String context;
    private final boolean includeAcceptanceCriteria;

    private RequirementGenerationRequest(Builder builder) {
        this.featureDescription = Objects.requireNonNull(builder.featureDescription, "featureDescription is required");
        this.personaId = builder.personaId;
        this.count = builder.count != null ? builder.count : 5;
        this.type = builder.type;
        this.context = builder.context;
        this.includeAcceptanceCriteria = builder.includeAcceptanceCriteria;
    }

    public String getFeatureDescription() {
        return featureDescription;
    }

    public String getPersonaId() {
        return personaId;
    }

    public Integer getCount() {
        return count;
    }

    public RequirementType getType() {
        return type;
    }

    public String getContext() {
        return context;
    }

    public boolean isIncludeAcceptanceCriteria() {
        return includeAcceptanceCriteria;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String featureDescription;
        private String personaId;
        private Integer count;
        private RequirementType type;
        private String context;
        private boolean includeAcceptanceCriteria;

        public Builder featureDescription(String featureDescription) {
            this.featureDescription = featureDescription;
            return this;
        }

        public Builder personaId(String personaId) {
            this.personaId = personaId;
            return this;
        }

        public Builder count(Integer count) {
            this.count = count;
            return this;
        }

        public Builder type(RequirementType type) {
            this.type = type;
            return this;
        }

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public Builder includeAcceptanceCriteria(boolean includeAcceptanceCriteria) {
            this.includeAcceptanceCriteria = includeAcceptanceCriteria;
            return this;
        }

        public RequirementGenerationRequest build() {
            return new RequirementGenerationRequest(this);
        }
    }
}
