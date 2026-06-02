package com.ghatana.platform.security.session;

import com.ghatana.platform.security.session.KernelSessionContextResolver.KernelSessionContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Tests for KernelSessionContextResolver.
 *
 * @doc.type class
 * @doc.purpose Test kernel session context resolver
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("Kernel Session Context Resolver Tests")
@ExtendWith(MockitoExtension.class)
class KernelSessionContextResolverTest extends EventloopTestBase {

    @Mock
    private SessionManager sessionManager;

    private KernelSessionContextResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new KernelSessionContextResolver(sessionManager);
    }

    @Test
    @DisplayName("Should resolve context from valid session")
    void shouldResolveContextFromValidSession() {
        // Arrange
        SessionState session = new SessionState();
        session.setUserId("user123");
        session.setTenantId("tenant1");
        session.setAttribute("role", "clinician");
        session.setAttribute("persona", "clinician");
        session.setAttribute("tier", "clinical");
        session.setAttribute("facilityId", "facility1");

        HttpRequest request = HttpRequest.get("http://localhost/test")
            .withHeader(HttpHeaders.of("Cookie"), "SESSION=session-id-123")
            .build();

        lenient().when(sessionManager.getSession("session-id-123"))
            .thenReturn(Promise.of(Optional.of(session)));

        // Act
        Optional<KernelSessionContext> context = resolver.resolveSync(request);

        // Assert
        assertThat(context).isPresent();
        KernelSessionContext ctx = context.get();
        assertThat(ctx.tenantId()).isEqualTo("tenant1");
        assertThat(ctx.principalId()).isEqualTo("user123");
        assertThat(ctx.role()).isEqualTo("clinician");
        assertThat(ctx.persona()).isEqualTo("clinician");
        assertThat(ctx.tier()).isEqualTo("clinical");
        assertThat(ctx.facilityId()).isEqualTo("facility1");
        assertThat(ctx.correlationId()).isNotNull();
    }

    @Test
    @DisplayName("Should return empty when session cookie is missing")
    void shouldReturnEmptyWhenSessionCookieMissing() {
        // Arrange
        HttpRequest request = HttpRequest.get("http://localhost/test").build();

        // Act
        Optional<KernelSessionContext> context = resolver.resolveSync(request);

        // Assert
        assertThat(context).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when session is expired")
    void shouldReturnEmptyWhenSessionExpired() {
        // Arrange
        SessionState session = new SessionState();
        session.setUserId("user123");
        session.setTenantId("tenant1");
        session.setMaxInactiveInterval(Duration.ofSeconds(-1).getSeconds());
        session.setAttribute("role", "patient");

        HttpRequest request = HttpRequest.get("http://localhost/test")
            .withHeader(HttpHeaders.of("Cookie"), "SESSION=session-id-123")
            .build();

        lenient().when(sessionManager.getSession("session-id-123"))
            .thenReturn(Promise.of(Optional.of(session)));

        // Act
        Optional<KernelSessionContext> context = resolver.resolveSync(request);

        // Assert
        assertThat(context).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when session is not found")
    void shouldReturnEmptyWhenSessionNotFound() {
        // Arrange
        HttpRequest request = HttpRequest.get("http://localhost/test")
            .withHeader(HttpHeaders.of("Cookie"), "SESSION=session-id-123")
            .build();

        lenient().when(sessionManager.getSession("session-id-123"))
            .thenReturn(Promise.of(Optional.empty()));

        // Act
        Optional<KernelSessionContext> context = resolver.resolveSync(request);

        // Assert
        assertThat(context).isEmpty();
    }

    @Test
    @DisplayName("Should resolve from session header when cookie is missing")
    void shouldResolveFromSessionHeaderWhenCookieMissing() {
        // Arrange
        SessionState session = new SessionState();
        session.setUserId("user123");
        session.setTenantId("tenant1");
        session.setAttribute("role", "patient");
        session.setAttribute("persona", "patient");
        session.setAttribute("tier", "core");

        HttpRequest request = HttpRequest.get("http://localhost/test")
            .withHeader(HttpHeaders.of("X-Session-Token"), "session-id-456")
            .build();

        lenient().when(sessionManager.getSession("session-id-456"))
            .thenReturn(Promise.of(Optional.of(session)));

        // Act
        Optional<KernelSessionContext> context = resolver.resolveSync(request);

        // Assert
        assertThat(context).isPresent();
        assertThat(context.get().principalId()).isEqualTo("user123");
    }

    @Test
    @DisplayName("Should create session with identity attributes")
    void shouldCreateSessionWithIdentityAttributes() {
        // Arrange
        SessionState newSession = new SessionState();
        lenient().when(sessionManager.createSession())
            .thenReturn(Promise.of(newSession));
        lenient().when(sessionManager.saveSession(any()))
            .thenReturn(Promise.of(null));

        // Act
        String sessionId = runPromise(() -> resolver.createSession(
            "tenant1",
            "user123",
            "clinician",
            "clinician",
            "clinical",
            "facility1",
            3600
        ));

        // Assert
        assertThat(sessionId).isNotNull();
        assertThat(newSession.getTenantId()).isEqualTo("tenant1");
        assertThat(newSession.getUserId()).isEqualTo("user123");
        assertThat((String) newSession.getAttribute("role")).isEqualTo("clinician");
        assertThat((String) newSession.getAttribute("persona")).isEqualTo("clinician");
        assertThat((String) newSession.getAttribute("tier")).isEqualTo("clinical");
        assertThat((String) newSession.getAttribute("facilityId")).isEqualTo("facility1");
        assertThat(newSession.getMaxInactiveInterval()).isEqualTo(3600);
        verify(sessionManager).saveSession(newSession);
    }

    @Test
    @DisplayName("Should invalidate session")
    void shouldInvalidateSession() {
        // Arrange
        lenient().when(sessionManager.deleteSession("session-id-123"))
            .thenReturn(Promise.of(true));

        // Act
        boolean deleted = runPromise(() -> resolver.invalidateSession("session-id-123"));

        // Assert
        assertThat(deleted).isTrue();
        verify(sessionManager).deleteSession("session-id-123");
    }

    @Test
    @DisplayName("Should check role allowed")
    void shouldCheckRoleAllowed() {
        // Arrange
        KernelSessionContext context = new KernelSessionContext(
            "tenant1",
            "user123",
            "clinician",
            "clinician",
            "clinical",
            "facility1",
            "corr-123"
        );

        // Act & Assert
        assertThat(context.isRoleAllowed(Set.of("patient", "clinician"))).isTrue();
        assertThat(context.isRoleAllowed(Set.of("patient", "admin"))).isFalse();
    }

    @Test
    @DisplayName("Should check persona allowed")
    void shouldCheckPersonaAllowed() {
        // Arrange
        KernelSessionContext context = new KernelSessionContext(
            "tenant1",
            "user123",
            "clinician",
            "clinician",
            "clinical",
            "facility1",
            "corr-123"
        );

        // Act & Assert
        assertThat(context.isPersonaAllowed(Set.of("patient", "clinician"))).isTrue();
        assertThat(context.isPersonaAllowed(Set.of("patient", "fchv"))).isFalse();
    }

    @Test
    @DisplayName("Should check tier allowed")
    void shouldCheckTierAllowed() {
        // Arrange
        KernelSessionContext context = new KernelSessionContext(
            "tenant1",
            "user123",
            "clinician",
            "clinician",
            "clinical",
            "facility1",
            "corr-123"
        );

        // Act & Assert
        assertThat(context.isTierAllowed(Set.of("core", "clinical"))).isTrue();
        assertThat(context.isTierAllowed(Set.of("core", "emergency"))).isFalse();
    }

    @Test
    @DisplayName("Should throw on null required fields in context")
    void shouldThrowOnNullRequiredFieldsInContext() {
        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new KernelSessionContext(null, "user123", "patient", "patient", "core", null, "corr-123")
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new KernelSessionContext("tenant1", null, "patient", "patient", "core", null, "corr-123")
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new KernelSessionContext("tenant1", "user123", null, "patient", "core", null, "corr-123")
        );
    }

    @Test
    @DisplayName("Should throw on blank required fields in context")
    void shouldThrowOnBlankRequiredFieldsInContext() {
        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new KernelSessionContext("", "user123", "patient", "patient", "core", null, "corr-123")
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new KernelSessionContext("tenant1", "", "patient", "patient", "core", null, "corr-123")
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new KernelSessionContext("tenant1", "user123", "", "patient", "core", null, "corr-123")
        );
    }

    @Test
    @DisplayName("Should use default values when session attributes are missing")
    void shouldUseDefaultValuesWhenSessionAttributesMissing() {
        // Arrange
        SessionState session = new SessionState();
        session.setUserId("user123");
        session.setTenantId("tenant1");
        // No role, persona, tier attributes set

        HttpRequest request = HttpRequest.get("http://localhost/test")
            .withHeader(HttpHeaders.of("Cookie"), "SESSION=session-id-123")
            .build();

        lenient().when(sessionManager.getSession("session-id-123"))
            .thenReturn(Promise.of(Optional.of(session)));

        // Act
        Optional<KernelSessionContext> context = resolver.resolveSync(request);

        // Assert
        assertThat(context).isPresent();
        KernelSessionContext ctx = context.get();
        assertThat(ctx.role()).isEqualTo("patient"); // default
        assertThat(ctx.persona()).isEqualTo("patient"); // default
        assertThat(ctx.tier()).isEqualTo("core"); // default
        assertThat(ctx.facilityId()).isNull();
    }
}
