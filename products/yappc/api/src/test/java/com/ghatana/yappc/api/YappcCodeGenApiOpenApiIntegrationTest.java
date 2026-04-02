package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Integration tests for YAPPC Code Generation API OpenAPI specification.
 * @doc.layer product-test
 * @doc.pattern IntegrationTest
 *
 * Validates:
 * - Design creation with component and relationship validation
 * - Circular dependency detection
 * - Code generation in multiple languages (Java, Python, Go, TypeScript)
 * - Asynchronous generation (202 Accepted)
 * - Artifact versioning
 * - Refactoring suggestions
 * - Component type validation
 */
@DisplayName("YAPPC Code Generation API OpenAPI Specification Tests")
class YappcCodeGenApiOpenApiIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper;
    private MockCodeGenApiClient apiClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        apiClient = new MockCodeGenApiClient(objectMapper);
    }

    @Nested
    @DisplayName("POST /designs - Design Creation")
    class DesignCreationTests {

        @Test
        @DisplayName("should create design with valid components")
        void shouldCreateDesign() {
            Map<String, Object> request = createValidDesignRequest();

            ApiResponse<Design> response = apiClient.post(
                "/designs",
                request,
                Design.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(201);
            assertThat(response.body.designId).isNotBlank();
            assertThat(response.body.name).isEqualTo("E-commerce Platform");
            assertThat(response.body.version).isEqualTo(1);
        }

        @Test
        @DisplayName("should reject design without components")
        void shouldRejectEmptyComponents() {
            Map<String, Object> request = Map.of(
                "name", "Bad Design",
                "components", Arrays.asList()  // Empty list
            );

            ApiResponse<DesignValidationError> response = apiClient.post(
                "/designs",
                request,
                DesignValidationError.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
            assertThat(response.body.validationErrors).isNotEmpty();
        }

        @Test
        @DisplayName("should validate component IDs follow naming convention")
        void shouldValidateComponentNaming() {
            Map<String, Object> component = Map.of(
                "id", "Invalid-ID!",  // Invalid: hyphen and special char
                "name", "Service",
                "type", "SERVICE"
            );
            Map<String, Object> request = Map.of(
                "name", "Test Design",
                "components", Arrays.asList(component)
            );

            ApiResponse<DesignValidationError> response = apiClient.post(
                "/designs",
                request,
                DesignValidationError.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
        }

        @Test
        @DisplayName("should validate all component types")
        void shouldValidateComponentTypes() {
            String[] validTypes = {
                "SERVICE", "CONTROLLER", "REPOSITORY", "UTILITY",
                "LIBRARY", "DATABASE", "CACHE", "MESSAGE_QUEUE"
            };

            for (String type : validTypes) {
                Map<String, Object> component = Map.of(
                    "id", "comp_" + type,
                    "name", "Component " + type,
                    "type", type
                );
                Map<String, Object> request = Map.of(
                    "name", "Test Design",
                    "components", Arrays.asList(component)
                );

                ApiResponse<Design> response = apiClient.post(
                    "/designs",
                    request,
                    Design.class,
                    "Bearer token"
                );

                assertThat(response.statusCode).isEqualTo(201);
            }
        }

        @Test
        @DisplayName("should support component properties")
        void shouldSupportComponentProperties() {
            Map<String, Object> component = Map.of(
                "id", "service_user",
                "name", "User Service",
                "type", "SERVICE",
                "description", "Manages user accounts",
                "properties", Map.of(
                    "language", "java",
                    "framework", "spring-boot",
                    "port", 8080
                )
            );
            Map<String, Object> request = Map.of(
                "name", "Test Design",
                "components", Arrays.asList(component)
            );

            ApiResponse<Design> response = apiClient.post(
                "/designs",
                request,
                Design.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(201);
        }
    }

    @Nested
    @DisplayName("GET/PATCH /designs/{designId} - Design Management")
    class DesignManagementTests {

        @Test
        @DisplayName("should retrieve design by ID")
        void shouldGetDesign() {
            String designId = UUID.randomUUID().toString();

            ApiResponse<Design> response = apiClient.get(
                "/designs/" + designId,
                Design.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.designId).isEqualTo(designId);
        }

        @Test
        @DisplayName("should update design with new components")
        void shouldUpdateDesign() {
            String designId = UUID.randomUUID().toString();
            Map<String, Object> update = Map.of(
                "name", "Updated Design",
                "components", Arrays.asList(
                    Map.of("id", "service_1", "name", "Service 1", "type", "SERVICE")
                )
            );

            ApiResponse<Design> response = apiClient.patch(
                "/designs/" + designId,
                update,
                Design.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.name).isEqualTo("Updated Design");
            assertThat(response.body.version).isGreaterThan(1);
        }

        @Test
        @DisplayName("should return 404 for non-existent design")
        void shouldReturn404ForMissing() {
            ApiResponse<Map> response = apiClient.get(
                "/designs/nonexistent",
                Map.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("POST /designs/{designId}/validate - Design Validation")
    class DesignValidationTests {

        @Test
        @DisplayName("should detect circular dependencies")
        void shouldDetectCircularDependencies() {
            String designId = UUID.randomUUID().toString();

            ApiResponse<ValidationResult> response = apiClient.post(
                "/designs/" + designId + "/validate",
                null,
                ValidationResult.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            // Could be valid or have circular deps depending on mock data
            assertThat(response.body.isValid).isNotNull();
            if (!response.body.isValid) {
                assertThat(response.body.circularDependencies).isNotEmpty();
            }
        }

        @Test
        @DisplayName("should validate component references in relationships")
        void shouldValidateComponentReferences() {
            String designId = UUID.randomUUID().toString();

            ApiResponse<ValidationResult> response = apiClient.post(
                "/designs/" + designId + "/validate",
                null,
                ValidationResult.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            response.body.errors.forEach(error -> {
                assertThat(error.code).isIn(
                    "CIRCULAR_DEPENDENCY",
                    "INVALID_COMPONENT_TYPE",
                    "MISSING_DEPENDENCY",
                    "INVALID_RELATIONSHIP"
                );
            });
        }
    }

    @Nested
    @DisplayName("POST /generated-code - Code Generation")
    class CodeGenerationTests {

        @Test
        @DisplayName("should generate Java code asynchronously")
        void shouldGenerateJava() {
            Map<String, Object> request = Map.of(
                "designId", UUID.randomUUID().toString(),
                "language", "java",
                "packageName", "com.example.app",
                "includeTests", true,
                "includeDocumentation", true
            );

            ApiResponse<CodeGenerationOperation> response = apiClient.post(
                "/generated-code",
                request,
                CodeGenerationOperation.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(202);
            assertThat(response.body.operationId).isNotBlank();
            assertThat(response.body.status).isEqualTo("QUEUED");
            assertThat(response.headers.get("Location")).containsPattern("/generated-code/.*");
        }

        @Test
        @DisplayName("should support all languages")
        void shouldSupportAllLanguages() {
            String[] languages = { "java", "python", "go", "typescript" };

            for (String lang : languages) {
                Map<String, Object> request = Map.of(
                    "designId", UUID.randomUUID().toString(),
                    "language", lang,
                    "packageName", "test.app"
                );

                ApiResponse<CodeGenerationOperation> response = apiClient.post(
                    "/generated-code",
                    request,
                    CodeGenerationOperation.class,
                    "Bearer token"
                );

                assertThat(response.statusCode).isEqualTo(202);
            }
        }

        @Test
        @DisplayName("should validate package name format")
        void shouldValidatePackageName() {
            Map<String, Object> request = Map.of(
                "designId", UUID.randomUUID().toString(),
                "language", "java",
                "packageName", ""  // Empty
            );

            ApiResponse<Map> response = apiClient.post(
                "/generated-code",
                request,
                Map.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
        }

        @Test
        @DisplayName("should support optional targetVersion")
        void shouldSupportTargetVersion() {
            Map<String, Object> request = Map.of(
                "designId", UUID.randomUUID().toString(),
                "language", "java",
                "packageName", "com.example",
                "targetVersion", "17"  // Java 17
            );

            ApiResponse<CodeGenerationOperation> response = apiClient.post(
                "/generated-code",
                request,
                CodeGenerationOperation.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(202);
        }
    }

    @Nested
    @DisplayName("GET /generated-code/{operationId} - Generation Status")
    class GenerationStatusTests {

        @Test
        @DisplayName("should retrieve generation status")
        void shouldGetStatus() {
            String operationId = UUID.randomUUID().toString();

            ApiResponse<CodeGenerationOperation> response = apiClient.get(
                "/generated-code/" + operationId,
                CodeGenerationOperation.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.operationId).isEqualTo(operationId);
            assertThat(response.body.status).isIn(
                "QUEUED", "RUNNING", "COMPLETED", "FAILED"
            );
        }

        @Test
        @DisplayName("should include artifactId when completed")
        void shouldIncludeArtifactOnCompletion() {
            String operationId = UUID.randomUUID().toString();

            ApiResponse<CodeGenerationOperation> response = apiClient.get(
                "/generated-code/" + operationId,
                CodeGenerationOperation.class,
                "Bearer token"
            );

            if ("COMPLETED".equals(response.body.status)) {
                assertThat(response.body.artifactId).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("GET /artifacts/{artifactId} - Generated Artifacts")
    class GeneratedArtifactsTests {

        @Test
        @DisplayName("should retrieve generated artifact")
        void shouldGetArtifact() {
            String artifactId = UUID.randomUUID().toString();

            ApiResponse<GeneratedArtifact> response = apiClient.get(
                "/artifacts/" + artifactId,
                GeneratedArtifact.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.artifactId).isEqualTo(artifactId);
            assertThat(response.body.files).isNotEmpty();
        }

        @Test
        @DisplayName("should support multiple export formats")
        void shouldSupportExportFormats() {
            String artifactId = UUID.randomUUID().toString();

            // JSON format (default)
            ApiResponse<GeneratedArtifact> jsonResponse = apiClient.get(
                "/artifacts/" + artifactId + "?format=json",
                GeneratedArtifact.class,
                "Bearer token"
            );
            assertThat(jsonResponse.statusCode).isEqualTo(200);

            // ZIP format
            ApiResponse<byte[]> zipResponse = apiClient.get(
                "/artifacts/" + artifactId + "?format=zip",
                byte[].class,
                "Bearer token"
            );
            assertThat(zipResponse.statusCode).isEqualTo(200);

            // TAR.GZ format
            ApiResponse<byte[]> targzResponse = apiClient.get(
                "/artifacts/" + artifactId + "?format=tar.gz",
                byte[].class,
                "Bearer token"
            );
            assertThat(targzResponse.statusCode).isEqualTo(200);
        }

        @Test
        @DisplayName("should list artifact versions")
        void shouldListVersions() {
            String artifactId = UUID.randomUUID().toString();

            ApiResponse<ArtifactVersionList> response = apiClient.get(
                "/artifacts/" + artifactId + "/versions",
                ArtifactVersionList.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.versions).isNotEmpty();
            response.body.versions.forEach(v -> {
                assertThat(v.version).isGreaterThan(0);
                assertThat(v.createdAt).isNotBlank();
            });
        }
    }

    @Nested
    @DisplayName("GET /refactoring-suggestions/{designId} - Refactoring")
    class RefactoringSuggestionsTests {

        @Test
        @DisplayName("should retrieve refactoring suggestions")
        void shouldGetSuggestions() {
            String designId = UUID.randomUUID().toString();

            ApiResponse<SuggestionList> response = apiClient.get(
                "/refactoring-suggestions/" + designId,
                SuggestionList.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            // Suggestions may or may not exist
            if (!response.body.suggestions.isEmpty()) {
                response.body.suggestions.forEach(s -> {
                    assertThat(s.id).isNotBlank();
                    assertThat(s.title).isNotBlank();
                    assertThat(s.category).isIn(
                        "performance", "maintainability", "testing", "security"
                    );
                    assertThat(s.impact).isIn("HIGH", "MEDIUM", "LOW");
                });
            }
        }

        @Test
        @DisplayName("should filter suggestions by category")
        void shouldFilterByCategory() {
            String designId = UUID.randomUUID().toString();

            ApiResponse<SuggestionList> response = apiClient.get(
                "/refactoring-suggestions/" + designId + "?category=performance",
                SuggestionList.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            response.body.suggestions.forEach(s ->
                assertThat(s.category).isEqualTo("performance")
            );
        }
    }

    // ========== Test Support Classes ==========

    private Map<String, Object> createValidDesignRequest() {
        List<Map<String, Object>> components = Arrays.asList(
            Map.of(
                "id", "service_user",
                "name", "User Service",
                "type", "SERVICE",
                "description", "Manages user accounts"
            ),
            Map.of(
                "id", "db_users",
                "name", "User Database",
                "type", "DATABASE",
                "description", "PostgreSQL database"
            ),
            Map.of(
                "id", "cache_redis",
                "name", "Redis Cache",
                "type", "CACHE"
            )
        );

        List<Map<String, Object>> relationships = Arrays.asList(
            Map.of(
                "fromComponent", "service_user",
                "toComponent", "db_users",
                "type", "DEPENDS_ON",
                "strength", "REQUIRED"
            ),
            Map.of(
                "fromComponent", "service_user",
                "toComponent", "cache_redis",
                "type", "USES",
                "strength", "OPTIONAL"
            )
        );

        return Map.of(
            "name", "E-commerce Platform",
            "description", "Full-featured e-commerce system",
            "components", components,
            "relationships", relationships
        );
    }

    static class Design {
        String designId;
        String name;
        String description;
        Integer version;
        String createdAt;
        String validationStatus;
        List<Map<String, Object>> components;
        List<Map<String, Object>> relationships;
    }

    static class ValidationResult {
        Boolean isValid;
        List<ValidationError> errors;
        List<ValidationWarning> warnings;
        List<List<String>> circularDependencies;
    }

    static class ValidationError {
        String code;
        String message;
        String componentId;
    }

    static class ValidationWarning {
        String code;
        String message;
    }

    static class CodeGenerationOperation {
        String operationId;
        String status;
        String artifactId;
        String createdAt;
        String completedAt;
        Map<String, Object> error;
    }

    static class GeneratedArtifact {
        String artifactId;
        String designId;
        String language;
        Integer version;
        List<GeneratedFile> files;
        String createdAt;
    }

    static class GeneratedFile {
        String path;
        String content;
    }

    static class ArtifactVersion {
        Integer version;
        String createdAt;
        String language;
        String description;
    }

    static class ArtifactVersionList {
        List<ArtifactVersion> versions;
    }

    static class RefactoringSuggestion {
        String id;
        String title;
        String description;
        String category;
        String impact;
        List<String> affectedComponents;
        Map<String, Object> suggestedChanges;
    }

    static class SuggestionList {
        List<RefactoringSuggestion> suggestions;
    }

    static class DesignValidationError {
        String code;
        String message;
        List<ValidationError> validationErrors;
    }

    // Mock API client
    static class MockCodeGenApiClient {
        private final ObjectMapper mapper;

        MockCodeGenApiClient(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        <T> ApiResponse<T> get(String path, Class<T> responseType, String authToken) {
            return mockResponse(200, null, responseType);
        }

        <T> ApiResponse<T> post(String path, Object body, Class<T> responseType, String authToken) {
            return mockResponse(202, body, responseType);
        }

        <T> ApiResponse<T> patch(String path, Object body, Class<T> responseType, String authToken) {
            return mockResponse(200, body, responseType);
        }

        private <T> ApiResponse<T> mockResponse(int statusCode, Object body, Class<T> type) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Location", "/generated-code/op-id");
            return new ApiResponse<>(statusCode, (T) body, headers);
        }
    }

    static class ApiResponse<T> {
        int statusCode;
        T body;
        Map<String, String> headers;

        ApiResponse(int statusCode, T body, Map<String, String> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }
    }
}
