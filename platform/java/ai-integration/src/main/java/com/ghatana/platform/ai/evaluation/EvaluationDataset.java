/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.ai.evaluation;

import java.util.List;
import java.util.Map;

/**
 * Represents an evaluation dataset for AI/ML model evaluation.
 *
 * <p>Contains input-output pairs for testing model performance against known ground truth.
 * Supports classification, regression, and generation tasks.</p>
 *
 * @doc.type class
 * @doc.purpose Dataset structure for AI/ML model evaluation
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class EvaluationDataset {

    private final String name;
    private final String description;
    private final String version;
    private final List<EvaluationExample> examples;
    private final Map<String, String> metadata;

    private EvaluationDataset(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.version = builder.version;
        this.examples = List.copyOf(builder.examples);
        this.metadata = Map.copyOf(builder.metadata);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public List<EvaluationExample> getExamples() {
        return examples;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private String version = "1.0.0";
        private final List<EvaluationExample> examples = new java.util.ArrayList<>();
        private final Map<String, String> metadata = new java.util.HashMap<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder addExample(EvaluationExample example) {
            this.examples.add(example);
            return this;
        }

        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public EvaluationDataset build() {
            if (name == null) {
                throw new IllegalStateException("name is required");
            }
            return new EvaluationDataset(this);
        }
    }

    /** A single input-output pair for evaluation. */
    public record EvaluationExample(
        String id,
        Map<String, Object> input,
        Map<String, Object> expectedOutput,
        Map<String, Object> metadata
    ) {}
}
