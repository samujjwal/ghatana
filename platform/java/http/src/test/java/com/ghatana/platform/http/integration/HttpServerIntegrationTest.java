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
@DisplayName("HTTP Server Integration Tests")
class HttpServerIntegrationTest extends EventloopTestBase {

    @ParameterizedTest
    @EnumSource(value = HttpMethod.class, names = {"GET", "POST"}) 
    @DisplayName("should handle GET and POST methods")
    void shouldHandleGetAndPost(HttpMethod method) { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(method, "/test", request -> 
                HttpResponse.ok200() 
                    .withBody(("Method: " + method.name()).getBytes(StandardCharsets.UTF_8)) 
                    .toPromise()) 
            .build(); 

        HttpRequest request = switch (method) { 
            case GET -> HttpRequest.get("http://localhost/test").build();
            case POST -> HttpRequest.post("http://localhost/test").build();
            default -> throw new IllegalStateException("Unexpected value: " + method); 
        };
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains(method.name()); 
    }

    @Test
    @DisplayName("should handle request with headers")
    void shouldHandleRequestWithHeaders() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.GET, "/headers", request -> { 
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION); 
                return HttpResponse.ok200() 
                    .withBody(("Auth: " + authHeader).getBytes(StandardCharsets.UTF_8)) 
                    .toPromise(); 
            })
            .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/headers")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token") 
            .build(); 

        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains("Bearer test-token");
    }

    @Test
    @DisplayName("should handle request with query parameters")
    void shouldHandleRequestWithQueryParameters() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.GET, "/search", request -> { 
                String query = request.getQueryParameter("q");
                return HttpResponse.ok200() 
                    .withBody(("Query: " + query).getBytes(StandardCharsets.UTF_8)) 
                    .toPromise(); 
            })
            .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/search?q=test").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains("Query: test");
    }

    @Test
    @DisplayName("should handle POST request with body")
    void shouldHandlePostRequestWithBody() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.POST, "/data", request -> { 
                String body = new String(request.getBody().asArray(), StandardCharsets.UTF_8); 
                return HttpResponse.ok200() 
                    .withBody(("Received: " + body).getBytes(StandardCharsets.UTF_8)) 
                    .toPromise(); 
            })
            .build(); 

        HttpRequest request = HttpRequest.post("http://localhost/data")
            .withBody("test data".getBytes(StandardCharsets.UTF_8)) 
            .build(); 

        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
        String responseBody = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(responseBody).contains("test data");
    }

    @Test
    @DisplayName("should handle JSON request and response")
    void shouldHandleJsonRequestAndResponse() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.POST, "/json", request -> { 
                return HttpResponse.ok200() 
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") 
                    .withBody("{\"status\":\"success\"}".getBytes(StandardCharsets.UTF_8)) 
                    .toPromise(); 
            })
            .build(); 

        HttpRequest request = HttpRequest.post("http://localhost/json")
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") 
            .withBody("{\"data\":\"test\"}".getBytes(StandardCharsets.UTF_8)) 
            .build(); 

        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/json");
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains("success");
    }

    @Test
    @DisplayName("should handle 404 for unknown routes")
    void shouldHandle404ForUnknownRoutes() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.GET, "/known", request -> HttpResponse.ok200().toPromise()) 
            .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/unknown").build();
        // RoutingServlet throws HttpError(404) for unknown routes — verify the exception code 
        assertThatThrownBy(() -> runPromise(() -> servlet.serve(request))) 
                .cause() 
                .isInstanceOfSatisfying(io.activej.http.HttpError.class, 
                        err -> assertThat(err.getCode()).isEqualTo(404)); 
    }

    @Test
    @DisplayName("should handle error responses")
    void shouldHandleErrorResponses() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.GET, "/error", request -> 
                HttpResponse.ofCode(500) 
                    .withBody("Internal Server Error".getBytes(StandardCharsets.UTF_8)) 
                    .toPromise()) 
            .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/error").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(500); 
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains("Internal Server Error");
    }

    @Test
    @DisplayName("should handle concurrent requests")
    void shouldHandleConcurrentRequests() { 
        AtomicInteger requestCount = new AtomicInteger(0); 

        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.GET, "/concurrent", request -> { 
                int count = requestCount.incrementAndGet(); 
                return HttpResponse.ok200() 
                    .withBody(("Request: " + count).getBytes(StandardCharsets.UTF_8)) 
                    .toPromise(); 
            })
            .build(); 

        HttpRequest request1 = HttpRequest.get("http://localhost/concurrent").build();
        HttpRequest request2 = HttpRequest.get("http://localhost/concurrent").build();
        HttpRequest request3 = HttpRequest.get("http://localhost/concurrent").build();

        HttpResponse response1 = runPromise(() -> servlet.serve(request1)); 
        HttpResponse response2 = runPromise(() -> servlet.serve(request2)); 
        HttpResponse response3 = runPromise(() -> servlet.serve(request3)); 

        assertThat(response1.getCode()).isEqualTo(200); 
        assertThat(response2.getCode()).isEqualTo(200); 
        assertThat(response3.getCode()).isEqualTo(200); 
        assertThat(requestCount.get()).isEqualTo(3); 
    }

    @Test
    @DisplayName("should handle custom response headers")
    void shouldHandleCustomResponseHeaders() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.GET, "/custom-headers", request -> 
                HttpResponse.ok200() 
                    .withHeader(HttpHeaders.of("X-Custom-Header"), "custom-value")
                    .withHeader(HttpHeaders.CACHE_CONTROL, "no-cache") 
                    .toPromise()) 
            .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/custom-headers").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
        assertThat(response.getHeader(HttpHeaders.of("X-Custom-Header"))).isEqualTo("custom-value");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("should handle HTTP PUT request")
    void shouldHandlePutRequest() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.PUT, "/update", request -> { 
                String body = new String(request.getBody().asArray(), StandardCharsets.UTF_8); 
                return HttpResponse.ok200() 
                    .withBody(("Updated: " + body).getBytes(StandardCharsets.UTF_8)) 
                    .toPromise(); 
            })
            .build(); 

        HttpRequest request = HttpRequest.put("http://localhost/update")
            .withBody("new data".getBytes(StandardCharsets.UTF_8)) 
            .build(); 
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
        String responseBody = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(responseBody).contains("new data");
    }

    @Test
    @DisplayName("should handle path parameters")
    void shouldHandlePathParameters() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.GET, "/users/:id", request -> { 
                String id = request.getPathParameter("id");
                return HttpResponse.ok200() 
                    .withBody(("User ID: " + id).getBytes(StandardCharsets.UTF_8)) 
                    .toPromise(); 
            })
            .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/users/123").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains("123");
    }

    @Test
    @DisplayName("should handle request timeout scenarios")
    void shouldHandleRequestTimeoutScenarios() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.GET, "/timeout", request -> 
                HttpResponse.ofCode(408) 
                    .withBody("Request Timeout".getBytes(StandardCharsets.UTF_8)) 
                    .toPromise()) 
            .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/timeout").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(408); 
    }

    @Test
    @DisplayName("should handle large request bodies")
    void shouldHandleLargeRequestBodies() { 
        byte[] largeBody = new byte[10000];
        for (int i = 0; i < largeBody.length; i++) { 
            largeBody[i] = (byte) (i % 256); 
        }

        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.POST, "/large", request -> { 
                int bodySize = request.getBody().asArray().length; 
                return HttpResponse.ok200() 
                    .withBody(("Size: " + bodySize).getBytes(StandardCharsets.UTF_8)) 
                    .toPromise(); 
            })
            .build(); 

        HttpRequest request = HttpRequest.post("http://localhost/large")
            .withBody(largeBody) 
            .build(); 

        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains("10000");
    }

    @Test
    @DisplayName("should handle content negotiation")
    void shouldHandleContentNegotiation() { 
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) 
            .with(HttpMethod.GET, "/negotiate", request -> { 
                String accept = request.getHeader(HttpHeaders.ACCEPT); 
                if (accept != null && accept.contains("application/json")) {
                    return HttpResponse.ok200() 
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") 
                        .withBody("{\"format\":\"json\"}".getBytes(StandardCharsets.UTF_8)) 
                        .toPromise(); 
                } else {
                    return HttpResponse.ok200() 
                        .withHeader(HttpHeaders.CONTENT_TYPE, "text/plain") 
                        .withBody("format: plain".getBytes(StandardCharsets.UTF_8)) 
                        .toPromise(); 
                }
            })
            .build(); 

        HttpRequest jsonRequest = HttpRequest.get("http://localhost/negotiate")
            .withHeader(HttpHeaders.ACCEPT, "application/json") 
            .build(); 

        HttpResponse jsonResponse = runPromise(() -> servlet.serve(jsonRequest)); 

        assertThat(jsonResponse.getCode()).isEqualTo(200); 
        assertThat(jsonResponse.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }
}
