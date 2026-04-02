package com.ghatana.platform.http.server.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for HttpsRedirectHandler HTTP→HTTPS 301 redirection
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("HttpsRedirectHandler — HTTP→HTTPS permanent redirect with path preservation")
class HttpsRedirectHandlerTest extends EventloopTestBase {

    // ── Factory method validation ─────────────────────────────────────────────

    @Test
    @DisplayName("create() uses default HTTPS port 443")
    void createUsesDefaultPort443() {
        HttpsRedirectHandler handler = HttpsRedirectHandler.create();
        assertThat(handler.getHttpsPort()).isEqualTo(443);
    }

    @Test
    @DisplayName("create(port) stores custom port")
    void createWithPortStoresCustomPort() {
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(8443);
        assertThat(handler.getHttpsPort()).isEqualTo(8443);
    }

    @Test
    @DisplayName("create(0) throws IllegalArgumentException for port 0")
    void createWithPort0Throws() {
        assertThatThrownBy(() -> HttpsRedirectHandler.create(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create(-1) throws IllegalArgumentException")
    void createWithNegativePortThrows() {
        assertThatThrownBy(() -> HttpsRedirectHandler.create(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create(65536) throws IllegalArgumentException for port above max")
    void createWithPortAboveMaxThrows() {
        assertThatThrownBy(() -> HttpsRedirectHandler.create(65536))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Redirect status code ──────────────────────────────────────────────────

    @Test
    @DisplayName("serve() returns 301 Moved Permanently status code")
    void serveReturns301Status() {
        HttpsRedirectHandler handler = HttpsRedirectHandler.create();
        HttpRequest request = HttpRequest.get("http://example.com/path")
                .withHeader(io.activej.http.HttpHeaders.HOST, "example.com")
                .build();

        HttpResponse response = runPromise(() -> handler.serve(request));

        assertThat(response.getCode()).isEqualTo(301);
    }

    // ── Location header construction ──────────────────────────────────────────

    @Test
    @DisplayName("redirect to standard port 443 omits port from Location header")
    void standardPortOmittedFromLocation() {
        HttpsRedirectHandler handler = HttpsRedirectHandler.create();
        HttpRequest request = HttpRequest.get("http://api.example.com/users")
                .withHeader(io.activej.http.HttpHeaders.HOST, "api.example.com")
                .build();

        HttpResponse response = runPromise(() -> handler.serve(request));

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION);
        assertThat(location).isEqualTo("https://api.example.com/users");
        assertThat(location).doesNotContain(":443");
    }

    @Test
    @DisplayName("redirect to custom port includes port in Location header")
    void customPortIncludedInLocation() {
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(8443);
        HttpRequest request = HttpRequest.get("http://api.example.com/api/data")
                .withHeader(io.activej.http.HttpHeaders.HOST, "api.example.com")
                .build();

        HttpResponse response = runPromise(() -> handler.serve(request));

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION);
        assertThat(location).isEqualTo("https://api.example.com:8443/api/data");
    }

    @Test
    @DisplayName("query parameters are preserved in the Location header")
    void queryParametersPreservedInLocation() {
        HttpsRedirectHandler handler = HttpsRedirectHandler.create();
        HttpRequest request = HttpRequest.get("http://example.com/search?q=test&page=1")
                .withHeader(io.activej.http.HttpHeaders.HOST, "example.com")
                .build();

        HttpResponse response = runPromise(() -> handler.serve(request));

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION);
        assertThat(location).startsWith("https://example.com/search");
        assertThat(location).contains("q=test");
        assertThat(location).contains("page=1");
    }

    @Test
    @DisplayName("root path redirect is preserved correctly")
    void rootPathPreserved() {
        HttpsRedirectHandler handler = HttpsRedirectHandler.create();
        HttpRequest request = HttpRequest.get("http://example.com/")
                .withHeader(io.activej.http.HttpHeaders.HOST, "example.com")
                .build();

        HttpResponse response = runPromise(() -> handler.serve(request));

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION);
        assertThat(location).isEqualTo("https://example.com/");
    }

    @Test
    @DisplayName("host with existing port in Host header strips old port before redirect")
    void hostWithPortStripsOldPort() {
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(9443);
        // Host header may contain port from the HTTP listener
        HttpRequest request = HttpRequest.get("http://example.com:8080/api")
                .withHeader(io.activej.http.HttpHeaders.HOST, "example.com:8080")
                .build();

        HttpResponse response = runPromise(() -> handler.serve(request));

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION);
        assertThat(location).startsWith("https://example.com:9443");
        assertThat(location).doesNotContain(":8080");
    }

    @Test
    @DisplayName("Location header uses https:// scheme for all redirects")
    void locationAlwaysUsesHttpsScheme() {
        HttpsRedirectHandler handler = HttpsRedirectHandler.create();
        HttpRequest request = HttpRequest.get("http://example.com/data")
                .withHeader(io.activej.http.HttpHeaders.HOST, "example.com")
                .build();

        HttpResponse response = runPromise(() -> handler.serve(request));

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION);
        assertThat(location).startsWith("https://");
        assertThat(location).doesNotContain("http://");
    }
}
