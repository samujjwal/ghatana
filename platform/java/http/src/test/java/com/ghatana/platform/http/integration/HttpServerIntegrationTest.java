package com.ghatana.platform.http.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for HTTP server functionality.
 *
 * @doc.type class
 * @doc.purpose Integration tests for HTTP server request/response flows
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("HTTP Server Integration Tests [GH-90000]")
class HttpServerIntegrationTest extends EventloopTestBase {

    @ParameterizedTest
    @EnumSource(value = HttpMethod.class, names = {"GET", "POST"}) // GH-90000
    @DisplayName("should handle GET and POST methods [GH-90000]")
    void shouldHandleGetAndPost(HttpMethod method) { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(method, "/test", request -> // GH-90000
                HttpResponse.ok200() // GH-90000
                    .withBody(("Method: " + method.name()).getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise()) // GH-90000
            .build(); // GH-90000

        HttpRequest request = switch (method) { // GH-90000
            case GET -> HttpRequest.get("http://localhost/test [GH-90000]").build();
            case POST -> HttpRequest.post("http://localhost/test [GH-90000]").build();
            default -> throw new IllegalStateException("Unexpected value: " + method); // GH-90000
        };
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains(method.name()); // GH-90000
    }

    @Test
    @DisplayName("should handle request with headers [GH-90000]")
    void shouldHandleRequestWithHeaders() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/headers", request -> { // GH-90000
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION); // GH-90000
                return HttpResponse.ok200() // GH-90000
                    .withBody(("Auth: " + authHeader).getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/headers [GH-90000]")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token") // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("Bearer test-token [GH-90000]");
    }

    @Test
    @DisplayName("should handle request with query parameters [GH-90000]")
    void shouldHandleRequestWithQueryParameters() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/search", request -> { // GH-90000
                String query = request.getQueryParameter("q [GH-90000]");
                return HttpResponse.ok200() // GH-90000
                    .withBody(("Query: " + query).getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/search?q=test [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("Query: test [GH-90000]");
    }

    @Test
    @DisplayName("should handle POST request with body [GH-90000]")
    void shouldHandlePostRequestWithBody() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.POST, "/data", request -> { // GH-90000
                String body = new String(request.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
                return HttpResponse.ok200() // GH-90000
                    .withBody(("Received: " + body).getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest request = HttpRequest.post("http://localhost/data [GH-90000]")
            .withBody("test data".getBytes(StandardCharsets.UTF_8)) // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String responseBody = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(responseBody).contains("test data [GH-90000]");
    }

    @Test
    @DisplayName("should handle JSON request and response [GH-90000]")
    void shouldHandleJsonRequestAndResponse() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.POST, "/json", request -> { // GH-90000
                return HttpResponse.ok200() // GH-90000
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                    .withBody("{\"status\":\"success\"}".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest request = HttpRequest.post("http://localhost/json [GH-90000]")
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
            .withBody("{\"data\":\"test\"}".getBytes(StandardCharsets.UTF_8)) // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/json [GH-90000]");
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("success [GH-90000]");
    }

    @Test
    @DisplayName("should handle 404 for unknown routes [GH-90000]")
    void shouldHandle404ForUnknownRoutes() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/known", request -> HttpResponse.ok200().toPromise()) // GH-90000
            .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/unknown [GH-90000]").build();
        // RoutingServlet throws HttpError(404) for unknown routes — verify the exception code // GH-90000
        assertThatThrownBy(() -> runPromise(() -> servlet.serve(request))) // GH-90000
                .cause() // GH-90000
                .isInstanceOfSatisfying(io.activej.http.HttpError.class, // GH-90000
                        err -> assertThat(err.getCode()).isEqualTo(404)); // GH-90000
    }

    @Test
    @DisplayName("should handle error responses [GH-90000]")
    void shouldHandleErrorResponses() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/error", request -> // GH-90000
                HttpResponse.ofCode(500) // GH-90000
                    .withBody("Internal Server Error".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise()) // GH-90000
            .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/error [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(500); // GH-90000
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("Internal Server Error [GH-90000]");
    }

    @Test
    @DisplayName("should handle concurrent requests [GH-90000]")
    void shouldHandleConcurrentRequests() { // GH-90000
        AtomicInteger requestCount = new AtomicInteger(0); // GH-90000

        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/concurrent", request -> { // GH-90000
                int count = requestCount.incrementAndGet(); // GH-90000
                return HttpResponse.ok200() // GH-90000
                    .withBody(("Request: " + count).getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest request1 = HttpRequest.get("http://localhost/concurrent [GH-90000]").build();
        HttpRequest request2 = HttpRequest.get("http://localhost/concurrent [GH-90000]").build();
        HttpRequest request3 = HttpRequest.get("http://localhost/concurrent [GH-90000]").build();

        HttpResponse response1 = runPromise(() -> servlet.serve(request1)); // GH-90000
        HttpResponse response2 = runPromise(() -> servlet.serve(request2)); // GH-90000
        HttpResponse response3 = runPromise(() -> servlet.serve(request3)); // GH-90000

        assertThat(response1.getCode()).isEqualTo(200); // GH-90000
        assertThat(response2.getCode()).isEqualTo(200); // GH-90000
        assertThat(response3.getCode()).isEqualTo(200); // GH-90000
        assertThat(requestCount.get()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("should handle custom response headers [GH-90000]")
    void shouldHandleCustomResponseHeaders() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/custom-headers", request -> // GH-90000
                HttpResponse.ok200() // GH-90000
                    .withHeader(HttpHeaders.of("X-Custom-Header [GH-90000]"), "custom-value")
                    .withHeader(HttpHeaders.CACHE_CONTROL, "no-cache") // GH-90000
                    .toPromise()) // GH-90000
            .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/custom-headers [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(response.getHeader(HttpHeaders.of("X-Custom-Header [GH-90000]"))).isEqualTo("custom-value [GH-90000]");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache [GH-90000]");
    }

    @Test
    @DisplayName("should handle HTTP PUT request [GH-90000]")
    void shouldHandlePutRequest() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.PUT, "/update", request -> { // GH-90000
                String body = new String(request.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
                return HttpResponse.ok200() // GH-90000
                    .withBody(("Updated: " + body).getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest request = HttpRequest.put("http://localhost/update [GH-90000]")
            .withBody("new data".getBytes(StandardCharsets.UTF_8)) // GH-90000
            .build(); // GH-90000
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String responseBody = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(responseBody).contains("new data [GH-90000]");
    }

    @Test
    @DisplayName("should handle path parameters [GH-90000]")
    void shouldHandlePathParameters() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/users/:id", request -> { // GH-90000
                String id = request.getPathParameter("id [GH-90000]");
                return HttpResponse.ok200() // GH-90000
                    .withBody(("User ID: " + id).getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/users/123 [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("123 [GH-90000]");
    }

    @Test
    @DisplayName("should handle request timeout scenarios [GH-90000]")
    void shouldHandleRequestTimeoutScenarios() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/timeout", request -> // GH-90000
                HttpResponse.ofCode(408) // GH-90000
                    .withBody("Request Timeout".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise()) // GH-90000
            .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/timeout [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(408); // GH-90000
    }

    @Test
    @DisplayName("should handle large request bodies [GH-90000]")
    void shouldHandleLargeRequestBodies() { // GH-90000
        byte[] largeBody = new byte[10000];
        for (int i = 0; i < largeBody.length; i++) { // GH-90000
            largeBody[i] = (byte) (i % 256); // GH-90000
        }

        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.POST, "/large", request -> { // GH-90000
                int bodySize = request.getBody().asArray().length; // GH-90000
                return HttpResponse.ok200() // GH-90000
                    .withBody(("Size: " + bodySize).getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest request = HttpRequest.post("http://localhost/large [GH-90000]")
            .withBody(largeBody) // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("10000 [GH-90000]");
    }

    @Test
    @DisplayName("should handle content negotiation [GH-90000]")
    void shouldHandleContentNegotiation() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/negotiate", request -> { // GH-90000
                String accept = request.getHeader(HttpHeaders.ACCEPT); // GH-90000
                if (accept != null && accept.contains("application/json [GH-90000]")) {
                    return HttpResponse.ok200() // GH-90000
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                        .withBody("{\"format\":\"json\"}".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                } else {
                    return HttpResponse.ok200() // GH-90000
                        .withHeader(HttpHeaders.CONTENT_TYPE, "text/plain") // GH-90000
                        .withBody("format: plain".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }
            })
            .build(); // GH-90000

        HttpRequest jsonRequest = HttpRequest.get("http://localhost/negotiate [GH-90000]")
            .withHeader(HttpHeaders.ACCEPT, "application/json") // GH-90000
            .build(); // GH-90000

        HttpResponse jsonResponse = runPromise(() -> servlet.serve(jsonRequest)); // GH-90000

        assertThat(jsonResponse.getCode()).isEqualTo(200); // GH-90000
        assertThat(jsonResponse.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/json [GH-90000]");
    }
}
