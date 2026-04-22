package com.ghatana.platform.http.security.filter;

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
@DisplayName("HttpsRedirectHandler — HTTP→HTTPS permanent redirect with path preservation [GH-90000]")
class HttpsRedirectHandlerTest extends EventloopTestBase {

    // ── Factory method validation ─────────────────────────────────────────────

    @Test
    @DisplayName("create() uses default HTTPS port 443 [GH-90000]")
    void createUsesDefaultPort443() { // GH-90000
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(); // GH-90000
        assertThat(handler.getHttpsPort()).isEqualTo(443); // GH-90000
    }

    @Test
    @DisplayName("create(port) stores custom port [GH-90000]")
    void createWithPortStoresCustomPort() { // GH-90000
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(8443); // GH-90000
        assertThat(handler.getHttpsPort()).isEqualTo(8443); // GH-90000
    }

    @Test
    @DisplayName("create(0) throws IllegalArgumentException for port 0 [GH-90000]")
    void createWithPort0Throws() { // GH-90000
        assertThatThrownBy(() -> HttpsRedirectHandler.create(0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("create(-1) throws IllegalArgumentException [GH-90000]")
    void createWithNegativePortThrows() { // GH-90000
        assertThatThrownBy(() -> HttpsRedirectHandler.create(-1)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("create(65536) throws IllegalArgumentException for port above max [GH-90000]")
    void createWithPortAboveMaxThrows() { // GH-90000
        assertThatThrownBy(() -> HttpsRedirectHandler.create(65536)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ── Redirect status code ──────────────────────────────────────────────────

    @Test
    @DisplayName("serve() returns 301 Moved Permanently status code [GH-90000]")
    void serveReturns301Status() { // GH-90000
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(); // GH-90000
        HttpRequest request = HttpRequest.get("http://example.com/path [GH-90000]")
                .withHeader(io.activej.http.HttpHeaders.HOST, "example.com") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handler.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(301); // GH-90000
    }

    // ── Location header construction ──────────────────────────────────────────

    @Test
    @DisplayName("redirect to standard port 443 omits port from Location header [GH-90000]")
    void standardPortOmittedFromLocation() { // GH-90000
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(); // GH-90000
        HttpRequest request = HttpRequest.get("http://api.example.com/users [GH-90000]")
                .withHeader(io.activej.http.HttpHeaders.HOST, "api.example.com") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handler.serve(request)); // GH-90000

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION); // GH-90000
        assertThat(location).isEqualTo("https://api.example.com/users [GH-90000]");
        assertThat(location).doesNotContain(":443 [GH-90000]");
    }

    @Test
    @DisplayName("redirect to custom port includes port in Location header [GH-90000]")
    void customPortIncludedInLocation() { // GH-90000
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(8443); // GH-90000
        HttpRequest request = HttpRequest.get("http://api.example.com/api/data [GH-90000]")
                .withHeader(io.activej.http.HttpHeaders.HOST, "api.example.com") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handler.serve(request)); // GH-90000

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION); // GH-90000
        assertThat(location).isEqualTo("https://api.example.com:8443/api/data [GH-90000]");
    }

    @Test
    @DisplayName("query parameters are preserved in the Location header [GH-90000]")
    void queryParametersPreservedInLocation() { // GH-90000
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(); // GH-90000
        HttpRequest request = HttpRequest.get("http://example.com/search?q=test&page=1 [GH-90000]")
                .withHeader(io.activej.http.HttpHeaders.HOST, "example.com") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handler.serve(request)); // GH-90000

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION); // GH-90000
        assertThat(location).startsWith("https://example.com/search [GH-90000]");
        assertThat(location).contains("q=test [GH-90000]");
        assertThat(location).contains("page=1 [GH-90000]");
    }

    @Test
    @DisplayName("root path redirect is preserved correctly [GH-90000]")
    void rootPathPreserved() { // GH-90000
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(); // GH-90000
        HttpRequest request = HttpRequest.get("http://example.com/ [GH-90000]")
                .withHeader(io.activej.http.HttpHeaders.HOST, "example.com") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handler.serve(request)); // GH-90000

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION); // GH-90000
        assertThat(location).isEqualTo("https://example.com/ [GH-90000]");
    }

    @Test
    @DisplayName("host with existing port in Host header strips old port before redirect [GH-90000]")
    void hostWithPortStripsOldPort() { // GH-90000
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(9443); // GH-90000
        // Host header may contain port from the HTTP listener
        HttpRequest request = HttpRequest.get("http://example.com:8080/api [GH-90000]")
                .withHeader(io.activej.http.HttpHeaders.HOST, "example.com:8080") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handler.serve(request)); // GH-90000

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION); // GH-90000
        assertThat(location).startsWith("https://example.com:9443 [GH-90000]");
        assertThat(location).doesNotContain(":8080 [GH-90000]");
    }

    @Test
    @DisplayName("Location header uses https:// scheme for all redirects [GH-90000]")
    void locationAlwaysUsesHttpsScheme() { // GH-90000
        HttpsRedirectHandler handler = HttpsRedirectHandler.create(); // GH-90000
        HttpRequest request = HttpRequest.get("http://example.com/data [GH-90000]")
                .withHeader(io.activej.http.HttpHeaders.HOST, "example.com") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handler.serve(request)); // GH-90000

        String location = response.getHeader(io.activej.http.HttpHeaders.LOCATION); // GH-90000
        assertThat(location).startsWith("https:// [GH-90000]");
        assertThat(location).doesNotContain("http:// [GH-90000]");
    }
}
