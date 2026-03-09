/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.rca;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Day 27: Additional context information for RCA analysis. Provides project metadata to improve AI
 * analysis accuracy.
 *
 * @doc.type class
 * @doc.purpose Day 27: Additional context information for RCA analysis. Provides project metadata to improve AI
 * @doc.layer platform
 * @doc.pattern Component
 */
public class RCAContext {

    @JsonProperty("projectName")
    private String projectName;

    @JsonProperty("projectType")
    private String projectType; // gradle, nx, cargo, etc.

    @JsonProperty("recentChanges")
    private List<String> recentChanges;

    @JsonProperty("dependencies")
    private List<String> dependencies;

    @JsonProperty("environment")
    private Map<String, String> environment;

    @JsonProperty("previousFailures")
    private List<String> previousFailures;

    @JsonProperty("customData")
    private Map<String, Object> customData;

    // Constructor
    public RCAContext() {}

    public RCAContext(
            String projectName,
            String projectType,
            List<String> recentChanges,
            List<String> dependencies,
            Map<String, String> environment,
            List<String> previousFailures,
            Map<String, Object> customData) {
        this.projectName = projectName;
        this.projectType = projectType;
        this.recentChanges = recentChanges;
        this.dependencies = dependencies;
        this.environment = environment;
        this.previousFailures = previousFailures;
        this.customData = customData;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    // Getters and setters
    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public List<String> getRecentChanges() {
        return recentChanges;
    }

    public void setRecentChanges(List<String> recentChanges) {
        this.recentChanges = recentChanges;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public List<String> getPreviousFailures() {
        return previousFailures;
    }

    public void setPreviousFailures(List<String> previousFailures) {
        this.previousFailures = previousFailures;
    }

    public Map<String, Object> getCustomData() {
        return customData;
    }

    public void setCustomData(Map<String, Object> customData) {
        this.customData = customData;
    }

    public static class Builder {
        private String projectName;
        private String projectType;
        private List<String> recentChanges;
        private List<String> dependencies;
        private Map<String, String> environment;
        private List<String> previousFailures;
        private Map<String, Object> customData;

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder projectType(String projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder recentChanges(List<String> recentChanges) {
            this.recentChanges = recentChanges;
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public Builder previousFailures(List<String> previousFailures) {
            this.previousFailures = previousFailures;
            return this;
        }

        public Builder customData(Map<String, Object> customData) {
            this.customData = customData;
            return this;
        }

        public RCAContext build() {
            return new RCAContext(
                    projectName,
                    projectType,
                    recentChanges,
                    dependencies,
                    environment,
                    previousFailures,
                    customData);
        }
    }
}
