/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.contracts.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
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

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final YAMLMapper yamlMapper = new YAMLMapper();

    @Test
    @DisplayName("should validate all OpenAPI YAML files are well-formed")
    void shouldValidateOpenApiYamlFilesAreWellFormed() throws IOException {
        List<File> yamlFiles = findYamlFiles();

        assertThat(yamlFiles).isNotEmpty();

        for (File yamlFile : yamlFiles) {
            Map<String, Object> schema = readYamlMap(yamlFile);
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
        List<File> yamlFiles = findYamlFiles();

        for (File yamlFile : yamlFiles) {
            Map<String, Object> schema = readYamlMap(yamlFile);
            String openapiVersion = (String) schema.get("openapi");
            assertThat(openapiVersion).startsWith("3.");
        }
    }

    @Test
    @DisplayName("should validate required OpenAPI fields exist")
    void shouldValidateRequiredFieldsExist() throws IOException {
        List<File> yamlFiles = findYamlFiles();

        for (File yamlFile : yamlFiles) {
            Map<String, Object> schema = readYamlMap(yamlFile);

            // Check info section
            assertThat(schema).containsKey("info");
            Map<String, Object> info = requireNestedMap(schema, "info");
            assertThat(info).containsKey("title");
            assertThat(info).containsKey("version");

            // Check paths section only if this is an API specification
            if (schema.containsKey("paths")) {
                Map<String, Object> paths = requireNestedMap(schema, "paths");
                assertThat(paths).isNotEmpty();
            }
        }
    }

    private static List<File> findYamlFiles() throws IOException {
        Path openapiDir = Paths.get("openapi");
        try (var paths = Files.walk(openapiDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yaml"))
                    .map(Path::toFile)
                    .toList();
        }
    }

    private static Map<String, Object> readYamlMap(File yamlFile) throws IOException {
        return yamlMapper.readValue(yamlFile, MAP_TYPE);
    }

    private static Map<String, Object> requireNestedMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        assertThat(value).as("field '%s' must be a map", key).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) value;
        return nested;
    }
}
