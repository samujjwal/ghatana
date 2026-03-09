package com.ghatana.platform.testing.assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpResponseAssertionsTest {

    private static final String TEST_URL = "http://example.com/api";
    private static final String JSON_CONTENT = "{\"id\":1,\"name\":\"Test\"}";
    
    private HttpRequest request;
    private HttpHeaders successHeaders;
    private HttpHeaders jsonHeaders;
    
    @BeforeEach
    void setUp() {
        request = HttpRequest.newBuilder()
            .uri(URI.create(TEST_URL))
            .GET()
            .build();
            
        successHeaders = HttpHeaders.of(
            Map.of("Content-Type", List.of("text/plain")),
            (name, value) -> true
        );
        
        jsonHeaders = HttpHeaders.of(
            Map.of(
                "Content-Type", List.of("application/json"),
                "X-Custom-Header", List.of("custom-value")
            ),
            (name, value) -> true
        );
    }
    
    @Test
    void testAssertStatusCode() {
        HttpResponse<String> response = new TestResponse<>(200, successHeaders, "OK");
        HttpResponseAssertions.assertStatusCode(response, 200);
        
        // Test failure case
        HttpResponse<String> errorResponse = new TestResponse<>(404, successHeaders, "Not Found");
        AssertionError error = assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertStatusCode(errorResponse, 200));
        assertTrue(error.getMessage().contains("Expected status code 200 but was 404"));
    }
    
    @Test
    void testAssertHeader() {
        HttpResponse<String> response = new TestResponse<>(200, jsonHeaders, JSON_CONTENT);
        HttpResponseAssertions.assertHeader(response, "Content-Type", "application/json");
        
        // Test missing header
        AssertionError error = assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertHeader(response, "Missing-Header", "value"));
        assertTrue(error.getMessage().contains("Header 'Missing-Header' not found"));
    }
    
    @Test
    void testAssertBodyContains() {
        HttpResponse<String> response = new TestResponse<>(200, jsonHeaders, JSON_CONTENT);
        HttpResponseAssertions.assertBodyContains(response, "Test");
        
        // Test missing text
        AssertionError error = assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertBodyContains(response, "NotFound"));
        assertTrue(error.getMessage().contains("Response body does not contain 'NotFound'"));
    }
    
    @Test
    void testAssertBodyMatches() {
        HttpResponse<String> response = new TestResponse<>(200, jsonHeaders, JSON_CONTENT);
        
        // Test with lambda predicate - positive case
        HttpResponseAssertions.assertBodyMatches(response, 
            body -> body.contains("Test"),
            "Body should contain 'Test'");
            
        // Test with method reference - check that the body is not empty
        HttpResponseAssertions.assertBodyMatches(response, 
            body -> !body.isEmpty(), 
            "Body should not be empty");
            
        // Test with a failing assertion
        AssertionError error = assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertBodyMatches(response, 
                body -> body.contains("NonExistent"),
                "Body should contain 'NonExistent'"));
        assertTrue(error.getMessage().contains("Body should contain 'NonExistent'"));
    }
    
    @Test
    void testAssertJsonContentType() {
        HttpResponse<String> response = new TestResponse<>(200, jsonHeaders, JSON_CONTENT);
        HttpResponseAssertions.assertJsonContentType(response);
        
        // Test with wrong content type
        HttpResponse<String> invalidResponse = new TestResponse<>(200, successHeaders, "Not JSON");
        AssertionError error = assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertJsonContentType(invalidResponse));
        assertTrue(error.getMessage().contains("Unexpected value for header 'Content-Type'"));
    }
    
    @Test
    void testAssertSuccess() {
        // Test success cases
        HttpResponseAssertions.assertSuccess(new TestResponse<>(200, successHeaders, "OK"));
        HttpResponseAssertions.assertSuccess(new TestResponse<>(201, successHeaders, "Created"));
        HttpResponseAssertions.assertSuccess(new TestResponse<>(204, successHeaders, ""));
        
        // Test error cases
        assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertSuccess(new TestResponse<>(400, successHeaders, "Bad Request")));
        assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertSuccess(new TestResponse<>(500, successHeaders, "Server Error")));
    }
    
    @Test
    void testAssertClientError() {
        // Test client error cases
        HttpResponseAssertions.assertClientError(new TestResponse<>(400, successHeaders, "Bad Request"));
        HttpResponseAssertions.assertClientError(new TestResponse<>(404, successHeaders, "Not Found"));
        
        // Test non-client error cases
        assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertClientError(new TestResponse<>(200, successHeaders, "OK")));
        assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertClientError(new TestResponse<>(500, successHeaders, "Server Error")));
    }
    
    @Test
    void testAssertServerError() {
        // Test server error cases
        HttpResponseAssertions.assertServerError(new TestResponse<>(500, successHeaders, "Internal Server Error"));
        HttpResponseAssertions.assertServerError(new TestResponse<>(503, successHeaders, "Service Unavailable"));
        
        // Test non-server error cases
        assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertServerError(new TestResponse<>(200, successHeaders, "OK")));
        assertThrows(AssertionError.class, 
            () -> HttpResponseAssertions.assertServerError(new TestResponse<>(404, successHeaders, "Not Found")));
    }
    
    // Helper class for testing
    private static class TestResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final HttpHeaders headers;
        private final T body;
        
        public TestResponse(int statusCode, HttpHeaders headers, T body) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
        }
        
        @Override public int statusCode() { return statusCode; }
        @Override public HttpHeaders headers() { return headers; }
        @Override public T body() { return body; }
        
        // Unimplemented methods
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return null; }
        @Override public java.net.http.HttpClient.Version version() { return null; }
    }
}
