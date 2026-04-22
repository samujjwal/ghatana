/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.sdk.documentation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documentation tests for the generated Data-Cloud SDK.
 * Verifies that generated SDK artifacts have complete and accurate documentation.
 *
 * @doc.type    class
 * @doc.purpose Verifies SDK documentation completeness and accuracy
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("SDK Documentation Tests [GH-90000]")
@Tag("documentation [GH-90000]")
class SDKDocumentationTest {

    private final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000

    @Test
    @DisplayName("generated Java SDK has complete Javadoc comments [GH-90000]")
    void generatedJavaSdkHasCompleteJavadocComments() throws Exception { // GH-90000
        String javaSource = Files.readString(generatedRoot() // GH-90000
            .resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java [GH-90000]"));
        
        // Verify class-level documentation
        assertThat(javaSource).contains("/** [GH-90000]");
        assertThat(javaSource).contains("* Data Cloud Java SDK [GH-90000]");
        
        // Verify method documentation for key methods
        assertThat(javaSource).contains("public Map<String, Object> health() [GH-90000]");
        assertThat(javaSource).contains("/** [GH-90000]")
            .contains("* Health check endpoint [GH-90000]")
            .contains("* @return [GH-90000]");
        
        assertThat(javaSource).contains("createEntity [GH-90000]");
        assertThat(javaSource).contains("/** [GH-90000]")
            .contains("* Create a new entity [GH-90000]")
            .contains("* @param collection [GH-90000]")
            .contains("* @param payload [GH-90000]")
            .contains("* @return [GH-90000]");
        
        assertThat(javaSource).contains("getEntity [GH-90000]");
        assertThat(javaSource).contains("/** [GH-90000]")
            .contains("* Get an entity by ID [GH-90000]")
            .contains("* @param collection [GH-90000]")
            .contains("* @param id [GH-90000]")
            .contains("* @return [GH-90000]");
        
        assertThat(javaSource).contains("queryEntities [GH-90000]");
        assertThat(javaSource).contains("/** [GH-90000]")
            .contains("* Query entities in a collection [GH-90000]")
            .contains("* @param collection [GH-90000]")
            .contains("* @param limit [GH-90000]")
            .contains("* @return [GH-90000]");
        
        assertThat(javaSource).contains("deleteEntity [GH-90000]");
        assertThat(javaSource).contains("/** [GH-90000]")
            .contains("* Delete an entity by ID [GH-90000]")
            .contains("* @param collection [GH-90000]")
            .contains("* @param id [GH-90000]")
            .contains("* @return [GH-90000]");
    }

    @Test
    @DisplayName("generated TypeScript SDK has complete TSDoc comments [GH-90000]")
    void generatedTypeScriptSdkHasCompleteTSDocComments() throws Exception { // GH-90000
        String tsSource = Files.readString(generatedRoot().resolve("typescript/src/index.ts [GH-90000]"));
        
        // Verify class-level documentation
        assertThat(tsSource).contains("/** [GH-90000]");
        assertThat(tsSource).contains("* Data Cloud TypeScript SDK [GH-90000]");
        
        // Verify method documentation
        assertThat(tsSource).contains("health() [GH-90000]");
        assertThat(tsSource).contains("/** [GH-90000]")
            .contains("* Health check endpoint [GH-90000]")
            .contains("* @returns [GH-90000]");
        
        assertThat(tsSource).contains("createEntity [GH-90000]");
        assertThat(tsSource).contains("/** [GH-90000]")
            .contains("* Create a new entity [GH-90000]")
            .contains("* @param collection [GH-90000]")
            .contains("* @param payload [GH-90000]")
            .contains("* @returns [GH-90000]");
        
        assertThat(tsSource).contains("getEntity [GH-90000]");
        assertThat(tsSource).contains("/** [GH-90000]")
            .contains("* Get an entity by ID [GH-90000]")
            .contains("* @param collection [GH-90000]")
            .contains("* @param id [GH-90000]")
            .contains("* @returns [GH-90000]");
        
        assertThat(tsSource).contains("queryEntities [GH-90000]");
        assertThat(tsSource).contains("/** [GH-90000]")
            .contains("* Query entities in a collection [GH-90000]")
            .contains("* @param collection [GH-90000]")
            .contains("* @param limit [GH-90000]")
            .contains("* @returns [GH-90000]");
        
        assertThat(tsSource).contains("deleteEntity [GH-90000]");
        assertThat(tsSource).contains("/** [GH-90000]")
            .contains("* Delete an entity by ID [GH-90000]")
            .contains("* @param collection [GH-90000]")
            .contains("* @param id [GH-90000]")
            .contains("* @returns [GH-90000]");
    }

    @Test
    @DisplayName("generated Python SDK has complete docstrings [GH-90000]")
    void generatedPythonSdkHasCompleteDocstrings() throws Exception { // GH-90000
        String pythonSource = Files.readString(generatedRoot().resolve("python/datacloud_sdk/client.py [GH-90000]"));
        
        // Verify class-level documentation
        assertThat(pythonSource).contains("\"\"\""); // GH-90000
        assertThat(pythonSource).contains("Data Cloud Python SDK [GH-90000]");
        
        // Verify method documentation
        assertThat(pythonSource).contains("def health(self) [GH-90000]");
        assertThat(pythonSource).contains("\"\"\"") // GH-90000
            .contains("Health check endpoint [GH-90000]")
            .contains(":return [GH-90000]");
        
        assertThat(pythonSource).contains("def create_entity [GH-90000]");
        assertThat(pythonSource).contains("\"\"\"") // GH-90000
            .contains("Create a new entity [GH-90000]")
            .contains(":param collection [GH-90000]")
            .contains(":param payload [GH-90000]")
            .contains(":return [GH-90000]");
        
        assertThat(pythonSource).contains("def get_entity [GH-90000]");
        assertThat(pythonSource).contains("\"\"\"") // GH-90000
            .contains("Get an entity by ID [GH-90000]")
            .contains(":param collection [GH-90000]")
            .contains(":param id [GH-90000]")
            .contains(":return [GH-90000]");
        
        assertThat(pythonSource).contains("def query_entities [GH-90000]");
        assertThat(pythonSource).contains("\"\"\"") // GH-90000
            .contains("Query entities in a collection [GH-90000]")
            .contains(":param collection [GH-90000]")
            .contains(":param limit [GH-90000]")
            .contains(":return [GH-90000]");
        
        assertThat(pythonSource).contains("def delete_entity [GH-90000]");
        assertThat(pythonSource).contains("\"\"\"") // GH-90000
            .contains("Delete an entity by ID [GH-90000]")
            .contains(":param collection [GH-90000]")
            .contains(":param id [GH-90000]")
            .contains(":return [GH-90000]");
    }

    @Test
    @DisplayName("metadata includes documentation for all documented endpoints [GH-90000]")
    void metadataIncludesDocumentationForAllDocumentedEndpoints() throws Exception { // GH-90000
        Map<String, Object> metadata = objectMapper.readValue( // GH-90000
            Files.readString(generatedRoot().resolve("metadata.json [GH-90000]")),
            new TypeReference<>() { } // GH-90000
        );
        
        Object documentedPaths = metadata.get("documentedPaths [GH-90000]");
        assertThat(documentedPaths).isInstanceOf(List.class); // GH-90000
        
        List<String> paths = asStringList(documentedPaths); // GH-90000
        
        // Verify core endpoints are documented
        assertThat(paths).contains("/health [GH-90000]");
        assertThat(paths).contains("/api/v1/entities/{collection} [GH-90000]");
        assertThat(paths).contains("/api/v1/entities/{collection}/{id} [GH-90000]");
    }

    @Test
    @DisplayName("metadata includes SDK version and compatibility information [GH-90000]")
    void metadataIncludesSdkVersionAndCompatibilityInformation() throws Exception { // GH-90000
        Map<String, Object> metadata = objectMapper.readValue( // GH-90000
            Files.readString(generatedRoot().resolve("metadata.json [GH-90000]")),
            new TypeReference<>() { } // GH-90000
        );
        
        assertThat(metadata).containsKey("title [GH-90000]");
        assertThat(metadata).containsKey("version [GH-90000]");
        assertThat(metadata).containsKey("generatedAt [GH-90000]");
        assertThat(metadata.get("title [GH-90000]")).isEqualTo("Data-Cloud Platform API [GH-90000]");
        assertThat(metadata.get("version [GH-90000]")).isInstanceOf(String.class);
        assertThat(metadata.get("generatedAt [GH-90000]")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("generated SDKs include README or usage documentation [GH-90000]")
    void generatedSdksIncludeReadmeOrUsageDocumentation() throws Exception { // GH-90000
        // Check for README files
        boolean hasJavaReadme = Files.exists(generatedRoot().resolve("java/README.md [GH-90000]"));
        boolean hasTsReadme = Files.exists(generatedRoot().resolve("typescript/README.md [GH-90000]"));
        boolean hasPythonReadme = Files.exists(generatedRoot().resolve("python/README.md [GH-90000]"));
        
        // At least one README should exist
        assertThat(hasJavaReadme || hasTsReadme || hasPythonReadme).isTrue(); // GH-90000
        
        // If README exists, verify it contains usage examples
        if (hasJavaReadme) { // GH-90000
            String javaReadme = Files.readString(generatedRoot().resolve("java/README.md [GH-90000]"));
            assertThat(javaReadme).contains("Usage [GH-90000]")
                .contains("DataCloudJavaSdk [GH-90000]")
                .contains("health() [GH-90000]");
        }
        
        if (hasTsReadme) { // GH-90000
            String tsReadme = Files.readString(generatedRoot().resolve("typescript/README.md [GH-90000]"));
            assertThat(tsReadme).contains("Usage [GH-90000]")
                .contains("DataCloudTypeScriptSdk [GH-90000]")
                .contains("health() [GH-90000]");
        }
        
        if (hasPythonReadme) { // GH-90000
            String pythonReadme = Files.readString(generatedRoot().resolve("python/README.md [GH-90000]"));
            assertThat(pythonReadme).contains("Usage [GH-90000]")
                .contains("DataCloudPythonSdk [GH-90000]")
                .contains("health() [GH-90000]");
        }
    }

    @Test
    @DisplayName("generated SDKs include example code snippets [GH-90000]")
    void generatedSdksIncludeExampleCodeSnippets() throws Exception { // GH-90000
        // Check for example files
        boolean hasJavaExamples = Files.exists(generatedRoot().resolve("java/examples/Example.java [GH-90000]"));
        boolean hasTsExamples = Files.exists(generatedRoot().resolve("typescript/examples/example.ts [GH-90000]"));
        boolean hasPythonExamples = Files.exists(generatedRoot().resolve("python/examples/example.py [GH-90000]"));
        
        // At least one example should exist
        assertThat(hasJavaExamples || hasTsExamples || hasPythonExamples).isTrue(); // GH-90000
        
        // If example exists, verify it demonstrates SDK usage
        if (hasJavaExamples) { // GH-90000
            String javaExample = Files.readString(generatedRoot().resolve("java/examples/Example.java [GH-90000]"));
            assertThat(javaExample).contains("DataCloudJavaSdk [GH-90000]")
                .contains(".health() [GH-90000]")
                .contains(".createEntity [GH-90000]")
                .contains(".getEntity [GH-90000]");
        }
        
        if (hasTsExamples) { // GH-90000
            String tsExample = Files.readString(generatedRoot().resolve("typescript/examples/example.ts [GH-90000]"));
            assertThat(tsExample).contains("DataCloudTypeScriptSdk [GH-90000]")
                .contains(".health() [GH-90000]")
                .contains(".createEntity [GH-90000]")
                .contains(".getEntity [GH-90000]");
        }
        
        if (hasPythonExamples) { // GH-90000
            String pythonExample = Files.readString(generatedRoot().resolve("python/examples/example.py [GH-90000]"));
            assertThat(pythonExample).contains("DataCloudPythonSdk [GH-90000]")
                .contains(".health() [GH-90000]")
                .contains(".create_entity [GH-90000]")
                .contains(".get_entity [GH-90000]");
        }
    }

    @Test
    @DisplayName("documentation includes error handling examples [GH-90000]")
    void documentationIncludesErrorHandlingExamples() throws Exception { // GH-90000
        String javaSource = Files.readString(generatedRoot() // GH-90000
            .resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java [GH-90000]"));
        
        // Verify error handling is documented
        assertThat(javaSource).contains("@throws [GH-90000]")
            .contains("RuntimeException [GH-90000]")
            .contains("IOException [GH-90000]");
    }

    @Test
    @DisplayName("documentation includes authentication and tenant information [GH-90000]")
    void documentationIncludesAuthenticationAndTenantInformation() throws Exception { // GH-90000
        String javaSource = Files.readString(generatedRoot() // GH-90000
            .resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java [GH-90000]"));
        
        // Verify constructor documents authentication parameters
        assertThat(javaSource).contains("public DataCloudJavaSdk [GH-90000]")
            .contains("baseUrl [GH-90000]")
            .contains("tenantId [GH-90000]");
    }

    private Path generatedRoot() { // GH-90000
        return Path.of(System.getProperty("datacloud.sdk.generatedRoot [GH-90000]"));
    }

    @SuppressWarnings("unchecked [GH-90000]")
    private List<String> asStringList(Object value) { // GH-90000
        return (List<String>) value; // GH-90000
    }
}
