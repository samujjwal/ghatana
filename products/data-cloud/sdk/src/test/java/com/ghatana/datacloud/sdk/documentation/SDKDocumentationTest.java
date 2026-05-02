/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("SDK Documentation Tests")
@Tag("documentation")
class SDKDocumentationTest {

    private final ObjectMapper objectMapper = new ObjectMapper(); 

    @Test
    @DisplayName("generated Java SDK has complete Javadoc comments")
    void generatedJavaSdkHasCompleteJavadocComments() throws Exception { 
        String javaSource = Files.readString(generatedRoot() 
            .resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java"));
        
        // Verify class-level documentation
        assertThat(javaSource).contains("/**");
        assertThat(javaSource).contains("* Data Cloud Java SDK");
        
        // Verify method documentation for key methods
        assertThat(javaSource).contains("public Map<String, Object> health()");
        assertThat(javaSource).contains("/**")
            .contains("* Health check endpoint")
            .contains("* @return");
        
        assertThat(javaSource).contains("createEntity");
        assertThat(javaSource).contains("/**")
            .contains("* Create a new entity")
            .contains("* @param collection")
            .contains("* @param payload")
            .contains("* @return");
        
        assertThat(javaSource).contains("getEntity");
        assertThat(javaSource).contains("/**")
            .contains("* Get an entity by ID")
            .contains("* @param collection")
            .contains("* @param id")
            .contains("* @return");
        
        assertThat(javaSource).contains("queryEntities");
        assertThat(javaSource).contains("/**")
            .contains("* Query entities in a collection")
            .contains("* @param collection")
            .contains("* @param limit")
            .contains("* @return");
        
        assertThat(javaSource).contains("deleteEntity");
        assertThat(javaSource).contains("/**")
            .contains("* Delete an entity by ID")
            .contains("* @param collection")
            .contains("* @param id")
            .contains("* @return");
    }

    @Test
    @DisplayName("generated TypeScript SDK has complete TSDoc comments")
    void generatedTypeScriptSdkHasCompleteTSDocComments() throws Exception { 
        String tsSource = Files.readString(generatedRoot().resolve("typescript/src/index.ts"));
        
        // Verify class-level documentation
        assertThat(tsSource).contains("/**");
        assertThat(tsSource).contains("* Data Cloud TypeScript SDK");
        
        // Verify method documentation
        assertThat(tsSource).contains("health()");
        assertThat(tsSource).contains("/**")
            .contains("* Health check endpoint")
            .contains("* @returns");
        
        assertThat(tsSource).contains("createEntity");
        assertThat(tsSource).contains("/**")
            .contains("* Create a new entity")
            .contains("* @param collection")
            .contains("* @param payload")
            .contains("* @returns");
        
        assertThat(tsSource).contains("getEntity");
        assertThat(tsSource).contains("/**")
            .contains("* Get an entity by ID")
            .contains("* @param collection")
            .contains("* @param id")
            .contains("* @returns");
        
        assertThat(tsSource).contains("queryEntities");
        assertThat(tsSource).contains("/**")
            .contains("* Query entities in a collection")
            .contains("* @param collection")
            .contains("* @param limit")
            .contains("* @returns");
        
        assertThat(tsSource).contains("deleteEntity");
        assertThat(tsSource).contains("/**")
            .contains("* Delete an entity by ID")
            .contains("* @param collection")
            .contains("* @param id")
            .contains("* @returns");
    }

    @Test
    @DisplayName("generated Python SDK has complete docstrings")
    void generatedPythonSdkHasCompleteDocstrings() throws Exception { 
        String pythonSource = Files.readString(generatedRoot().resolve("python/datacloud_sdk/client.py"));
        
        // Verify class-level documentation
        assertThat(pythonSource).contains("\"\"\""); 
        assertThat(pythonSource).contains("Data Cloud Python SDK");
        
        // Verify method documentation
        assertThat(pythonSource).contains("def health(self)");
        assertThat(pythonSource).contains("\"\"\"") 
            .contains("Health check endpoint")
            .contains(":return");
        
        assertThat(pythonSource).contains("def create_entity");
        assertThat(pythonSource).contains("\"\"\"") 
            .contains("Create a new entity")
            .contains(":param collection")
            .contains(":param payload")
            .contains(":return");
        
        assertThat(pythonSource).contains("def get_entity");
        assertThat(pythonSource).contains("\"\"\"") 
            .contains("Get an entity by ID")
            .contains(":param collection")
            .contains(":param id")
            .contains(":return");
        
        assertThat(pythonSource).contains("def query_entities");
        assertThat(pythonSource).contains("\"\"\"") 
            .contains("Query entities in a collection")
            .contains(":param collection")
            .contains(":param limit")
            .contains(":return");
        
        assertThat(pythonSource).contains("def delete_entity");
        assertThat(pythonSource).contains("\"\"\"") 
            .contains("Delete an entity by ID")
            .contains(":param collection")
            .contains(":param id")
            .contains(":return");
    }

    @Test
    @DisplayName("metadata includes documentation for all documented endpoints")
    void metadataIncludesDocumentationForAllDocumentedEndpoints() throws Exception { 
        Map<String, Object> metadata = objectMapper.readValue( 
            Files.readString(generatedRoot().resolve("metadata.json")),
            new TypeReference<>() { } 
        );
        
        Object documentedPaths = metadata.get("documentedPaths");
        assertThat(documentedPaths).isInstanceOf(List.class); 
        
        List<String> paths = asStringList(documentedPaths); 
        
        // Verify core endpoints are documented
        assertThat(paths).contains("/health");
        assertThat(paths).contains("/api/v1/entities/{collection}");
        assertThat(paths).contains("/api/v1/entities/{collection}/{id}");
    }

    @Test
    @DisplayName("metadata includes SDK version and compatibility information")
    void metadataIncludesSdkVersionAndCompatibilityInformation() throws Exception { 
        Map<String, Object> metadata = objectMapper.readValue( 
            Files.readString(generatedRoot().resolve("metadata.json")),
            new TypeReference<>() { } 
        );
        
        assertThat(metadata).containsKey("title");
        assertThat(metadata).containsKey("version");
        assertThat(metadata).containsKey("generatedAt");
        assertThat(metadata.get("title")).isEqualTo("Data-Cloud Platform API");
        assertThat(metadata.get("version")).isInstanceOf(String.class);
        assertThat(metadata.get("generatedAt")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("generated SDKs include README or usage documentation")
    void generatedSdksIncludeReadmeOrUsageDocumentation() throws Exception { 
        // Check for README files
        boolean hasJavaReadme = Files.exists(generatedRoot().resolve("java/README.md"));
        boolean hasTsReadme = Files.exists(generatedRoot().resolve("typescript/README.md"));
        boolean hasPythonReadme = Files.exists(generatedRoot().resolve("python/README.md"));
        
        // At least one README should exist
        assertThat(hasJavaReadme || hasTsReadme || hasPythonReadme).isTrue(); 
        
        // If README exists, verify it contains usage examples
        if (hasJavaReadme) { 
            String javaReadme = Files.readString(generatedRoot().resolve("java/README.md"));
            assertThat(javaReadme).contains("Usage")
                .contains("DataCloudJavaSdk")
                .contains("health()");
        }
        
        if (hasTsReadme) { 
            String tsReadme = Files.readString(generatedRoot().resolve("typescript/README.md"));
            assertThat(tsReadme).contains("Usage")
                .contains("DataCloudTypeScriptSdk")
                .contains("health()");
        }
        
        if (hasPythonReadme) { 
            String pythonReadme = Files.readString(generatedRoot().resolve("python/README.md"));
            assertThat(pythonReadme).contains("Usage")
                .contains("DataCloudPythonSdk")
                .contains("health()");
        }
    }

    @Test
    @DisplayName("generated SDKs include example code snippets")
    void generatedSdksIncludeExampleCodeSnippets() throws Exception { 
        // Check for example files
        boolean hasJavaExamples = Files.exists(generatedRoot().resolve("java/examples/Example.java"));
        boolean hasTsExamples = Files.exists(generatedRoot().resolve("typescript/examples/example.ts"));
        boolean hasPythonExamples = Files.exists(generatedRoot().resolve("python/examples/example.py"));
        
        // At least one example should exist
        assertThat(hasJavaExamples || hasTsExamples || hasPythonExamples).isTrue(); 
        
        // If example exists, verify it demonstrates SDK usage
        if (hasJavaExamples) { 
            String javaExample = Files.readString(generatedRoot().resolve("java/examples/Example.java"));
            assertThat(javaExample).contains("DataCloudJavaSdk")
                .contains(".health()")
                .contains(".createEntity")
                .contains(".getEntity");
        }
        
        if (hasTsExamples) { 
            String tsExample = Files.readString(generatedRoot().resolve("typescript/examples/example.ts"));
            assertThat(tsExample).contains("DataCloudTypeScriptSdk")
                .contains(".health()")
                .contains(".createEntity")
                .contains(".getEntity");
        }
        
        if (hasPythonExamples) { 
            String pythonExample = Files.readString(generatedRoot().resolve("python/examples/example.py"));
            assertThat(pythonExample).contains("DataCloudPythonSdk")
                .contains(".health()")
                .contains(".create_entity")
                .contains(".get_entity");
        }
    }

    @Test
    @DisplayName("documentation includes error handling examples")
    void documentationIncludesErrorHandlingExamples() throws Exception { 
        String javaSource = Files.readString(generatedRoot() 
            .resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java"));
        
        // Verify error handling is documented
        assertThat(javaSource).contains("@throws")
            .contains("RuntimeException")
            .contains("IOException");
    }

    @Test
    @DisplayName("documentation includes authentication and tenant information")
    void documentationIncludesAuthenticationAndTenantInformation() throws Exception { 
        String javaSource = Files.readString(generatedRoot() 
            .resolve("java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java"));
        
        // Verify constructor documents authentication parameters
        assertThat(javaSource).contains("public DataCloudJavaSdk")
            .contains("baseUrl")
            .contains("tenantId");
    }

    private Path generatedRoot() { 
        return Path.of(System.getProperty("datacloud.sdk.generatedRoot"));
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) { 
        return (List<String>) value; 
    }
}
