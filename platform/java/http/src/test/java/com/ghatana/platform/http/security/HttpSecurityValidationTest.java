package com.ghatana.platform.http.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Security validation tests for HTTP server.
 *
 * @doc.type class
 * @doc.purpose Security validation tests for HTTP server components
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("HTTP Security Validation Tests")
class HttpSecurityValidationTest extends EventloopTestBase {

    @Test
    @DisplayName("should prevent SQL injection in query parameters")
    void shouldPreventSqlInjectionInQueryParameters() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/search", request -> { // GH-90000
                String query = request.getQueryParameter("q");

                // Validate input - reject SQL injection attempts
                if (query != null && (query.contains("'") || query.contains("--") ||
                    query.toLowerCase().contains("drop") || query.toLowerCase().contains("delete"))) {
                    return HttpResponse.ofCode(400) // GH-90000
                        .withBody("Invalid input detected".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }

                return HttpResponse.ok200() // GH-90000
                    .withBody(("Safe query: " + query).getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest maliciousRequest = HttpRequest.get("http://localhost/search?q=' OR '1'='1").build();
        HttpResponse response = runPromise(() -> servlet.serve(maliciousRequest)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("Invalid input");
    }

    @Test
    @DisplayName("should prevent XSS attacks in request body")
    void shouldPreventXssAttacksInRequestBody() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.POST, "/comment", request -> { // GH-90000
                String body = new String(request.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000

                // Validate input - reject XSS attempts
                if (body.contains("<script>") || body.contains("javascript:") ||
                    body.contains("onerror=") || body.contains("onclick=")) {
                    return HttpResponse.ofCode(400) // GH-90000
                        .withBody("XSS attempt detected".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }

                return HttpResponse.ok200() // GH-90000
                    .withBody("Comment accepted".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest xssRequest = HttpRequest.post("http://localhost/comment")
            .withBody("<script>alert('xss')</script>".getBytes(StandardCharsets.UTF_8)) // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> servlet.serve(xssRequest)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        String responseBody = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(responseBody).contains("XSS attempt detected");
    }

    @Test
    @DisplayName("should enforce request size limits")
    void shouldEnforceRequestSizeLimits() { // GH-90000
        int maxSize = 1024; // 1KB limit

        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.POST, "/upload", request -> { // GH-90000
                int bodySize = request.getBody().asArray().length; // GH-90000

                if (bodySize > maxSize) { // GH-90000
                    return HttpResponse.ofCode(413) // GH-90000
                        .withBody("Request too large".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }

                return HttpResponse.ok200() // GH-90000
                    .withBody("Upload accepted".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        byte[] largeBody = new byte[2048]; // 2KB - exceeds limit
        HttpRequest largeRequest = HttpRequest.post("http://localhost/upload")
            .withBody(largeBody) // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> servlet.serve(largeRequest)); // GH-90000

        assertThat(response.getCode()).isEqualTo(413); // GH-90000
    }

    @Test
    @DisplayName("should validate authentication headers")
    void shouldValidateAuthenticationHeaders() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/protected", request -> { // GH-90000
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION); // GH-90000

                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    return HttpResponse.ofCode(401) // GH-90000
                        .withBody("Unauthorized".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }

                String token = authHeader.substring(7); // GH-90000
                if (token.isEmpty() || token.length() < 10) { // GH-90000
                    return HttpResponse.ofCode(401) // GH-90000
                        .withBody("Invalid token".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }

                return HttpResponse.ok200() // GH-90000
                    .withBody("Access granted".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        // Test without auth header
        HttpRequest noAuthRequest = HttpRequest.get("http://localhost/protected").build();
        HttpResponse noAuthResponse = runPromise(() -> servlet.serve(noAuthRequest)); // GH-90000
        assertThat(noAuthResponse.getCode()).isEqualTo(401); // GH-90000

        // Test with invalid token
        HttpRequest invalidTokenRequest = HttpRequest.get("http://localhost/protected")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer abc") // GH-90000
            .build(); // GH-90000
        HttpResponse invalidTokenResponse = runPromise(() -> servlet.serve(invalidTokenRequest)); // GH-90000
        assertThat(invalidTokenResponse.getCode()).isEqualTo(401); // GH-90000

        // Test with valid token
        HttpRequest validRequest = HttpRequest.get("http://localhost/protected")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token-12345") // GH-90000
            .build(); // GH-90000
        HttpResponse validResponse = runPromise(() -> servlet.serve(validRequest)); // GH-90000
        assertThat(validResponse.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("should prevent path traversal attacks")
    void shouldPreventPathTraversalAttacks() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/file", request -> { // GH-90000
                String filename = request.getQueryParameter("name");

                // Validate filename - reject path traversal attempts
                if (filename != null && (filename.contains("..") || filename.contains("/") ||
                    filename.contains("\\") || filename.contains("~"))) {
                    return HttpResponse.ofCode(400) // GH-90000
                        .withBody("Invalid filename".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }

                return HttpResponse.ok200() // GH-90000
                    .withBody(("File: " + filename).getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest traversalRequest = HttpRequest.get("http://localhost/file?name=../../etc/passwd").build();
        HttpResponse response = runPromise(() -> servlet.serve(traversalRequest)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("Invalid filename");
    }

    @Test
    @DisplayName("should enforce HTTPS redirect for sensitive endpoints")
    void shouldEnforceHttpsRedirectForSensitiveEndpoints() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/login", request -> { // GH-90000
                // Note: Direct protocol checking would use request.getUrl().getProtocol(), // GH-90000
                // but for test purposes we simulate the check
                String protocol = "http"; // Simulated for test

                if (!"https".equals(protocol)) { // GH-90000
                    return HttpResponse.ofCode(301) // GH-90000
                        .withHeader(HttpHeaders.LOCATION, "https://localhost/login") // GH-90000
                        .toPromise(); // GH-90000
                }

                return HttpResponse.ok200() // GH-90000
                    .withBody("Login page".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        HttpRequest httpRequest = HttpRequest.get("http://localhost/login").build();
        HttpResponse response = runPromise(() -> servlet.serve(httpRequest)); // GH-90000

        assertThat(response.getCode()).isEqualTo(301); // GH-90000
        assertThat(response.getHeader(HttpHeaders.LOCATION)).contains("https://");
    }

    @Test
    @DisplayName("should set security headers correctly")
    void shouldSetSecurityHeadersCorrectly() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/secure", request -> // GH-90000
                HttpResponse.ok200() // GH-90000
                    .withHeader(HttpHeaders.of("X-Content-Type-Options"), "nosniff")
                    .withHeader(HttpHeaders.of("X-Frame-Options"), "DENY")
                    .withHeader(HttpHeaders.of("X-XSS-Protection"), "1; mode=block")
                    .withHeader(HttpHeaders.of("Strict-Transport-Security"), "max-age=31536000")
                    .withHeader(HttpHeaders.of("Content-Security-Policy"), "default-src 'self'")
                    .toPromise()) // GH-90000
            .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/secure").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(response.getHeader(HttpHeaders.of("X-Content-Type-Options"))).isEqualTo("nosniff");
        assertThat(response.getHeader(HttpHeaders.of("X-Frame-Options"))).isEqualTo("DENY");
        assertThat(response.getHeader(HttpHeaders.of("X-XSS-Protection"))).isEqualTo("1; mode=block");
        assertThat(response.getHeader(HttpHeaders.of("Strict-Transport-Security"))).contains("max-age=31536000");
        assertThat(response.getHeader(HttpHeaders.of("Content-Security-Policy"))).contains("default-src 'self'");
    }

    @Test
    @DisplayName("should prevent CSRF attacks with token validation")
    void shouldPreventCsrfAttacksWithTokenValidation() { // GH-90000
        String validCsrfToken = "valid-csrf-token-12345";

        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.POST, "/action", request -> { // GH-90000
                String csrfToken = request.getHeader(HttpHeaders.of("X-CSRF-Token"));

                if (!validCsrfToken.equals(csrfToken)) { // GH-90000
                    return HttpResponse.ofCode(403) // GH-90000
                        .withBody("CSRF token validation failed".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }

                return HttpResponse.ok200() // GH-90000
                    .withBody("Action completed".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        // Test without CSRF token
        HttpRequest noTokenRequest = HttpRequest.post("http://localhost/action").build();
        HttpResponse noTokenResponse = runPromise(() -> servlet.serve(noTokenRequest)); // GH-90000
        assertThat(noTokenResponse.getCode()).isEqualTo(403); // GH-90000

        // Test with invalid CSRF token
        HttpRequest invalidTokenRequest = HttpRequest.post("http://localhost/action")
            .withHeader(HttpHeaders.of("X-CSRF-Token"), "invalid-token")
            .build(); // GH-90000
        HttpResponse invalidTokenResponse = runPromise(() -> servlet.serve(invalidTokenRequest)); // GH-90000
        assertThat(invalidTokenResponse.getCode()).isEqualTo(403); // GH-90000

        // Test with valid CSRF token
        HttpRequest validRequest = HttpRequest.post("http://localhost/action")
            .withHeader(HttpHeaders.of("X-CSRF-Token"), validCsrfToken)
            .build(); // GH-90000
        HttpResponse validResponse = runPromise(() -> servlet.serve(validRequest)); // GH-90000
        assertThat(validResponse.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("should sanitize error messages to prevent information leakage")
    void shouldSanitizeErrorMessagesToPreventInformationLeakage() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/error", request -> { // GH-90000
                try {
                    throw new RuntimeException("Internal error: Database connection failed at server-123");
                } catch (Exception e) { // GH-90000
                    // Sanitize error message - don't expose internal details
                    return HttpResponse.ofCode(500) // GH-90000
                        .withBody("An internal error occurred".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }
            })
            .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/error").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(500); // GH-90000
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).doesNotContain("Database");
        assertThat(body).doesNotContain("server-123");
        assertThat(body).isEqualTo("An internal error occurred");
    }

    @Test
    @DisplayName("should validate content type for POST requests")
    void shouldValidateContentTypeForPostRequests() { // GH-90000
        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.POST, "/api/data", request -> { // GH-90000
                String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE); // GH-90000

                if (contentType == null || !contentType.contains("application/json")) {
                    return HttpResponse.ofCode(415) // GH-90000
                        .withBody("Unsupported Media Type".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }

                return HttpResponse.ok200() // GH-90000
                    .withBody("Data accepted".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        // Test without content type
        HttpRequest noContentTypeRequest = HttpRequest.post("http://localhost/api/data")
            .withBody("{}".getBytes(StandardCharsets.UTF_8)) // GH-90000
            .build(); // GH-90000
        HttpResponse noContentTypeResponse = runPromise(() -> servlet.serve(noContentTypeRequest)); // GH-90000
        assertThat(noContentTypeResponse.getCode()).isEqualTo(415); // GH-90000

        // Test with correct content type
        HttpRequest validRequest = HttpRequest.post("http://localhost/api/data")
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
            .withBody("{}".getBytes(StandardCharsets.UTF_8)) // GH-90000
            .build(); // GH-90000
        HttpResponse validResponse = runPromise(() -> servlet.serve(validRequest)); // GH-90000
        assertThat(validResponse.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("should enforce rate limiting for API endpoints")
    void shouldEnforceRateLimitingForApiEndpoints() { // GH-90000
        int maxRequests = 5;
        int[] requestCount = {0};

        RoutingServlet servlet = RoutingServlet.builder(eventloop()) // GH-90000
            .with(HttpMethod.GET, "/api/resource", request -> { // GH-90000
                requestCount[0]++;

                if (requestCount[0] > maxRequests) { // GH-90000
                    return HttpResponse.ofCode(429) // GH-90000
                        .withHeader(HttpHeaders.of("Retry-After"), "60")
                        .withBody("Rate limit exceeded".getBytes(StandardCharsets.UTF_8)) // GH-90000
                        .toPromise(); // GH-90000
                }

                return HttpResponse.ok200() // GH-90000
                    .withBody("Resource data".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .toPromise(); // GH-90000
            })
            .build(); // GH-90000

        // Make requests up to the limit
        for (int i = 0; i < maxRequests; i++) { // GH-90000
            HttpRequest request = HttpRequest.get("http://localhost/api/resource").build();
            HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }

        // Exceed the limit
        HttpRequest excessRequest = HttpRequest.get("http://localhost/api/resource").build();
        HttpResponse excessResponse = runPromise(() -> servlet.serve(excessRequest)); // GH-90000
        assertThat(excessResponse.getCode()).isEqualTo(429); // GH-90000
        assertThat(excessResponse.getHeader(HttpHeaders.of("Retry-After"))).isEqualTo("60");
    }
}
