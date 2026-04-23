package com.ghatana.platform.http.security.filter;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for RequestSizeLimitFilter request body size enforcement
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RequestSizeLimitFilter — body size limit enforcement")
class RequestSizeLimitFilterTest extends EventloopTestBase {

    private static final long LIMIT = 1024L; // 1 KB limit for tests

    private AsyncServlet okServlet() { // GH-90000
        return request -> Promise.of(HttpResponse.ok200().build()); // GH-90000
    }

    @Test
    @DisplayName("constructor throws for non-positive max body size")
    void constructorThrowsForNonPositiveSize() { // GH-90000
        assertThatThrownBy(() -> new RequestSizeLimitFilter(0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        assertThatThrownBy(() -> new RequestSizeLimitFilter(-1)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("getMaxBodySize returns configured limit")
    void getMaxBodySizeReturnsConfiguredLimit() { // GH-90000
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT); // GH-90000
        assertThat(filter.getMaxBodySize()).isEqualTo(LIMIT); // GH-90000
    }

    @Test
    @DisplayName("request within size limit is passed to delegate servlet")
    void requestWithinLimitPassesToDelegate() throws Exception { // GH-90000
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT); // GH-90000
        AsyncServlet wrapped = request -> filter.apply(request, okServlet()); // GH-90000

        HttpRequest request = HttpRequest.post("http://example.com/api")
                .withHeader(HttpHeaders.CONTENT_LENGTH, "512") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("request exactly at limit is passed to delegate servlet")
    void requestAtExactLimitPasses() throws Exception { // GH-90000
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT); // GH-90000
        AsyncServlet wrapped = request -> filter.apply(request, okServlet()); // GH-90000

        HttpRequest request = HttpRequest.post("http://example.com/api")
                .withHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(LIMIT)) // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("request exceeding size limit returns 413")
    void requestExceedingLimitReturns413() throws Exception { // GH-90000
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT); // GH-90000
        AsyncServlet wrapped = request -> filter.apply(request, okServlet()); // GH-90000

        HttpRequest request = HttpRequest.post("http://example.com/api")
                .withHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(LIMIT + 1)) // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(413); // GH-90000
    }

    @Test
    @DisplayName("malformed Content-Length returns 400")
    void malformedContentLengthReturns400() throws Exception { // GH-90000
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT); // GH-90000
        AsyncServlet wrapped = request -> filter.apply(request, okServlet()); // GH-90000

        HttpRequest request = HttpRequest.post("http://example.com/api")
                .withHeader(HttpHeaders.CONTENT_LENGTH, "not-a-number") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("request without Content-Length is passed to delegate")
    void requestWithoutContentLengthPassesToDelegate() throws Exception { // GH-90000
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT); // GH-90000
        AsyncServlet wrapped = request -> filter.apply(request, okServlet()); // GH-90000

        HttpRequest request = HttpRequest.get("http://example.com/api").build();

        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }
}
