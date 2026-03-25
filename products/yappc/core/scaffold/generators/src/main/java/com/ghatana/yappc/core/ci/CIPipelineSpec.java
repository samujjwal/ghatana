/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
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
package com.ghatana.yappc.core.ci;

import java.util.List;
import java.util.Map;

/**
 * Comprehensive CI/CD pipeline specification schema for multi-platform,
 * multi-language projects. Supports GitHub Actions, GitLab CI, Azure DevOps,
 * and custom CI systems.
 *
 * <p>
 * Week 8 Day 36: CI generator schema with cross-platform CI/CD support.
 */
public record CIPipelineSpec(
        String name,
        String version,
        CIPlatform platform,
        List<CIStage> stages,
        List<CITrigger> triggers,
        Map<String, String> environment,
        List<String> secrets,
        CIMatrix matrix,
        List<CIArtifact> artifacts,
        CISecurityConfig security,
        List<CINotification> notifications,
        Map<String, Object> platformSpecific) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String version = "1.0.0";
        private CIPlatform platform;
        private List<CIStage> stages = List.of();
        private List<CITrigger> triggers = List.of();
        private Map<String, String> environment = Map.of();
        private List<String> secrets = List.of();
        private CIMatrix matrix;
        private List<CIArtifact> artifacts = List.of();
        private CISecurityConfig security;
        private List<CINotification> notifications = List.of();
        private Map<String, Object> platformSpecific = Map.of();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder platform(CIPlatform platform) {
            this.platform = platform;
            return this;
        }

        public Builder stages(List<CIStage> stages) {
            this.stages = stages;
            return this;
        }

        public Builder triggers(List<CITrigger> triggers) {
            this.triggers = triggers;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public Builder secrets(List<String> secrets) {
            this.secrets = secrets;
            return this;
        }

        public Builder matrix(CIMatrix matrix) {
            this.matrix = matrix;
            return this;
        }

        public Builder artifacts(List<CIArtifact> artifacts) {
            this.artifacts = artifacts;
            return this;
        }

        public Builder security(CISecurityConfig security) {
            this.security = security;
            return this;
        }

        public Builder notifications(List<CINotification> notifications) {
            this.notifications = notifications;
            return this;
        }

        public Builder platformSpecific(Map<String, Object> platformSpecific) {
            this.platformSpecific = platformSpecific;
            return this;
        }

        public CIPipelineSpec build() {
            return new CIPipelineSpec(
                    name,
                    version,
                    platform,
                    stages,
                    triggers,
                    environment,
                    secrets,
                    matrix,
                    artifacts,
                    security,
                    notifications,
                    platformSpecific);
        }
    }

    /**
     * CI/CD platform types supported by YAPPC.
     *
     * @doc.type enum
     * @doc.purpose Comprehensive CI/CD pipeline specification schema for
     * multi-platform, multi-language projects
     * @doc.layer platform
     * @doc.pattern Enumeration
     */
    public enum CIPlatform {
        GITHUB_ACTIONS("github-actions", "GitHub Actions"),
        GITLAB_CI("gitlab-ci", "GitLab CI/CD"),
        AZURE_DEVOPS("azure-devops", "Azure DevOps Pipelines"),
        JENKINS("jenkins", "Jenkins Pipeline"),
        CIRCLECI("circleci", "CircleCI"),
        CUSTOM("custom", "Custom CI System");

        private final String key;
        private final String displayName;

        CIPlatform(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Individual CI/CD pipeline stage.
     */
    public static record CIStage(
            String name,
            List<CIJob> jobs,
            List<String> dependsOn,
            Map<String, String> environment,
            boolean parallel) {

        

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private List<CIJob> jobs = List.of();
        private List<String> dependsOn = List.of();
        private Map<String, String> environment = Map.of();
        private boolean parallel = false;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder jobs(List<CIJob> jobs) {
            this.jobs = jobs;
            return this;
        }

        public Builder dependsOn(List<String> dependsOn) {
            this.dependsOn = dependsOn;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public Builder parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        public CIStage build() {
            return new CIStage(name, jobs, dependsOn, environment, parallel);
        }
    }
}

/**
 * Individual CI/CD job within a stage.
 */
public static record CIJob(
        String name,
        String runsOn,
        List<CIStep> steps,
        Map<String, String> environment,
        int timeoutMinutes,
        boolean continueOnError) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String runsOn = "ubuntu-latest";
        private List<CIStep> steps = List.of();
        private Map<String, String> environment = Map.of();
        private int timeoutMinutes = 30;
        private boolean continueOnError = false;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder runsOn(String runsOn) {
            this.runsOn = runsOn;
            return this;
        }

        public Builder steps(List<CIStep> steps) {
            this.steps = steps;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public Builder timeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
            return this;
        }

        public Builder continueOnError(boolean continueOnError) {
            this.continueOnError = continueOnError;
            return this;
        }

        public CIJob build() {
            return new CIJob(name, runsOn, steps, environment, timeoutMinutes, continueOnError);
        }
    }
}

/**
 * Individual step within a CI/CD job.
 */
public static record CIStep(
        String name,
        CIStepType type,
        String action,
        String command,
        Map<String, String> with,
        Map<String, String> environment,
        boolean continueOnError) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private CIStepType type;
        private String action;
        private String command;
        private Map<String, String> with = Map.of();
        private Map<String, String> environment = Map.of();
        private boolean continueOnError = false;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(CIStepType type) {
            this.type = type;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder with(Map<String, String> with) {
            this.with = with;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public Builder continueOnError(boolean continueOnError) {
            this.continueOnError = continueOnError;
            return this;
        }

        public CIStep build() {
            return new CIStep(name, type, action, command, with, environment, continueOnError);
        }
    }

    public enum CIStepType {
        CHECKOUT,
        SETUP_LANGUAGE,
        RUN_COMMAND,
        USE_ACTION,
        CACHE,
        ARTIFACT_UPLOAD,
        ARTIFACT_DOWNLOAD,
        DEPLOY,
        SECURITY_SCAN,
        NOTIFICATION
    }
}

/**
 * CI/CD pipeline trigger configuration.
 */
public static record CITrigger(
        CITriggerType type,
        List<String> branches,
        List<String> paths,
        String schedule,
        Map<String, Object> conditions) {

    public enum CITriggerType {
        PUSH,
        PULL_REQUEST,
        SCHEDULE,
        WORKFLOW_DISPATCH,
        RELEASE,
        TAG,
        EXTERNAL_EVENT
    }
}

/**
 * Matrix build configuration for multi-platform/multi-language testing.
 */
public static record CIMatrix(
        List<String> operatingSystems,
        List<String> languageVersions,
        List<String> buildTools,
        Map<String, List<String>> customDimensions,
        List<Map<String, String>> excludes,
        boolean failFast) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<String> operatingSystems = List.of("ubuntu-latest");
        private List<String> languageVersions = List.of();
        private List<String> buildTools = List.of();
        private Map<String, List<String>> customDimensions = Map.of();
        private List<Map<String, String>> excludes = List.of();
        private boolean failFast = true;

        public Builder operatingSystems(List<String> operatingSystems) {
            this.operatingSystems = operatingSystems;
            return this;
        }

        public Builder languageVersions(List<String> languageVersions) {
            this.languageVersions = languageVersions;
            return this;
        }

        public Builder buildTools(List<String> buildTools) {
            this.buildTools = buildTools;
            return this;
        }

        public Builder customDimensions(Map<String, List<String>> customDimensions) {
            this.customDimensions = customDimensions;
            return this;
        }

        public Builder excludes(List<Map<String, String>> excludes) {
            this.excludes = excludes;
            return this;
        }

        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public CIMatrix build() {
            return new CIMatrix(
                    operatingSystems,
                    languageVersions,
                    buildTools,
                    customDimensions,
                    excludes,
                    failFast);
        }
    }
}

/**
 * CI/CD artifact configuration.
 */
public static record CIArtifact(
        String name, List<String> paths, int retentionDays, boolean required) {

}

/**
 * Security configuration for CI/CD pipelines.
 */
public static record CISecurityConfig(
        boolean enableSecurityScanning,
        List<String> scanTools,
        boolean blockOnCritical,
        boolean enableDependencyScanning,
        boolean enableSecretsScanning,
        Map<String, String> scanConfiguration) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean enableSecurityScanning = true;
        private List<String> scanTools = List.of("trivy", "snyk");
        private boolean blockOnCritical = true;
        private boolean enableDependencyScanning = true;
        private boolean enableSecretsScanning = true;
        private Map<String, String> scanConfiguration = Map.of();

        public Builder enableSecurityScanning(boolean enableSecurityScanning) {
            this.enableSecurityScanning = enableSecurityScanning;
            return this;
        }

        public Builder scanTools(List<String> scanTools) {
            this.scanTools = scanTools;
            return this;
        }

        public Builder blockOnCritical(boolean blockOnCritical) {
            this.blockOnCritical = blockOnCritical;
            return this;
        }

        public Builder enableDependencyScanning(boolean enableDependencyScanning) {
            this.enableDependencyScanning = enableDependencyScanning;
            return this;
        }

        public Builder enableSecretsScanning(boolean enableSecretsScanning) {
            this.enableSecretsScanning = enableSecretsScanning;
            return this;
        }

        public Builder scanConfiguration(Map<String, String> scanConfiguration) {
            this.scanConfiguration = scanConfiguration;
            return this;
        }

        public CISecurityConfig build() {
            return new CISecurityConfig(
                    enableSecurityScanning,
                    scanTools,
                    blockOnCritical,
                    enableDependencyScanning,
                    enableSecretsScanning,
                    scanConfiguration);
        }
    }
}

/**
 * Notification configuration for CI/CD events.
 */
public static record CINotification(
        CINotificationType type,
        List<String> channels,
        List<CINotificationEvent> events,
        Map<String, String> configuration) {

    public enum CINotificationType {
        EMAIL,
        SLACK,
        TEAMS,
        WEBHOOK,
        SMS
    }

    public enum CINotificationEvent {
        SUCCESS,
        FAILURE,
        CANCELLED,
        STARTED,
        SECURITY_ISSUE,
        DEPLOYMENT_SUCCESS,
        DEPLOYMENT_FAILURE
    }
}

/**
 * Creates a builder initialized with current values.
 */
public Builder toBuilder() {
        return new Builder()
                .name(name)
                .version(version)
                .platform(platform)
                .stages(stages)
                .triggers(triggers)
                .environment(environment)
                .secrets(secrets)
                .matrix(matrix)
                .artifacts(artifacts)
                .security(security)
                .notifications(notifications)
                .platformSpecific(platformSpecific);
    }
}
