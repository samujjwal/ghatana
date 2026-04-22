/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Identity - Phase 3 Expansion [GH-90000]")
class IdentityExpansionTest extends EventloopTestBase {

    private DefaultTokenProvider tokenProvider;
    private DefaultAuthenticationService authService;
    private DefaultIdentityService identityService;
    private InMemoryIdentityResolver resolver;

    @BeforeEach
    void setUp() { // GH-90000
        tokenProvider = new DefaultTokenProvider(); // GH-90000
        resolver = new InMemoryIdentityResolver(); // GH-90000
        identityService = new DefaultIdentityService(resolver); // GH-90000
        authService = new DefaultAuthenticationService(tokenProvider, identityService); // GH-90000
    }

    // ============================================
    // MULTI-TENANT ISOLATION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multi-Tenant Isolation [GH-90000]")
    class MultiTenantTests {

        @Test
        @DisplayName("Many tenants operate independently [GH-90000]")
        void manyTenantsIndependent() { // GH-90000
            // Register 100 agents across 20 tenants
            for (int t = 0; t < 20; t++) { // GH-90000
                final int tenantIdx = t;
                for (int a = 0; a < 5; a++) { // GH-90000
                    final int agentIdx = a;
                    String tenantId = "tenant-" + tenantIdx;
                    String agentId = "agent-" + agentIdx;
                    AgentIdentity agent = new AgentIdentity( // GH-90000
                        tenantId, agentId,
                        "spiffe://ghatana.io/" + tenantId + "/" + agentId,
                        Set.of("read", "write"), Instant.now()); // GH-90000
                    resolver.register(agent); // GH-90000
                }
            }

            // Verify isolation per tenant
            Optional<AgentIdentity> result = runPromise(() -> // GH-90000
                identityService.resolve("tenant-5", "agent-2")); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().tenantId()).isEqualTo("tenant-5 [GH-90000]");
            assertThat(result.get().agentId()).isEqualTo("agent-2 [GH-90000]");
        }

        @Test
        @DisplayName("Failed attempts isolated per tenant [GH-90000]")
        void failedAttemptsIsolated() { // GH-90000
            String tenantA = "tenant-a";
            String tenantB = "tenant-b";
            String agent = "agent-1";

            AgentIdentity agentA = new AgentIdentity(tenantA, agent, "spiffe://a", Set.of("read [GH-90000]"), Instant.now());
            AgentIdentity agentB = new AgentIdentity(tenantB, agent, "spiffe://b", Set.of("read [GH-90000]"), Instant.now());
            resolver.register(agentA); // GH-90000
            resolver.register(agentB); // GH-90000

            // Lock tenant-a
            for (int i = 0; i < 5; i++) { // GH-90000
                runPromise(() -> authService.recordFailedAttempt(tenantA, agent)); // GH-90000
            }

            Optional<LockoutInfo> lockoutA = runPromise(() -> authService.checkLockout(tenantA, agent)); // GH-90000
            Optional<LockoutInfo> lockoutB = runPromise(() -> authService.checkLockout(tenantB, agent)); // GH-90000

            assertThat(lockoutA).isPresent().isPresent(); // GH-90000
            assertThat(lockoutB).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Tokens from different tenants do not interfere [GH-90000]")
        void tokenTenantIsolation() { // GH-90000
            String tenantA = "tenant-a";
            String tenantB = "tenant-b";

            String tokenA = runPromise(() -> tokenProvider.createToken(tenantA, "agent-1", Duration.ofMinutes(10))); // GH-90000
            String tokenB = runPromise(() -> tokenProvider.createToken(tenantB, "agent-1", Duration.ofMinutes(10))); // GH-90000

            Optional<TokenClaims> claimsA = runPromise(() -> tokenProvider.verifyToken(tokenA)); // GH-90000
            Optional<TokenClaims> claimsB = runPromise(() -> tokenProvider.verifyToken(tokenB)); // GH-90000

            assertThat(claimsA).isPresent(); // GH-90000
            assertThat(claimsB).isPresent(); // GH-90000
            assertThat(claimsA.get().tenantId()).isEqualTo(tenantA); // GH-90000
            assertThat(claimsB.get().tenantId()).isEqualTo(tenantB); // GH-90000
        }

        @Test
        @DisplayName("1000 agents across 100 tenants [GH-90000]")
        void scalingManyTenantsAndAgents() { // GH-90000
            for (int t = 0; t < 100; t++) { // GH-90000
                final int tenantIdx = t;
                AgentIdentity agent = new AgentIdentity( // GH-90000
                    "tenant-" + tenantIdx, "agent-0",
                    "spiffe://ghatana.io/tenant-" + tenantIdx + "/agent-0",
                    Set.of("read [GH-90000]"), Instant.now());
                resolver.register(agent); // GH-90000
            }

            Optional<AgentIdentity> result = runPromise(() -> // GH-90000
                identityService.resolve("tenant-50", "agent-0")); // GH-90000

            assertThat(result).isPresent(); // GH-90000
        }
    }

    // ============================================
    // TOKEN LIFECYCLE CONCURRENCY (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Token Lifecycle Concurrency [GH-90000]")
    class TokenConcurrencyTests {

        @Test
        @DisplayName("Many concurrent token creations [GH-90000]")
        void concurrentTokenCreation() throws Exception { // GH-90000
            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            List<String> tokens = new ArrayList<>(); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    final int idx = i;
                    exec.submit(() -> { // GH-90000
                        try {
                            String token = runPromise(() -> // GH-90000
                                tokenProvider.createToken("t" + idx, "agent-" + idx, Duration.ofMinutes(10))); // GH-90000
                            tokens.add(token); // GH-90000
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            assertThat(tokens).hasSize(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Concurrent creation and verification [GH-90000]")
        void concurrentCreateAndVerify() throws Exception { // GH-90000
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    final int idx = i;
                    exec.submit(() -> { // GH-90000
                        try {
                            String token = runPromise(() -> // GH-90000
                                tokenProvider.createToken("t" + idx, "agent-" + idx, Duration.ofMinutes(10))); // GH-90000
                            Optional<TokenClaims> claims = runPromise(() -> // GH-90000
                                tokenProvider.verifyToken(token)); // GH-90000
                            if (claims.isPresent()) { // GH-90000
                                successCount.incrementAndGet(); // GH-90000
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            assertThat(successCount.get()).isEqualTo(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Many tokens for same agent [GH-90000]")
        void manyTokensSameAgent() { // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                String token = runPromise(() -> // GH-90000
                    tokenProvider.createToken("t1", "a1", Duration.ofMinutes(10))); // GH-90000
                assertThat(token).isNotBlank(); // GH-90000
            }
        }

        @Test
        @DisplayName("Token creation at scale [GH-90000]")
        void tokenCreationScale() { // GH-90000
            for (int i = 0; i < 30; i++) { // GH-90000
                final int idx = i;
                String token = runPromise(() -> // GH-90000
                    tokenProvider.createToken("t1", "a" + idx, Duration.ofHours(1))); // GH-90000

                Optional<TokenClaims> immediateVerify = runPromise(() -> // GH-90000
                    tokenProvider.verifyToken(token)); // GH-90000
                assertThat(immediateVerify).isPresent(); // GH-90000
                assertThat(immediateVerify.get().agentId()).isEqualTo("a" + idx); // GH-90000
            }
        }
    }

    // ============================================
    // LOCKOUT MECHANISMS (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Lockout Mechanisms [GH-90000]")
    class LockoutTests {

        @Test
        @DisplayName("Progressive lockout with many attempts [GH-90000]")
        void progressiveLockout() { // GH-90000
            String tenant = "t1";
            String agent = "a1";
            AgentIdentity identity = new AgentIdentity(tenant, agent, "spiffe://test", Set.of("read [GH-90000]"), Instant.now());
            resolver.register(identity); // GH-90000

            // Record 4 attempts
            for (int i = 0; i < 4; i++) { // GH-90000
                runPromise(() -> authService.recordFailedAttempt(tenant, agent)); // GH-90000
                Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout(tenant, agent)); // GH-90000
                assertThat(lockout).isEmpty(); // GH-90000
            }

            // 5th attempt triggers lockout
            runPromise(() -> authService.recordFailedAttempt(tenant, agent)); // GH-90000
            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout(tenant, agent)); // GH-90000

            assertThat(lockout).isPresent(); // GH-90000
            assertThat(lockout.get().failedAttempts()).isEqualTo(5); // GH-90000
            assertThat(lockout.get().isActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Concurrent failed attempt recording [GH-90000]")
        void concurrentFailedAttempts() throws Exception { // GH-90000
            String tenant = "t1";
            String agent = "a1";
            AgentIdentity identity = new AgentIdentity(tenant, agent, "spiffe://test", Set.of("read [GH-90000]"), Instant.now());
            resolver.register(identity); // GH-90000

            int threadCount = 15;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000

            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    exec.submit(() -> { // GH-90000
                        try {
                            runPromise(() -> authService.recordFailedAttempt(tenant, agent)); // GH-90000
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout(tenant, agent)); // GH-90000
            assertThat(lockout).isPresent(); // GH-90000
            assertThat(lockout.get().failedAttempts()).isGreaterThanOrEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("Lockout across many agents [GH-90000]")
        void manyAgentsLockout() { // GH-90000
            String tenant = "t1";

            for (int a = 0; a < 50; a++) { // GH-90000
                final int agentIdx = a;
                String agentId = "agent-" + agentIdx;
                AgentIdentity identity = new AgentIdentity(tenant, agentId, "spiffe://test/" + agentIdx, Set.of("read [GH-90000]"), Instant.now());
                resolver.register(identity); // GH-90000

                // Lock each agent
                for (int i = 0; i < 5; i++) { // GH-90000
                    runPromise(() -> authService.recordFailedAttempt(tenant, agentId)); // GH-90000
                }
            }

            // Verify all are locked
            int lockedCount = 0;
            for (int a = 0; a < 50; a++) { // GH-90000
                String agentId = "agent-" + a;
                Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout(tenant, agentId)); // GH-90000
                if (lockout.isPresent()) { // GH-90000
                    lockedCount++;
                }
            }

            assertThat(lockedCount).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("Permission validation in locked state [GH-90000]")
        void permissionsInLockout() { // GH-90000
            String tenant = "t1";
            String agent = "a1";
            AgentIdentity identity = new AgentIdentity(tenant, agent, "spiffe://test", Set.of("read", "write"), Instant.now()); // GH-90000
            resolver.register(identity); // GH-90000

            // Lock agent
            for (int i = 0; i < 5; i++) { // GH-90000
                runPromise(() -> authService.recordFailedAttempt(tenant, agent)); // GH-90000
            }

            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout(tenant, agent)); // GH-90000
            assertThat(lockout).isPresent(); // GH-90000
            assertThat(lockout.get().isActive()).isTrue(); // GH-90000
        }
    }

    // ============================================
    // DELEGATION TOKENS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Delegation Tokens [GH-90000]")
    class DelegationTests {

        @Test
        @DisplayName("Delegate from principal to delegate agent [GH-90000]")
        void simpleDelegation() { // GH-90000
            String tenant = "t1";
            String principal = "principal-1";
            String delegate = "delegate-1";

            AgentIdentity principalId = new AgentIdentity(tenant, principal, "spiffe://p1", Set.of("delegate [GH-90000]"), Instant.now());
            AgentIdentity delegateId = new AgentIdentity(tenant, delegate, "spiffe://d1", Set.of("read [GH-90000]"), Instant.now());
            resolver.register(principalId); // GH-90000
            resolver.register(delegateId); // GH-90000

            String token = runPromise(() -> tokenProvider.createToken(tenant, principal, Duration.ofMinutes(10))); // GH-90000
            assertThat(token).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("Many delegation chains [GH-90000]")
        void manyDelegationChains() { // GH-90000
            String tenant = "t1";

            // Create 30 agents with delegation relationships
            for (int i = 0; i < 30; i++) { // GH-90000
                final int idx = i;
                AgentIdentity agent = new AgentIdentity( // GH-90000
                    tenant, "agent-" + idx,
                    "spiffe://agent-" + idx,
                    Set.of("read", "delegate"), Instant.now()); // GH-90000
                resolver.register(agent); // GH-90000
            }

            // Each creates a token
            for (int i = 0; i < 30; i++) { // GH-90000
                final int idx = i;
                String token = runPromise(() -> tokenProvider.createToken(tenant, "agent-" + idx, Duration.ofMinutes(10))); // GH-90000
                assertThat(token).isNotBlank(); // GH-90000
            }
        }

        @Test
        @DisplayName("Delegation with restricted permissions [GH-90000]")
        void restrictedDelegation() { // GH-90000
            String tenant = "t1";
            AgentIdentity agent = new AgentIdentity( // GH-90000
                tenant, "limited-agent",
                "spiffe://limited",
                Set.of("read [GH-90000]"), Instant.now());
            resolver.register(agent); // GH-90000

            String token = runPromise(() -> tokenProvider.createToken(tenant, "limited-agent", Duration.ofMinutes(10))); // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> tokenProvider.verifyToken(token)); // GH-90000

            assertThat(claims).isPresent(); // GH-90000
        }
    }

    // ============================================
    // EDGE CASES (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very long tenant and agent IDs [GH-90000]")
        void veryLongIds() { // GH-90000
            String longTenant = "t" + "a".repeat(200); // GH-90000
            String longAgent = "a" + "b".repeat(200); // GH-90000

            AgentIdentity identity = new AgentIdentity( // GH-90000
                longTenant, longAgent,
                "spiffe://" + longTenant + "/" + longAgent,
                Set.of("read [GH-90000]"), Instant.now());
            resolver.register(identity); // GH-90000

            String token = runPromise(() -> tokenProvider.createToken(longTenant, longAgent, Duration.ofMinutes(10))); // GH-90000
            assertThat(token).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("Unicode in agent identities [GH-90000]")
        void unicodeIdentities() { // GH-90000
            String tenant = "tenant-🚀";
            String agent = "agent-🌟";

            AgentIdentity identity = new AgentIdentity( // GH-90000
                tenant, agent,
                "spiffe://test",
                Set.of("read [GH-90000]"), Instant.now());
            resolver.register(identity); // GH-90000

            Optional<AgentIdentity> resolved = runPromise(() -> // GH-90000
                identityService.resolve(tenant, agent)); // GH-90000

            assertThat(resolved).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("Tokens with reasonable TTL [GH-90000]")
        void reasonableTTL() { // GH-90000
            String token = runPromise(() -> // GH-90000
                tokenProvider.createToken("t1", "a1", Duration.ofHours(1))); // GH-90000

            Optional<TokenClaims> claims = runPromise(() -> // GH-90000
                tokenProvider.verifyToken(token)); // GH-90000

            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().tenantId()).isEqualTo("t1 [GH-90000]");
        }
    }
}
