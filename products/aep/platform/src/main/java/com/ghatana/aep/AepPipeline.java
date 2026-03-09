/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep;

import java.util.List;
import java.util.Objects;

/**
 * Represents a pipeline for AEP execution.
 *
 * @doc.type class
 * @doc.purpose Immutable value object representing an AEP execution pipeline with ordered operator references
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class AepPipeline {
    private final String id;
    private final String name;
    private final String description;
    private final List<String> operatorIds;
    private final boolean enabled;

    public AepPipeline(String id, String name, String description, List<String> operatorIds, boolean enabled) {
        this.id = Objects.requireNonNull(id, "id required");
        this.name = Objects.requireNonNull(name, "name required");
        this.description = description;
        this.operatorIds = Objects.requireNonNull(operatorIds, "operatorIds required");
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getOperatorIds() {
        return operatorIds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private List<String> operatorIds;
        private boolean enabled = true;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder operatorIds(List<String> operatorIds) {
            this.operatorIds = operatorIds;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public AepPipeline build() {
            return new AepPipeline(id, name, description, operatorIds, enabled);
        }
    }
}
