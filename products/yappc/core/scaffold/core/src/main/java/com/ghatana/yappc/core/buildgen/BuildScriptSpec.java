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

package com.ghatana.yappc.core.buildgen;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Day 28: Build script generation request specification. Captures requirements for AI-powered build
 * script generation.
 *
 * @doc.type class
 * @doc.purpose Day 28: Build script generation request specification. Captures requirements for AI-powered build
 * @doc.layer platform
 * @doc.pattern Specification
 */
public class BuildScriptSpec {

    @JsonProperty("projectType")
    private String projectType; // "java", "kotlin", "scala", "groovy"

    @JsonProperty("buildTool")
    private String buildTool; // "gradle", "maven"

    @JsonProperty("javaVersion")
    private String javaVersion; // "11", "17", "21"

    @JsonProperty("dependencies")
    private List<Dependency> dependencies;

    @JsonProperty("plugins")
    private List<Plugin> plugins;

    @JsonProperty("repositories")
    private List<Repository> repositories;

    @JsonProperty("testFrameworks")
    private List<String> testFrameworks; // "junit5", "testng", "spock"

    @JsonProperty("qualityTools")
    private List<String> qualityTools; // "spotless", "jacoco", "detekt", "checkstyle"

    @JsonProperty("packaging")
    private String packaging; // "jar", "war", "ear"

    @JsonProperty("mainClass")
    private String mainClass;

    @JsonProperty("buildRequirements")
    private BuildRequirements buildRequirements;

    @JsonProperty("customTasks")
    private List<CustomTask> customTasks;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    // Constructors
    public BuildScriptSpec() {}

    public BuildScriptSpec(
            String projectType,
            String buildTool,
            String javaVersion,
            List<Dependency> dependencies,
            List<Plugin> plugins,
            List<Repository> repositories,
            List<String> testFrameworks,
            List<String> qualityTools,
            String packaging,
            String mainClass,
            BuildRequirements buildRequirements,
            List<CustomTask> customTasks,
            Map<String, Object> metadata) {
        this.projectType = projectType;
        this.buildTool = buildTool;
        this.javaVersion = javaVersion;
        this.dependencies = dependencies;
        this.plugins = plugins;
        this.repositories = repositories;
        this.testFrameworks = testFrameworks;
        this.qualityTools = qualityTools;
        this.packaging = packaging;
        this.mainClass = mainClass;
        this.buildRequirements = buildRequirements;
        this.customTasks = customTasks;
        this.metadata = metadata;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    // Getters and setters
    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getBuildTool() {
        return buildTool;
    }

    public void setBuildTool(String buildTool) {
        this.buildTool = buildTool;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    public List<Repository> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<Repository> repositories) {
        this.repositories = repositories;
    }

    public List<String> getTestFrameworks() {
        return testFrameworks;
    }

    public void setTestFrameworks(List<String> testFrameworks) {
        this.testFrameworks = testFrameworks;
    }

    public List<String> getQualityTools() {
        return qualityTools;
    }

    public void setQualityTools(List<String> qualityTools) {
        this.qualityTools = qualityTools;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public BuildRequirements getBuildRequirements() {
        return buildRequirements;
    }

    public void setBuildRequirements(BuildRequirements buildRequirements) {
        this.buildRequirements = buildRequirements;
    }

    public List<CustomTask> getCustomTasks() {
        return customTasks;
    }

    public void setCustomTasks(List<CustomTask> customTasks) {
        this.customTasks = customTasks;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
 * Dependency specification */
    public static class Dependency {
        @JsonProperty("group")
        private String group;

        @JsonProperty("name")
        private String name;

        @JsonProperty("version")
        private String version;

        @JsonProperty("scope")
        private String scope; // "implementation", "testImplementation", "api", "compileOnly"

        @JsonProperty("classifier")
        private String classifier;

        @JsonProperty("exclude")
        private List<String> exclude;

        // Constructors
        public Dependency() {}

        public Dependency(String group, String name, String version, String scope) {
            this.group = group;
            this.name = name;
            this.version = version;
            this.scope = scope;
        }

        // Getters and setters
        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public String getClassifier() {
            return classifier;
        }

        public void setClassifier(String classifier) {
            this.classifier = classifier;
        }

        public List<String> getExclude() {
            return exclude;
        }

        public void setExclude(List<String> exclude) {
            this.exclude = exclude;
        }
    }

    /**
 * Plugin specification */
    public static class Plugin {
        @JsonProperty("id")
        private String id;

        @JsonProperty("version")
        private String version;

        @JsonProperty("apply")
        private boolean apply = true;

        // Constructors
        public Plugin() {}

        public Plugin(String id, String version) {
            this.id = id;
            this.version = version;
        }

        public Plugin(String id, String version, boolean apply) {
            this.id = id;
            this.version = version;
            this.apply = apply;
        }

        // Getters and setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public boolean isApply() {
            return apply;
        }

        public void setApply(boolean apply) {
            this.apply = apply;
        }
    }

    /**
 * Repository specification */
    public static class Repository {
        @JsonProperty("name")
        private String name;

        @JsonProperty("url")
        private String url;

        @JsonProperty("type")
        private String type; // "maven", "ivy", "flatDir"

        // Constructors
        public Repository() {}

        public Repository(String name, String url, String type) {
            this.name = name;
            this.url = url;
            this.type = type;
        }

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /**
 * Build requirements and constraints */
    public static class BuildRequirements {
        @JsonProperty("minimumGradleVersion")
        private String minimumGradleVersion;

        @JsonProperty("jvmArgs")
        private List<String> jvmArgs;

        @JsonProperty("systemProperties")
        private Map<String, String> systemProperties;

        @JsonProperty("parallelExecution")
        private Boolean parallelExecution;

        @JsonProperty("configurationCache")
        private Boolean configurationCache;

        @JsonProperty("buildCache")
        private Boolean buildCache;

        // Constructors
        public BuildRequirements() {}

        // Getters and setters
        public String getMinimumGradleVersion() {
            return minimumGradleVersion;
        }

        public void setMinimumGradleVersion(String minimumGradleVersion) {
            this.minimumGradleVersion = minimumGradleVersion;
        }

        public List<String> getJvmArgs() {
            return jvmArgs;
        }

        public void setJvmArgs(List<String> jvmArgs) {
            this.jvmArgs = jvmArgs;
        }

        public Map<String, String> getSystemProperties() {
            return systemProperties;
        }

        public void setSystemProperties(Map<String, String> systemProperties) {
            this.systemProperties = systemProperties;
        }

        public Boolean getParallelExecution() {
            return parallelExecution;
        }

        public void setParallelExecution(Boolean parallelExecution) {
            this.parallelExecution = parallelExecution;
        }

        public Boolean getConfigurationCache() {
            return configurationCache;
        }

        public void setConfigurationCache(Boolean configurationCache) {
            this.configurationCache = configurationCache;
        }

        public Boolean getBuildCache() {
            return buildCache;
        }

        public void setBuildCache(Boolean buildCache) {
            this.buildCache = buildCache;
        }
    }

    /**
 * Custom task specification */
    public static class CustomTask {
        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private String type; // "Exec", "Copy", "Jar", "Test", "JavaExec", etc.

        @JsonProperty("description")
        private String description;

        @JsonProperty("group")
        private String group;

        @JsonProperty("dependsOn")
        private List<String> dependsOn;

        @JsonProperty("configuration")
        private Map<String, Object> configuration;

        // Constructors
        public CustomTask() {}

        public CustomTask(String name, String type, String description, String group) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.group = group;
        }

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public List<String> getDependsOn() {
            return dependsOn;
        }

        public void setDependsOn(List<String> dependsOn) {
            this.dependsOn = dependsOn;
        }

        public Map<String, Object> getConfiguration() {
            return configuration;
        }

        public void setConfiguration(Map<String, Object> configuration) {
            this.configuration = configuration;
        }
    }

    public static class Builder {
        private String projectType;
        private String buildTool;
        private String javaVersion;
        private List<Dependency> dependencies;
        private List<Plugin> plugins;
        private List<Repository> repositories;
        private List<String> testFrameworks;
        private List<String> qualityTools;
        private String packaging;
        private String mainClass;
        private BuildRequirements buildRequirements;
        private List<CustomTask> customTasks;
        private Map<String, Object> metadata;

        public Builder projectType(String projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder buildTool(String buildTool) {
            this.buildTool = buildTool;
            return this;
        }

        public Builder javaVersion(String javaVersion) {
            this.javaVersion = javaVersion;
            return this;
        }

        public Builder dependencies(List<Dependency> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder plugins(List<Plugin> plugins) {
            this.plugins = plugins;
            return this;
        }

        public Builder repositories(List<Repository> repositories) {
            this.repositories = repositories;
            return this;
        }

        public Builder testFrameworks(List<String> testFrameworks) {
            this.testFrameworks = testFrameworks;
            return this;
        }

        public Builder qualityTools(List<String> qualityTools) {
            this.qualityTools = qualityTools;
            return this;
        }

        public Builder packaging(String packaging) {
            this.packaging = packaging;
            return this;
        }

        public Builder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public Builder buildRequirements(BuildRequirements buildRequirements) {
            this.buildRequirements = buildRequirements;
            return this;
        }

        public Builder customTasks(List<CustomTask> customTasks) {
            this.customTasks = customTasks;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public BuildScriptSpec build() {
            return new BuildScriptSpec(
                    projectType,
                    buildTool,
                    javaVersion,
                    dependencies,
                    plugins,
                    repositories,
                    testFrameworks,
                    qualityTools,
                    packaging,
                    mainClass,
                    buildRequirements,
                    customTasks,
                    metadata);
        }
    }
}
