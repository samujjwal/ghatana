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
@DisplayName("Token Management Tests")
@Tag("integration")
class TokenManagementTest extends EventloopTestBase {

    private InMemoryIdentityResolver resolver;
    private DefaultIdentityService identityService;
    private DefaultDelegationTokenService delegationService;

    @BeforeEach
    void setUp() {
        resolver = new InMemoryIdentityResolver();
        identityService = new DefaultIdentityService(resolver);
        delegationService = new DefaultDelegationTokenService();
    }

    private AgentIdentity registerAgent(String tenantId, String agentId) {
        AgentIdentity identity = new AgentIdentity(agentId, tenantId, agentId + "-type", Set.of("read"), Instant.now());
        resolver.register(identity);
        return identity;
    }

    // ── Credential revocation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("credential revocation")
    class CredentialRevocation {

        @Test
        @DisplayName("revoked token is no longer valid")
        void revokedToken_isNoLongerValid() {
            registerAgent("tenant-a", "agent-rev");
            CredentialToken token = runPromise(
                    () -> identityService.issueCredential("tenant-a", "agent-rev", Duration.ofMinutes(30)));

            runPromise(() -> identityService.revoke(token.tokenId()));

            boolean isValid = runPromise(() -> identityService.isValid(token.tokenId()));
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("non-revoked token is valid")
        void nonRevokedToken_isValid() {
            registerAgent("tenant-a", "agent-valid");
            CredentialToken token = runPromise(
                    () -> identityService.issueCredential("tenant-a", "agent-valid", Duration.ofMinutes(30)));

            boolean isValid = runPromise(() -> identityService.isValid(token.tokenId()));
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("revoking same token twice is idempotent")
        void revokingSameTokenTwice_isIdempotent() {
            registerAgent("tenant-a", "agent-double-rev");
            CredentialToken token = runPromise(
                    () -> identityService.issueCredential("tenant-a", "agent-double-rev", Duration.ofMinutes(30)));

            runPromise(() -> identityService.revoke(token.tokenId()));
            runPromise(() -> identityService.revoke(token.tokenId())); // second revoke is no-op

            boolean isValid = runPromise(() -> identityService.isValid(token.tokenId()));
            assertThat(isValid).isFalse();
        }
    }

    // ── Delegation token ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("delegation token")
    class DelegationTokenTests {

        @Test
        @DisplayName("delegation token records delegator, delegatee, and scopes")
        void delegationToken_recordsDelegatorDelegateeAndScopes() {
            DelegationToken token = runPromise(
                    () -> delegationService.delegate(
                            "tenant-a", "agent-a", "agent-b",
                            Set.of("read", "write"), Duration.ofHours(1)));

            assertThat(token.delegator()).isEqualTo("agent-a");
            assertThat(token.delegatee()).isEqualTo("agent-b");
            assertThat(token.scopes()).containsExactlyInAnyOrder("read", "write");
            assertThat(token.tenantId()).isEqualTo("tenant-a");
        }

        @Test
        @DisplayName("delegation chain includes both delegator and delegatee")
        void delegationChain_includesBothDelegatorAndDelegatee() {
            DelegationToken token = runPromise(
                    () -> delegationService.delegate(
                            "tenant-a", "root-agent", "child-agent",
                            Set.of("read"), Duration.ofMinutes(30)));

            assertThat(token.chain()).containsExactly("root-agent", "child-agent");
        }

        @Test
        @DisplayName("freshly issued delegation token is not expired")
        void freshlyIssuedDelegationToken_isNotExpired() {
            DelegationToken token = runPromise(
                    () -> delegationService.delegate(
                            "tenant-a", "a", "b", Set.of("read"), Duration.ofMinutes(10)));

            assertThat(token.isExpired()).isFalse();
        }

        @Test
        @DisplayName("delegation TTL beyond 8 hours is capped at 8 hours")
        void delegationTtlBeyond8Hours_isCappedAt8Hours() {
            DelegationToken token = runPromise(
                    () -> delegationService.delegate(
                            "tenant-a", "a", "b", Set.of("read"), Duration.ofHours(24)));

            Duration actualTtl = Duration.between(token.issuedAt(), token.expiresAt());
            assertThat(actualTtl).isLessThanOrEqualTo(Duration.ofHours(8));
        }
    }

    // ── Scope enforcement ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("scope enforcement")
    class ScopeEnforcement {

        @Test
        @DisplayName("delegation token scopes are immutable after creation")
        void delegationTokenScopes_areImmutableAfterCreation() {
            DelegationToken token = runPromise(
                    () -> delegationService.delegate(
                            "tenant-a", "a", "b", Set.of("read"), Duration.ofMinutes(60)));

            // Attempt to modify — should throw
            UnsupportedOperationException exception = null;
            try {
                token.scopes().add("write");
            } catch (UnsupportedOperationException e) {
                exception = e;
            }

            assertThat(exception).isNotNull();
        }

        @Test
        @DisplayName("delegation token scopes are an exact copy of the input set")
        void delegationTokenScopes_areExactCopyOfInputSet() {
            Set<String> inputScopes = Set.of("read", "metrics");
            DelegationToken token = runPromise(
                    () -> delegationService.delegate(
                            "tenant-a", "a", "b", inputScopes, Duration.ofMinutes(60)));

            assertThat(token.scopes()).containsExactlyInAnyOrder("read", "metrics");
        }
    }
}
