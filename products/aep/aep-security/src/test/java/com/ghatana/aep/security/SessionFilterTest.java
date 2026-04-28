/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SessionFilter}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the HTTP session filter
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("SessionFilter")
class SessionFilterTest extends EventloopTestBase {
    private static final AepAuthFilter.JwtPayload JWT_PAYLOAD = new AepAuthFilter.JwtPayload(
        "user-1",
        "test-suite",
        System.currentTimeMillis() / 1000L + 3600,
        System.currentTimeMillis() / 1000L,
        java.util.List.of("operator"),
        java.util.List.of("pipeline:write"),
        "tenant-1");

    @Mock
    private AsyncServlet next;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        lenient().when(next.serve(any())).thenReturn(Promise.of(HttpResponse.ok200().build())); // GH-90000
    }

    private HttpResponse serve(SessionFilter filter, HttpRequest request) { // GH-90000
        return runPromise(() -> filter.serve(request)); // GH-90000
    }

    @Test
    @DisplayName("POST /api/v1/session issues a token and returns it in body and header")
    void postToSessionPath_issuesToken() { // GH-90000
        SessionFilter filter = new SessionFilter(next, Duration.ofHours(1)); // GH-90000

        HttpRequest request = HttpRequest.post("http://localhost" + SessionFilter.SESSION_PATH).build(); // GH-90000
        request.attach(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT, JWT_PAYLOAD);
        HttpResponse response = serve(filter, request); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String header = response.getHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER)); // GH-90000
        assertThat(header).isNotNull().isNotBlank(); // GH-90000

        String body = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"session\"").contains("\"expiresInSeconds\""); // GH-90000
        assertThat(body).contains(header); // token in body matches header // GH-90000
    }

    @Test
    @DisplayName("valid session token in X-AEP-Session header delegates to downstream")
    void validSessionToken_delegatesToNext() throws Exception { // GH-90000
        SessionFilter filter = new SessionFilter(next, Duration.ofHours(1)); // GH-90000

        // First: issue a session
        HttpRequest issueRequest = HttpRequest.post("http://localhost" + SessionFilter.SESSION_PATH).build(); // GH-90000
        issueRequest.attach(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT, JWT_PAYLOAD);
        HttpResponse issueResponse = serve(filter, issueRequest); // GH-90000
        String token = issueResponse.getHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER)); // GH-90000
        assertThat(token).isNotNull(); // GH-90000

        // Then: use that session on a regular request
        HttpRequest apiRequest = HttpRequest.get("http://localhost/api/v1/agents")
                .withHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER), token) // GH-90000
                .build(); // GH-90000
        HttpResponse apiResponse = serve(filter, apiRequest); // GH-90000

        assertThat(apiResponse.getCode()).isEqualTo(200); // GH-90000
        verify(next).serve(any()); // GH-90000
    }

    @Test
    @DisplayName("request without session header still delegates to downstream (session is optional)")
    void noSessionHeader_stillDelegates() throws Exception { // GH-90000
        SessionFilter filter = new SessionFilter(next, Duration.ofHours(1)); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents").build();
        HttpResponse response = serve(filter, request); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        verify(next).serve(any()); // GH-90000
    }

    @Test
    @DisplayName("expired session token still delegates (session is optional, not a hard gate)")
    void expiredSessionToken_stillDelegates() throws Exception { // GH-90000
        // Use a negative TTL so the issued token is immediately expired
        SessionFilter filter = new SessionFilter(next, Duration.ofSeconds(-100)); // GH-90000

        // Issue a session with a negative TTL (will be expired on arrival) // GH-90000
        HttpRequest issueRequest = HttpRequest.post("http://localhost" + SessionFilter.SESSION_PATH).build(); // GH-90000
        issueRequest.attach(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT, JWT_PAYLOAD);
        HttpResponse issueResponse = serve(filter, issueRequest); // GH-90000
        String expiredToken = issueResponse.getHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER)); // GH-90000
        assertThat(expiredToken).isNotNull(); // GH-90000

        // Use the expired token — filter should not hard-reject, just delegate without session context
        HttpRequest apiRequest = HttpRequest.get("http://localhost/api/v1/agents")
                .withHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER), expiredToken) // GH-90000
                .build(); // GH-90000
        HttpResponse apiResponse = serve(filter, apiRequest); // GH-90000

        assertThat(apiResponse.getCode()).isEqualTo(200); // GH-90000
        verify(next).serve(any()); // GH-90000
    }

    @Test
    @DisplayName("unknown session token is treated as no-session and delegates")
    void unknownSessionToken_treatedAsNoSession() throws Exception { // GH-90000
        SessionFilter filter = new SessionFilter(next, Duration.ofHours(1)); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents")
                .withHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER), "not-a-real-token") // GH-90000
                .build(); // GH-90000
        HttpResponse response = serve(filter, request); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        verify(next).serve(any()); // GH-90000
    }

    @Test
    @DisplayName("POST /api/v1/session rejects requests without a verified JWT attachment")
    void postToSessionPath_requiresVerifiedJwt() {
        SessionFilter filter = new SessionFilter(next, Duration.ofHours(1));

        HttpRequest request = HttpRequest.post("http://localhost" + SessionFilter.SESSION_PATH).build();
        HttpResponse response = serve(filter, request);

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER))).isNull();
        assertThat(response.getBody().asString(StandardCharsets.UTF_8))
            .contains("Verified JWT required");
    }
}
