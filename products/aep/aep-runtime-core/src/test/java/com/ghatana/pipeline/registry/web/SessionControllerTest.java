package com.ghatana.pipeline.registry.web;

import com.ghatana.platform.security.session.SessionManager;
import com.ghatana.platform.security.session.SessionState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SessionController}.
 *
 * @doc.type test
 * @doc.purpose Verify HTTP responses for session management endpoints
 * @doc.layer product
 * @doc.pattern ControllerTest
 */
@DisplayName("SessionController tests")
@ExtendWith(MockitoExtension.class)
class SessionControllerTest extends EventloopTestBase {

    @Mock
    private SessionManager sessionManager;

    private SessionController controller;

    @BeforeEach
    void setUp() {
        controller = new SessionController(sessionManager);
    }

    @Test
    @DisplayName("getCurrentSession: no active session → 404 Not Found")
    void getCurrentSession_noSession_returns404() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/sessions/current").build();

        HttpResponse response = runPromise(() -> controller.getCurrentSession(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("invalidateSession: no active session → 404 Not Found")
    void invalidateSession_noSession_returns404() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/sessions/invalidate").build();

        HttpResponse response = runPromise(() -> controller.invalidateSession(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("getUserSessions: missing userId query param → 400 Bad Request")
    void getUserSessions_missingParam_returns400() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/sessions").build();

        HttpResponse response = runPromise(() -> controller.getUserSessions(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("getUserSessions: valid userId → 200 OK")
    void getUserSessions_validParam_returns200() {
        when(sessionManager.findSessionsByUserId("user42"))
                .thenReturn(Promise.of(Set.of("session-1", "session-2")));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/sessions?userId=user42").build();

        HttpResponse response = runPromise(() -> controller.getUserSessions(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("getTenantSessions: missing tenantId query param → 400 Bad Request")
    void getTenantSessions_missingParam_returns400() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/sessions/tenant").build();

        HttpResponse response = runPromise(() -> controller.getTenantSessions(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("getTenantSessions: valid tenantId → 200 OK")
    void getTenantSessions_validParam_returns200() {
        when(sessionManager.findSessionsByTenantId("tenant-1"))
                .thenReturn(Promise.of(Set.of("session-1")));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/sessions/tenant?tenantId=tenant-1").build();

        HttpResponse response = runPromise(() -> controller.getTenantSessions(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("cleanupSessions: returns 200 OK with deleted count")
    void cleanupSessions_returns200() {
        when(sessionManager.deleteExpiredSessions()).thenReturn(Promise.of(5L));

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/sessions/cleanup").build();

        HttpResponse response = runPromise(() -> controller.cleanupSessions(request));

        assertThat(response.getCode()).isEqualTo(200);
    }
}
