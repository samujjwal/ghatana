/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.http.server.session;

import com.ghatana.platform.security.session.SessionManager;
import com.ghatana.platform.security.session.SessionState;
import com.ghatana.platform.security.session.RequestContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Unit tests for SessionFilter using an in-memory SessionManager stub
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("SessionFilter — session lifecycle and access control")
class SessionFilterTest extends EventloopTestBase {

    // ── In-memory SessionManager stub ─────────────────────────────────────────

    private static final class InMemorySessionManager implements SessionManager {

        private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

        @Override
        public Promise<SessionState> createSession() {
            SessionState session = new SessionState();
            sessions.put(session.getId(), session);
            return Promise.of(session);
        }

        @Override
        public Promise<Optional<SessionState>> getSession(String sessionId) {
            return Promise.of(Optional.ofNullable(sessions.get(sessionId)));
        }

        @Override
        public Promise<Void> saveSession(SessionState session) {
            sessions.put(session.getId(), session);
            return Promise.of(null);
        }

        @Override
        public Promise<Boolean> deleteSession(String sessionId) {
            return Promise.of(sessions.remove(sessionId) != null);
        }

        @Override
        public Promise<Set<String>> findSessionsByUserId(String userId) {
            return Promise.of(Set.of());
        }

        @Override
        public Promise<Set<String>> findSessionsByTenantId(String tenantId) {
            return Promise.of(Set.of());
        }

        @Override
        public Promise<Long> deleteExpiredSessions() {
            return Promise.of(0L);
        }

        public Map<String, SessionState> getAll() {
            return java.util.Collections.unmodifiableMap(sessions);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final Function<HttpRequest, Promise<HttpResponse>> OK_HANDLER =
            req -> Promise.of(HttpResponse.ok200().build());

    private static HttpRequest getRequest(String path) {
        return HttpRequest.get("http://localhost" + path).build();
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    // ── createIfMissing=true (default) ────────────────────────────────────────

    @Test
    @DisplayName("creates a new session when no session identifier is provided")
    void createsNewSessionWhenNoIdentifierProvided() throws Exception {
        InMemorySessionManager manager = new InMemorySessionManager();
        SessionFilter filter = SessionFilter.builder()
                .sessionManager(manager)
                .createIfMissing(true)
                .requireSession(false)
                .persistSession(true)
                .build();

        HttpResponse response = runPromise(() ->
                filter.filter(getRequest("/api/data"), OK_HANDLER));

        assertThat(response.getCode()).isEqualTo(200);
        // A new session was created and stored
        assertThat(manager.getAll()).hasSize(1);
    }

    @Test
    @DisplayName("does not create session when createIfMissing is false and no identifier")
    void doesNotCreateSessionWhenCreateIfMissingFalse() throws Exception {
        InMemorySessionManager manager = new InMemorySessionManager();
        SessionFilter filter = SessionFilter.builder()
                .sessionManager(manager)
                .createIfMissing(false)
                .requireSession(false)
                .persistSession(false)
                .build();

        HttpResponse response = runPromise(() ->
                filter.filter(getRequest("/api/data"), OK_HANDLER));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(manager.getAll()).isEmpty();
    }

    // ── requireSession=true → 401 when no session ─────────────────────────────

    @Test
    @DisplayName("returns 401 when requireSession=true and no session is available")
    void returns401WhenSessionRequiredAndMissing() throws Exception {
        InMemorySessionManager manager = new InMemorySessionManager();
        SessionFilter filter = SessionFilter.builder()
                .sessionManager(manager)
                .createIfMissing(false)
                .requireSession(true)
                .persistSession(false)
                .build();

        HttpResponse response = runPromise(() ->
                filter.filter(getRequest("/api/secure"), OK_HANDLER));

        assertThat(response.getCode()).isEqualTo(401);
    }

    // ── X-Session-ID header ────────────────────────────────────────────────────

    @Test
    @DisplayName("loads existing session when valid session ID header is provided")
    void loadsExistingSessionFromHeader() throws Exception {
        InMemorySessionManager manager = new InMemorySessionManager();
        // Pre-create a session
        SessionState existing = runPromise(manager::createSession);

        SessionFilter filter = SessionFilter.builder()
                .sessionManager(manager)
                .createIfMissing(false)
                .requireSession(true)
                .persistSession(false)
                .build();

        HttpRequest requestWithHeader = HttpRequest.get("http://localhost/api/secure")
                .withHeader(HttpHeaders.of("X-Session-ID"), existing.getId())
                .build();

        HttpResponse response = runPromise(() ->
                filter.filter(requestWithHeader, OK_HANDLER));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("creates new session when header session ID is unknown and createIfMissing=true")
    void createsNewSessionWhenUnknownHeaderProvided() throws Exception {
        InMemorySessionManager manager = new InMemorySessionManager();
        SessionFilter filter = SessionFilter.builder()
                .sessionManager(manager)
                .createIfMissing(true)
                .requireSession(false)
                .persistSession(true)
                .build();

        HttpRequest requestWithUnknownId = HttpRequest.get("http://localhost/api/data")
                .withHeader(HttpHeaders.of("X-Session-ID"), "unknown-session-id-xyz")
                .build();

        HttpResponse response = runPromise(() ->
                filter.filter(requestWithUnknownId, OK_HANDLER));

        assertThat(response.getCode()).isEqualTo(200);
        // A new session was created since the provided ID didn't resolve
        assertThat(manager.getAll()).hasSize(1);
    }

    // ── persistSession=true ────────────────────────────────────────────────────

    @Test
    @DisplayName("persists session when persistSession=true after handling request")
    void persistsSessionAfterRequest() throws Exception {
        InMemorySessionManager manager = new InMemorySessionManager();
        SessionFilter filter = SessionFilter.builder()
                .sessionManager(manager)
                .createIfMissing(true)
                .requireSession(false)
                .persistSession(true)
                .build();

        // First request creates + saves a session
        HttpResponse response = runPromise(() ->
                filter.filter(getRequest("/api/resource"), OK_HANDLER));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(manager.getAll()).isNotEmpty();
    }

    // ── Builder validation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("builder throws IllegalStateException when sessionManager is not set")
    void builderThrowsWhenSessionManagerMissing() {
        assertThatThrownBy(() ->
                SessionFilter.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SessionManager");
    }

    @Test
    @DisplayName("builder() returns a non-null Builder instance")
    void builderReturnsNonNull() {
        assertThat(SessionFilter.builder()).isNotNull();
    }

    // ── Request context metadata ───────────────────────────────────────────────

    @Test
    @DisplayName("handles X-Forwarded-For header for client IP detection")
    void handlesXForwardedForHeader() throws Exception {
        InMemorySessionManager manager = new InMemorySessionManager();
        SessionFilter filter = SessionFilter.builder()
                .sessionManager(manager)
                .createIfMissing(true)
                .requireSession(false)
                .persistSession(false)
                .build();

        HttpRequest requestWithProxy = HttpRequest.get("http://localhost/api/data")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "10.0.0.1, 172.16.0.1")
                .build();

        HttpResponse response = runPromise(() ->
                filter.filter(requestWithProxy, OK_HANDLER));

        // Filter processes the request without error
        assertThat(response.getCode()).isEqualTo(200);
    }
}
