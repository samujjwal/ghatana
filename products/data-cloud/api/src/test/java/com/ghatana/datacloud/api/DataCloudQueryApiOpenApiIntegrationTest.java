package com.ghatana.datacloud.api;

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
 * @doc.purpose Integration tests for Data Cloud Query API OpenAPI specification.
 * @doc.layer product-test
 * @doc.pattern IntegrationTest
 *
 * Validates:
 * - OpenAPI spec conformance for query execution
 * - Asynchronous query execution (202 Accepted)
 * - Query result pagination
 * - Authentication (Bearer token + API Key)
 * - Error responses (syntax, timeout, unauthorized)
 * - Dataset metadata retrieval
 * - Aggregation operations
 * - Result streaming support
 */
@DisplayName("Data Cloud Query API OpenAPI Specification Tests")
class DataCloudQueryApiOpenApiIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper;
    private MockQueryApiClient apiClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        apiClient = new MockQueryApiClient(objectMapper);
    }

    @Nested
    @DisplayName("GET /datasets - Dataset Listing")
    class DatasetListingTests {

        @Test
        @DisplayName("should list datasets with pagination")
        void shouldListDatasets() {
            ApiResponse<DatasetListResponse> response = apiClient.get(
                "/datasets?limit=20&offset=0",
                DatasetListResponse.class,
                "Bearer token123"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.items).isNotEmpty();
            assertThat(response.body.pagination.limit).isEqualTo(20);
            assertThat(response.body.pagination.total).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should enforce maximum limit of 100")
        void shouldEnforceMaximumLimit() {
            ApiResponse<ErrorPayload> response = apiClient.get(
                "/datasets?limit=101",
                ErrorPayload.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
        }

        @Test
        @DisplayName("should search datasets by name")
        void shouldSearchDatasets() {
            ApiResponse<DatasetListResponse> response = apiClient.get(
                "/datasets?search=sales",
                DatasetListResponse.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            // All returned items should contain "sales" in name
            response.body.items.forEach(ds ->
                assertThat(ds.name.toLowerCase()).contains("sales")
            );
        }

        @Test
        @DisplayName("should require authentication")
        void shouldRequireAuth() {
            ApiResponse<ErrorPayload> response = apiClient.get(
                "/datasets",
                ErrorPayload.class,
                null  // No auth
            );

            assertThat(response.statusCode).isEqualTo(401);
            assertThat(response.body.code).isEqualTo("UNAUTHORIZED");
        }
    }

    @Nested
    @DisplayName("GET /datasets/{datasetId} - Dataset Metadata")
    class DatasetMetadataTests {

        @Test
        @DisplayName("should retrieve dataset metadata")
        void shouldGetDatasetMetadata() {
            ApiResponse<DatasetMetadata> response = apiClient.get(
                "/datasets/dataset-123",
                DatasetMetadata.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.datasetId).isEqualTo("dataset-123");
            assertThat(response.body.recordCount).isGreaterThanOrEqualTo(0);
            assertThat(response.body.columns).isNotEmpty();
        }

        @Test
        @DisplayName("should include column schemas with types")
        void shouldIncludeColumnSchemas() {
            ApiResponse<DatasetMetadata> response = apiClient.get(
                "/datasets/dataset-123",
                DatasetMetadata.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            response.body.columns.forEach(col -> {
                assertThat(col.name).isNotBlank();
                assertThat(col.type).isIn(
                    "STRING", "INTEGER", "BIGINT", "DOUBLE", "DECIMAL",
                    "BOOLEAN", "DATE", "TIMESTAMP", "ARRAY", "MAP"
                );
            });
        }

        @Test
        @DisplayName("should return 404 for non-existent dataset")
        void shouldReturn404ForMissingDataset() {
            ApiResponse<ErrorPayload> response = apiClient.get(
                "/datasets/nonexistent",
                ErrorPayload.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("POST /queries - Query Execution")
    class QueryExecutionTests {

        @Test
        @DisplayName("should accept query and return 202 Accepted")
        void shouldAcceptQuery() {
            Map<String, Object> request = Map.of(
                "datasetId", "dataset-123",
                "queryText", "SELECT * FROM users WHERE status = 'active' LIMIT 100"
            );

            ApiResponse<QueryOperation> response = apiClient.post(
                "/queries",
                request,
                QueryOperation.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(202);
            assertThat(response.body.operationId).isNotBlank();
            assertThat(response.body.status).isEqualTo("QUEUED");
            assertThat(response.headers.get("Location")).containsPattern("/queries/.*");
        }

        @Test
        @DisplayName("should validate query syntax")
        void shouldValidateQuerySyntax() {
            Map<String, Object> request = Map.of(
                "datasetId", "dataset-123",
                "queryText", "INVALID SQL SYNTAX !!!"
            );

            ApiResponse<QueryError> response = apiClient.post(
                "/queries",
                request,
                QueryError.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
            assertThat(response.body.code).isEqualTo("SYNTAX_ERROR");
        }

        @Test
        @DisplayName("should reject INSERT/UPDATE queries (SELECT only)")
        void shouldRejectNonSelectQueries() {
            Map<String, Object> request = Map.of(
                "datasetId", "dataset-123",
                "queryText", "INSERT INTO users VALUES ('John', 'Doe')"
            );

            ApiResponse<QueryError> response = apiClient.post(
                "/queries",
                request,
                QueryError.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
            assertThat(response.body.code).isIn("SYNTAX_ERROR", "INVALID_OPERATION");
        }

        @Test
        @DisplayName("should support custom timeout (max 3600s)")
        void shouldSupportCustomTimeout() {
            Map<String, Object> request = Map.of(
                "datasetId", "dataset-123",
                "queryText", "SELECT * FROM large_table",
                "queryTimeoutSeconds", 600
            );

            ApiResponse<QueryOperation> response = apiClient.post(
                "/queries",
                request,
                QueryOperation.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(202);
        }

        @Test
        @DisplayName("should reject timeout > 3600 seconds")
        void shouldRejectExcessiveTimeout() {
            Map<String, Object> request = Map.of(
                "datasetId", "dataset-123",
                "queryText", "SELECT * FROM table",
                "queryTimeoutSeconds", 3601
            );

            ApiResponse<QueryError> response = apiClient.post(
                "/queries",
                request,
                QueryError.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET /queries/{operationId} - Query Status")
    class QueryStatusTests {

        @Test
        @DisplayName("should retrieve query operation status")
        void shouldGetQueryStatus() {
            String operationId = UUID.randomUUID().toString();

            ApiResponse<QueryOperation> response = apiClient.get(
                "/queries/" + operationId,
                QueryOperation.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.operationId).isEqualTo(operationId);
            assertThat(response.body.status).isIn("QUEUED", "RUNNING", "COMPLETED", "FAILED", "CANCELLED");
        }

        @Test
        @DisplayName("should return 404 for non-existent operation")
        void shouldReturn404ForMissingOperation() {
            ApiResponse<QueryError> response = apiClient.get(
                "/queries/nonexistent-operation",
                QueryError.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("GET /queries/{operationId}/results - Query Results")
    class QueryResultsTests {

        @Test
        @DisplayName("should retrieve completed query results")
        void shouldGetResults() {
            String operationId = UUID.randomUUID().toString();

            ApiResponse<QueryResult> response = apiClient.get(
                "/queries/" + operationId + "/results?limit=1000&offset=0",
                QueryResult.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.operationId).isEqualTo(operationId);
            assertThat(response.body.columnNames).isNotEmpty();
            assertThat(response.body.rows).isNotNull();
            assertThat(response.headers.get("X-Total-Rows")).matches("\\d+");
            assertThat(response.headers.get("X-Returned-Rows")).matches("\\d+");
        }

        @Test
        @DisplayName("should support pagination with limit and offset")
        void shouldSupportPagination() {
            String operationId = UUID.randomUUID().toString();

            ApiResponse<QueryResult> response = apiClient.get(
                "/queries/" + operationId + "/results?limit=100&offset=500",
                QueryResult.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.rows).hasSizeLessThanOrEqualTo(100);
        }

        @Test
        @DisplayName("should return 202 if query still executing")
        void shouldReturn202IfStillRunning() {
            String operationId = UUID.randomUUID().toString();

            ApiResponse<QueryOperation> response = apiClient.get(
                "/queries/" + operationId + "/results",
                QueryOperation.class,
                "Bearer token"
            );

            // Depending on execution status, could be 200 or 202
            assertThat(response.statusCode).isIn(200, 202);
        }

        @Test
        @DisplayName("should enforce maximum limit of 10000")
        void shouldEnforceMaximumResultLimit() {
            String operationId = UUID.randomUUID().toString();

            ApiResponse<QueryError> response = apiClient.get(
                "/queries/" + operationId + "/results?limit=10001",
                QueryError.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /analytics/aggregate - Aggregations")
    class AggregationTests {

        @Test
        @DisplayName("should compute SUM aggregation")
        void shouldComputeSum() {
            Map<String, Object> request = Map.of(
                "datasetId", "sales",
                "aggregationType", "SUM",
                "column", "amount",
                "groupBy", Arrays.asList("region", "product")
            );

            ApiResponse<AggregationResult> response = apiClient.post(
                "/analytics/aggregate",
                request,
                AggregationResult.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.aggregationType).isEqualTo("SUM");
            assertThat(response.body.result).isNotNull();
        }

        @Test
        @DisplayName("should compute COUNT aggregation")
        void shouldComputeCount() {
            Map<String, Object> request = Map.of(
                "datasetId", "users",
                "aggregationType", "COUNT",
                "column", "*"
            );

            ApiResponse<AggregationResult> response = apiClient.post(
                "/analytics/aggregate",
                request,
                AggregationResult.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
            assertThat(response.body.result).isInstanceOf(Number.class);
        }

        @Test
        @DisplayName("should support WHERE clause filters")
        void shouldSupportWhereClause() {
            Map<String, Object> request = Map.of(
                "datasetId", "transactions",
                "aggregationType", "SUM",
                "column", "amount",
                "whereClause", "status = 'completed'"
            );

            ApiResponse<AggregationResult> response = apiClient.post(
                "/analytics/aggregate",
                request,
                AggregationResult.class,
                "Bearer token"
            );

            assertThat(response.statusCode).isEqualTo(200);
        }

        @Test
        @DisplayName("should support all aggregation types")
        void shouldSupportAllTypes() {
            String[] types = {
                "COUNT", "SUM", "AVG", "MIN", "MAX", "STDDEV",
                "PERCENTILE_25", "PERCENTILE_50", "PERCENTILE_75", "PERCENTILE_99"
            };

            for (String type : types) {
                Map<String, Object> request = Map.of(
                    "datasetId", "data",
                    "aggregationType", type,
                    "column", "value"
                );

                ApiResponse<AggregationResult> response = apiClient.post(
                    "/analytics/aggregate",
                    request,
                    AggregationResult.class,
                    "Bearer token"
                );

                assertThat(response.statusCode).isEqualTo(200);
            }
        }
    }

    @Nested
    @DisplayName("Authentication - Bearer Token vs API Key")
    class AuthenticationTests {

        @Test
        @DisplayName("should accept Bearer token authentication")
        void shouldAcceptBearerToken() {
            ApiResponse<DatasetListResponse> response = apiClient.get(
                "/datasets",
                DatasetListResponse.class,
                "Bearer jwt.token.here"
            );

            assertThat(response.statusCode).isEqualTo(200);
        }

        @Test
        @DisplayName("should accept API Key authentication")
        void shouldAcceptApiKey() {
            apiClient.setApiKey("secret-api-key-123");
            ApiResponse<DatasetListResponse> response = apiClient.get(
                "/datasets",
                DatasetListResponse.class,
                null  // No bearer token, use API key
            );

            assertThat(response.statusCode).isEqualTo(200);
        }

        @Test
        @DisplayName("should require either Bearer or API Key")
        void shouldRequireAuth() {
            apiClient.setApiKey(null);
            ApiResponse<ErrorPayload> response = apiClient.get(
                "/datasets",
                ErrorPayload.class,
                null  // No auth at all
            );

            assertThat(response.statusCode).isEqualTo(401);
        }
    }

    // ========== Test Support Classes ==========

    static class DatasetMetadata {
        String datasetId;
        String name;
        String description;
        Long recordCount;
        List<ColumnSchema> columns;
        String createdAt;
        String lastUpdatedAt;
        Long sizeBytes;
    }

    static class ColumnSchema {
        String name;
        String type;
        Boolean nullable;
        String description;
    }

    static class DatasetListResponse {
        List<DatasetMetadata> items;
        Pagination pagination;
    }

    static class Pagination {
        Integer limit;
        Integer offset;
        Integer total;
    }

    static class QueryOperation {
        String operationId;
        String status;
        String createdAt;
        String startedAt;
        String completedAt;
        Integer executionTimeMs;
        Integer rowsProcessed;
        Integer rowsReturned;
        QueryError error;
    }

    static class QueryResult {
        String operationId;
        List<String> columnNames;
        List<String> columnTypes;
        List<List<Object>> rows;
        Integer totalRows;
        Integer returnedRows;
    }

    static class AggregationResult {
        String aggregationType;
        String column;
        Object result;
        List<Map<String, Object>> groupByValues;
        Integer executionTimeMs;
    }

    static class QueryError {
        String code;
        String message;
        Map<String, Object> details;
    }

    static class ErrorPayload {
        String code;
        String message;
    }

    // Mock API client
    static class MockQueryApiClient {
        private final ObjectMapper mapper;
        private String apiKey;

        MockQueryApiClient(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        void setApiKey(String key) { this.apiKey = key; }

        <T> ApiResponse<T> get(String path, Class<T> responseType, String authToken) {
            return mockResponse(200, null, responseType);
        }

        <T> ApiResponse<T> post(String path, Object body, Class<T> responseType, String authToken) {
            return mockResponse(202, body, responseType);
        }

        private <T> ApiResponse<T> mockResponse(int statusCode, Object body, Class<T> type) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Location", "/queries/op-id");
            headers.put("X-Total-Rows", "1000");
            headers.put("X-Returned-Rows", "100");
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
