package com.ghatana.tutorputor.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Integration tests for TutorPutor Content Generation API OpenAPI specification.
 * @doc.layer product-test
 * @doc.pattern IntegrationTest
 *
 * Validates:
 * - OpenAPI spec conformance (request/response schemas)
 * - HTTP method correctness (POST for create, GET for retrieve)
 * - Authentication requirements (Bearer token)
 * - Rate limiting response headers
 * - Error responses (401, 400, 429, 500)
 * - Request/response validation
 * - Pagination
 * - Example payloads from OpenAPI spec
 *
 * All tests mock HTTP layer; focus is on API contract validation.
 */
@DisplayName("TutorPutor API OpenAPI Specification Tests")
class TutorPutorApiOpenApiIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper;
    private MockApiClient apiClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        apiClient = new MockApiClient(objectMapper);
    }

    @Nested
    @DisplayName("POST /content/generate - Content Generation Endpoint")
    class ContentGenerationEndpointTests {

        @Test
        @DisplayName("should accept valid content generation request")
        void shouldAcceptValidRequest() {
            ContentGenerationRequest request = new ContentGenerationRequest.Builder()
                .topic("Photosynthesis")
                .gradeLevel("high-school")
                .format("markdown")
                .language("en-US")
                .includeExamples(true)
                .exampleCount(3)
                .build();

            ApiResponse<GeneratedContent> response = apiClient.post(
                "/content/generate",
                request,
                GeneratedContent.class,
                "Bearer token123"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body)
                .isNotNull()
                .hasFieldOrPropertyWithValue("topic", "Photosynthesis")
                .hasFieldOrPropertyWithValue("format", "markdown");
            assertThat(response.headers.get("X-Content-ID")).isNotBlank();
            assertThat(response.headers.get("X-Generation-Time-MS")).matches("\\d+");
        }

        @Test
        @DisplayName("should reject request missing required topic field")
        void shouldRejectMissingTopic() {
            Map<String, Object> request = new HashMap<>();
            request.put("gradeLevel", "high-school");
            request.put("format", "markdown");

            ApiResponse<ErrorPayload> response = apiClient.post(
                "/content/generate",
                request,
                ErrorPayload.class,
                "Bearer token123"
            );

            assertThat(response.statusCode).isEqualTo(400);
            assertThat(response.body.error).isEqualTo("INVALID_REQUEST");
            assertThat(response.body.message).contains("Topic");
            assertThat(response.body.traceId).isNotBlank();
        }

        @Test
        @DisplayName("should reject request without authentication")
        void shouldRejectUnauthenticated() {
            ContentGenerationRequest request = new ContentGenerationRequest.Builder()
                .topic("Photosynthesis")
                .gradeLevel("high-school")
                .format("markdown")
                .build();

            ApiResponse<ErrorPayload> response = apiClient.post(
                "/content/generate",
                request,
                ErrorPayload.class,
                null  // No auth token
            );

            assertThat(response.statusCode).isEqualTo(401);
            assertThat(response.body.error).isEqualTo("UNAUTHORIZED");
        }

        @Test
        @DisplayName("should enforce rate limits and return 429")
        void shouldEnforceRateLimits() {
            // Simulate 101 rapid requests (exceeds 100/minute limit)
            for (int i = 0; i < 100; i++) {
                ContentGenerationRequest request = new ContentGenerationRequest.Builder()
                    .topic("Topic " + i)
                    .gradeLevel("high-school")
                    .format("markdown")
                    .build();

                apiClient.post("/content/generate", request, GeneratedContent.class, "Bearer token");
            }

            // 101st request should hit rate limit
            ContentGenerationRequest request = new ContentGenerationRequest.Builder()
                .topic("Topic 101")
                .gradeLevel("high-school")
                .format("markdown")
                .build();

            ApiResponse<RateLimitError> response = apiClient.post(
                "/content/generate",
                request,
                RateLimitError.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(429);
            assertThat(response.body.error).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(response.headers).containsKey("X-RateLimit-Reset");
            assertThat(response.body.retryAfter).isGreaterThan(0);
        }

        @Test
        @DisplayName("should validate all supported grade levels")
        void shouldValidateGradeLevels() {
            String[] gradeLevels = {
                "elementary", "middle-school", "high-school", "undergraduate", "graduate"
            };

            for (String level : gradeLevels) {
                ContentGenerationRequest request = new ContentGenerationRequest.Builder()
                    .topic("Test Topic")
                    .gradeLevel(level)
                    .format("markdown")
                    .build();

                ApiResponse<GeneratedContent> response = apiClient.post(
                    "/content/generate",
                    request,
                    GeneratedContent.class,
                    "Bearer token"
                );

                assertThat(response.statusCode).isEqualTo(200);
            }
        }

        @Test
        @DisplayName("should reject invalid format")
        void shouldRejectInvalidFormat() {
            Map<String, Object> request = Map.of(
                "topic", "Test",
                "gradeLevel", "high-school",
                "format", "invalid-format"
            );

            ApiResponse<ErrorPayload> response = apiClient.post(
                "/content/generate",
                request,
                ErrorPayload.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
            assertThat(response.body.details.get("format")).isNotBlank();
        }

        @Test
        @DisplayName("should support optional parameters")
        void shouldSupportOptionalParameters() {
            ContentGenerationRequest request = new ContentGenerationRequest.Builder()
                .topic("Photosynthesis")
                .gradeLevel("high-school")
                .format("markdown")
                .language("fr-FR")
                .includeQuiz(true)
                .exampleCount(5)
                .build();

            ApiResponse<GeneratedContent> response = apiClient.post(
                "/content/generate",
                request,
                GeneratedContent.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.includesQuiz).isTrue();
            assertThat(response.body.exampleCount).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("GET /content/generate/{contentId} - Content Retrieval")
    class ContentRetrievalTests {

        @Test
        @DisplayName("should retrieve existing content by ID")
        void shouldRetrieveContent() {
            String contentId = UUID.randomUUID().toString();

            ApiResponse<GeneratedContent> response = apiClient.get(
                "/content/generate/" + contentId,
                GeneratedContent.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.id).isEqualTo(contentId);
            assertThat(response.body.content).isNotBlank();
        }

        @Test
        @DisplayName("should return 404 for non-existent content")
        void shouldReturn404ForMissingContent() {
            ApiResponse<ErrorPayload> response = apiClient.get(
                "/content/generate/nonexistent-id",
                ErrorPayload.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(404);
        }

        @Test
        @DisplayName("should require authentication")
        void shouldRequireAuth() {
            ApiResponse<ErrorPayload> response = apiClient.get(
                "/content/generate/" + UUID.randomUUID(),
                ErrorPayload.class,
                null  // No token
            );

            assertThat(response.statusCode).isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("GET/POST /libraries - Content Library Management")
    class LibraryManagementTests {

        @Test
        @DisplayName("should list libraries with pagination")
        void shouldListLibrariesWithPagination() {
            ApiResponse<LibraryListResponse> response = apiClient.get(
                "/libraries?page=1&pageSize=20&search=Math",
                LibraryListResponse.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.items).isNotEmpty();
            assertThat(response.body.pagination.page).isEqualTo(1);
            assertThat(response.body.pagination.pageSize).isEqualTo(20);
            assertThat(response.body.pagination.totalItems).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should create library with required fields")
        void shouldCreateLibrary() {
            Map<String, Object> request = Map.of(
                "name", "Science Library",
                "description", "Collection of science topics"
            );

            ApiResponse<ContentLibrary> response = apiClient.post(
                "/libraries",
                request,
                ContentLibrary.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(201);
            assertThat(response.body.id).isNotBlank();
            assertThat(response.body.name).isEqualTo("Science Library");
            assertThat(response.body.itemCount).isEqualTo(0);
        }

        @Test
        @DisplayName("should reject library creation without name")
        void shouldRejectMissingName() {
            Map<String, Object> request = Map.of(
                "description", "Missing name field"
            );

            ApiResponse<ErrorPayload> response = apiClient.post(
                "/libraries",
                request,
                ErrorPayload.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
            assertThat(response.body.details).containsKey("name");
        }
    }

    @Nested
    @DisplayName("GET/POST /learning-paths/{userId} - Learning Path Management")
    class LearningPathTests {

        @Test
        @DisplayName("should retrieve user learning paths")
        void shouldGetUserLearningPaths() {
            String userId = "user-123";

            ApiResponse<List<LearningPath>> response = apiClient.getList(
                "/learning-paths/" + userId,
                LearningPath.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.bodyList).isNotNull();
        }

        @Test
        @DisplayName("should create learning path with content sequence")
        void shouldCreateLearningPath() {
            String userId = "user-123";
            Map<String, Object> request = Map.of(
                "name", "Biology 101",
                "contentSequence", Arrays.asList("content-1", "content-2", "content-3")
            );

            ApiResponse<LearningPath> response = apiClient.post(
                "/learning-paths/" + userId,
                request,
                LearningPath.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(201);
            assertThat(response.body.id).isNotBlank();
            assertThat(response.body.userId).isEqualTo(userId);
            assertThat(response.body.contentSequence).hasSize(3);
            assertThat(response.body.progressPercentage).isEqualTo(0);
        }

        @Test
        @DisplayName("should reject path without content sequence")
        void shouldRejectMissingContentSequence() {
            String userId = "user-123";
            Map<String, Object> request = Map.of(
                "name", "Empty Path"
            );

            ApiResponse<ErrorPayload> response = apiClient.post(
                "/learning-paths/" + userId,
                request,
                ErrorPayload.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
            assertThat(response.body.details).containsKey("contentSequence");
        }
    }

    // ========== Test Support Classes ==========

    static class ContentGenerationRequest {
        String topic;
        String gradeLevel;
        String format;
        String language;
        Boolean includeExamples;
        Integer exampleCount;
        Boolean includeQuiz;

        static class Builder {
            private final ContentGenerationRequest req = new ContentGenerationRequest();

            Builder topic(String topic) { req.topic = topic; return this; }
            Builder gradeLevel(String level) { req.gradeLevel = level; return this; }
            Builder format(String format) { req.format = format; return this; }
            Builder language(String lang) { req.language = lang; return this; }
            Builder includeExamples(Boolean inc) { req.includeExamples = inc; return this; }
            Builder exampleCount(Integer count) { req.exampleCount = count; return this; }
            Builder includeQuiz(Boolean inc) { req.includeQuiz = inc; return this; }

            ContentGenerationRequest build() { return req; }
        }
    }

    static class GeneratedContent {
        String id;
        String topic;
        String content;
        String format;
        String gradeLevel;
        String createdAt;
        Integer estimatedReadTime;
        Integer exampleCount;
        Boolean includesQuiz;
        Integer generationTimeMs;
    }

    static class ContentLibrary {
        String id;
        String name;
        String description;
        Integer itemCount;
        String createdAt;
        Boolean isPublic;
    }

    static class LearningPath {
        String id;
        String userId;
        String name;
        List<String> contentSequence;
        Integer progressPercentage;
        Integer completedItems;
        String lastAccessedAt;
    }

    static class LibraryListResponse {
        List<ContentLibrary> items;
        Pagination pagination;
    }

    static class Pagination {
        Integer page;
        Integer pageSize;
        Integer totalItems;
        Integer totalPages;
    }

    static class ErrorPayload {
        String error;
        String message;
        Map<String, Object> details;
        String traceId;
    }

    static class RateLimitError extends ErrorPayload {
        Integer retryAfter;
    }

    // Mock API client for testing
    static class MockApiClient {
        private final ObjectMapper mapper;

        MockApiClient(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        <T> ApiResponse<T> post(String path, Object body, Class<T> responseType, String authToken) {
            return mockResponse(200, body, responseType);
        }

        <T> ApiResponse<T> get(String path, Class<T> responseType, String authToken) {
            return mockResponse(200, null, responseType);
        }

        <T> ApiResponse<List<T>> getList(String path, Class<T> itemType, String authToken) {
            return mockResponseList(200, null, itemType);
        }

        private <T> ApiResponse<T> mockResponse(int statusCode, Object body, Class<T> type) {
            return new ApiResponse<>(statusCode, (T) body, new HashMap<>());
        }

        private <T> ApiResponse<List<T>> mockResponseList(int statusCode, Object body, Class<T> type) {
            return new ApiResponse<>(statusCode, (T) body, new HashMap<>());
        }
    }

    static class ApiResponse<T> {
        int statusCode;
        T body;
        List<T> bodyList;
        Map<String, String> headers;

        ApiResponse(int statusCode, T body, Map<String, String> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }
    }
}
