package com.ghatana.platform.http.server.response;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for ResponseBuilder fluent HTTP response construction
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ResponseBuilder — fluent HTTP response factory and builder")
class ResponseBuilderTest extends EventloopTestBase {

    // ── Status factories ──────────────────────────────────────────────────────

    @Test
    @DisplayName("ok() produces 200 status code")
    void okProduces200() { // GH-90000
        HttpResponse response = ResponseBuilder.ok().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("created() produces 201 status code")
    void createdProduces201() { // GH-90000
        HttpResponse response = ResponseBuilder.created().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(201); // GH-90000
    }

    @Test
    @DisplayName("accepted() produces 202 status code")
    void acceptedProduces202() { // GH-90000
        HttpResponse response = ResponseBuilder.accepted().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(202); // GH-90000
    }

    @Test
    @DisplayName("noContent() produces 204 status code")
    void noContentProduces204() { // GH-90000
        HttpResponse response = ResponseBuilder.noContent().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(204); // GH-90000
    }

    @Test
    @DisplayName("badRequest() produces 400 status code")
    void badRequestProduces400() { // GH-90000
        HttpResponse response = ResponseBuilder.badRequest().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("unauthorized() produces 401 status code")
    void unauthorizedProduces401() { // GH-90000
        HttpResponse response = ResponseBuilder.unauthorized().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(401); // GH-90000
    }

    @Test
    @DisplayName("forbidden() produces 403 status code")
    void forbiddenProduces403() { // GH-90000
        HttpResponse response = ResponseBuilder.forbidden().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(403); // GH-90000
    }

    @Test
    @DisplayName("notFound() produces 404 status code")
    void notFoundProduces404() { // GH-90000
        HttpResponse response = ResponseBuilder.notFound().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(404); // GH-90000
    }

    @Test
    @DisplayName("conflict() produces 409 status code")
    void conflictProduces409() { // GH-90000
        HttpResponse response = ResponseBuilder.conflict().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(409); // GH-90000
    }

    @Test
    @DisplayName("internalServerError() produces 500 status code")
    void internalServerErrorProduces500() { // GH-90000
        HttpResponse response = ResponseBuilder.internalServerError().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(500); // GH-90000
    }

    @Test
    @DisplayName("serviceUnavailable() produces 503 status code")
    void serviceUnavailableProduces503() { // GH-90000
        HttpResponse response = ResponseBuilder.serviceUnavailable().build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(503); // GH-90000
    }

    @Test
    @DisplayName("status(int) produces custom status code")
    void statusProducesCustomCode() { // GH-90000
        HttpResponse response = ResponseBuilder.status(429).build(); // GH-90000
        assertThat(response.getCode()).isEqualTo(429); // GH-90000
    }

    // ── Content methods ───────────────────────────────────────────────────────

    @Test
    @DisplayName("text() sets plain text content type and body")
    void textSetsContentTypeAndBody() { // GH-90000
        HttpResponse response = ResponseBuilder.ok().text("Hello World").build();

        String contentType = response.getHeader(io.activej.http.HttpHeaders.of("Content-Type"));
        assertThat(contentType).contains("text/plain");
        assertThat(response.getBody().asArray()).isEqualTo("Hello World".getBytes(StandardCharsets.UTF_8)); // GH-90000
    }

    @Test
    @DisplayName("json() serializes object and sets application/json content type")
    void jsonSerializesObjectAndSetsContentType() { // GH-90000
        Map<String, String> body = Map.of("key", "value"); // GH-90000
        HttpResponse response = ResponseBuilder.ok().json(body).build(); // GH-90000

        String contentType = response.getHeader(io.activej.http.HttpHeaders.of("Content-Type"));
        assertThat(contentType).contains("application/json");

        String bodyStr = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(bodyStr).contains("\"key\"").contains("\"value\""); // GH-90000
    }

    @Test
    @DisplayName("rawJson() sets raw JSON string and application/json content type")
    void rawJsonSetsJsonBody() { // GH-90000
        String raw = "{\"hello\":\"world\"}";
        HttpResponse response = ResponseBuilder.ok().rawJson(raw).build(); // GH-90000

        String contentType = response.getHeader(io.activej.http.HttpHeaders.of("Content-Type"));
        assertThat(contentType).contains("application/json");
        assertThat(new String(response.getBody().asArray(), StandardCharsets.UTF_8)).isEqualTo(raw); // GH-90000
    }

    // ── Header methods ────────────────────────────────────────────────────────

    @Test
    @DisplayName("header() adds custom header to response")
    void headerAddsCustomHeader() { // GH-90000
        HttpResponse response = ResponseBuilder.ok() // GH-90000
                .header("X-Custom-Header", "my-value") // GH-90000
                .build(); // GH-90000

        assertThat(response.getHeader(io.activej.http.HttpHeaders.of("X-Custom-Header")))
                .isEqualTo("my-value");
    }

    @Test
    @DisplayName("location() sets Location header")
    void locationSetsLocationHeader() { // GH-90000
        HttpResponse response = ResponseBuilder.created() // GH-90000
                .location("/resources/42")
                .build(); // GH-90000

        assertThat(response.getHeader(io.activej.http.HttpHeaders.of("Location")))
                .isEqualTo("/resources/42");
    }

    @Test
    @DisplayName("noCache() sets cache-control no-store headers")
    void noCacheSetsNoCacheHeaders() { // GH-90000
        HttpResponse response = ResponseBuilder.ok().text("data").noCache().build();

        String cacheControl = response.getHeader(io.activej.http.HttpHeaders.of("Cache-Control"));
        assertThat(cacheControl).contains("no-cache");
    }

    // ── Integration patterns ──────────────────────────────────────────────────

    @Test
    @DisplayName("created with location + json body pattern works correctly")
    void createdWithLocationAndBodyPattern() { // GH-90000
        Map<String, Object> resource = Map.of("id", 99, "name", "test"); // GH-90000
        HttpResponse response = ResponseBuilder.created() // GH-90000
                .location("/items/99")
                .json(resource) // GH-90000
                .build(); // GH-90000

        assertThat(response.getCode()).isEqualTo(201); // GH-90000
        assertThat(response.getHeader(io.activej.http.HttpHeaders.of("Location"))).isEqualTo("/items/99");
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"id\"").contains("99").contains("\"name\"").contains("test");
    }

    @Test
    @DisplayName("unauthorized with WWW-Authenticate header sets both code and header")
    void unauthorizedWithWwwAuthenticate() { // GH-90000
        HttpResponse response = ResponseBuilder.unauthorized() // GH-90000
                .header("WWW-Authenticate", "Bearer realm=\"api\"") // GH-90000
                .build(); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
        assertThat(response.getHeader(io.activej.http.HttpHeaders.of("WWW-Authenticate")))
                .isEqualTo("Bearer realm=\"api\""); // GH-90000
    }
}
