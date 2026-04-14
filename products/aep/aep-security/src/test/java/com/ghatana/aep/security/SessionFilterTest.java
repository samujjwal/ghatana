/*
 * Copyright (c) 2026 Ghatana Inc.
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
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionFilter")
class SessionFilterTest extends EventloopTestBase {

    @Mock
    private AsyncServlet next;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(next.serve(any())).thenReturn(Promise.of(HttpResponse.ok200().build()));
    }

    private HttpResponse serve(SessionFilter filter, HttpRequest request) {
        return runPromise(() -> filter.serve(request));
    }

    @Test
    @DisplayName("POST /api/v1/session issues a token and returns it in body and header")
    void postToSessionPath_issuesToken() {
        SessionFilter filter = new SessionFilter(next, Duration.ofHours(1));

        HttpRequest request = HttpRequest.post("http://localhost" + SessionFilter.SESSION_PATH).build();
        HttpResponse response = serve(filter, request);

        assertThat(response.getCode()).isEqualTo(200);
        String header = response.getHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER));
        assertThat(header).isNotNull().isNotBlank();

        String body = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"session\"").contains("\"expiresInSeconds\"");
        assertThat(body).contains(header); // token in body matches header
    }

    @Test
    @DisplayName("valid session token in X-AEP-Session header delegates to downstream")
    void validSessionToken_delegatesToNext() throws Exception {
        SessionFilter filter = new SessionFilter(next, Duration.ofHours(1));

        // First: issue a session
        HttpRequest issueRequest = HttpRequest.post("http://localhost" + SessionFilter.SESSION_PATH).build();
        HttpResponse issueResponse = serve(filter, issueRequest);
        String token = issueResponse.getHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER));
        assertThat(token).isNotNull();

        // Then: use that session on a regular request
        HttpRequest apiRequest = HttpRequest.get("http://localhost/api/v1/agents")
                .withHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER), token)
                .build();
        HttpResponse apiResponse = serve(filter, apiRequest);

        assertThat(apiResponse.getCode()).isEqualTo(200);
        verify(next).serve(any());
    }

    @Test
    @DisplayName("request without session header still delegates to downstream (session is optional)")
    void noSessionHeader_stillDelegates() throws Exception {
        SessionFilter filter = new SessionFilter(next, Duration.ofHours(1));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents").build();
        HttpResponse response = serve(filter, request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(next).serve(any());
    }

    @Test
    @DisplayName("expired session token still delegates (session is optional, not a hard gate)")
    void expiredSessionToken_stillDelegates() throws Exception {
        // Use a negative TTL so the issued token is immediately expired
        SessionFilter filter = new SessionFilter(next, Duration.ofSeconds(-100));

        // Issue a session with a negative TTL (will be expired on arrival)
        HttpRequest issueRequest = HttpRequest.post("http://localhost" + SessionFilter.SESSION_PATH).build();
        HttpResponse issueResponse = serve(filter, issueRequest);
        String expiredToken = issueResponse.getHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER));
        assertThat(expiredToken).isNotNull();

        // Use the expired token — filter should not hard-reject, just delegate without session context
        HttpRequest apiRequest = HttpRequest.get("http://localhost/api/v1/agents")
                .withHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER), expiredToken)
                .build();
        HttpResponse apiResponse = serve(filter, apiRequest);

        assertThat(apiResponse.getCode()).isEqualTo(200);
        verify(next).serve(any());
    }

    @Test
    @DisplayName("unknown session token is treated as no-session and delegates")
    void unknownSessionToken_treatedAsNoSession() throws Exception {
        SessionFilter filter = new SessionFilter(next, Duration.ofHours(1));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents")
                .withHeader(HttpHeaders.of(SessionFilter.SESSION_HEADER), "not-a-real-token")
                .build();
        HttpResponse response = serve(filter, request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(next).serve(any());
    }
}
