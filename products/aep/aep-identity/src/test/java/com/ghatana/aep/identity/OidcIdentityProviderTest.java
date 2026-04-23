package com.ghatana.aep.identity;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.oauth2.TokenIntrospector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OidcIdentityProvider}.
 *
 * @doc.type class
 * @doc.purpose Verify OIDC-backed federated agent identity resolution
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("OidcIdentityProvider")
@ExtendWith(MockitoExtension.class) // GH-90000
class OidcIdentityProviderTest extends EventloopTestBase {

    @Mock
    private TokenIntrospector tokenIntrospector;

    @Test
    @DisplayName("resolve returns federated agent identity when introspected subject matches registration")
    void resolveReturnsFederatedAgentIdentity() { // GH-90000
        OidcIdentityProvider provider = new OidcIdentityProvider( // GH-90000
            tokenIntrospector,
            "https://issuer.example.com",
            List.of(new OidcIdentityProvider.FederatedAgentRegistration( // GH-90000
                "tenant-a",
                "agent-1",
                "oidc-subject-1",
                "token-1",
                Set.of("aep:capability:routing"))));

        when(tokenIntrospector.introspect("token-1")).thenReturn(Promise.of(User.builder()
            .userId("oidc-subject-1")
            .username("agent-1")
            .email("agent-1@example.com")
            .build())); // GH-90000

        Optional<AgentIdentity> identity = runPromise(() -> provider.resolve("tenant-a", "agent-1")); // GH-90000

        assertThat(identity).isPresent(); // GH-90000
        assertThat(identity.orElseThrow().tenantId()).isEqualTo("tenant-a");
        assertThat(identity.orElseThrow().agentId()).isEqualTo("agent-1");
        assertThat(identity.orElseThrow().spiffeId()) // GH-90000
            .isEqualTo("https://issuer.example.com/subject/oidc-subject-1");
        assertThat(identity.orElseThrow().scopes()) // GH-90000
            .contains("aep:execute", "aep:capability:routing"); // GH-90000
    }

    @Test
    @DisplayName("resolve returns empty when the OIDC subject does not match the registration")
    void resolveReturnsEmptyWhenSubjectDoesNotMatch() { // GH-90000
        OidcIdentityProvider provider = new OidcIdentityProvider( // GH-90000
            tokenIntrospector,
            "https://issuer.example.com",
            List.of(new OidcIdentityProvider.FederatedAgentRegistration( // GH-90000
                "tenant-a",
                "agent-1",
                "oidc-subject-1",
                "token-1",
                Set.of()))); // GH-90000

        when(tokenIntrospector.introspect("token-1")).thenReturn(Promise.of(User.builder()
            .userId("unexpected-subject")
            .username("agent-1")
            .email("agent-1@example.com")
            .build())); // GH-90000

        Optional<AgentIdentity> identity = runPromise(() -> provider.resolve("tenant-a", "agent-1")); // GH-90000

        assertThat(identity).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("supports and resolve return negative results for unregistered agents")
    void unregisteredAgentsAreNotSupported() { // GH-90000
        OidcIdentityProvider provider = new OidcIdentityProvider( // GH-90000
            tokenIntrospector,
            "https://issuer.example.com",
            List.of()); // GH-90000

        assertThat(provider.supports("tenant-a", "agent-1")).isFalse(); // GH-90000
        assertThat(runPromise(() -> provider.resolve("tenant-a", "agent-1"))).isEmpty(); // GH-90000
    }
}