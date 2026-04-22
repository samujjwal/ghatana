package com.ghatana.platform.http.server;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for JsonServlet helper methods via concrete subclass
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("JsonServlet — JSON request parsing and response helpers [GH-90000]")
class JsonServletTest extends EventloopTestBase {

    /**
     * Minimal concrete subclass exposing the protected helpers for testing.
     */
    private static class TestServlet extends JsonServlet {

        HttpResponse doOk(Object body) { // GH-90000
            return ok(body); // GH-90000
        }

        HttpResponse doCreated(Object body) { // GH-90000
            return created(body); // GH-90000
        }

        HttpResponse doNoContent() { // GH-90000
            return noContent(); // GH-90000
        }

        HttpResponse doBadRequest(String message) { // GH-90000
            return badRequest(message); // GH-90000
        }

        HttpResponse doUnauthorized(String message) { // GH-90000
            return unauthorized(message); // GH-90000
        }

        HttpResponse doForbidden(String message) { // GH-90000
            return forbidden(message); // GH-90000
        }

        HttpResponse doNotFound(String message) { // GH-90000
            return notFound(message); // GH-90000
        }

        HttpResponse doInternalError(String message) { // GH-90000
            return internalError(message); // GH-90000
        }

        HttpResponse doJsonResponse(int code, Object body) { // GH-90000
            return jsonResponse(code, body); // GH-90000
        }

        Promise<HttpResponse> doPromiseOf(HttpResponse response) { // GH-90000
            return promiseOf(response); // GH-90000
        }

        <T> Promise<T> doParseBodyAsync(HttpRequest request, Class<T> type) { // GH-90000
            return parseBodyAsync(request, type); // GH-90000
        }
    }

    private TestServlet servlet;

    @BeforeEach
    void setUp() { // GH-90000
        servlet = new TestServlet(); // GH-90000
    }

    // ── ok ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ok() returns 200 status code [GH-90000]")
    void okReturns200() { // GH-90000
        HttpResponse response = servlet.doOk(Map.of("result", "value")); // GH-90000
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("ok() response has application/json content type [GH-90000]")
    void okHasJsonContentType() { // GH-90000
        HttpResponse response = servlet.doOk(Map.of("key", "val")); // GH-90000
        String contentType = response.getHeader(io.activej.http.HttpHeaders.CONTENT_TYPE); // GH-90000
        assertThat(contentType).contains("application/json [GH-90000]");
    }

    // ── created ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("created() returns 201 status code [GH-90000]")
    void createdReturns201() { // GH-90000
        HttpResponse response = servlet.doCreated(Map.of("id", "123")); // GH-90000
        assertThat(response.getCode()).isEqualTo(201); // GH-90000
    }

    // ── noContent ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("noContent() returns 204 status code [GH-90000]")
    void noContentReturns204() { // GH-90000
        HttpResponse response = servlet.doNoContent(); // GH-90000
        assertThat(response.getCode()).isEqualTo(204); // GH-90000
    }

    // ── error responses ───────────────────────────────────────────────────────

    @Test
    @DisplayName("badRequest() returns 400 status code [GH-90000]")
    void badRequestReturns400() { // GH-90000
        HttpResponse response = servlet.doBadRequest("Invalid input [GH-90000]");
        assertThat(response.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("unauthorized() returns 401 status code [GH-90000]")
    void unauthorizedReturns401() { // GH-90000
        HttpResponse response = servlet.doUnauthorized("Not authenticated [GH-90000]");
        assertThat(response.getCode()).isEqualTo(401); // GH-90000
    }

    @Test
    @DisplayName("forbidden() returns 403 status code [GH-90000]")
    void forbiddenReturns403() { // GH-90000
        HttpResponse response = servlet.doForbidden("No access [GH-90000]");
        assertThat(response.getCode()).isEqualTo(403); // GH-90000
    }

    @Test
    @DisplayName("notFound() returns 404 status code [GH-90000]")
    void notFoundReturns404() { // GH-90000
        HttpResponse response = servlet.doNotFound("Resource not found [GH-90000]");
        assertThat(response.getCode()).isEqualTo(404); // GH-90000
    }

    @Test
    @DisplayName("internalError(String) returns 500 status code [GH-90000]")
    void internalErrorReturns500() { // GH-90000
        HttpResponse response = servlet.doInternalError("Something broke [GH-90000]");
        assertThat(response.getCode()).isEqualTo(500); // GH-90000
    }

    // ── jsonResponse ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("jsonResponse() with custom code serializes body to JSON [GH-90000]")
    void jsonResponseSerializesBody() { // GH-90000
        HttpResponse response = servlet.doJsonResponse(202, Map.of("status", "queued")); // GH-90000

        assertThat(response.getCode()).isEqualTo(202); // GH-90000
        String body = response.getBody() != null // GH-90000
                ? new String(response.getBody().asArray(), StandardCharsets.UTF_8) // GH-90000
                : "";
        assertThat(body).contains("queued [GH-90000]");
    }

    @Test
    @DisplayName("jsonResponse() falls back to 500 when body cannot be serialized [GH-90000]")
    void jsonResponseFallsBackOn500WhenSerializationFails() { // GH-90000
        // An object that cannot be serialized by Jackson (no-arg constructor missing, not a Map/POJO) // GH-90000
        Object unserializable = new Object() { // GH-90000
            // anonymous inner class — Jackson cannot serialize this
        };

        HttpResponse response = servlet.doJsonResponse(200, unserializable); // GH-90000

        assertThat(response.getCode()).isEqualTo(500); // GH-90000
    }

    // ── promiseOf ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("promiseOf() wraps an HttpResponse in a resolved Promise [GH-90000]")
    void promiseOfWrapsResponse() { // GH-90000
        HttpResponse inner = HttpResponse.ok200().build(); // GH-90000
        HttpResponse result = runPromise(() -> servlet.doPromiseOf(inner)); // GH-90000
        assertThat(result).isSameAs(inner); // GH-90000
    }

    // ── parseBodyAsync ────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseBodyAsync() deserializes JSON body from request [GH-90000]")
    void parseBodyAsyncDeserializesBody() { // GH-90000
        String json = "{\"name\":\"test\"}";
        HttpRequest request = HttpRequest.post("http://localhost/data [GH-90000]")
                .withBody(json.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000

        Map<?, ?> result = runPromise(() -> // GH-90000
                servlet.doParseBodyAsync(request, Map.class)); // GH-90000

        assertThat((Map<String, Object>) result).containsKey("name [GH-90000]");
    }
}
