package com.ghatana.platform.http.server.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for HstsHeaderFilter HSTS header injection
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("HstsHeaderFilter — HSTS header injection and configuration")
class HstsHeaderFilterTest extends EventloopTestBase {

    private static AsyncServlet okServlet() {
        return request -> Promise.of(HttpResponse.ok200().build());
    }

    @Test
    @DisplayName("default constructor uses 31536000 second max-age")
    void defaultConstructorUsesOneYearMaxAge() {
        HstsHeaderFilter filter = new HstsHeaderFilter();
        AsyncServlet wrapped = filter.wrap(okServlet());

        HttpRequest request = HttpRequest.get("http://example.com/").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request));

        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security"));
        assertThat(sts).isNotNull();
        assertThat(sts).contains("max-age=31536000");
    }

    @Test
    @DisplayName("custom max-age appears in HSTS header value")
    void customMaxAgeAppearsInHeader() {
        HstsHeaderFilter filter = new HstsHeaderFilter(86400);
        AsyncServlet wrapped = filter.wrap(okServlet());

        HttpRequest request = HttpRequest.get("http://example.com/").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request));

        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security"));
        assertThat(sts).contains("max-age=86400");
    }

    @Test
    @DisplayName("HSTS header includes includeSubDomains directive")
    void hstsIncludesSubdomainsDirective() {
        HstsHeaderFilter filter = new HstsHeaderFilter();
        AsyncServlet wrapped = filter.wrap(okServlet());

        HttpRequest request = HttpRequest.get("http://example.com/").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request));

        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security"));
        assertThat(sts).contains("includeSubDomains");
    }

    @Test
    @DisplayName("HSTS header includes preload directive")
    void hstsIncludesPreloadDirective() {
        HstsHeaderFilter filter = new HstsHeaderFilter();
        AsyncServlet wrapped = filter.wrap(okServlet());

        HttpRequest request = HttpRequest.get("http://example.com/").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request));

        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security"));
        assertThat(sts).contains("preload");
    }

    @Test
    @DisplayName("wrapped servlet preserves original response status code")
    void preservesOriginalStatusCode() {
        AsyncServlet servlet = request -> Promise.of(HttpResponse.ofCode(201).build());
        HstsHeaderFilter filter = new HstsHeaderFilter();
        AsyncServlet wrapped = filter.wrap(servlet);

        HttpRequest request = HttpRequest.get("http://example.com/").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security"));
        assertThat(sts).isNotNull();
    }

    @Test
    @DisplayName("negative max-age throws IllegalArgumentException")
    void negativeMaxAgeThrows() {
        assertThatThrownBy(() -> new HstsHeaderFilter(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("zero max-age is allowed (removal mode)")
    void zeroMaxAgeIsAllowed() {
        HstsHeaderFilter filter = new HstsHeaderFilter(0);
        AsyncServlet wrapped = filter.wrap(okServlet());

        HttpRequest request = HttpRequest.get("http://example.com/").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request));

        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security"));
        assertThat(sts).contains("max-age=0");
    }
}
