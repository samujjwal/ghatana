package com.ghatana.yappc.core.maven;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Specification for Maven POM generation with comprehensive project configuration. Supports
 * Maven-specific features like profiles, build plugins, and dependency management.
 *
 * @doc.type class
 * @doc.purpose Specification for Maven POM generation with comprehensive project configuration. Supports
 * @doc.layer platform
 * @doc.pattern Specification
 */
public class MavenBuildSpec {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String packaging;
    private final String name;
    private final String description;
    private final String javaVersion;
    private final List<MavenDependency> dependencies;
    private final List<MavenPlugin> plugins;
    private final List<MavenProfile> profiles;
    private final Map<String, String> properties;
    private final MavenParent parent;
    private final List<MavenRepository> repositories;
    private final MavenDependencyManagement dependencyManagement;

    @JsonCreator
    public MavenBuildSpec(
            @JsonProperty("groupId") String groupId,
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("version") String version,
            @JsonProperty("packaging") String packaging,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("javaVersion") String javaVersion,
            @JsonProperty("dependencies") List<MavenDependency> dependencies,
            @JsonProperty("plugins") List<MavenPlugin> plugins,
            @JsonProperty("profiles") List<MavenProfile> profiles,
            @JsonProperty("properties") Map<String, String> properties,
            @JsonProperty("parent") MavenParent parent,
            @JsonProperty("repositories") List<MavenRepository> repositories,
            @JsonProperty("dependencyManagement") MavenDependencyManagement dependencyManagement) {
        this.groupId = Objects.requireNonNull(groupId, "groupId cannot be null");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.packaging = packaging != null ? packaging : "jar";
        this.name = name;
        this.description = description;
        this.javaVersion = javaVersion != null ? javaVersion : "17";
        this.dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        this.plugins = plugins != null ? List.copyOf(plugins) : List.of();
        this.profiles = profiles != null ? List.copyOf(profiles) : List.of();
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
        this.parent = parent;
        this.repositories = repositories != null ? List.copyOf(repositories) : List.of();
        this.dependencyManagement = dependencyManagement;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getPackaging() {
        return packaging;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public List<MavenDependency> getDependencies() {
        return dependencies;
    }

    public List<MavenPlugin> getPlugins() {
        return plugins;
    }

    public List<MavenProfile> getProfiles() {
        return profiles;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public MavenParent getParent() {
        return parent;
    }

    public List<MavenRepository> getRepositories() {
        return repositories;
    }

    public MavenDependencyManagement getDependencyManagement() {
        return dependencyManagement;
    }

    /**
 * Maven dependency specification */
    public static class MavenDependency {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String scope;
        private final String type;
        private final String classifier;
        private final List<MavenExclusion> exclusions;

        @JsonCreator
        public MavenDependency(
                @JsonProperty("groupId") String groupId,
                @JsonProperty("artifactId") String artifactId,
                @JsonProperty("version") String version,
                @JsonProperty("scope") String scope,
                @JsonProperty("type") String type,
                @JsonProperty("classifier") String classifier,
                @JsonProperty("exclusions") List<MavenExclusion> exclusions) {
            this.groupId = Objects.requireNonNull(groupId, "dependency groupId cannot be null");
            this.artifactId =
                    Objects.requireNonNull(artifactId, "dependency artifactId cannot be null");
            this.version = version;
            this.scope = scope;
            this.type = type;
            this.classifier = classifier;
            this.exclusions = exclusions != null ? List.copyOf(exclusions) : List.of();
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        public String getScope() {
            return scope;
        }

        public String getType() {
            return type;
        }

        public String getClassifier() {
            return classifier;
        }

        public List<MavenExclusion> getExclusions() {
            return exclusions;
        }
    }

    /**
 * Maven plugin specification */
    public static class MavenPlugin {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final Map<String, Object> configuration;
        private final List<MavenExecution> executions;

        @JsonCreator
        public MavenPlugin(
                @JsonProperty("groupId") String groupId,
                @JsonProperty("artifactId") String artifactId,
                @JsonProperty("version") String version,
                @JsonProperty("configuration") Map<String, Object> configuration,
                @JsonProperty("executions") List<MavenExecution> executions) {
            this.groupId = groupId;
            this.artifactId =
                    Objects.requireNonNull(artifactId, "plugin artifactId cannot be null");
            this.version = version;
            this.configuration = configuration != null ? Map.copyOf(configuration) : Map.of();
            this.executions = executions != null ? List.copyOf(executions) : List.of();
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        public Map<String, Object> getConfiguration() {
            return configuration;
        }

        public List<MavenExecution> getExecutions() {
            return executions;
        }
    }

    /**
 * Maven plugin execution specification */
    public static class MavenExecution {
        private final String id;
        private final String phase;
        private final List<String> goals;
        private final Map<String, Object> configuration;

        @JsonCreator
        public MavenExecution(
                @JsonProperty("id") String id,
                @JsonProperty("phase") String phase,
                @JsonProperty("goals") List<String> goals,
                @JsonProperty("configuration") Map<String, Object> configuration) {
            this.id = id;
            this.phase = phase;
            this.goals = goals != null ? List.copyOf(goals) : List.of();
            this.configuration = configuration != null ? Map.copyOf(configuration) : Map.of();
        }

        public String getId() {
            return id;
        }

        public String getPhase() {
            return phase;
        }

        public List<String> getGoals() {
            return goals;
        }

        public Map<String, Object> getConfiguration() {
            return configuration;
        }
    }

    /**
 * Maven profile specification */
    public static class MavenProfile {
        private final String id;
        private final boolean activeByDefault;
        private final Map<String, String> properties;
        private final List<MavenDependency> dependencies;
        private final List<MavenPlugin> plugins;

        @JsonCreator
        public MavenProfile(
                @JsonProperty("id") String id,
                @JsonProperty("activeByDefault") boolean activeByDefault,
                @JsonProperty("properties") Map<String, String> properties,
                @JsonProperty("dependencies") List<MavenDependency> dependencies,
                @JsonProperty("plugins") List<MavenPlugin> plugins) {
            this.id = Objects.requireNonNull(id, "profile id cannot be null");
            this.activeByDefault = activeByDefault;
            this.properties = properties != null ? Map.copyOf(properties) : Map.of();
            this.dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
            this.plugins = plugins != null ? List.copyOf(plugins) : List.of();
        }

        public String getId() {
            return id;
        }

        public boolean isActiveByDefault() {
            return activeByDefault;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public List<MavenDependency> getDependencies() {
            return dependencies;
        }

        public List<MavenPlugin> getPlugins() {
            return plugins;
        }
    }

    /**
 * Maven parent specification */
    public static class MavenParent {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String relativePath;

        @JsonCreator
        public MavenParent(
                @JsonProperty("groupId") String groupId,
                @JsonProperty("artifactId") String artifactId,
                @JsonProperty("version") String version,
                @JsonProperty("relativePath") String relativePath) {
            this.groupId = Objects.requireNonNull(groupId, "parent groupId cannot be null");
            this.artifactId =
                    Objects.requireNonNull(artifactId, "parent artifactId cannot be null");
            this.version = Objects.requireNonNull(version, "parent version cannot be null");
            this.relativePath = relativePath;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        public String getRelativePath() {
            return relativePath;
        }
    }

    /**
 * Maven repository specification */
    public static class MavenRepository {
        private final String id;
        private final String url;
        private final String layout;
        private final boolean releases;
        private final boolean snapshots;

        @JsonCreator
        public MavenRepository(
                @JsonProperty("id") String id,
                @JsonProperty("url") String url,
                @JsonProperty("layout") String layout,
                @JsonProperty("releases") boolean releases,
                @JsonProperty("snapshots") boolean snapshots) {
            this.id = Objects.requireNonNull(id, "repository id cannot be null");
            this.url = Objects.requireNonNull(url, "repository url cannot be null");
            this.layout = layout != null ? layout : "default";
            this.releases = releases;
            this.snapshots = snapshots;
        }

        public String getId() {
            return id;
        }

        public String getUrl() {
            return url;
        }

        public String getLayout() {
            return layout;
        }

        public boolean isReleases() {
            return releases;
        }

        public boolean isSnapshots() {
            return snapshots;
        }
    }

    /**
 * Maven exclusion specification */
    public static class MavenExclusion {
        private final String groupId;
        private final String artifactId;

        @JsonCreator
        public MavenExclusion(
                @JsonProperty("groupId") String groupId,
                @JsonProperty("artifactId") String artifactId) {
            this.groupId = Objects.requireNonNull(groupId, "exclusion groupId cannot be null");
            this.artifactId =
                    Objects.requireNonNull(artifactId, "exclusion artifactId cannot be null");
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }
    }

    /**
 * Maven dependency management specification */
    public static class MavenDependencyManagement {
        private final List<MavenDependency> dependencies;

        @JsonCreator
        public MavenDependencyManagement(
                @JsonProperty("dependencies") List<MavenDependency> dependencies) {
            this.dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        }

        public List<MavenDependency> getDependencies() {
            return dependencies;
        }
    }
}
