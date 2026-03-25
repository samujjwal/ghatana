package com.ghatana.yappc.core.make;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Specification for Makefile generation with comprehensive C/C++ project configuration. Supports
 * Make-specific features like targets, variables, and cross-platform builds.
 *
 * @doc.type class
 * @doc.purpose Specification for Makefile generation with comprehensive C/C++ project configuration. Supports
 * @doc.layer platform
 * @doc.pattern Specification
 */
public class MakeBuildSpec {

    private final String projectName;
    private final String language; // "c", "cpp", "c++"
    private final String standard; // "c99", "c11", "c++11", "c++17", "c++20", etc.
    private final List<String> sourceFiles;
    private final List<String> headerFiles;
    private final List<String> includeDirectories;
    private final List<String> libraryDirectories;
    private final List<String> libraries;
    private final List<MakeTarget> targets;
    private final Map<String, String> variables;
    private final List<String> compilerFlags;
    private final List<String> linkerFlags;
    private final String compiler;
    private final String linker;
    private final String buildDirectory;
    private final String sourceDirectory;
    private final boolean enableDebugging;
    private final boolean enableOptimization;
    private final boolean enableWarnings;
    private final List<MakePlatform> platforms;
    private final List<MakeRule> customRules;

    @JsonCreator
    public MakeBuildSpec(
            @JsonProperty("projectName") String projectName,
            @JsonProperty("language") String language,
            @JsonProperty("standard") String standard,
            @JsonProperty("sourceFiles") List<String> sourceFiles,
            @JsonProperty("headerFiles") List<String> headerFiles,
            @JsonProperty("includeDirectories") List<String> includeDirectories,
            @JsonProperty("libraryDirectories") List<String> libraryDirectories,
            @JsonProperty("libraries") List<String> libraries,
            @JsonProperty("targets") List<MakeTarget> targets,
            @JsonProperty("variables") Map<String, String> variables,
            @JsonProperty("compilerFlags") List<String> compilerFlags,
            @JsonProperty("linkerFlags") List<String> linkerFlags,
            @JsonProperty("compiler") String compiler,
            @JsonProperty("linker") String linker,
            @JsonProperty("buildDirectory") String buildDirectory,
            @JsonProperty("sourceDirectory") String sourceDirectory,
            @JsonProperty("enableDebugging") boolean enableDebugging,
            @JsonProperty("enableOptimization") boolean enableOptimization,
            @JsonProperty("enableWarnings") boolean enableWarnings,
            @JsonProperty("platforms") List<MakePlatform> platforms,
            @JsonProperty("customRules") List<MakeRule> customRules) {
        this.projectName = Objects.requireNonNull(projectName, "projectName cannot be null");
        this.language = language != null ? language : "c";
        this.standard = standard;
        this.sourceFiles = sourceFiles != null ? List.copyOf(sourceFiles) : List.of();
        this.headerFiles = headerFiles != null ? List.copyOf(headerFiles) : List.of();
        this.includeDirectories =
                includeDirectories != null ? List.copyOf(includeDirectories) : List.of();
        this.libraryDirectories =
                libraryDirectories != null ? List.copyOf(libraryDirectories) : List.of();
        this.libraries = libraries != null ? List.copyOf(libraries) : List.of();
        this.targets = targets != null ? List.copyOf(targets) : List.of();
        this.variables = variables != null ? Map.copyOf(variables) : Map.of();
        this.compilerFlags = compilerFlags != null ? List.copyOf(compilerFlags) : List.of();
        this.linkerFlags = linkerFlags != null ? List.copyOf(linkerFlags) : List.of();
        this.compiler = compiler;
        this.linker = linker;
        this.buildDirectory = buildDirectory != null ? buildDirectory : "build";
        this.sourceDirectory = sourceDirectory != null ? sourceDirectory : "src";
        this.enableDebugging = enableDebugging;
        this.enableOptimization = enableOptimization;
        this.enableWarnings = enableWarnings;
        this.platforms = platforms != null ? List.copyOf(platforms) : List.of();
        this.customRules = customRules != null ? List.copyOf(customRules) : List.of();
    }

    // Getters
    public String getProjectName() {
        return projectName;
    }

    public String getLanguage() {
        return language;
    }

    public String getStandard() {
        return standard;
    }

    public List<String> getSourceFiles() {
        return sourceFiles;
    }

    public List<String> getHeaderFiles() {
        return headerFiles;
    }

    public List<String> getIncludeDirectories() {
        return includeDirectories;
    }

    public List<String> getLibraryDirectories() {
        return libraryDirectories;
    }

    public List<String> getLibraries() {
        return libraries;
    }

    public List<MakeTarget> getTargets() {
        return targets;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public List<String> getCompilerFlags() {
        return compilerFlags;
    }

    public List<String> getLinkerFlags() {
        return linkerFlags;
    }

    public String getCompiler() {
        return compiler;
    }

    public String getLinker() {
        return linker;
    }

    public String getBuildDirectory() {
        return buildDirectory;
    }

    public String getSourceDirectory() {
        return sourceDirectory;
    }

    public boolean isEnableDebugging() {
        return enableDebugging;
    }

    public boolean isEnableOptimization() {
        return enableOptimization;
    }

    public boolean isEnableWarnings() {
        return enableWarnings;
    }

    public List<MakePlatform> getPlatforms() {
        return platforms;
    }

    public List<MakeRule> getCustomRules() {
        return customRules;
    }

    /**
 * Make target specification */
    public static class MakeTarget {
        private final String name;
        private final List<String> dependencies;
        private final List<String> commands;
        private final String description;
        private final boolean phony;

        @JsonCreator
        public MakeTarget(
                @JsonProperty("name") String name,
                @JsonProperty("dependencies") List<String> dependencies,
                @JsonProperty("commands") List<String> commands,
                @JsonProperty("description") String description,
                @JsonProperty("phony") boolean phony) {
            this.name = Objects.requireNonNull(name, "target name cannot be null");
            this.dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
            this.commands = commands != null ? List.copyOf(commands) : List.of();
            this.description = description;
            this.phony = phony;
        }

        public String getName() {
            return name;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public List<String> getCommands() {
            return commands;
        }

        public String getDescription() {
            return description;
        }

        public boolean isPhony() {
            return phony;
        }
    }

    /**
 * Platform-specific configuration */
    public static class MakePlatform {
        private final String name; // "linux", "windows", "macos", "unix"
        private final String compiler;
        private final String linker;
        private final List<String> compilerFlags;
        private final List<String> linkerFlags;
        private final List<String> libraries;
        private final Map<String, String> variables;

        @JsonCreator
        public MakePlatform(
                @JsonProperty("name") String name,
                @JsonProperty("compiler") String compiler,
                @JsonProperty("linker") String linker,
                @JsonProperty("compilerFlags") List<String> compilerFlags,
                @JsonProperty("linkerFlags") List<String> linkerFlags,
                @JsonProperty("libraries") List<String> libraries,
                @JsonProperty("variables") Map<String, String> variables) {
            this.name = Objects.requireNonNull(name, "platform name cannot be null");
            this.compiler = compiler;
            this.linker = linker;
            this.compilerFlags = compilerFlags != null ? List.copyOf(compilerFlags) : List.of();
            this.linkerFlags = linkerFlags != null ? List.copyOf(linkerFlags) : List.of();
            this.libraries = libraries != null ? List.copyOf(libraries) : List.of();
            this.variables = variables != null ? Map.copyOf(variables) : Map.of();
        }

        public String getName() {
            return name;
        }

        public String getCompiler() {
            return compiler;
        }

        public String getLinker() {
            return linker;
        }

        public List<String> getCompilerFlags() {
            return compilerFlags;
        }

        public List<String> getLinkerFlags() {
            return linkerFlags;
        }

        public List<String> getLibraries() {
            return libraries;
        }

        public Map<String, String> getVariables() {
            return variables;
        }
    }

    /**
 * Custom Make rule specification */
    public static class MakeRule {
        private final String pattern;
        private final List<String> prerequisites;
        private final List<String> recipe;
        private final String description;

        @JsonCreator
        public MakeRule(
                @JsonProperty("pattern") String pattern,
                @JsonProperty("prerequisites") List<String> prerequisites,
                @JsonProperty("recipe") List<String> recipe,
                @JsonProperty("description") String description) {
            this.pattern = Objects.requireNonNull(pattern, "rule pattern cannot be null");
            this.prerequisites = prerequisites != null ? List.copyOf(prerequisites) : List.of();
            this.recipe = recipe != null ? List.copyOf(recipe) : List.of();
            this.description = description;
        }

        public String getPattern() {
            return pattern;
        }

        public List<String> getPrerequisites() {
            return prerequisites;
        }

        public List<String> getRecipe() {
            return recipe;
        }

        public String getDescription() {
            return description;
        }
    }
}
