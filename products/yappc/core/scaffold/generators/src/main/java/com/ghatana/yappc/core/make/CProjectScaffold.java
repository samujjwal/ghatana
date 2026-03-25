package com.ghatana.yappc.core.make;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Generated C/C++ project scaffold with source files and directory structure.
 * @doc.type class
 * @doc.purpose Generated C/C++ project scaffold with source files and directory structure.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class CProjectScaffold {

    private final Map<String, String> sourceFiles;
    private final Map<String, String> headerFiles;
    private final Map<String, String> testFiles;
    private final Map<String, String> exampleFiles;
    private final List<String> directories;

    @JsonCreator
    public CProjectScaffold(
            @JsonProperty("sourceFiles") Map<String, String> sourceFiles,
            @JsonProperty("headerFiles") Map<String, String> headerFiles,
            @JsonProperty("testFiles") Map<String, String> testFiles,
            @JsonProperty("exampleFiles") Map<String, String> exampleFiles,
            @JsonProperty("directories") List<String> directories) {
        this.sourceFiles = sourceFiles != null ? Map.copyOf(sourceFiles) : Map.of();
        this.headerFiles = headerFiles != null ? Map.copyOf(headerFiles) : Map.of();
        this.testFiles = testFiles != null ? Map.copyOf(testFiles) : Map.of();
        this.exampleFiles = exampleFiles != null ? Map.copyOf(exampleFiles) : Map.of();
        this.directories = directories != null ? List.copyOf(directories) : List.of();
    }

    public Map<String, String> getSourceFiles() {
        return sourceFiles;
    }

    public Map<String, String> getHeaderFiles() {
        return headerFiles;
    }

    public Map<String, String> getTestFiles() {
        return testFiles;
    }

    public Map<String, String> getExampleFiles() {
        return exampleFiles;
    }

    public List<String> getDirectories() {
        return directories;
    }
}
