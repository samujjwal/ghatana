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

    private AsyncServlet okServlet() {
        return request -> Promise.of(HttpResponse.ok200().build());
    }

    @Test
    @DisplayName("constructor throws for non-positive max body size")
    void constructorThrowsForNonPositiveSize() {
        assertThatThrownBy(() -> new RequestSizeLimitFilter(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestSizeLimitFilter(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getMaxBodySize returns configured limit")
    void getMaxBodySizeReturnsConfiguredLimit() {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT);
        assertThat(filter.getMaxBodySize()).isEqualTo(LIMIT);
    }

    @Test
    @DisplayName("request within size limit is passed to delegate servlet")
    void requestWithinLimitPassesToDelegate() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT);
        AsyncServlet wrapped = request -> filter.apply(request, okServlet());

        HttpRequest request = HttpRequest.post("http://example.com/api")
                .withHeader(HttpHeaders.CONTENT_LENGTH, "512")
                .build();

        HttpResponse response = runPromise(() -> wrapped.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("request exactly at limit is passed to delegate servlet")
    void requestAtExactLimitPasses() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT);
        AsyncServlet wrapped = request -> filter.apply(request, okServlet());

        HttpRequest request = HttpRequest.post("http://example.com/api")
                .withHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(LIMIT))
                .build();

        HttpResponse response = runPromise(() -> wrapped.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("request exceeding size limit returns 413")
    void requestExceedingLimitReturns413() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT);
        AsyncServlet wrapped = request -> filter.apply(request, okServlet());

        HttpRequest request = HttpRequest.post("http://example.com/api")
                .withHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(LIMIT + 1))
                .build();

        HttpResponse response = runPromise(() -> wrapped.serve(request));

        assertThat(response.getCode()).isEqualTo(413);
    }

    @Test
    @DisplayName("malformed Content-Length returns 400")
    void malformedContentLengthReturns400() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT);
        AsyncServlet wrapped = request -> filter.apply(request, okServlet());

        HttpRequest request = HttpRequest.post("http://example.com/api")
                .withHeader(HttpHeaders.CONTENT_LENGTH, "not-a-number")
                .build();

        HttpResponse response = runPromise(() -> wrapped.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("request without Content-Length is passed to delegate")
    void requestWithoutContentLengthPassesToDelegate() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT);
        AsyncServlet wrapped = request -> filter.apply(request, okServlet());

        HttpRequest request = HttpRequest.get("http://example.com/api").build();

        HttpResponse response = runPromise(() -> wrapped.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }
}
