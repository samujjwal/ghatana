package com.ghatana.platform.testing.assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.function.Predicate;

/**
 * Custom assertions for HTTP responses to make tests more readable and maintainable.
 *
 * @doc.type class
 * @doc.purpose Custom HTTP response assertions for status codes, headers, and body content
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class HttpResponseAssertions {

    private HttpResponseAssertions() {
        // Utility class
    }

    /**
     * Asserts that the response has the expected status code.
     *
     * @param response the HTTP response to check
     * @param expectedStatus the expected HTTP status code
     * @param <T> the response body type
     * @throws AssertionError if the status code doesn't match
     */
    public static <T> void assertStatusCode(HttpResponse<T> response, int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode(), 
            String.format("Expected status code %d but was %d", 
                expectedStatus, response.statusCode()));
    }

    /**
     * Asserts that the response contains the specified header with the expected value.
     *
     * @param response the HTTP response to check
     * @param headerName the name of the header to check
     * @param expectedValue the expected header value
     * @param <T> the response body type
     * @throws AssertionError if the header is missing or has a different value
     */
    public static <T> void assertHeader(HttpResponse<T> response, String headerName, String expectedValue) {
        assertTrue(response.headers().firstValue(headerName).isPresent(),
            String.format("Header '%s' not found in response", headerName));
        assertEquals(expectedValue, response.headers().firstValue(headerName).get(),
            String.format("Unexpected value for header '%s'", headerName));
    }

    /**
     * Asserts that the response body contains the specified text.
     *
     * @param response the HTTP response to check
     * @param expectedText the text to search for in the response body
     * @param <T> the response body type (must be String or CharSequence)
     * @throws AssertionError if the body doesn't contain the expected text
     */
    public static <T> void assertBodyContains(HttpResponse<T> response, String expectedText) {
        assertNotNull(response.body(), "Response body is null");
        assertTrue(response.body().toString().contains(expectedText),
            String.format("Response body does not contain '%s'. Actual body: %s", 
                expectedText, response.body()));
    }

    /**
     * Asserts that the response body matches the given predicate.
     *
     * @param response the HTTP response to check
     * @param predicate the predicate to test the response body
     * @param message the assertion message
     * @param <T> the response body type
     * @throws AssertionError if the body doesn't match the predicate
     */
    public static <T> void assertBodyMatches(
            HttpResponse<T> response, 
            Predicate<T> predicate, 
            String message) {
        assertTrue(predicate.test(response.body()),
            String.format("%s. Response body: %s", message, response.body()));
    }

    /**
     * Asserts that the response body matches the given predicate.
     *
     * @param response the HTTP response to check
     * @param predicate the predicate to test the response body
     * @param <T> the response body type
     * @throws AssertionError if the body doesn't match the predicate
     */
    public static <T> void assertBodyMatches(
            HttpResponse<T> response, 
            Predicate<T> predicate) {
        assertBodyMatches(response, predicate, "Response body does not match the expected condition");
    }

    /**
     * Asserts that the response has a JSON content type.
     *
     * @param response the HTTP response to check
     * @param <T> the response body type
     * @throws AssertionError if the content type is not JSON
     */
    public static <T> void assertJsonContentType(HttpResponse<T> response) {
        assertHeader(response, "Content-Type", "application/json");
    }

    /**
     * Asserts that the response has the expected content type.
     *
     * @param response the HTTP response to check
     * @param expectedContentType the expected content type
     * @param <T> the response body type
     * @throws AssertionError if the content type doesn't match
     */
    public static <T> void assertContentType(
            HttpResponse<T> response, 
            String expectedContentType) {
        assertHeader(response, "Content-Type", expectedContentType);
    }

    /**
     * Asserts that the response has a successful status code (2xx).
     *
     * @param response the HTTP response to check
     * @param <T> the response body type
     * @throws AssertionError if the status code is not in the 2xx range
     */
    public static <T> void assertSuccess(HttpResponse<T> response) {
        int status = response.statusCode();
        assertTrue(status >= 200 && status < 300,
            String.format("Expected successful status (2xx) but was %d", status));
    }

    /**
     * Asserts that the response has a client error status code (4xx).
     *
     * @param response the HTTP response to check
     * @param <T> the response body type
     * @throws AssertionError if the status code is not in the 4xx range
     */
    public static <T> void assertClientError(HttpResponse<T> response) {
        int status = response.statusCode();
        assertTrue(status >= 400 && status < 500,
            String.format("Expected client error status (4xx) but was %d", status));
    }

    /**
     * Asserts that the response has a server error status code (5xx).
     *
     * @param response the HTTP response to check
     * @param <T> the response body type
     * @throws AssertionError if the status code is not in the 5xx range
     */
    public static <T> void assertServerError(HttpResponse<T> response) {
        int status = response.statusCode();
        assertTrue(status >= 500 && status < 600,
            String.format("Expected server error status (5xx) but was %d", status));
    }
}
