package com.ghatana.identity.lifecycle;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.identity.CredentialToken;
import com.ghatana.identity.DefaultIdentityService;
import com.ghatana.identity.spi.InMemoryIdentityResolver;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for identity lifecycle — validates agent registration, resolution,
 * deregistration, and credential issuance lifecycle.
 *
 * @doc.type class
 * @doc.purpose Tests for identity registration, resolution, and credential lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Identity Lifecycle Tests")
@Tag("integration")
class IdentityLifecycleTest extends EventloopTestBase {

    private InMemoryIdentityResolver resolver;
    private DefaultIdentityService identityService;

    @BeforeEach
    void setUp() { // GH-90000
        resolver = new InMemoryIdentityResolver(); // GH-90000
        identityService = new DefaultIdentityService(resolver); // GH-90000
    }

    // ── Agent registration ────────────────────────────────────────────────────

    @Nested
    @DisplayName("agent identity registration")
    class AgentIdentityRegistration {

        @Test
        @DisplayName("registered identity is resolvable by tenant and agent ID")
        void registeredIdentity_isResolvableByTenantAndAgentId() { // GH-90000
            AgentIdentity identity = new AgentIdentity( // GH-90000
                    "tenant-a", "agent-001", "reasoning-agent", Set.of("read", "execute"), Instant.now()); // GH-90000
            resolver.register(identity); // GH-90000

            Optional<AgentIdentity> result = runPromise( // GH-90000
                    () -> identityService.resolve("tenant-a", "agent-001")); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().agentId()).isEqualTo("agent-001");
            assertThat(result.get().tenantId()).isEqualTo("tenant-a");
        }

        @Test
        @DisplayName("unregistered agent ID returns empty Optional")
        void unregisteredAgentId_returnsEmptyOptional() { // GH-90000
            Optional<AgentIdentity> result = runPromise( // GH-90000
                    () -> identityService.resolve("tenant-a", "unknown-agent")); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("agent registered in one tenant is not visible in another tenant")
        void agentInOneTenant_notVisibleInAnotherTenant() { // GH-90000
            AgentIdentity identity = new AgentIdentity( // GH-90000
                    "tenant-a", "agent-001", "code-agent", Set.of("read"), Instant.now());
            resolver.register(identity); // GH-90000

            Optional<AgentIdentity> tenantBResult = runPromise( // GH-90000
                    () -> identityService.resolve("tenant-b", "agent-001")); // GH-90000

            assertThat(tenantBResult).isEmpty(); // GH-90000
        }
    }

    // ── Agent deregistration ──────────────────────────────────────────────────

    @Nested
    @DisplayName("agent deregistration")
    class AgentDeregistration {

        @Test
        @DisplayName("deregistered agent is no longer resolvable")
        void deregisteredAgent_isNoLongerResolvable() { // GH-90000
            AgentIdentity identity = new AgentIdentity( // GH-90000
                    "tenant-a", "agent-002", "temp-agent", Set.of("read"), Instant.now());
            resolver.register(identity); // GH-90000
            resolver.deregister("tenant-a", "agent-002"); // GH-90000

            Optional<AgentIdentity> result = runPromise( // GH-90000
                    () -> identityService.resolve("tenant-a", "agent-002")); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("deregistering non-existent agent is a no-op")
        void deregisteringNonExistentAgent_isNoOp() { // GH-90000
            resolver.deregister("tenant-a", "does-not-exist"); // GH-90000
            assertThat(resolver.size()).isEqualTo(0); // GH-90000
        }
    }

    // ── Credential issuance ───────────────────────────────────────────────────

    @Nested
    @DisplayName("credential issuance")
    class CredentialIssuance {

        @Test
        @DisplayName("credential issued for valid agent has all required fields")
        void credentialIssued_forValidAgent_hasAllRequiredFields() { // GH-90000
            AgentIdentity identity = new AgentIdentity( // GH-90000
                    "tenant-a", "agent-003", "data-agent", Set.of("read"), Instant.now());
            resolver.register(identity); // GH-90000

            CredentialToken token = runPromise( // GH-90000
                    () -> identityService.issueCredential("tenant-a", "agent-003", Duration.ofMinutes(15))); // GH-90000

            assertThat(token).isNotNull(); // GH-90000
            assertThat(token.tokenId()).isNotBlank(); // GH-90000
            assertThat(token.agentId()).isEqualTo("agent-003");
            assertThat(token.tenantId()).isEqualTo("tenant-a");
            assertThat(token.issuedAt()).isNotNull(); // GH-90000
            assertThat(token.expiresAt()).isAfter(token.issuedAt()); // GH-90000
            assertThat(token.signedJwt()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("freshly issued token is not expired")
        void freshlyIssuedToken_isNotExpired() { // GH-90000
            AgentIdentity identity = new AgentIdentity( // GH-90000
                    "tenant-a", "agent-004", "fresh-agent", Set.of("read"), Instant.now());
            resolver.register(identity); // GH-90000

            CredentialToken token = runPromise( // GH-90000
                    () -> identityService.issueCredential("tenant-a", "agent-004", Duration.ofMinutes(30))); // GH-90000

            assertThat(token.isExpired()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("TTL beyond max is capped at 1 hour")
        void ttlBeyondMax_isCappedAtOneHour() { // GH-90000
            AgentIdentity identity = new AgentIdentity( // GH-90000
                    "tenant-a", "agent-005", "capped-agent", Set.of("read"), Instant.now());
            resolver.register(identity); // GH-90000

            CredentialToken token = runPromise( // GH-90000
                    () -> identityService.issueCredential("tenant-a", "agent-005", Duration.ofHours(24))); // GH-90000

            Duration actualTtl = Duration.between(token.issuedAt(), token.expiresAt()); // GH-90000
            assertThat(actualTtl).isLessThanOrEqualTo(Duration.ofHours(1)); // GH-90000
        }
    }
}
