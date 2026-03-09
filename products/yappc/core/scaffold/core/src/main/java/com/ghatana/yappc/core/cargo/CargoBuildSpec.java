package com.ghatana.yappc.core.cargo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Specification for Cargo.toml generation with comprehensive Rust project configuration. Supports
 * Cargo-specific features like workspace configuration, build scripts, and targets.
 *
 * @doc.type class
 * @doc.purpose Specification for Cargo.toml generation with comprehensive Rust project configuration. Supports
 * @doc.layer platform
 * @doc.pattern Specification
 */
public class CargoBuildSpec {

    private final String name;
    private final String version;
    private final String edition;
    private final String description;
    private final List<String> authors;
    private final String license;
    private final String repository;
    private final String homepage;
    private final String documentation;
    private final List<String> keywords;
    private final List<String> categories;
    private final boolean publish;

    // Dependencies
    private final Map<String, CargoDependency> dependencies;
    private final Map<String, CargoDependency> devDependencies;
    private final Map<String, CargoDependency> buildDependencies;

    // Build configuration
    private final List<CargoBinary> binaries;
    private final List<CargoLibrary> libraries;
    private final List<CargoExample> examples;
    private final List<CargoTest> tests;
    private final List<CargoBench> benches;

    // Build settings
    private final CargoBuild build;
    private final CargoFeatures features;
    private final CargoWorkspace workspace;

    // Metadata
    private final Map<String, Object> metadata;

    @JsonCreator
    public CargoBuildSpec(
            @JsonProperty("name") String name,
            @JsonProperty("version") String version,
            @JsonProperty("edition") String edition,
            @JsonProperty("description") String description,
            @JsonProperty("authors") List<String> authors,
            @JsonProperty("license") String license,
            @JsonProperty("repository") String repository,
            @JsonProperty("homepage") String homepage,
            @JsonProperty("documentation") String documentation,
            @JsonProperty("keywords") List<String> keywords,
            @JsonProperty("categories") List<String> categories,
            @JsonProperty("publish") boolean publish,
            @JsonProperty("dependencies") Map<String, CargoDependency> dependencies,
            @JsonProperty("devDependencies") Map<String, CargoDependency> devDependencies,
            @JsonProperty("buildDependencies") Map<String, CargoDependency> buildDependencies,
            @JsonProperty("binaries") List<CargoBinary> binaries,
            @JsonProperty("libraries") List<CargoLibrary> libraries,
            @JsonProperty("examples") List<CargoExample> examples,
            @JsonProperty("tests") List<CargoTest> tests,
            @JsonProperty("benches") List<CargoBench> benches,
            @JsonProperty("build") CargoBuild build,
            @JsonProperty("features") CargoFeatures features,
            @JsonProperty("workspace") CargoWorkspace workspace,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.edition = edition != null ? edition : "2021";
        this.description = description;
        this.authors = authors != null ? List.copyOf(authors) : List.of();
        this.license = license;
        this.repository = repository;
        this.homepage = homepage;
        this.documentation = documentation;
        this.keywords = keywords != null ? List.copyOf(keywords) : List.of();
        this.categories = categories != null ? List.copyOf(categories) : List.of();
        this.publish = publish;
        this.dependencies = dependencies != null ? Map.copyOf(dependencies) : Map.of();
        this.devDependencies = devDependencies != null ? Map.copyOf(devDependencies) : Map.of();
        this.buildDependencies =
                buildDependencies != null ? Map.copyOf(buildDependencies) : Map.of();
        this.binaries = binaries != null ? List.copyOf(binaries) : List.of();
        this.libraries = libraries != null ? List.copyOf(libraries) : List.of();
        this.examples = examples != null ? List.copyOf(examples) : List.of();
        this.tests = tests != null ? List.copyOf(tests) : List.of();
        this.benches = benches != null ? List.copyOf(benches) : List.of();
        this.build = build;
        this.features = features;
        this.workspace = workspace;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getEdition() {
        return edition;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public String getLicense() {
        return license;
    }

    public String getRepository() {
        return repository;
    }

    public String getHomepage() {
        return homepage;
    }

    public String getDocumentation() {
        return documentation;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public List<String> getCategories() {
        return categories;
    }

    public boolean isPublish() {
        return publish;
    }

    public Map<String, CargoDependency> getDependencies() {
        return dependencies;
    }

    public Map<String, CargoDependency> getDevDependencies() {
        return devDependencies;
    }

    public Map<String, CargoDependency> getBuildDependencies() {
        return buildDependencies;
    }

    public List<CargoBinary> getBinaries() {
        return binaries;
    }

    public List<CargoLibrary> getLibraries() {
        return libraries;
    }

    public List<CargoExample> getExamples() {
        return examples;
    }

    public List<CargoTest> getTests() {
        return tests;
    }

    public List<CargoBench> getBenches() {
        return benches;
    }

    public CargoBuild getBuild() {
        return build;
    }

    public CargoFeatures getFeatures() {
        return features;
    }

    public CargoWorkspace getWorkspace() {
        return workspace;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
 * Cargo dependency specification */
    public static class CargoDependency {
        private final String version;
        private final String path;
        private final String git;
        private final String branch;
        private final String tag;
        private final String rev;
        private final List<String> features;
        private final boolean defaultFeatures;
        private final boolean optional;

        @JsonCreator
        public CargoDependency(
                @JsonProperty("version") String version,
                @JsonProperty("path") String path,
                @JsonProperty("git") String git,
                @JsonProperty("branch") String branch,
                @JsonProperty("tag") String tag,
                @JsonProperty("rev") String rev,
                @JsonProperty("features") List<String> features,
                @JsonProperty("defaultFeatures") boolean defaultFeatures,
                @JsonProperty("optional") boolean optional) {
            this.version = version;
            this.path = path;
            this.git = git;
            this.branch = branch;
            this.tag = tag;
            this.rev = rev;
            this.features = features != null ? List.copyOf(features) : List.of();
            this.defaultFeatures = defaultFeatures;
            this.optional = optional;
        }

        public String getVersion() {
            return version;
        }

        public String getPath() {
            return path;
        }

        public String getGit() {
            return git;
        }

        public String getBranch() {
            return branch;
        }

        public String getTag() {
            return tag;
        }

        public String getRev() {
            return rev;
        }

        public List<String> getFeatures() {
            return features;
        }

        public boolean isDefaultFeatures() {
            return defaultFeatures;
        }

        public boolean isOptional() {
            return optional;
        }
    }

    /**
 * Cargo binary target */
    public static class CargoBinary {
        private final String name;
        private final String path;

        @JsonCreator
        public CargoBinary(@JsonProperty("name") String name, @JsonProperty("path") String path) {
            this.name = Objects.requireNonNull(name, "binary name cannot be null");
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }
    }

    /**
 * Cargo library target */
    public static class CargoLibrary {
        private final String name;
        private final String path;
        private final List<String> crateType;

        @JsonCreator
        public CargoLibrary(
                @JsonProperty("name") String name,
                @JsonProperty("path") String path,
                @JsonProperty("crateType") List<String> crateType) {
            this.name = name;
            this.path = path;
            this.crateType = crateType != null ? List.copyOf(crateType) : List.of();
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public List<String> getCrateType() {
            return crateType;
        }
    }

    /**
 * Cargo example target */
    public static class CargoExample {
        private final String name;
        private final String path;

        @JsonCreator
        public CargoExample(@JsonProperty("name") String name, @JsonProperty("path") String path) {
            this.name = Objects.requireNonNull(name, "example name cannot be null");
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }
    }

    /**
 * Cargo test target */
    public static class CargoTest {
        private final String name;
        private final String path;
        private final boolean harness;

        @JsonCreator
        public CargoTest(
                @JsonProperty("name") String name,
                @JsonProperty("path") String path,
                @JsonProperty("harness") boolean harness) {
            this.name = Objects.requireNonNull(name, "test name cannot be null");
            this.path = path;
            this.harness = harness;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public boolean isHarness() {
            return harness;
        }
    }

    /**
 * Cargo benchmark target */
    public static class CargoBench {
        private final String name;
        private final String path;
        private final boolean harness;

        @JsonCreator
        public CargoBench(
                @JsonProperty("name") String name,
                @JsonProperty("path") String path,
                @JsonProperty("harness") boolean harness) {
            this.name = Objects.requireNonNull(name, "bench name cannot be null");
            this.path = path;
            this.harness = harness;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public boolean isHarness() {
            return harness;
        }
    }

    /**
 * Cargo build configuration */
    public static class CargoBuild {
        private final String rustcFlags;
        private final String rustdocFlags;
        private final String target;
        private final String targetDir;
        private final int jobs;

        @JsonCreator
        public CargoBuild(
                @JsonProperty("rustcFlags") String rustcFlags,
                @JsonProperty("rustdocFlags") String rustdocFlags,
                @JsonProperty("target") String target,
                @JsonProperty("targetDir") String targetDir,
                @JsonProperty("jobs") int jobs) {
            this.rustcFlags = rustcFlags;
            this.rustdocFlags = rustdocFlags;
            this.target = target;
            this.targetDir = targetDir;
            this.jobs = jobs;
        }

        public String getRustcFlags() {
            return rustcFlags;
        }

        public String getRustdocFlags() {
            return rustdocFlags;
        }

        public String getTarget() {
            return target;
        }

        public String getTargetDir() {
            return targetDir;
        }

        public int getJobs() {
            return jobs;
        }
    }

    /**
 * Cargo features configuration */
    public static class CargoFeatures {
        private final Map<String, List<String>> features;
        private final List<String> defaultFeatures;

        @JsonCreator
        public CargoFeatures(
                @JsonProperty("features") Map<String, List<String>> features,
                @JsonProperty("defaultFeatures") List<String> defaultFeatures) {
            this.features = features != null ? Map.copyOf(features) : Map.of();
            this.defaultFeatures =
                    defaultFeatures != null ? List.copyOf(defaultFeatures) : List.of();
        }

        public Map<String, List<String>> getFeatures() {
            return features;
        }

        public List<String> getDefaultFeatures() {
            return defaultFeatures;
        }
    }

    /**
 * Cargo workspace configuration */
    public static class CargoWorkspace {
        private final List<String> members;
        private final List<String> exclude;
        private final Map<String, CargoDependency> dependencies;

        @JsonCreator
        public CargoWorkspace(
                @JsonProperty("members") List<String> members,
                @JsonProperty("exclude") List<String> exclude,
                @JsonProperty("dependencies") Map<String, CargoDependency> dependencies) {
            this.members = members != null ? List.copyOf(members) : List.of();
            this.exclude = exclude != null ? List.copyOf(exclude) : List.of();
            this.dependencies = dependencies != null ? Map.copyOf(dependencies) : Map.of();
        }

        public List<String> getMembers() {
            return members;
        }

        public List<String> getExclude() {
            return exclude;
        }

        public Map<String, CargoDependency> getDependencies() {
            return dependencies;
        }
    }
}
