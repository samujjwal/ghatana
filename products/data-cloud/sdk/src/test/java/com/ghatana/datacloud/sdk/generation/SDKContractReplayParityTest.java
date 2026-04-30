/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * DC-A19: Contract replay parity checks for generated SDKs.
 *
 * <p>Validates that Java, TypeScript, and Python generated clients all expose
 * a consistent helper surface for the canonical health + entity CRUD contract
 * captured in metadata.json.
 *
 * @doc.type class
 * @doc.purpose Cross-language SDK contract replay parity checks
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SDK contract replay parity")
class SDKContractReplayParityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("java/typescript/python generated clients expose canonical contract helpers")
    void generatedClientsExposeCanonicalContractHelpers() throws Exception {
        Path root = generatedRoot();
        String javaSource = Files.readString(root.resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java"));
        String typeScriptSource = Files.readString(root.resolve("typescript/src/index.ts"));
        String pythonSource = Files.readString(root.resolve("python/datacloud_sdk/client.py"));

        Map<String, Object> metadata = objectMapper.readValue(
                Files.readString(root.resolve("metadata.json")),
                new TypeReference<>() { }
        );

        List<String> paths = asStringList(metadata.get("documentedPaths"));
        assertThat(paths).contains(
                "/health",
                "/api/v1/entities/{collection}",
                "/api/v1/entities/{collection}/{id}"
        );

        // Java SDK helper parity
        assertThat(javaSource)
                .contains("health()")
                .contains("createEntity")
                .contains("getEntity")
                .contains("queryEntities")
                .contains("deleteEntity");

        // TypeScript SDK helper parity
        assertThat(typeScriptSource)
                .contains("health(): Promise")
                .contains("createEntity(collection: string, payload: JsonObject)")
                .contains("getEntity(collection: string, entityId: string)")
                .contains("queryEntities(collection: string, limit = 100)")
                .contains("deleteEntity(collection: string, entityId: string)");

        // Python SDK helper parity
        assertThat(pythonSource)
                .contains("def health(self)")
                .contains("def create_entity(self, collection: str, payload: Dict[str, Any])")
                .contains("def get_entity(self, collection: str, entity_id: str)")
                .contains("def query_entities(self, collection: str, limit: int = 100)")
                .contains("def delete_entity(self, collection: str, entity_id: str)");
    }

    private Path generatedRoot() {
        return Path.of(System.getProperty("datacloud.sdk.generatedRoot"));
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        return (List<String>) value;
    }
}
