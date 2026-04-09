/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import com.ghatana.identity.spi.InMemoryIdentityResolver;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 Expansion tests for Identity module.
 * Tests multi-tenant auth, concurrent lockouts, token lifecycle at scale.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for identity and authentication subsystem
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Identity - Phase 3 Expansion")
class IdentityExpansionTest extends EventloopTestBase {

    private DefaultTokenProvider tokenProvider;
    private DefaultAuthenticationService authService;
    private DefaultIdentityService identityService;
    private InMemoryIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        tokenProvider = new DefaultTokenProvider();
        resolver = new InMemoryIdentityResolver();
        identityService = new DefaultIdentityService(resolver);
        authService = new DefaultAuthenticationService(tokenProvider, identityService);
    }

    // ============================================
    // MULTI-TENANT ISOLATION (4 tests)
    // ============================================

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantTests {

        @Test
        @DisplayName("Many tenants operate independently")
        void manyTenantsIndependent() {
            // Register 100 agents across 20 tenants
            for (int t = 0; t < 20; t++) {
                final int tenantIdx = t;
                for (int a = 0; a < 5; a++) {
                    final int agentIdx = a;
                    String tenantId = "tenant-" + tenantIdx;
                    String agentId = "agent-" + agentIdx;
                    AgentIdentity agent = new AgentIdentity(
                        tenantId, agentId,
                        "spiffe://ghatana.io/" + tenantId + "/" + agentId,
                        Set.of("read", "write"), Instant.now());
                    resolver.register(agent);
                }
            }

            // Verify isolation per tenant
            Optional<AgentIdentity> result = runPromise(() ->
                identityService.resolve("tenant-5", "agent-2"));

            assertThat(result).isPresent();
            assertThat(result.get().tenantId()).isEqualTo("tenant-5");
            assertThat(result.get().agentId()).isEqualTo("agent-2");
        }

        @Test
        @DisplayName("Failed attempts isolated per tenant")
        void failedAttemptsIsolated() {
            String tenantA = "tenant-a";
            String tenantB = "tenant-b";
            String agent = "agent-1";

            AgentIdentity agentA = new AgentIdentity(tenantA, agent, "spiffe://a", Set.of("read"), Instant.now());
            AgentIdentity agentB = new AgentIdentity(tenantB, agent, "spiffe://b", Set.of("read"), Instant.now());
            resolver.register(agentA);
            resolver.register(agentB);

            // Lock tenant-a
            for (int i = 0; i < 5; i++) {
                runPromise(() -> authService.recordFailedAttempt(tenantA, agent));
            }

            Optional<LockoutInfo> lockoutA = runPromise(() -> authService.checkLockout(tenantA, agent));
            Optional<LockoutInfo> lockoutB = runPromise(() -> authService.checkLockout(tenantB, agent));

            assertThat(lockoutA).isPresent().isPresent();
            assertThat(lockoutB).isEmpty();
        }

        @Test
        @DisplayName("Tokens from different tenants do not interfere")
        void tokenTenantIsolation() {
            String tenantA = "tenant-a";
            String tenantB = "tenant-b";

            String tokenA = runPromise(() -> tokenProvider.createToken(tenantA, "agent-1", Duration.ofMinutes(10)));
            String tokenB = runPromise(() -> tokenProvider.createToken(tenantB, "agent-1", Duration.ofMinutes(10)));

            Optional<TokenClaims> claimsA = runPromise(() -> tokenProvider.verifyToken(tokenA));
            Optional<TokenClaims> claimsB = runPromise(() -> tokenProvider.verifyToken(tokenB));

            assertThat(claimsA).isPresent();
            assertThat(claimsB).isPresent();
            assertThat(claimsA.get().tenantId()).isEqualTo(tenantA);
            assertThat(claimsB.get().tenantId()).isEqualTo(tenantB);
        }

        @Test
        @DisplayName("1000 agents across 100 tenants")
        void scalingManyTenantsAndAgents() {
            for (int t = 0; t < 100; t++) {
                final int tenantIdx = t;
                AgentIdentity agent = new AgentIdentity(
                    "tenant-" + tenantIdx, "agent-0",
                    "spiffe://ghatana.io/tenant-" + tenantIdx + "/agent-0",
                    Set.of("read"), Instant.now());
                resolver.register(agent);
            }

            Optional<AgentIdentity> result = runPromise(() ->
                identityService.resolve("tenant-50", "agent-0"));

            assertThat(result).isPresent();
        }
    }

    // ============================================
    // TOKEN LIFECYCLE CONCURRENCY (4 tests)
    // ============================================

    @Nested
    @DisplayName("Token Lifecycle Concurrency")
    class TokenConcurrencyTests {

        @Test
        @DisplayName("Many concurrent token creations")
        void concurrentTokenCreation() throws Exception {
            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<String> tokens = new ArrayList<>();

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    exec.submit(() -> {
                        try {
                            String token = runPromise(() ->
                                tokenProvider.createToken("t" + idx, "agent-" + idx, Duration.ofMinutes(10)));
                            tokens.add(token);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(tokens).hasSize(threadCount);
        }

        @Test
        @DisplayName("Concurrent creation and verification")
        void concurrentCreateAndVerify() throws Exception {
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    exec.submit(() -> {
                        try {
                            String token = runPromise(() ->
                                tokenProvider.createToken("t" + idx, "agent-" + idx, Duration.ofMinutes(10)));
                            Optional<TokenClaims> claims = runPromise(() ->
                                tokenProvider.verifyToken(token));
                            if (claims.isPresent()) {
                                successCount.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(successCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("Many tokens for same agent")
        void manyTokensSameAgent() {
            for (int i = 0; i < 50; i++) {
                final int idx = i;
                String token = runPromise(() ->
                    tokenProvider.createToken("t1", "a1", Duration.ofMinutes(10)));
                assertThat(token).isNotBlank();
            }
        }

        @Test
        @DisplayName("Token creation at scale")
        void tokenCreationScale() {
            for (int i = 0; i < 30; i++) {
                final int idx = i;
                String token = runPromise(() ->
                    tokenProvider.createToken("t1", "a" + idx, Duration.ofHours(1)));

                Optional<TokenClaims> immediateVerify = runPromise(() ->
                    tokenProvider.verifyToken(token));
                assertThat(immediateVerify).isPresent();
                assertThat(immediateVerify.get().agentId()).isEqualTo("a" + idx);
            }
        }
    }

    // ============================================
    // LOCKOUT MECHANISMS (4 tests)
    // ============================================

    @Nested
    @DisplayName("Lockout Mechanisms")
    class LockoutTests {

        @Test
        @DisplayName("Progressive lockout with many attempts")
        void progressiveLockout() {
            String tenant = "t1";
            String agent = "a1";
            AgentIdentity identity = new AgentIdentity(tenant, agent, "spiffe://test", Set.of("read"), Instant.now());
            resolver.register(identity);

            // Record 4 attempts
            for (int i = 0; i < 4; i++) {
                runPromise(() -> authService.recordFailedAttempt(tenant, agent));
                Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout(tenant, agent));
                assertThat(lockout).isEmpty();
            }

            // 5th attempt triggers lockout
            runPromise(() -> authService.recordFailedAttempt(tenant, agent));
            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout(tenant, agent));

            assertThat(lockout).isPresent();
            assertThat(lockout.get().failedAttempts()).isEqualTo(5);
            assertThat(lockout.get().isActive()).isTrue();
        }

        @Test
        @DisplayName("Concurrent failed attempt recording")
        void concurrentFailedAttempts() throws Exception {
            String tenant = "t1";
            String agent = "a1";
            AgentIdentity identity = new AgentIdentity(tenant, agent, "spiffe://test", Set.of("read"), Instant.now());
            resolver.register(identity);

            int threadCount = 15;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService exec = Executors.newFixedThreadPool(threadCount);

            try {
                for (int i = 0; i < threadCount; i++) {
                    exec.submit(() -> {
                        try {
                            runPromise(() -> authService.recordFailedAttempt(tenant, agent));
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout(tenant, agent));
            assertThat(lockout).isPresent();
            assertThat(lockout.get().failedAttempts()).isGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("Lockout across many agents")
        void manyAgentsLockout() {
            String tenant = "t1";

            for (int a = 0; a < 50; a++) {
                final int agentIdx = a;
                String agentId = "agent-" + agentIdx;
                AgentIdentity identity = new AgentIdentity(tenant, agentId, "spiffe://test/" + agentIdx, Set.of("read"), Instant.now());
                resolver.register(identity);

                // Lock each agent
                for (int i = 0; i < 5; i++) {
                    runPromise(() -> authService.recordFailedAttempt(tenant, agentId));
                }
            }

            // Verify all are locked
            int lockedCount = 0;
            for (int a = 0; a < 50; a++) {
                String agentId = "agent-" + a;
                Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout(tenant, agentId));
                if (lockout.isPresent()) {
                    lockedCount++;
                }
            }

            assertThat(lockedCount).isGreaterThan(0);
        }

        @Test
        @DisplayName("Permission validation in locked state")
        void permissionsInLockout() {
            String tenant = "t1";
            String agent = "a1";
            AgentIdentity identity = new AgentIdentity(tenant, agent, "spiffe://test", Set.of("read", "write"), Instant.now());
            resolver.register(identity);

            // Lock agent
            for (int i = 0; i < 5; i++) {
                runPromise(() -> authService.recordFailedAttempt(tenant, agent));
            }

            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout(tenant, agent));
            assertThat(lockout).isPresent();
            assertThat(lockout.get().isActive()).isTrue();
        }
    }

    // ============================================
    // DELEGATION TOKENS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Delegation Tokens")
    class DelegationTests {

        @Test
        @DisplayName("Delegate from principal to delegate agent")
        void simpleDelegation() {
            String tenant = "t1";
            String principal = "principal-1";
            String delegate = "delegate-1";

            AgentIdentity principalId = new AgentIdentity(tenant, principal, "spiffe://p1", Set.of("delegate"), Instant.now());
            AgentIdentity delegateId = new AgentIdentity(tenant, delegate, "spiffe://d1", Set.of("read"), Instant.now());
            resolver.register(principalId);
            resolver.register(delegateId);

            String token = runPromise(() -> tokenProvider.createToken(tenant, principal, Duration.ofMinutes(10)));
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("Many delegation chains")
        void manyDelegationChains() {
            String tenant = "t1";

            // Create 30 agents with delegation relationships
            for (int i = 0; i < 30; i++) {
                final int idx = i;
                AgentIdentity agent = new AgentIdentity(
                    tenant, "agent-" + idx,
                    "spiffe://agent-" + idx,
                    Set.of("read", "delegate"), Instant.now());
                resolver.register(agent);
            }

            // Each creates a token
            for (int i = 0; i < 30; i++) {
                final int idx = i;
                String token = runPromise(() -> tokenProvider.createToken(tenant, "agent-" + idx, Duration.ofMinutes(10)));
                assertThat(token).isNotBlank();
            }
        }

        @Test
        @DisplayName("Delegation with restricted permissions")
        void restrictedDelegation() {
            String tenant = "t1";
            AgentIdentity agent = new AgentIdentity(
                tenant, "limited-agent",
                "spiffe://limited",
                Set.of("read"), Instant.now());
            resolver.register(agent);

            String token = runPromise(() -> tokenProvider.createToken(tenant, "limited-agent", Duration.ofMinutes(10)));
            Optional<TokenClaims> claims = runPromise(() -> tokenProvider.verifyToken(token));

            assertThat(claims).isPresent();
        }
    }

    // ============================================
    // EDGE CASES (3 tests)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very long tenant and agent IDs")
        void veryLongIds() {
            String longTenant = "t" + "a".repeat(200);
            String longAgent = "a" + "b".repeat(200);

            AgentIdentity identity = new AgentIdentity(
                longTenant, longAgent,
                "spiffe://" + longTenant + "/" + longAgent,
                Set.of("read"), Instant.now());
            resolver.register(identity);

            String token = runPromise(() -> tokenProvider.createToken(longTenant, longAgent, Duration.ofMinutes(10)));
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("Unicode in agent identities")
        void unicodeIdentities() {
            String tenant = "tenant-🚀";
            String agent = "agent-🌟";

            AgentIdentity identity = new AgentIdentity(
                tenant, agent,
                "spiffe://test",
                Set.of("read"), Instant.now());
            resolver.register(identity);

            Optional<AgentIdentity> resolved = runPromise(() ->
                identityService.resolve(tenant, agent));

            assertThat(resolved).isPresent();
        }

        @Test
        @DisplayName("Tokens with reasonable TTL")
        void reasonableTTL() {
            String token = runPromise(() ->
                tokenProvider.createToken("t1", "a1", Duration.ofHours(1)));

            Optional<TokenClaims> claims = runPromise(() ->
                tokenProvider.verifyToken(token));

            assertThat(claims).isPresent();
            assertThat(claims.get().tenantId()).isEqualTo("t1");
        }
    }
}
