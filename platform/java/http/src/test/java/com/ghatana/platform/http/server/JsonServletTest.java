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
@DisplayName("JsonServlet — JSON request parsing and response helpers")
class JsonServletTest extends EventloopTestBase {

    /**
     * Minimal concrete subclass exposing the protected helpers for testing.
     */
    private static class TestServlet extends JsonServlet {

        HttpResponse doOk(Object body) { 
            return ok(body); 
        }

        HttpResponse doCreated(Object body) { 
            return created(body); 
        }

        HttpResponse doNoContent() { 
            return noContent(); 
        }

        HttpResponse doBadRequest(String message) { 
            return badRequest(message); 
        }

        HttpResponse doUnauthorized(String message) { 
            return unauthorized(message); 
        }

        HttpResponse doForbidden(String message) { 
            return forbidden(message); 
        }

        HttpResponse doNotFound(String message) { 
            return notFound(message); 
        }

        HttpResponse doInternalError(String message) { 
            return internalError(message); 
        }

        HttpResponse doJsonResponse(int code, Object body) { 
            return jsonResponse(code, body); 
        }

        Promise<HttpResponse> doPromiseOf(HttpResponse response) { 
            return promiseOf(response); 
        }

        <T> Promise<T> doParseBodyAsync(HttpRequest request, Class<T> type) { 
            return parseBodyAsync(request, type); 
        }
    }

    private TestServlet servlet;

    @BeforeEach
    void setUp() { 
        servlet = new TestServlet(); 
    }

    // ── ok ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ok() returns 200 status code")
    void okReturns200() { 
        HttpResponse response = servlet.doOk(Map.of("result", "value")); 
        assertThat(response.getCode()).isEqualTo(200); 
    }

    @Test
    @DisplayName("ok() response has application/json content type")
    void okHasJsonContentType() { 
        HttpResponse response = servlet.doOk(Map.of("key", "val")); 
        String contentType = response.getHeader(io.activej.http.HttpHeaders.CONTENT_TYPE); 
        assertThat(contentType).contains("application/json");
    }

    // ── created ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("created() returns 201 status code")
    void createdReturns201() { 
        HttpResponse response = servlet.doCreated(Map.of("id", "123")); 
        assertThat(response.getCode()).isEqualTo(201); 
    }

    // ── noContent ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("noContent() returns 204 status code")
    void noContentReturns204() { 
        HttpResponse response = servlet.doNoContent(); 
        assertThat(response.getCode()).isEqualTo(204); 
    }

    // ── error responses ───────────────────────────────────────────────────────

    @Test
    @DisplayName("badRequest() returns 400 status code")
    void badRequestReturns400() { 
        HttpResponse response = servlet.doBadRequest("Invalid input");
        assertThat(response.getCode()).isEqualTo(400); 
    }

    @Test
    @DisplayName("unauthorized() returns 401 status code")
    void unauthorizedReturns401() { 
        HttpResponse response = servlet.doUnauthorized("Not authenticated");
        assertThat(response.getCode()).isEqualTo(401); 
    }

    @Test
    @DisplayName("forbidden() returns 403 status code")
    void forbiddenReturns403() { 
        HttpResponse response = servlet.doForbidden("No access");
        assertThat(response.getCode()).isEqualTo(403); 
    }

    @Test
    @DisplayName("notFound() returns 404 status code")
    void notFoundReturns404() { 
        HttpResponse response = servlet.doNotFound("Resource not found");
        assertThat(response.getCode()).isEqualTo(404); 
    }

    @Test
    @DisplayName("internalError(String) returns 500 status code")
    void internalErrorReturns500() { 
        HttpResponse response = servlet.doInternalError("Something broke");
        assertThat(response.getCode()).isEqualTo(500); 
    }

    // ── jsonResponse ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("jsonResponse() with custom code serializes body to JSON")
    void jsonResponseSerializesBody() { 
        HttpResponse response = servlet.doJsonResponse(202, Map.of("status", "queued")); 

        assertThat(response.getCode()).isEqualTo(202); 
        String body = response.getBody() != null 
                ? new String(response.getBody().asArray(), StandardCharsets.UTF_8) 
                : "";
        assertThat(body).contains("queued");
    }

    @Test
    @DisplayName("jsonResponse() falls back to 500 when body cannot be serialized")
    void jsonResponseFallsBackOn500WhenSerializationFails() { 
        // An object that cannot be serialized by Jackson (no-arg constructor missing, not a Map/POJO) 
        Object unserializable = new Object() { 
            // anonymous inner class — Jackson cannot serialize this
        };

        HttpResponse response = servlet.doJsonResponse(200, unserializable); 

        assertThat(response.getCode()).isEqualTo(500); 
    }

    // ── promiseOf ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("promiseOf() wraps an HttpResponse in a resolved Promise")
    void promiseOfWrapsResponse() { 
        HttpResponse inner = HttpResponse.ok200().build(); 
        HttpResponse result = runPromise(() -> servlet.doPromiseOf(inner)); 
        assertThat(result).isSameAs(inner); 
    }

    // ── parseBodyAsync ────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseBodyAsync() deserializes JSON body from request")
    void parseBodyAsyncDeserializesBody() { 
        String json = "{\"name\":\"test\"}";
        HttpRequest request = HttpRequest.post("http://localhost/data")
                .withBody(json.getBytes(StandardCharsets.UTF_8)) 
                .build(); 

        Map<?, ?> result = runPromise(() -> 
                servlet.doParseBodyAsync(request, Map.class)); 

        assertThat((Map<String, Object>) result).containsKey("name");
    }
}
