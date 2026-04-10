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
    void setUp() {
        resolver = new InMemoryIdentityResolver();
        identityService = new DefaultIdentityService(resolver);
    }

    // ── Agent registration ────────────────────────────────────────────────────

    @Nested
    @DisplayName("agent identity registration")
    class AgentIdentityRegistration {

        @Test
        @DisplayName("registered identity is resolvable by tenant and agent ID")
        void registeredIdentity_isResolvableByTenantAndAgentId() {
            AgentIdentity identity = new AgentIdentity(
                    "tenant-a", "agent-001", "reasoning-agent", Set.of("read", "execute"), Instant.now());
            resolver.register(identity);

            Optional<AgentIdentity> result = runPromise(
                    () -> identityService.resolve("tenant-a", "agent-001"));

            assertThat(result).isPresent();
            assertThat(result.get().agentId()).isEqualTo("agent-001");
            assertThat(result.get().tenantId()).isEqualTo("tenant-a");
        }

        @Test
        @DisplayName("unregistered agent ID returns empty Optional")
        void unregisteredAgentId_returnsEmptyOptional() {
            Optional<AgentIdentity> result = runPromise(
                    () -> identityService.resolve("tenant-a", "unknown-agent"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("agent registered in one tenant is not visible in another tenant")
        void agentInOneTenant_notVisibleInAnotherTenant() {
            AgentIdentity identity = new AgentIdentity(
                    "tenant-a", "agent-001", "code-agent", Set.of("read"), Instant.now());
            resolver.register(identity);

            Optional<AgentIdentity> tenantBResult = runPromise(
                    () -> identityService.resolve("tenant-b", "agent-001"));

            assertThat(tenantBResult).isEmpty();
        }
    }

    // ── Agent deregistration ──────────────────────────────────────────────────

    @Nested
    @DisplayName("agent deregistration")
    class AgentDeregistration {

        @Test
        @DisplayName("deregistered agent is no longer resolvable")
        void deregisteredAgent_isNoLongerResolvable() {
            AgentIdentity identity = new AgentIdentity(
                    "tenant-a", "agent-002", "temp-agent", Set.of("read"), Instant.now());
            resolver.register(identity);
            resolver.deregister("tenant-a", "agent-002");

            Optional<AgentIdentity> result = runPromise(
                    () -> identityService.resolve("tenant-a", "agent-002"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deregistering non-existent agent is a no-op")
        void deregisteringNonExistentAgent_isNoOp() {
            resolver.deregister("tenant-a", "does-not-exist");
            assertThat(resolver.size()).isEqualTo(0);
        }
    }

    // ── Credential issuance ───────────────────────────────────────────────────

    @Nested
    @DisplayName("credential issuance")
    class CredentialIssuance {

        @Test
        @DisplayName("credential issued for valid agent has all required fields")
        void credentialIssued_forValidAgent_hasAllRequiredFields() {
            AgentIdentity identity = new AgentIdentity(
                    "tenant-a", "agent-003", "data-agent", Set.of("read"), Instant.now());
            resolver.register(identity);

            CredentialToken token = runPromise(
                    () -> identityService.issueCredential("tenant-a", "agent-003", Duration.ofMinutes(15)));

            assertThat(token).isNotNull();
            assertThat(token.tokenId()).isNotBlank();
            assertThat(token.agentId()).isEqualTo("agent-003");
            assertThat(token.tenantId()).isEqualTo("tenant-a");
            assertThat(token.issuedAt()).isNotNull();
            assertThat(token.expiresAt()).isAfter(token.issuedAt());
            assertThat(token.signedJwt()).isNotBlank();
        }

        @Test
        @DisplayName("freshly issued token is not expired")
        void freshlyIssuedToken_isNotExpired() {
            AgentIdentity identity = new AgentIdentity(
                    "tenant-a", "agent-004", "fresh-agent", Set.of("read"), Instant.now());
            resolver.register(identity);

            CredentialToken token = runPromise(
                    () -> identityService.issueCredential("tenant-a", "agent-004", Duration.ofMinutes(30)));

            assertThat(token.isExpired()).isFalse();
        }

        @Test
        @DisplayName("TTL beyond max is capped at 1 hour")
        void ttlBeyondMax_isCappedAtOneHour() {
            AgentIdentity identity = new AgentIdentity(
                    "tenant-a", "agent-005", "capped-agent", Set.of("read"), Instant.now());
            resolver.register(identity);

            CredentialToken token = runPromise(
                    () -> identityService.issueCredential("tenant-a", "agent-005", Duration.ofHours(24)));

            Duration actualTtl = Duration.between(token.issuedAt(), token.expiresAt());
            assertThat(actualTtl).isLessThanOrEqualTo(Duration.ofHours(1));
        }
    }
}
