package com.ghatana.yappc.api.codegen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Preview of code to be generated.
 * 
 * @doc.type record
 * @doc.purpose Code preview before generation
 * @doc.layer product
 * @doc.pattern DTO
 */
public record CodePreview(
        @JsonProperty("files") List<FilePreview> files,
        @JsonProperty("structure") String structure) {
    
    /**
     * Preview of a single file.
     */
    public record FilePreview(
            @JsonProperty("path") String path,
            @JsonProperty("content") String content,
            @JsonProperty("language") String language) {
    }
}
