package com.ghatana.platform.testing.assertions;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpResponseAssertionsTest {

    private static final String TEST_URL = "http://example.com/api";
    private static final String JSON_CONTENT = "{\"id\":1,\"name\":\"Test\"}";

    private HttpRequest request;
    private HttpHeaders successHeaders;
    private HttpHeaders jsonHeaders;

    @BeforeEach
    void setUp() { // GH-90000
        request = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create(TEST_URL)) // GH-90000
            .GET() // GH-90000
            .build(); // GH-90000

        successHeaders = HttpHeaders.of( // GH-90000
            Map.of("Content-Type", List.of("text/plain [GH-90000]")),
            (name, value) -> true // GH-90000
        );

        jsonHeaders = HttpHeaders.of( // GH-90000
            Map.of( // GH-90000
                "Content-Type", List.of("application/json [GH-90000]"),
                "X-Custom-Header", List.of("custom-value [GH-90000]")
            ),
            (name, value) -> true // GH-90000
        );
    }

    @Test
    void testAssertStatusCode() { // GH-90000
        HttpResponse<String> response = new TestResponse<>(200, successHeaders, "OK"); // GH-90000
        HttpResponseAssertions.assertStatusCode(response, 200); // GH-90000

        // Test failure case
        HttpResponse<String> errorResponse = new TestResponse<>(404, successHeaders, "Not Found"); // GH-90000
        AssertionError error = assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertStatusCode(errorResponse, 200)); // GH-90000
        assertTrue(error.getMessage().contains("Expected status code 200 but was 404 [GH-90000]"));
    }

    @Test
    void testAssertHeader() { // GH-90000
        HttpResponse<String> response = new TestResponse<>(200, jsonHeaders, JSON_CONTENT); // GH-90000
        HttpResponseAssertions.assertHeader(response, "Content-Type", "application/json"); // GH-90000

        // Test missing header
        AssertionError error = assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertHeader(response, "Missing-Header", "value")); // GH-90000
        assertTrue(error.getMessage().contains("Header 'Missing-Header' not found [GH-90000]"));
    }

    @Test
    void testAssertBodyContains() { // GH-90000
        HttpResponse<String> response = new TestResponse<>(200, jsonHeaders, JSON_CONTENT); // GH-90000
        HttpResponseAssertions.assertBodyContains(response, "Test"); // GH-90000

        // Test missing text
        AssertionError error = assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertBodyContains(response, "NotFound")); // GH-90000
        assertTrue(error.getMessage().contains("Response body does not contain 'NotFound' [GH-90000]"));
    }

    @Test
    void testAssertBodyMatches() { // GH-90000
        HttpResponse<String> response = new TestResponse<>(200, jsonHeaders, JSON_CONTENT); // GH-90000

        // Test with lambda predicate - positive case
        HttpResponseAssertions.assertBodyMatches(response, // GH-90000
            body -> body.contains("Test [GH-90000]"),
            "Body should contain 'Test'");

        // Test with method reference - check that the body is not empty
        HttpResponseAssertions.assertBodyMatches(response, // GH-90000
            body -> !body.isEmpty(), // GH-90000
            "Body should not be empty");

        // Test with a failing assertion
        AssertionError error = assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertBodyMatches(response, // GH-90000
                body -> body.contains("NonExistent [GH-90000]"),
                "Body should contain 'NonExistent'"));
        assertTrue(error.getMessage().contains("Body should contain 'NonExistent' [GH-90000]"));
    }

    @Test
    void testAssertJsonContentType() { // GH-90000
        HttpResponse<String> response = new TestResponse<>(200, jsonHeaders, JSON_CONTENT); // GH-90000
        HttpResponseAssertions.assertJsonContentType(response); // GH-90000

        // Test with wrong content type
        HttpResponse<String> invalidResponse = new TestResponse<>(200, successHeaders, "Not JSON"); // GH-90000
        AssertionError error = assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertJsonContentType(invalidResponse)); // GH-90000
        assertTrue(error.getMessage().contains("Unexpected value for header 'Content-Type' [GH-90000]"));
    }

    @Test
    void testAssertSuccess() { // GH-90000
        // Test success cases
        HttpResponseAssertions.assertSuccess(new TestResponse<>(200, successHeaders, "OK")); // GH-90000
        HttpResponseAssertions.assertSuccess(new TestResponse<>(201, successHeaders, "Created")); // GH-90000
        HttpResponseAssertions.assertSuccess(new TestResponse<>(204, successHeaders, "")); // GH-90000

        // Test error cases
        assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertSuccess(new TestResponse<>(400, successHeaders, "Bad Request"))); // GH-90000
        assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertSuccess(new TestResponse<>(500, successHeaders, "Server Error"))); // GH-90000
    }

    @Test
    void testAssertClientError() { // GH-90000
        // Test client error cases
        HttpResponseAssertions.assertClientError(new TestResponse<>(400, successHeaders, "Bad Request")); // GH-90000
        HttpResponseAssertions.assertClientError(new TestResponse<>(404, successHeaders, "Not Found")); // GH-90000

        // Test non-client error cases
        assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertClientError(new TestResponse<>(200, successHeaders, "OK"))); // GH-90000
        assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertClientError(new TestResponse<>(500, successHeaders, "Server Error"))); // GH-90000
    }

    @Test
    void testAssertServerError() { // GH-90000
        // Test server error cases
        HttpResponseAssertions.assertServerError(new TestResponse<>(500, successHeaders, "Internal Server Error")); // GH-90000
        HttpResponseAssertions.assertServerError(new TestResponse<>(503, successHeaders, "Service Unavailable")); // GH-90000

        // Test non-server error cases
        assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertServerError(new TestResponse<>(200, successHeaders, "OK"))); // GH-90000
        assertThrows(AssertionError.class, // GH-90000
            () -> HttpResponseAssertions.assertServerError(new TestResponse<>(404, successHeaders, "Not Found"))); // GH-90000
    }

    // Helper class for testing
    private static class TestResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final HttpHeaders headers;
        private final T body;

        public TestResponse(int statusCode, HttpHeaders headers, T body) { // GH-90000
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
        }

        @Override public int statusCode() { return statusCode; } // GH-90000
        @Override public HttpHeaders headers() { return headers; } // GH-90000
        @Override public T body() { return body; } // GH-90000

        // Unimplemented methods
        @Override public HttpRequest request() { return null; } // GH-90000
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); } // GH-90000
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); } // GH-90000
        @Override public URI uri() { return null; } // GH-90000
        @Override public java.net.http.HttpClient.Version version() { return null; } // GH-90000
    }
}
