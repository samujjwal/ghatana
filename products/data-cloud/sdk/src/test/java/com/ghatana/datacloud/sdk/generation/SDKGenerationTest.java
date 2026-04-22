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
@DisplayName("SDK Generation Tests [GH-90000]")
class SDKGenerationTest {

    private final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000

    @Test
    @DisplayName("build generates Java, TypeScript, and Python SDK artifacts [GH-90000]")
    void buildGeneratesMultiLanguageArtifacts() throws Exception { // GH-90000
        Path generatedRoot = generatedRoot(); // GH-90000

        assertThat(Files.exists(generatedRoot.resolve("metadata.json [GH-90000]"))).isTrue();
        assertThat(Files.exists(generatedRoot.resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java [GH-90000]"))).isTrue();
        assertThat(Files.exists(generatedRoot.resolve("typescript/src/index.ts [GH-90000]"))).isTrue();
        assertThat(Files.exists(generatedRoot.resolve("python/datacloud_sdk/client.py [GH-90000]"))).isTrue();
    }

    @Test
    @DisplayName("metadata captures canonical CRUD and health endpoints from OpenAPI [GH-90000]")
    void metadataCapturesCanonicalEndpoints() throws Exception { // GH-90000
        Map<String, Object> metadata = objectMapper.readValue( // GH-90000
            Files.readString(generatedRoot().resolve("metadata.json [GH-90000]")),
            new TypeReference<>() { } // GH-90000
        );

        assertThat(metadata).containsEntry("title", "Data-Cloud Platform API"); // GH-90000
        assertThat(metadata).containsEntry("version", "1.0.0-SNAPSHOT"); // GH-90000
        Object documentedPaths = metadata.get("documentedPaths [GH-90000]");
        assertThat(documentedPaths).isInstanceOf(List.class); // GH-90000
        assertThat(asStringList(documentedPaths)) // GH-90000
            .contains("/health", "/api/v1/entities/{collection}", "/api/v1/entities/{collection}/{id}"); // GH-90000
    }

    @Test
    @DisplayName("generated SDK sources expose health and entity CRUD helpers [GH-90000]")
    void generatedSourcesExposeHealthAndEntityCrudHelpers() throws Exception { // GH-90000
        String javaSource = Files.readString(generatedRoot() // GH-90000
            .resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java [GH-90000]"));
        String typeScriptSource = Files.readString(generatedRoot().resolve("typescript/src/index.ts [GH-90000]"));
        String pythonSource = Files.readString(generatedRoot().resolve("python/datacloud_sdk/client.py [GH-90000]"));

        assertThat(javaSource).contains("public Map<String, Object> health() [GH-90000]")
            .contains("createEntity [GH-90000]")
            .contains("getEntity [GH-90000]")
            .contains("queryEntities [GH-90000]")
            .contains("deleteEntity [GH-90000]");
        assertThat(typeScriptSource).contains("export class DataCloudTypeScriptSdk [GH-90000]")
            .contains("health(): Promise<JsonObject> [GH-90000]")
            .contains("createEntity(collection: string, payload: JsonObject) [GH-90000]");
        assertThat(pythonSource).contains("class DataCloudPythonSdk [GH-90000]")
            .contains("def health(self) [GH-90000]")
            .contains("def create_entity(self, collection: str, payload: Dict[str, Any]) [GH-90000]");
    }

    private Path generatedRoot() { // GH-90000
        return Path.of(System.getProperty("datacloud.sdk.generatedRoot [GH-90000]"));
    }

    @SuppressWarnings("unchecked [GH-90000]")
    private List<String> asStringList(Object value) { // GH-90000
        return (List<String>) value; // GH-90000
    }
}
