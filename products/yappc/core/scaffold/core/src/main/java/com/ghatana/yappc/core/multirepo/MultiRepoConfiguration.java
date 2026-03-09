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

package com.ghatana.yappc.core.multirepo;

import java.util.List;
import java.util.Map;

/**
 * Day 21: Multi-repository workspace configuration. Contains coordination settings for workspaces
 * spanning multiple repositories.
 *
 * @doc.type class
 * @doc.purpose Day 21: Multi-repository workspace configuration. Contains coordination settings for workspaces
 * @doc.layer platform
 * @doc.pattern Configuration
 */
public class MultiRepoConfiguration {

    private String workspaceName;
    private List<String> repositories;
    private String orchestrationStrategy;
    private boolean crossRepoDepencencyManagement;
    private Map<String, Object> sharedConfiguration;

    // Default constructor for Jackson
    public MultiRepoConfiguration() {}

    private MultiRepoConfiguration(Builder builder) {
        this.workspaceName = builder.workspaceName;
        this.repositories = builder.repositories;
        this.orchestrationStrategy = builder.orchestrationStrategy;
        this.crossRepoDepencencyManagement = builder.crossRepoDepencencyManagement;
        this.sharedConfiguration = builder.sharedConfiguration;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getWorkspaceName() {
        return workspaceName;
    }

    public List<String> getRepositories() {
        return repositories;
    }

    public String getOrchestrationStrategy() {
        return orchestrationStrategy;
    }

    public boolean isCrossRepoDepencencyManagement() {
        return crossRepoDepencencyManagement;
    }

    public Map<String, Object> getSharedConfiguration() {
        return sharedConfiguration;
    }

    // Setters (for Jackson)
    public void setWorkspaceName(String workspaceName) {
        this.workspaceName = workspaceName;
    }

    public void setRepositories(List<String> repositories) {
        this.repositories = repositories;
    }

    public void setOrchestrationStrategy(String orchestrationStrategy) {
        this.orchestrationStrategy = orchestrationStrategy;
    }

    public void setCrossRepoDepencencyManagement(boolean crossRepoDepencencyManagement) {
        this.crossRepoDepencencyManagement = crossRepoDepencencyManagement;
    }

    public void setSharedConfiguration(Map<String, Object> sharedConfiguration) {
        this.sharedConfiguration = sharedConfiguration;
    }

    public static class Builder {
        private String workspaceName;
        private List<String> repositories;
        private String orchestrationStrategy;
        private boolean crossRepoDepencencyManagement;
        private Map<String, Object> sharedConfiguration;

        public Builder workspaceName(String workspaceName) {
            this.workspaceName = workspaceName;
            return this;
        }

        public Builder repositories(List<String> repositories) {
            this.repositories = repositories;
            return this;
        }

        public Builder orchestrationStrategy(String orchestrationStrategy) {
            this.orchestrationStrategy = orchestrationStrategy;
            return this;
        }

        public Builder crossRepoDepencencyManagement(boolean crossRepoDepencencyManagement) {
            this.crossRepoDepencencyManagement = crossRepoDepencencyManagement;
            return this;
        }

        public Builder sharedConfiguration(Map<String, Object> sharedConfiguration) {
            this.sharedConfiguration = sharedConfiguration;
            return this;
        }

        public MultiRepoConfiguration build() {
            return new MultiRepoConfiguration(this);
        }
    }
}
