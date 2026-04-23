/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.memory;

import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.security.MemoryRedactionFilter;
import com.ghatana.agent.memory.security.TenantIsolatingMemorySecurityManager;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioral tests for {@link GovernedMemoryPlane} — covers plan items 2.7.4 and 2.7.5.
 *
 * <p><b>2.7.4 — PII Redaction</b></p>
 * Episodes containing PII (emails, phone numbers, etc.) must have those fields // GH-90000
 * replaced with {@code [REDACTED]} before the payload reaches the delegate store.
 * The caller-visible return value should reflect the redacted content.
 *
 * <p><b>2.7.5 — Tenant Isolation</b></p>
 * Episodes owned by tenant-A must not be visible to tenant-B queries, even if the
 * underlying delegate store returns them. The {@link GovernedMemoryPlane} applies
 * post-read filtering via {@link TenantIsolatingMemorySecurityManager}.
 *
 * @doc.type class
 * @doc.purpose Tests for GovernedMemoryPlane PII redaction and tenant isolation (2.7.4, 2.7.5) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("GovernedMemoryPlane Tests (2.7.4 ‧ 2.7.5)")
class GovernedMemoryPlaneTest extends EventloopTestBase {

    private static final String OWN_TENANT = "tenant-alpha";
    private static final String OTHER_TENANT = "tenant-beta";

    @Mock
    private MemoryPlane delegate;

    private GovernedMemoryPlane plane;

    @BeforeEach
    void setUp() { // GH-90000
        MemoryRedactionFilter filter = new MemoryRedactionFilter(true, true); // GH-90000
        TenantIsolatingMemorySecurityManager security = new TenantIsolatingMemorySecurityManager(); // GH-90000
        plane = new GovernedMemoryPlane(delegate, filter, security); // GH-90000

        // Set the caller's tenant context on the eventloop thread (TenantContext is ThreadLocal) // GH-90000
        TenantContext.setCurrentTenantId(OWN_TENANT); // GH-90000
        runBlocking(() -> TenantContext.setCurrentTenantId(OWN_TENANT)); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        runBlocking(TenantContext::clear); // GH-90000
        TenantContext.clear(); // GH-90000
    }

    // =========================================================================
    // 2.7.4 — PII Redaction Tests
    // =========================================================================

    @Nested
    @DisplayName("2.7.4 — PII redaction before persistence")
    class PiiRedactionTests {

        @Test
        @DisplayName("email address in episode input is redacted before reaching delegate")
        void shouldRedactEmailInEpisodeInput() { // GH-90000
            // GIVEN: an episode containing an email address
            EnhancedEpisode episode = EnhancedEpisode.builder() // GH-90000
                    .id("ep-pii-1")
                    .agentId("intent-agent")
                    .turnId("turn-001")
                    .tenantId(OWN_TENANT) // GH-90000
                    .input("Please reach out to john.doe@example.com about the project")
                    .output("Intent captured successfully")
                    .build(); // GH-90000

            ArgumentCaptor<EnhancedEpisode> captor = ArgumentCaptor.forClass(EnhancedEpisode.class); // GH-90000
            when(delegate.storeEpisode(any())).thenReturn(Promise.of(episode)); // GH-90000

            // WHEN: stored through the governed plane
            runPromise(() -> plane.storeEpisode(episode)); // GH-90000

            // THEN: the delegate receives a version with the email redacted
            verify(delegate).storeEpisode(captor.capture()); // GH-90000
            EnhancedEpisode stored = captor.getValue(); // GH-90000

            assertThat(stored.getInput()) // GH-90000
                    .as("Email address must be redacted in stored episode input")
                    .doesNotContain("john.doe@example.com");
            assertThat(stored.getInput()) // GH-90000
                    .as("Redaction placeholder must be present")
                    .contains("[REDACTED]");
        }

        @Test
        @DisplayName("email address in episode output is redacted before reaching delegate")
        void shouldRedactEmailInEpisodeOutput() { // GH-90000
            EnhancedEpisode episode = EnhancedEpisode.builder() // GH-90000
                    .id("ep-pii-2")
                    .agentId("intent-agent")
                    .turnId("turn-002")
                    .tenantId(OWN_TENANT) // GH-90000
                    .input("Analyze this request")
                    .output("Contact customer at alice@company.org for follow-up")
                    .build(); // GH-90000

            ArgumentCaptor<EnhancedEpisode> captor = ArgumentCaptor.forClass(EnhancedEpisode.class); // GH-90000
            when(delegate.storeEpisode(any())).thenReturn(Promise.of(episode)); // GH-90000

            runPromise(() -> plane.storeEpisode(episode)); // GH-90000

            verify(delegate).storeEpisode(captor.capture()); // GH-90000
            EnhancedEpisode stored = captor.getValue(); // GH-90000

            assertThat(stored.getOutput()).doesNotContain("alice@company.org");
            assertThat(stored.getOutput()).contains("[REDACTED]");
        }

        @Test
        @DisplayName("episode with no PII passes through without modification markers")
        void shouldPassEpisodeWithoutPiiUnchanged() { // GH-90000
            EnhancedEpisode episode = EnhancedEpisode.builder() // GH-90000
                    .id("ep-clean")
                    .agentId("intent-agent")
                    .turnId("turn-003")
                    .tenantId(OWN_TENANT) // GH-90000
                    .input("Describe the system architecture using hexagonal design")
                    .output("The system follows ports-and-adapters architecture")
                    .build(); // GH-90000

            ArgumentCaptor<EnhancedEpisode> captor = ArgumentCaptor.forClass(EnhancedEpisode.class); // GH-90000
            when(delegate.storeEpisode(any())).thenReturn(Promise.of(episode)); // GH-90000

            runPromise(() -> plane.storeEpisode(episode)); // GH-90000

            verify(delegate).storeEpisode(captor.capture()); // GH-90000
            EnhancedEpisode stored = captor.getValue(); // GH-90000

            assertThat(stored.getInput()).isEqualTo(episode.getInput()); // GH-90000
            assertThat(stored.getOutput()).isEqualTo(episode.getOutput()); // GH-90000
        }

        @Test
        @DisplayName("redaction level is set to APPLIED on stored episode")
        void shouldMarkRedactionLevelAsApplied() { // GH-90000
            EnhancedEpisode episode = EnhancedEpisode.builder() // GH-90000
                    .id("ep-mark")
                    .agentId("intent-agent")
                    .turnId("turn-004")
                    .tenantId(OWN_TENANT) // GH-90000
                    .input("User: user@test.com requested access")
                    .output("Access reviewed")
                    .build(); // GH-90000

            ArgumentCaptor<EnhancedEpisode> captor = ArgumentCaptor.forClass(EnhancedEpisode.class); // GH-90000
            when(delegate.storeEpisode(any())).thenReturn(Promise.of(episode)); // GH-90000

            runPromise(() -> plane.storeEpisode(episode)); // GH-90000

            verify(delegate).storeEpisode(captor.capture()); // GH-90000
            assertThat(captor.getValue().getRedactionLevel()).isEqualTo("APPLIED");
        }
    }

    // =========================================================================
    // 2.7.5 — Tenant Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("2.7.5 — Tenant isolation on read operations")
    class TenantIsolationTests {

        @Test
        @DisplayName("episodes from a different tenant are filtered out of query results")
        void shouldFilterEpisodesFromOtherTenant() { // GH-90000
            // GIVEN: delegate returns an episode owned by OTHER_TENANT
            EnhancedEpisode betaEpisode = EnhancedEpisode.builder() // GH-90000
                    .id("ep-beta")
                    .agentId("shared-agent")
                    .turnId("turn-beta")
                    .tenantId(OTHER_TENANT)   // does NOT match OWN_TENANT in TenantContext // GH-90000
                    .input("Beta tenant data")
                    .output("Beta result")
                    .build(); // GH-90000

            MemoryQuery query = MemoryQuery.builder().agentId("shared-agent").build();
            when(delegate.queryEpisodes(any())).thenReturn(Promise.of(List.of(betaEpisode))); // GH-90000

            // WHEN: caller is OWN_TENANT (set in @BeforeEach) // GH-90000
            List<EnhancedEpisode> result = runPromise(() -> plane.queryEpisodes(query)); // GH-90000

            // THEN: cross-tenant episode is filtered out
            assertThat(result) // GH-90000
                    .as("Episodes from '%s' must not be visible to '%s'", OTHER_TENANT, OWN_TENANT) // GH-90000
                    .isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("own tenant episodes are returned from query results")
        void shouldAllowEpisodesFromOwnTenant() { // GH-90000
            // GIVEN: delegate returns an episode owned by OWN_TENANT
            EnhancedEpisode ownEpisode = EnhancedEpisode.builder() // GH-90000
                    .id("ep-own")
                    .agentId("shared-agent")
                    .turnId("turn-own")
                    .tenantId(OWN_TENANT) // GH-90000
                    .input("Alpha tenant data")
                    .output("Alpha result")
                    .build(); // GH-90000

            MemoryQuery query = MemoryQuery.builder().agentId("shared-agent").build();
            when(delegate.queryEpisodes(any())).thenReturn(Promise.of(List.of(ownEpisode))); // GH-90000

            // WHEN
            List<EnhancedEpisode> result = runPromise(() -> plane.queryEpisodes(query)); // GH-90000

            // THEN
            assertThat(result).hasSize(1); // GH-90000
            assertThat(result.get(0).getId()).isEqualTo("ep-own");
        }

        @Test
        @DisplayName("mixed tenant results: only own tenant episodes pass through")
        void shouldReturnOnlyOwnTenantEpisodesFromMixedResults() { // GH-90000
            EnhancedEpisode own = EnhancedEpisode.builder() // GH-90000
                    .id("ep-mix-own").agentId("agent").turnId("t1")
                    .tenantId(OWN_TENANT).input("own").output("own-out").build();

            EnhancedEpisode foreign = EnhancedEpisode.builder() // GH-90000
                    .id("ep-mix-foreign").agentId("agent").turnId("t2")
                    .tenantId(OTHER_TENANT).input("foreign").output("foreign-out").build();

            MemoryQuery query = MemoryQuery.builder().agentId("agent").build();
            when(delegate.queryEpisodes(any())).thenReturn(Promise.of(List.of(own, foreign))); // GH-90000

            List<EnhancedEpisode> result = runPromise(() -> plane.queryEpisodes(query)); // GH-90000

            assertThat(result).hasSize(1); // GH-90000
            assertThat(result.get(0).getId()).isEqualTo("ep-mix-own");
        }

        @Test
        @DisplayName("writing an episode owned by a different tenant throws SecurityException")
        void shouldDenyWriteWhenEpisodeTenantMismatch() { // GH-90000
            // Caller is OWN_TENANT but episode belongs to OTHER_TENANT
            EnhancedEpisode foreignEpisode = EnhancedEpisode.builder() // GH-90000
                    .id("ep-foreign-write")
                    .agentId("agent")
                    .turnId("t-fw")
                    .tenantId(OTHER_TENANT) // GH-90000
                    .input("foreign input")
                    .output("foreign output")
                    .build(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> plane.storeEpisode(foreignEpisode))) // GH-90000
                    .isInstanceOfAny(SecurityException.class, RuntimeException.class) // GH-90000
                    .satisfies(e -> { // GH-90000
                        Throwable root = e.getCause() != null ? e.getCause() : e; // GH-90000
                        assertThat(root).isInstanceOf(SecurityException.class); // GH-90000
                        assertThat(root.getMessage()).contains("tenant mismatch");
                    });
        }
    }
}
