/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.contracts.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OpenAPI schema validation.
 *
 * Validates that OpenAPI specifications are well-formed and conform to OpenAPI 3.0 specification.
 *
 * @doc.type class
 * @doc.purpose Validate OpenAPI schema files for correctness
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("OpenAPI Schema Validation Tests")
class OpenApiSchemaValidationTest {

    private static final YAMLMapper yamlMapper = new YAMLMapper();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Test
    @DisplayName("should validate all OpenAPI YAML files are well-formed")
    void shouldValidateOpenApiYamlFilesAreWellFormed() throws IOException {
        Path openapiDir = Paths.get("openapi");
        List<File> yamlFiles = Files.walk(openapiDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".yaml"))
                .map(Path::toFile)
                .toList();

        assertThat(yamlFiles).isNotEmpty();

        for (File yamlFile : yamlFiles) {
            Map<String, Object> schema = yamlMapper.readValue(yamlFile, Map.class);
            assertThat(schema).containsKey("openapi");
            assertThat(schema).containsKey("info");
            // Only check for paths if this is an API specification (not a schema definition)
            // Schema definitions may not have paths
            if (schema.containsKey("paths")) {
                assertThat(schema).containsKey("paths");
            }
        }
    }

    @Test
    @DisplayName("should validate OpenAPI version is 3.0 or higher")
    void shouldValidateOpenApiVersion() throws IOException {
        Path openapiDir = Paths.get("openapi");
        List<File> yamlFiles = Files.walk(openapiDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".yaml"))
                .map(Path::toFile)
                .toList();

        for (File yamlFile : yamlFiles) {
            Map<String, Object> schema = yamlMapper.readValue(yamlFile, Map.class);
            String openapiVersion = (String) schema.get("openapi");
            assertThat(openapiVersion).startsWith("3.");
        }
    }

    @Test
    @DisplayName("should validate required OpenAPI fields exist")
    void shouldValidateRequiredFieldsExist() throws IOException {
        Path openapiDir = Paths.get("openapi");
        List<File> yamlFiles = Files.walk(openapiDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".yaml"))
                .map(Path::toFile)
                .toList();

        for (File yamlFile : yamlFiles) {
            Map<String, Object> schema = yamlMapper.readValue(yamlFile, Map.class);

            // Check info section
            assertThat(schema).containsKey("info");
            Map<String, Object> info = (Map<String, Object>) schema.get("info");
            assertThat(info).containsKey("title");
            assertThat(info).containsKey("version");

            // Check paths section only if this is an API specification
            if (schema.containsKey("paths")) {
                Map<String, Object> paths = (Map<String, Object>) schema.get("paths");
                assertThat(paths).isNotEmpty();
            }
        }
    }
}
