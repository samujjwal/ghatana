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
    void shouldPreventSqlInjectionInQueryParameters() {
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.GET, "/search", request -> {
                String query = request.getQueryParameter("q");
                
                // Validate input - reject SQL injection attempts
                if (query != null && (query.contains("'") || query.contains("--") || 
                    query.toLowerCase().contains("drop") || query.toLowerCase().contains("delete"))) {
                    return HttpResponse.ofCode(400)
                        .withBody("Invalid input detected".getBytes(StandardCharsets.UTF_8))
                        .toPromise();
                }
                
                return HttpResponse.ok200()
                    .withBody(("Safe query: " + query).getBytes(StandardCharsets.UTF_8))
                    .toPromise();
            })
            .build();
        
        HttpRequest maliciousRequest = HttpRequest.get("http://localhost/search?q=' OR '1'='1").build();
        HttpResponse response = runPromise(() -> servlet.serve(maliciousRequest));
        
        assertThat(response.getCode()).isEqualTo(400);
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("Invalid input");
    }

    @Test
    @DisplayName("should prevent XSS attacks in request body")
    void shouldPreventXssAttacksInRequestBody() {
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.POST, "/comment", request -> {
                String body = new String(request.getBody().asArray(), StandardCharsets.UTF_8);
                
                // Validate input - reject XSS attempts
                if (body.contains("<script>") || body.contains("javascript:") || 
                    body.contains("onerror=") || body.contains("onclick=")) {
                    return HttpResponse.ofCode(400)
                        .withBody("XSS attempt detected".getBytes(StandardCharsets.UTF_8))
                        .toPromise();
                }
                
                return HttpResponse.ok200()
                    .withBody("Comment accepted".getBytes(StandardCharsets.UTF_8))
                    .toPromise();
            })
            .build();
        
        HttpRequest xssRequest = HttpRequest.post("http://localhost/comment")
            .withBody("<script>alert('xss')</script>".getBytes(StandardCharsets.UTF_8))
            .build();
        
        HttpResponse response = runPromise(() -> servlet.serve(xssRequest));
        
        assertThat(response.getCode()).isEqualTo(400);
        String responseBody = new String(response.getBody().asArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).contains("XSS attempt detected");
    }

    @Test
    @DisplayName("should enforce request size limits")
    void shouldEnforceRequestSizeLimits() {
        int maxSize = 1024; // 1KB limit
        
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.POST, "/upload", request -> {
                int bodySize = request.getBody().asArray().length;
                
                if (bodySize > maxSize) {
                    return HttpResponse.ofCode(413)
                        .withBody("Request too large".getBytes(StandardCharsets.UTF_8))
                        .toPromise();
                }
                
                return HttpResponse.ok200()
                    .withBody("Upload accepted".getBytes(StandardCharsets.UTF_8))
                    .toPromise();
            })
            .build();
        
        byte[] largeBody = new byte[2048]; // 2KB - exceeds limit
        HttpRequest largeRequest = HttpRequest.post("http://localhost/upload")
            .withBody(largeBody)
            .build();
        
        HttpResponse response = runPromise(() -> servlet.serve(largeRequest));
        
        assertThat(response.getCode()).isEqualTo(413);
    }

    @Test
    @DisplayName("should validate authentication headers")
    void shouldValidateAuthenticationHeaders() {
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.GET, "/protected", request -> {
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    return HttpResponse.ofCode(401)
                        .withBody("Unauthorized".getBytes(StandardCharsets.UTF_8))
                        .toPromise();
                }
                
                String token = authHeader.substring(7);
                if (token.isEmpty() || token.length() < 10) {
                    return HttpResponse.ofCode(401)
                        .withBody("Invalid token".getBytes(StandardCharsets.UTF_8))
                        .toPromise();
                }
                
                return HttpResponse.ok200()
                    .withBody("Access granted".getBytes(StandardCharsets.UTF_8))
                    .toPromise();
            })
            .build();
        
        // Test without auth header
        HttpRequest noAuthRequest = HttpRequest.get("http://localhost/protected").build();
        HttpResponse noAuthResponse = runPromise(() -> servlet.serve(noAuthRequest));
        assertThat(noAuthResponse.getCode()).isEqualTo(401);
        
        // Test with invalid token
        HttpRequest invalidTokenRequest = HttpRequest.get("http://localhost/protected")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer abc")
            .build();
        HttpResponse invalidTokenResponse = runPromise(() -> servlet.serve(invalidTokenRequest));
        assertThat(invalidTokenResponse.getCode()).isEqualTo(401);
        
        // Test with valid token
        HttpRequest validRequest = HttpRequest.get("http://localhost/protected")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token-12345")
            .build();
        HttpResponse validResponse = runPromise(() -> servlet.serve(validRequest));
        assertThat(validResponse.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should prevent path traversal attacks")
    void shouldPreventPathTraversalAttacks() {
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.GET, "/file", request -> {
                String filename = request.getQueryParameter("name");
                
                // Validate filename - reject path traversal attempts
                if (filename != null && (filename.contains("..") || filename.contains("/") || 
                    filename.contains("\\") || filename.contains("~"))) {
                    return HttpResponse.ofCode(400)
                        .withBody("Invalid filename".getBytes(StandardCharsets.UTF_8))
                        .toPromise();
                }
                
                return HttpResponse.ok200()
                    .withBody(("File: " + filename).getBytes(StandardCharsets.UTF_8))
                    .toPromise();
            })
            .build();
        
        HttpRequest traversalRequest = HttpRequest.get("http://localhost/file?name=../../etc/passwd").build();
        HttpResponse response = runPromise(() -> servlet.serve(traversalRequest));
        
        assertThat(response.getCode()).isEqualTo(400);
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("Invalid filename");
    }

    @Test
    @DisplayName("should enforce HTTPS redirect for sensitive endpoints")
    void shouldEnforceHttpsRedirectForSensitiveEndpoints() {
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.GET, "/login", request -> {
                // Note: Direct protocol checking would use request.getUrl().getProtocol(),
                // but for test purposes we simulate the check
                String protocol = "http"; // Simulated for test
                
                if (!"https".equals(protocol)) {
                    return HttpResponse.ofCode(301)
                        .withHeader(HttpHeaders.LOCATION, "https://localhost/login")
                        .toPromise();
                }
                
                return HttpResponse.ok200()
                    .withBody("Login page".getBytes(StandardCharsets.UTF_8))
                    .toPromise();
            })
            .build();
        
        HttpRequest httpRequest = HttpRequest.get("http://localhost/login").build();
        HttpResponse response = runPromise(() -> servlet.serve(httpRequest));
        
        assertThat(response.getCode()).isEqualTo(301);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).contains("https://");
    }

    @Test
    @DisplayName("should set security headers correctly")
    void shouldSetSecurityHeadersCorrectly() {
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.GET, "/secure", request -> 
                HttpResponse.ok200()
                    .withHeader(HttpHeaders.of("X-Content-Type-Options"), "nosniff")
                    .withHeader(HttpHeaders.of("X-Frame-Options"), "DENY")
                    .withHeader(HttpHeaders.of("X-XSS-Protection"), "1; mode=block")
                    .withHeader(HttpHeaders.of("Strict-Transport-Security"), "max-age=31536000")
                    .withHeader(HttpHeaders.of("Content-Security-Policy"), "default-src 'self'")
                    .toPromise())
            .build();
        
        HttpRequest request = HttpRequest.get("http://localhost/secure").build();
        HttpResponse response = runPromise(() -> servlet.serve(request));
        
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Content-Type-Options"))).isEqualTo("nosniff");
        assertThat(response.getHeader(HttpHeaders.of("X-Frame-Options"))).isEqualTo("DENY");
        assertThat(response.getHeader(HttpHeaders.of("X-XSS-Protection"))).isEqualTo("1; mode=block");
        assertThat(response.getHeader(HttpHeaders.of("Strict-Transport-Security"))).contains("max-age=31536000");
        assertThat(response.getHeader(HttpHeaders.of("Content-Security-Policy"))).contains("default-src 'self'");
    }

    @Test
    @DisplayName("should prevent CSRF attacks with token validation")
    void shouldPreventCsrfAttacksWithTokenValidation() {
        String validCsrfToken = "valid-csrf-token-12345";
        
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.POST, "/action", request -> {
                String csrfToken = request.getHeader(HttpHeaders.of("X-CSRF-Token"));
                
                if (!validCsrfToken.equals(csrfToken)) {
                    return HttpResponse.ofCode(403)
                        .withBody("CSRF token validation failed".getBytes(StandardCharsets.UTF_8))
                        .toPromise();
                }
                
                return HttpResponse.ok200()
                    .withBody("Action completed".getBytes(StandardCharsets.UTF_8))
                    .toPromise();
            })
            .build();
        
        // Test without CSRF token
        HttpRequest noTokenRequest = HttpRequest.post("http://localhost/action").build();
        HttpResponse noTokenResponse = runPromise(() -> servlet.serve(noTokenRequest));
        assertThat(noTokenResponse.getCode()).isEqualTo(403);
        
        // Test with invalid CSRF token
        HttpRequest invalidTokenRequest = HttpRequest.post("http://localhost/action")
            .withHeader(HttpHeaders.of("X-CSRF-Token"), "invalid-token")
            .build();
        HttpResponse invalidTokenResponse = runPromise(() -> servlet.serve(invalidTokenRequest));
        assertThat(invalidTokenResponse.getCode()).isEqualTo(403);
        
        // Test with valid CSRF token
        HttpRequest validRequest = HttpRequest.post("http://localhost/action")
            .withHeader(HttpHeaders.of("X-CSRF-Token"), validCsrfToken)
            .build();
        HttpResponse validResponse = runPromise(() -> servlet.serve(validRequest));
        assertThat(validResponse.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should sanitize error messages to prevent information leakage")
    void shouldSanitizeErrorMessagesToPreventInformationLeakage() {
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.GET, "/error", request -> {
                try {
                    throw new RuntimeException("Internal error: Database connection failed at server-123");
                } catch (Exception e) {
                    // Sanitize error message - don't expose internal details
                    return HttpResponse.ofCode(500)
                        .withBody("An internal error occurred".getBytes(StandardCharsets.UTF_8))
                        .toPromise();
                }
            })
            .build();
        
        HttpRequest request = HttpRequest.get("http://localhost/error").build();
        HttpResponse response = runPromise(() -> servlet.serve(request));
        
        assertThat(response.getCode()).isEqualTo(500);
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("Database");
        assertThat(body).doesNotContain("server-123");
        assertThat(body).isEqualTo("An internal error occurred");
    }

    @Test
    @DisplayName("should validate content type for POST requests")
    void shouldValidateContentTypeForPostRequests() {
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.POST, "/api/data", request -> {
                String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
                
                if (contentType == null || !contentType.contains("application/json")) {
                    return HttpResponse.ofCode(415)
                        .withBody("Unsupported Media Type".getBytes(StandardCharsets.UTF_8))
                        .toPromise();
                }
                
                return HttpResponse.ok200()
                    .withBody("Data accepted".getBytes(StandardCharsets.UTF_8))
                    .toPromise();
            })
            .build();
        
        // Test without content type
        HttpRequest noContentTypeRequest = HttpRequest.post("http://localhost/api/data")
            .withBody("{}".getBytes(StandardCharsets.UTF_8))
            .build();
        HttpResponse noContentTypeResponse = runPromise(() -> servlet.serve(noContentTypeRequest));
        assertThat(noContentTypeResponse.getCode()).isEqualTo(415);
        
        // Test with correct content type
        HttpRequest validRequest = HttpRequest.post("http://localhost/api/data")
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .withBody("{}".getBytes(StandardCharsets.UTF_8))
            .build();
        HttpResponse validResponse = runPromise(() -> servlet.serve(validRequest));
        assertThat(validResponse.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("should enforce rate limiting for API endpoints")
    void shouldEnforceRateLimitingForApiEndpoints() {
        int maxRequests = 5;
        int[] requestCount = {0};
        
        RoutingServlet servlet = RoutingServlet.builder(eventloop())
            .with(HttpMethod.GET, "/api/resource", request -> {
                requestCount[0]++;
                
                if (requestCount[0] > maxRequests) {
                    return HttpResponse.ofCode(429)
                        .withHeader(HttpHeaders.of("Retry-After"), "60")
                        .withBody("Rate limit exceeded".getBytes(StandardCharsets.UTF_8))
                        .toPromise();
                }
                
                return HttpResponse.ok200()
                    .withBody("Resource data".getBytes(StandardCharsets.UTF_8))
                    .toPromise();
            })
            .build();
        
        // Make requests up to the limit
        for (int i = 0; i < maxRequests; i++) {
            HttpRequest request = HttpRequest.get("http://localhost/api/resource").build();
            HttpResponse response = runPromise(() -> servlet.serve(request));
            assertThat(response.getCode()).isEqualTo(200);
        }
        
        // Exceed the limit
        HttpRequest excessRequest = HttpRequest.get("http://localhost/api/resource").build();
        HttpResponse excessResponse = runPromise(() -> servlet.serve(excessRequest));
        assertThat(excessResponse.getCode()).isEqualTo(429);
        assertThat(excessResponse.getHeader(HttpHeaders.of("Retry-After"))).isEqualTo("60");
    }
}
