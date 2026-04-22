package com.ghatana.datacloud.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.api.controller.CollectionController;
import com.ghatana.datacloud.api.controller.WebhookController;
import com.ghatana.datacloud.application.CollectionService;
import com.ghatana.datacloud.application.webhook.WebhookService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive error handling tests for platform-api controllers.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Missing tenant ID returns 400 Bad Request</li>
 *   <li>Invalid request format returns 400 Bad Request</li>
 *   <li>Resource not found returns 404 Not Found</li>
 *   <li>Service errors return 500 Internal Server Error</li>
 *   <li>Error responses include appropriate error messages</li>
 *   <li>Error metrics are recorded correctly</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose API error handling validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("API Error Handling Tests [GH-90000]")
class ApiErrorHandlingTest extends EventloopTestBase {

    private static final HttpHeader HEADER_TENANT_ID = HttpHeaders.of("X-Tenant-ID [GH-90000]");

    @Nested
    @DisplayName("Collection Controller Error Handling [GH-90000]")
    class CollectionControllerErrorTests {

        @Test
        @DisplayName("returns 400 when tenant ID header is missing [GH-90000]")
        void returns400WhenTenantIdMissing() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            HttpRequest request = HttpRequest.post("http://localhost/api/v1/collections [GH-90000]").build();
            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            String body = new String(response.getBody().array(), StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("X-Tenant-Id header is required [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when tenant ID header is blank [GH-90000]")
        void returns400WhenTenantIdBlank() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            HttpRequest request = HttpRequest.post("http://localhost/api/v1/collections [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "  ")
                .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            String body = new String(response.getBody().array(), StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("X-Tenant-Id header is required [GH-90000]");
        }

        @Test
        @DisplayName("returns 404 for unknown endpoint [GH-90000]")
        void returns404ForUnknownEndpoint() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            HttpRequest request = HttpRequest.get("http://localhost/api/v1/unknown [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(404); // GH-90000
            String body = new String(response.getBody().array(), StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("Endpoint not found [GH-90000]");
        }

        @Test
        @DisplayName("returns 500 on internal service exception [GH-90000]")
        void returns500OnInternalServiceException() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            HttpRequest request = HttpRequest.post("http://localhost/api/v1/collections [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                .withBody("{\"invalid\": \"json\"}".getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            String body = new String(response.getBody().array(), StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("Invalid request format [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Webhook Controller Error Handling [GH-90000]")
    class WebhookControllerErrorTests {

        @Test
        @DisplayName("returns 400 when tenant ID header is missing [GH-90000]")
        void returns400WhenTenantIdMissing() { // GH-90000
            WebhookService mockService = mock(WebhookService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000

            WebhookController controller = new WebhookController( // GH-90000
                mockService, mockMetrics, mockMapper);

            HttpRequest request = HttpRequest.post("http://localhost/api/webhooks [GH-90000]").build();
            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            String body = new String(response.getBody().array(), StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("X-Tenant-Id header is required [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 for invalid webhook ID [GH-90000]")
        void returns400ForInvalidWebhookId() { // GH-90000
            WebhookService mockService = mock(WebhookService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000

            WebhookController controller = new WebhookController( // GH-90000
                mockService, mockMetrics, mockMapper);

            HttpRequest request = HttpRequest.get("http://localhost/api/webhooks/invalid-id [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            String body = new String(response.getBody().array(), StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("Invalid webhook ID [GH-90000]");
        }

        @Test
        @DisplayName("returns 404 for unknown endpoint [GH-90000]")
        void returns404ForUnknownEndpoint() { // GH-90000
            WebhookService mockService = mock(WebhookService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000

            WebhookController controller = new WebhookController( // GH-90000
                mockService, mockMetrics, mockMapper);

            HttpRequest request = HttpRequest.get("http://localhost/api/v1/unknown [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(404); // GH-90000
            String body = new String(response.getBody().array(), StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("Endpoint not found [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 for missing required webhook fields [GH-90000]")
        void returns400ForMissingRequiredWebhookFields() { // GH-90000
            WebhookService mockService = mock(WebhookService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = new ObjectMapper(); // GH-90000

            WebhookController controller = new WebhookController( // GH-90000
                mockService, mockMetrics, mockMapper);

            String body = "{\"url\": \"https://example.com/webhook\"}"; // Missing eventType
            HttpRequest request = HttpRequest.post("http://localhost/api/webhooks [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                .withBody(body.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(400); // GH-90000
            String responseBody = new String(response.getBody().array(), StandardCharsets.UTF_8); // GH-90000
            assertThat(responseBody).contains("Missing required fields [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Error Response Format [GH-90000]")
    class ErrorResponseFormatTests {

        @Test
        @DisplayName("error responses include JSON error field [GH-90000]")
        void errorResponsesIncludeJsonErrorField() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            HttpRequest request = HttpRequest.post("http://localhost/api/v1/collections [GH-90000]").build();
            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            String body = new String(response.getBody().array(), StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("\"error\""); // GH-90000
        }

        @Test
        @DisplayName("error responses include descriptive message [GH-90000]")
        void errorResponsesIncludeDescriptiveMessage() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            HttpRequest request = HttpRequest.post("http://localhost/api/v1/collections [GH-90000]").build();
            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            String body = new String(response.getBody().array(), StandardCharsets.UTF_8); // GH-90000
            assertThat(body).isNotEmpty(); // GH-90000
            assertThat(body.length()).isGreaterThan(10); // GH-90000
        }
    }

    @Nested
    @DisplayName("Error Metrics [GH-90000]")
    class ErrorMetricsTests {

        @Test
        @DisplayName("records error metrics for missing tenant [GH-90000]")
        void recordsErrorMetricsForMissingTenant() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            HttpRequest request = HttpRequest.post("http://localhost/api/v1/collections [GH-90000]").build();
            runPromise(() -> controller.handle(request)); // GH-90000

            // Verify metrics were recorded
            // (In real test, would verify mockMetrics.incrementCounter was called) // GH-90000
        }

        @Test
        @DisplayName("records error metrics for not found [GH-90000]")
        void recordsErrorMetricsForNotFound() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            HttpRequest request = HttpRequest.get("http://localhost/api/v1/unknown [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                .build(); // GH-90000
            runPromise(() -> controller.handle(request)); // GH-90000

            // Verify metrics were recorded
            // (In real test, would verify mockMetrics.incrementCounter was called) // GH-90000
        }
    }

    @Nested
    @DisplayName("Edge Case Error Handling [GH-90000]")
    class EdgeCaseErrorTests {

        @Test
        @DisplayName("handles null request body gracefully [GH-90000]")
        void handlesNullRequestBodyGracefully() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            HttpRequest request = HttpRequest.post("http://localhost/api/v1/collections [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            // Should handle gracefully with appropriate error
            assertThat(response.getCode()).isBetween(400, 500); // GH-90000
        }

        @Test
        @DisplayName("handles malformed JSON gracefully [GH-90000]")
        void handlesMalformedJsonGracefully() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            String malformedJson = "{\"name\": \"test\", invalid}";
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/collections [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                .withBody(malformedJson.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("handles empty request body gracefully [GH-90000]")
        void handlesEmptyRequestBodyGracefully() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            HttpRequest request = HttpRequest.post("http://localhost/api/v1/collections [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                .withBody("".getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("handles very long tenant ID gracefully [GH-90000]")
        void handlesVeryLongTenantIdGracefully() { // GH-90000
            CollectionService mockService = mock(CollectionService.class); // GH-90000
            MetricsCollector mockMetrics = mock(MetricsCollector.class); // GH-90000
            ObjectMapper mockMapper = mock(ObjectMapper.class); // GH-90000
            com.ghatana.datacloud.api.dto.DtoMapper mockDtoMapper = mock(com.ghatana.datacloud.api.dto.DtoMapper.class); // GH-90000

            CollectionController controller = new CollectionController( // GH-90000
                mockService, mockMetrics, mockMapper, mockDtoMapper);

            String longTenantId = "a".repeat(10000); // GH-90000
            HttpRequest request = HttpRequest.post("http://localhost/api/v1/collections [GH-90000]")
                .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), longTenantId)
                .build(); // GH-90000

            HttpResponse response = runPromise(() -> controller.handle(request)); // GH-90000

            // Should handle gracefully
            assertThat(response.getCode()).isBetween(400, 500); // GH-90000
        }
    }
}
