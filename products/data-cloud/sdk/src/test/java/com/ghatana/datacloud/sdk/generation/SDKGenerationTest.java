/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.sdk.generation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for generated SDK artifacts.
 *
 * @doc.type    class
 * @doc.purpose Verifies generated SDK artifacts and metadata from canonical OpenAPI
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("SDK Generation Tests")
class SDKGenerationTest {

    private final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000

    @Test
    @DisplayName("build generates Java, TypeScript, and Python SDK artifacts")
    void buildGeneratesMultiLanguageArtifacts() throws Exception { // GH-90000
        Path generatedRoot = generatedRoot(); // GH-90000

        assertThat(Files.exists(generatedRoot.resolve("metadata.json"))).isTrue();
        assertThat(Files.exists(generatedRoot.resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java"))).isTrue();
        assertThat(Files.exists(generatedRoot.resolve("typescript/src/index.ts"))).isTrue();
        assertThat(Files.exists(generatedRoot.resolve("python/datacloud_sdk/client.py"))).isTrue();
    }

    @Test
    @DisplayName("metadata captures canonical CRUD and health endpoints from OpenAPI")
    void metadataCapturesCanonicalEndpoints() throws Exception { // GH-90000
        Map<String, Object> metadata = objectMapper.readValue( // GH-90000
            Files.readString(generatedRoot().resolve("metadata.json")),
            new TypeReference<>() { } // GH-90000
        );

        assertThat(metadata).containsEntry("title", "Data-Cloud Platform API"); // GH-90000
        assertThat(metadata).containsEntry("version", "1.0.0-SNAPSHOT"); // GH-90000
        Object documentedPaths = metadata.get("documentedPaths");
        assertThat(documentedPaths).isInstanceOf(List.class); // GH-90000
        assertThat(asStringList(documentedPaths)) // GH-90000
            .contains("/health", "/api/v1/entities/{collection}", "/api/v1/entities/{collection}/{id}"); // GH-90000
    }

    @Test
    @DisplayName("generated SDK sources expose health and entity CRUD helpers")
    void generatedSourcesExposeHealthAndEntityCrudHelpers() throws Exception { // GH-90000
        String javaSource = Files.readString(generatedRoot() // GH-90000
            .resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java"));
        String typeScriptSource = Files.readString(generatedRoot().resolve("typescript/src/index.ts"));
        String pythonSource = Files.readString(generatedRoot().resolve("python/datacloud_sdk/client.py"));

        assertThat(javaSource).contains("public Map<String, Object> health()")
            .contains("createEntity")
            .contains("getEntity")
            .contains("queryEntities")
            .contains("deleteEntity");
        assertThat(typeScriptSource).contains("export class DataCloudTypeScriptSdk")
            .contains("health(): Promise<JsonObject>")
            .contains("createEntity(collection: string, payload: JsonObject)");
        assertThat(pythonSource).contains("class DataCloudPythonSdk")
            .contains("def health(self)")
            .contains("def create_entity(self, collection: str, payload: Dict[str, Any])");
    }

    private Path generatedRoot() { // GH-90000
        return Path.of(System.getProperty("datacloud.sdk.generatedRoot"));
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) { // GH-90000
        return (List<String>) value; // GH-90000
    }
}
