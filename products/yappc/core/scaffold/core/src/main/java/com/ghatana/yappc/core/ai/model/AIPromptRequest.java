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

package com.ghatana.yappc.core.ai.model;

/**
 * Day 16: AI prompt request model for LangChain4J integration. Represents user input for AI-powered
 * pack recommendation and spec generation.
 *
 * @doc.type class
 * @doc.purpose Day 16: AI prompt request model for LangChain4J integration. Represents user input for AI-powered
 * @doc.layer platform
 * @doc.pattern Component
 */
public class AIPromptRequest {
    private String prompt;
    private String projectType;
    private String primaryLanguage;
    private String preferredFramework;
    private String aiModel;
    private int maxResults;
    private boolean includeExplanations;
    private boolean enableSafetyGuardrails;
    private boolean validateDependencyAllowlist;
    private boolean detectSecrets;

    private AIPromptRequest(Builder builder) {
        this.prompt = builder.prompt;
        this.projectType = builder.projectType;
        this.primaryLanguage = builder.primaryLanguage;
        this.preferredFramework = builder.preferredFramework;
        this.aiModel = builder.aiModel;
        this.maxResults = builder.maxResults;
        this.includeExplanations = builder.includeExplanations;
        this.enableSafetyGuardrails = builder.enableSafetyGuardrails;
        this.validateDependencyAllowlist = builder.validateDependencyAllowlist;
        this.detectSecrets = builder.detectSecrets;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String prompt;
        private String projectType;
        private String primaryLanguage;
        private String preferredFramework;
        private String aiModel = "gpt-4";
        private int maxResults = 5;
        private boolean includeExplanations = false;
        private boolean enableSafetyGuardrails = true;
        private boolean validateDependencyAllowlist = true;
        private boolean detectSecrets = true;

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder projectType(String projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder primaryLanguage(String primaryLanguage) {
            this.primaryLanguage = primaryLanguage;
            return this;
        }

        public Builder preferredFramework(String preferredFramework) {
            this.preferredFramework = preferredFramework;
            return this;
        }

        public Builder aiModel(String aiModel) {
            this.aiModel = aiModel;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder includeExplanations(boolean includeExplanations) {
            this.includeExplanations = includeExplanations;
            return this;
        }

        public Builder enableSafetyGuardrails(boolean enableSafetyGuardrails) {
            this.enableSafetyGuardrails = enableSafetyGuardrails;
            return this;
        }

        public Builder validateDependencyAllowlist(boolean validateDependencyAllowlist) {
            this.validateDependencyAllowlist = validateDependencyAllowlist;
            return this;
        }

        public Builder detectSecrets(boolean detectSecrets) {
            this.detectSecrets = detectSecrets;
            return this;
        }

        public AIPromptRequest build() {
            return new AIPromptRequest(this);
        }
    }

    // Getters
    public String getPrompt() {
        return prompt;
    }

    public String getProjectType() {
        return projectType;
    }

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    public String getPreferredFramework() {
        return preferredFramework;
    }

    public String getAiModel() {
        return aiModel;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public boolean isIncludeExplanations() {
        return includeExplanations;
    }

    public boolean isEnableSafetyGuardrails() {
        return enableSafetyGuardrails;
    }

    public boolean isValidateDependencyAllowlist() {
        return validateDependencyAllowlist;
    }

    public boolean isDetectSecrets() {
        return detectSecrets;
    }
}
