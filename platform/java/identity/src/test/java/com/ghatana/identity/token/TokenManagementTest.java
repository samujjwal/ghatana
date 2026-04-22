package com.ghatana.identity.token;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.identity.CredentialToken;
import com.ghatana.identity.DefaultDelegationTokenService;
import com.ghatana.identity.DefaultIdentityService;
import com.ghatana.identity.DelegationToken;
import com.ghatana.identity.spi.InMemoryIdentityResolver;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for token management — validates credential revocation, delegation token
 * chain tracking, scope enforcement, and TTL capping.
 *
 * @doc.type class
 * @doc.purpose Tests for identity credential and delegation token lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Token Management Tests [GH-90000]")
@Tag("integration [GH-90000]")
class TokenManagementTest extends EventloopTestBase {

    private InMemoryIdentityResolver resolver;
    private DefaultIdentityService identityService;
    private DefaultDelegationTokenService delegationService;

    @BeforeEach
    void setUp() { // GH-90000
        resolver = new InMemoryIdentityResolver(); // GH-90000
        identityService = new DefaultIdentityService(resolver); // GH-90000
        delegationService = new DefaultDelegationTokenService(); // GH-90000
    }

    private AgentIdentity registerAgent(String tenantId, String agentId) { // GH-90000
        AgentIdentity identity = new AgentIdentity(agentId, tenantId, agentId + "-type", Set.of("read [GH-90000]"), Instant.now());
        resolver.register(identity); // GH-90000
        return identity;
    }

    // ── Credential revocation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("credential revocation [GH-90000]")
    class CredentialRevocation {

        @Test
        @DisplayName("revoked token is no longer valid [GH-90000]")
        void revokedToken_isNoLongerValid() { // GH-90000
            registerAgent("tenant-a", "agent-rev"); // GH-90000
            CredentialToken token = runPromise( // GH-90000
                    () -> identityService.issueCredential("tenant-a", "agent-rev", Duration.ofMinutes(30))); // GH-90000

            runPromise(() -> identityService.revokeCredential(token.tokenId())); // GH-90000

            boolean isValid = runPromise(() -> identityService.isCredentialValid(token.tokenId())); // GH-90000
            assertThat(isValid).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("non-revoked token is valid [GH-90000]")
        void nonRevokedToken_isValid() { // GH-90000
            registerAgent("tenant-a", "agent-valid"); // GH-90000
            CredentialToken token = runPromise( // GH-90000
                    () -> identityService.issueCredential("tenant-a", "agent-valid", Duration.ofMinutes(30))); // GH-90000

            boolean isValid = runPromise(() -> identityService.isCredentialValid(token.tokenId())); // GH-90000
            assertThat(isValid).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("revoking same token twice is idempotent [GH-90000]")
        void revokingSameTokenTwice_isIdempotent() { // GH-90000
            registerAgent("tenant-a", "agent-double-rev"); // GH-90000
            CredentialToken token = runPromise( // GH-90000
                    () -> identityService.issueCredential("tenant-a", "agent-double-rev", Duration.ofMinutes(30))); // GH-90000

            runPromise(() -> identityService.revokeCredential(token.tokenId())); // GH-90000
            runPromise(() -> identityService.revokeCredential(token.tokenId())); // second revoke is no-op // GH-90000

            boolean isValid = runPromise(() -> identityService.isCredentialValid(token.tokenId())); // GH-90000
            assertThat(isValid).isFalse(); // GH-90000
        }
    }

    // ── Delegation token ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("delegation token [GH-90000]")
    class DelegationTokenTests {

        @Test
        @DisplayName("delegation token records delegator, delegatee, and scopes [GH-90000]")
        void delegationToken_recordsDelegatorDelegateeAndScopes() { // GH-90000
            DelegationToken token = runPromise( // GH-90000
                    () -> delegationService.delegate( // GH-90000
                            "tenant-a", "agent-a", "agent-b",
                            Set.of("read", "write"), Duration.ofHours(1))); // GH-90000

            assertThat(token.delegator()).isEqualTo("agent-a [GH-90000]");
            assertThat(token.delegatee()).isEqualTo("agent-b [GH-90000]");
            assertThat(token.scopes()).containsExactlyInAnyOrder("read", "write"); // GH-90000
            assertThat(token.tenantId()).isEqualTo("tenant-a [GH-90000]");
        }

        @Test
        @DisplayName("delegation chain includes both delegator and delegatee [GH-90000]")
        void delegationChain_includesBothDelegatorAndDelegatee() { // GH-90000
            DelegationToken token = runPromise( // GH-90000
                    () -> delegationService.delegate( // GH-90000
                            "tenant-a", "root-agent", "child-agent",
                            Set.of("read [GH-90000]"), Duration.ofMinutes(30)));

            assertThat(token.chain()).containsExactly("root-agent", "child-agent"); // GH-90000
        }

        @Test
        @DisplayName("freshly issued delegation token is not expired [GH-90000]")
        void freshlyIssuedDelegationToken_isNotExpired() { // GH-90000
            DelegationToken token = runPromise( // GH-90000
                    () -> delegationService.delegate( // GH-90000
                            "tenant-a", "a", "b", Set.of("read [GH-90000]"), Duration.ofMinutes(10)));

            assertThat(token.isExpired()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("delegation TTL beyond 8 hours is capped at 8 hours [GH-90000]")
        void delegationTtlBeyond8Hours_isCappedAt8Hours() { // GH-90000
            DelegationToken token = runPromise( // GH-90000
                    () -> delegationService.delegate( // GH-90000
                            "tenant-a", "a", "b", Set.of("read [GH-90000]"), Duration.ofHours(24)));

            Duration actualTtl = Duration.between(token.issuedAt(), token.expiresAt()); // GH-90000
            assertThat(actualTtl).isLessThanOrEqualTo(Duration.ofHours(8)); // GH-90000
        }
    }

    // ── Scope enforcement ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("scope enforcement [GH-90000]")
    class ScopeEnforcement {

        @Test
        @DisplayName("delegation token scopes are immutable after creation [GH-90000]")
        void delegationTokenScopes_areImmutableAfterCreation() { // GH-90000
            DelegationToken token = runPromise( // GH-90000
                    () -> delegationService.delegate( // GH-90000
                            "tenant-a", "a", "b", Set.of("read [GH-90000]"), Duration.ofMinutes(60)));

            // Attempt to modify — should throw
            UnsupportedOperationException exception = null;
            try {
                token.scopes().add("write [GH-90000]");
            } catch (UnsupportedOperationException e) { // GH-90000
                exception = e;
            }

            assertThat(exception).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("delegation token scopes are an exact copy of the input set [GH-90000]")
        void delegationTokenScopes_areExactCopyOfInputSet() { // GH-90000
            Set<String> inputScopes = Set.of("read", "metrics"); // GH-90000
            DelegationToken token = runPromise( // GH-90000
                    () -> delegationService.delegate( // GH-90000
                            "tenant-a", "a", "b", inputScopes, Duration.ofMinutes(60))); // GH-90000

            assertThat(token.scopes()).containsExactlyInAnyOrder("read", "metrics"); // GH-90000
        }
    }
}
