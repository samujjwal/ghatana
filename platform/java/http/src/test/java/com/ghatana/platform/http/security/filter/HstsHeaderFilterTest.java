package com.ghatana.platform.http.security.filter;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
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
@DisplayName("HstsHeaderFilter — HSTS header injection and configuration [GH-90000]")
class HstsHeaderFilterTest extends EventloopTestBase {

    private static AsyncServlet okServlet() { // GH-90000
        return request -> Promise.of(HttpResponse.ok200().build()); // GH-90000
    }

    @Test
    @DisplayName("default constructor uses 31536000 second max-age [GH-90000]")
    void defaultConstructorUsesOneYearMaxAge() { // GH-90000
        HstsHeaderFilter filter = new HstsHeaderFilter(); // GH-90000
        AsyncServlet wrapped = filter.wrap(okServlet()); // GH-90000

        HttpRequest request = HttpRequest.get("http://example.com/ [GH-90000]").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security [GH-90000]"));
        assertThat(sts).isNotNull(); // GH-90000
        assertThat(sts).contains("max-age=31536000 [GH-90000]");
    }

    @Test
    @DisplayName("custom max-age appears in HSTS header value [GH-90000]")
    void customMaxAgeAppearsInHeader() { // GH-90000
        HstsHeaderFilter filter = new HstsHeaderFilter(86400); // GH-90000
        AsyncServlet wrapped = filter.wrap(okServlet()); // GH-90000

        HttpRequest request = HttpRequest.get("http://example.com/ [GH-90000]").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security [GH-90000]"));
        assertThat(sts).contains("max-age=86400 [GH-90000]");
    }

    @Test
    @DisplayName("HSTS header includes includeSubDomains directive [GH-90000]")
    void hstsIncludesSubdomainsDirective() { // GH-90000
        HstsHeaderFilter filter = new HstsHeaderFilter(); // GH-90000
        AsyncServlet wrapped = filter.wrap(okServlet()); // GH-90000

        HttpRequest request = HttpRequest.get("http://example.com/ [GH-90000]").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security [GH-90000]"));
        assertThat(sts).contains("includeSubDomains [GH-90000]");
    }

    @Test
    @DisplayName("HSTS header includes preload directive [GH-90000]")
    void hstsIncludesPreloadDirective() { // GH-90000
        HstsHeaderFilter filter = new HstsHeaderFilter(); // GH-90000
        AsyncServlet wrapped = filter.wrap(okServlet()); // GH-90000

        HttpRequest request = HttpRequest.get("http://example.com/ [GH-90000]").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security [GH-90000]"));
        assertThat(sts).contains("preload [GH-90000]");
    }

    @Test
    @DisplayName("wrapped servlet preserves original response status code [GH-90000]")
    void preservesOriginalStatusCode() { // GH-90000
        AsyncServlet servlet = request -> Promise.of(HttpResponse.ofCode(201).build()); // GH-90000
        HstsHeaderFilter filter = new HstsHeaderFilter(); // GH-90000
        AsyncServlet wrapped = filter.wrap(servlet); // GH-90000

        HttpRequest request = HttpRequest.get("http://example.com/ [GH-90000]").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(201); // GH-90000
        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security [GH-90000]"));
        assertThat(sts).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("negative max-age throws IllegalArgumentException [GH-90000]")
    void negativeMaxAgeThrows() { // GH-90000
        assertThatThrownBy(() -> new HstsHeaderFilter(-1)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("zero max-age is allowed (removal mode) [GH-90000]")
    void zeroMaxAgeIsAllowed() { // GH-90000
        HstsHeaderFilter filter = new HstsHeaderFilter(0); // GH-90000
        AsyncServlet wrapped = filter.wrap(okServlet()); // GH-90000

        HttpRequest request = HttpRequest.get("http://example.com/ [GH-90000]").build();
        HttpResponse response = runPromise(() -> wrapped.serve(request)); // GH-90000

        String sts = response.getHeader(io.activej.http.HttpHeaders.of("Strict-Transport-Security [GH-90000]"));
        assertThat(sts).contains("max-age=0 [GH-90000]");
    }
}
