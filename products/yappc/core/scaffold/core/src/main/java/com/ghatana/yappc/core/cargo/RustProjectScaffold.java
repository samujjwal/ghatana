package com.ghatana.yappc.core.cargo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Generated Rust project scaffold with source files and directory structure.
 * @doc.type class
 * @doc.purpose Generated Rust project scaffold with source files and directory structure.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class RustProjectScaffold {

    private final Map<String, String> sourceFiles;
    private final Map<String, String> testFiles;
    private final Map<String, String> exampleFiles;
    private final Map<String, String> benchFiles;
    private final List<String> directories;

    @JsonCreator
    public RustProjectScaffold(
            @JsonProperty("sourceFiles") Map<String, String> sourceFiles,
            @JsonProperty("testFiles") Map<String, String> testFiles,
            @JsonProperty("exampleFiles") Map<String, String> exampleFiles,
            @JsonProperty("benchFiles") Map<String, String> benchFiles,
            @JsonProperty("directories") List<String> directories) {
        this.sourceFiles = sourceFiles != null ? Map.copyOf(sourceFiles) : Map.of();
        this.testFiles = testFiles != null ? Map.copyOf(testFiles) : Map.of();
        this.exampleFiles = exampleFiles != null ? Map.copyOf(exampleFiles) : Map.of();
        this.benchFiles = benchFiles != null ? Map.copyOf(benchFiles) : Map.of();
        this.directories = directories != null ? List.copyOf(directories) : List.of();
    }

    public Map<String, String> getSourceFiles() {
        return sourceFiles;
    }

    public Map<String, String> getTestFiles() {
        return testFiles;
    }

    public Map<String, String> getExampleFiles() {
        return exampleFiles;
    }

    public Map<String, String> getBenchFiles() {
        return benchFiles;
    }

    public List<String> getDirectories() {
        return directories;
    }
}
