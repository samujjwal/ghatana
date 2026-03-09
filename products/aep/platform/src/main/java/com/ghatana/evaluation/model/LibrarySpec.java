package com.ghatana.evaluation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Specification for a library or component to be evaluated.
 * This class contains all the information needed to identify and retrieve a library.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LibrarySpec {

    /**
     * The group ID of the library (e.g., "org.apache.commons").
     */
    @NotBlank
    private String groupId;

    /**
     * The artifact ID of the library (e.g., "commons-lang3").
     */
    @NotBlank
    private String artifactId;

    /**
     * The version of the library (e.g., "3.12.0").
     */
    @NotBlank
    private String version;

    /**
     * The type of the library (e.g., "jar", "war", "pom").
     */
    private String type;

    /**
     * The classifier of the library (e.g., "sources", "javadoc").
     */
    private String classifier;

    /**
     * The repository URL where the library can be found.
     */
    private String repositoryUrl;

    /**
     * The source code URL for the library.
     */
    private String sourceUrl;

    /**
     * The documentation URL for the library.
     */
    private String documentationUrl;

    /**
     * The license(s) under which the library is distributed.
     */
    private List<String> licenses;

    /**
     * Dependencies required by the library.
     */
    private List<LibrarySpec> dependencies;

    /**
     * Additional metadata about the library.
     */
    private Map<String, Object> metadata;
}
