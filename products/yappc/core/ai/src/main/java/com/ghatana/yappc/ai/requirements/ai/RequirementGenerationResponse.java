package com.ghatana.yappc.ai.requirements.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Response containing AI-generated requirements.
 *
 * <p>
 * <b>Purpose</b><br>
 * Returns generated requirements along with metadata about the generation
 * process including token usage, latency, and persona used.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * RequirementGenerationResponse response = aiService.generateRequirements(request).getResult();
 *
 * for (GeneratedRequirement req : response.getRequirements()) {
 *     System.out.println(req.getDescription());
 * }
 *
 * System.out.println("Generated in " + response.getLatencyMs() + "ms");
 * System.out.println("Used " + response.getTokensUsed() + " tokens");
 * }</pre>
 *
 * @see RequirementGenerationRequest
 * @see GeneratedRequirement
 * @doc.type class
 * @doc.purpose AI requirement generation response
 * @doc.layer product
 * @doc.pattern DTO
 */
public class RequirementGenerationResponse {

    private final List<GeneratedRequirement> requirements;
    private final String personaId;
    private final String model;
    private final int tokensUsed;
    private final long latencyMs;
    private final Instant generatedAt;
    private final String requestId;

    private RequirementGenerationResponse(Builder builder) {
        this.requirements = Collections.unmodifiableList(new ArrayList<>(builder.requirements));
        this.personaId = builder.personaId;
        this.model = Objects.requireNonNull(builder.model, "model is required");
        this.tokensUsed = builder.tokensUsed;
        this.latencyMs = builder.latencyMs;
        this.generatedAt = Objects.requireNonNull(builder.generatedAt, "generatedAt is required");
        this.requestId = builder.requestId;
    }

    /**
     * Gets list of generated requirements.
     *
     * @return unmodifiable list of requirements
     */
    public List<GeneratedRequirement> getRequirements() {
        return requirements;
    }

    /**
     * Gets persona ID used for generation.
     *
     * @return persona ID, may be null if default persona used
     */
    public String getPersonaId() {
        return personaId;
    }

    /**
     * Gets LLM model used for generation.
     *
     * @return model name (e.g., "gpt-4", "gpt-3.5-turbo")
     */
    public String getModel() {
        return model;
    }

    /**
     * Gets total tokens consumed by generation.
     *
     * @return token count (prompt + completion tokens)
     */
    public int getTokensUsed() {
        return tokensUsed;
    }

    /**
     * Gets latency of generation operation.
     *
     * @return latency in milliseconds
     */
    public long getLatencyMs() {
        return latencyMs;
    }

    /**
     * Gets timestamp when requirements were generated.
     *
     * @return generation timestamp
     */
    public Instant getGeneratedAt() {
        return generatedAt;
    }

    /**
     * Gets unique request ID for tracing.
     *
     * @return request ID
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Gets number of requirements generated.
     *
     * @return count of requirements
     */
    public int getCount() {
        return requirements.size();
    }

    /**
     * Creates builder for RequirementGenerationResponse.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RequirementGenerationResponse.
     */
    public static class Builder {

        private List<GeneratedRequirement> requirements = new ArrayList<>();
        private String personaId;
        private String model;
        private int tokensUsed;
        private long latencyMs;
        private Instant generatedAt = Instant.now();
        private String requestId;

        /**
         * Sets requirements list.
         *
         * @param requirements list of generated requirements
         * @return this builder
         */
        public Builder requirements(List<GeneratedRequirement> requirements) {
            this.requirements = requirements != null ? requirements : new ArrayList<>();
            return this;
        }

        /**
         * Adds single requirement.
         *
         * @param requirement requirement to add
         * @return this builder
         */
        public Builder addRequirement(GeneratedRequirement requirement) {
            if (requirement != null) {
                this.requirements.add(requirement);
            }
            return this;
        }

        /**
         * Sets persona ID.
         *
         * @param personaId persona identifier
         * @return this builder
         */
        public Builder personaId(String personaId) {
            this.personaId = personaId;
            return this;
        }

        /**
         * Sets LLM model name.
         *
         * @param model model identifier
         * @return this builder
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Sets total tokens used.
         *
         * @param tokensUsed token count
         * @return this builder
         */
        public Builder tokensUsed(int tokensUsed) {
            this.tokensUsed = tokensUsed;
            return this;
        }

        /**
         * Sets generation latency.
         *
         * @param latencyMs latency in milliseconds
         * @return this builder
         */
        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        /**
         * Sets generation timestamp.
         *
         * @param generatedAt timestamp
         * @return this builder
         */
        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        /**
         * Sets request ID for tracing.
         *
         * @param requestId unique request identifier
         * @return this builder
         */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /**
         * Builds RequirementGenerationResponse instance.
         *
         * @return new response instance
         * @throws NullPointerException if required fields are null
         */
        public RequirementGenerationResponse build() {
            return new RequirementGenerationResponse(this);
        }
    }

    @Override
    public String toString() {
        return "RequirementGenerationResponse{"
                + "count=" + requirements.size()
                + ", personaId='" + personaId + '\''
                + ", model='" + model + '\''
                + ", tokensUsed=" + tokensUsed
                + ", latencyMs=" + latencyMs
                + ", requestId='" + requestId + '\''
                + '}';
    }
}
