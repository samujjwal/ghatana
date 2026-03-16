/*
 * Copyright (c) 2025 Ghatana Technologies
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
 * <h2>2.7.4 — PII Redaction</h2>
 * Episodes containing PII (emails, phone numbers, etc.) must have those fields
 * replaced with {@code [REDACTED]} before the payload reaches the delegate store.
 * The caller-visible return value should reflect the redacted content.
 *
 * <h2>2.7.5 — Tenant Isolation</h2>
 * Episodes owned by tenant-A must not be visible to tenant-B queries, even if the
 * underlying delegate store returns them. The {@link GovernedMemoryPlane} applies
 * post-read filtering via {@link TenantIsolatingMemorySecurityManager}.
 *
 * @doc.type class
 * @doc.purpose Tests for GovernedMemoryPlane PII redaction and tenant isolation (2.7.4, 2.7.5)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GovernedMemoryPlane Tests (2.7.4 ‧ 2.7.5)")
class GovernedMemoryPlaneTest extends EventloopTestBase {

    private static final String OWN_TENANT = "tenant-alpha";
    private static final String OTHER_TENANT = "tenant-beta";

    @Mock
    private MemoryPlane delegate;

    private GovernedMemoryPlane plane;

    @BeforeEach
    void setUp() {
        MemoryRedactionFilter filter = new MemoryRedactionFilter(true, true);
        TenantIsolatingMemorySecurityManager security = new TenantIsolatingMemorySecurityManager();
        plane = new GovernedMemoryPlane(delegate, filter, security);

        // Set the caller's tenant context for all tests
        TenantContext.setCurrentTenantId(OWN_TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // 2.7.4 — PII Redaction Tests
    // =========================================================================

    @Nested
    @DisplayName("2.7.4 — PII redaction before persistence")
    class PiiRedactionTests {

        @Test
        @DisplayName("email address in episode input is redacted before reaching delegate")
        void shouldRedactEmailInEpisodeInput() {
            // GIVEN: an episode containing an email address
            EnhancedEpisode episode = EnhancedEpisode.builder()
                    .id("ep-pii-1")
                    .agentId("intent-agent")
                    .turnId("turn-001")
                    .tenantId(OWN_TENANT)
                    .input("Please reach out to john.doe@example.com about the project")
                    .output("Intent captured successfully")
                    .build();

            ArgumentCaptor<EnhancedEpisode> captor = ArgumentCaptor.forClass(EnhancedEpisode.class);
            when(delegate.storeEpisode(any())).thenReturn(Promise.of(episode));

            // WHEN: stored through the governed plane
            runPromise(() -> plane.storeEpisode(episode));

            // THEN: the delegate receives a version with the email redacted
            verify(delegate).storeEpisode(captor.capture());
            EnhancedEpisode stored = captor.getValue();

            assertThat(stored.getInput())
                    .as("Email address must be redacted in stored episode input")
                    .doesNotContain("john.doe@example.com");
            assertThat(stored.getInput())
                    .as("Redaction placeholder must be present")
                    .contains("[REDACTED]");
        }

        @Test
        @DisplayName("email address in episode output is redacted before reaching delegate")
        void shouldRedactEmailInEpisodeOutput() {
            EnhancedEpisode episode = EnhancedEpisode.builder()
                    .id("ep-pii-2")
                    .agentId("intent-agent")
                    .turnId("turn-002")
                    .tenantId(OWN_TENANT)
                    .input("Analyze this request")
                    .output("Contact customer at alice@company.org for follow-up")
                    .build();

            ArgumentCaptor<EnhancedEpisode> captor = ArgumentCaptor.forClass(EnhancedEpisode.class);
            when(delegate.storeEpisode(any())).thenReturn(Promise.of(episode));

            runPromise(() -> plane.storeEpisode(episode));

            verify(delegate).storeEpisode(captor.capture());
            EnhancedEpisode stored = captor.getValue();

            assertThat(stored.getOutput()).doesNotContain("alice@company.org");
            assertThat(stored.getOutput()).contains("[REDACTED]");
        }

        @Test
        @DisplayName("episode with no PII passes through without modification markers")
        void shouldPassEpisodeWithoutPiiUnchanged() {
            EnhancedEpisode episode = EnhancedEpisode.builder()
                    .id("ep-clean")
                    .agentId("intent-agent")
                    .turnId("turn-003")
                    .tenantId(OWN_TENANT)
                    .input("Describe the system architecture using hexagonal design")
                    .output("The system follows ports-and-adapters architecture")
                    .build();

            ArgumentCaptor<EnhancedEpisode> captor = ArgumentCaptor.forClass(EnhancedEpisode.class);
            when(delegate.storeEpisode(any())).thenReturn(Promise.of(episode));

            runPromise(() -> plane.storeEpisode(episode));

            verify(delegate).storeEpisode(captor.capture());
            EnhancedEpisode stored = captor.getValue();

            assertThat(stored.getInput()).isEqualTo(episode.getInput());
            assertThat(stored.getOutput()).isEqualTo(episode.getOutput());
        }

        @Test
        @DisplayName("redaction level is set to APPLIED on stored episode")
        void shouldMarkRedactionLevelAsApplied() {
            EnhancedEpisode episode = EnhancedEpisode.builder()
                    .id("ep-mark")
                    .agentId("intent-agent")
                    .turnId("turn-004")
                    .tenantId(OWN_TENANT)
                    .input("User: user@test.com requested access")
                    .output("Access reviewed")
                    .build();

            ArgumentCaptor<EnhancedEpisode> captor = ArgumentCaptor.forClass(EnhancedEpisode.class);
            when(delegate.storeEpisode(any())).thenReturn(Promise.of(episode));

            runPromise(() -> plane.storeEpisode(episode));

            verify(delegate).storeEpisode(captor.capture());
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
        void shouldFilterEpisodesFromOtherTenant() {
            // GIVEN: delegate returns an episode owned by OTHER_TENANT
            EnhancedEpisode betaEpisode = EnhancedEpisode.builder()
                    .id("ep-beta")
                    .agentId("shared-agent")
                    .turnId("turn-beta")
                    .tenantId(OTHER_TENANT)   // does NOT match OWN_TENANT in TenantContext
                    .input("Beta tenant data")
                    .output("Beta result")
                    .build();

            MemoryQuery query = MemoryQuery.builder().agentId("shared-agent").build();
            when(delegate.queryEpisodes(any())).thenReturn(Promise.of(List.of(betaEpisode)));

            // WHEN: caller is OWN_TENANT (set in @BeforeEach)
            List<EnhancedEpisode> result = runPromise(() -> plane.queryEpisodes(query));

            // THEN: cross-tenant episode is filtered out
            assertThat(result)
                    .as("Episodes from '%s' must not be visible to '%s'", OTHER_TENANT, OWN_TENANT)
                    .isEmpty();
        }

        @Test
        @DisplayName("own tenant episodes are returned from query results")
        void shouldAllowEpisodesFromOwnTenant() {
            // GIVEN: delegate returns an episode owned by OWN_TENANT
            EnhancedEpisode ownEpisode = EnhancedEpisode.builder()
                    .id("ep-own")
                    .agentId("shared-agent")
                    .turnId("turn-own")
                    .tenantId(OWN_TENANT)
                    .input("Alpha tenant data")
                    .output("Alpha result")
                    .build();

            MemoryQuery query = MemoryQuery.builder().agentId("shared-agent").build();
            when(delegate.queryEpisodes(any())).thenReturn(Promise.of(List.of(ownEpisode)));

            // WHEN
            List<EnhancedEpisode> result = runPromise(() -> plane.queryEpisodes(query));

            // THEN
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("ep-own");
        }

        @Test
        @DisplayName("mixed tenant results: only own tenant episodes pass through")
        void shouldReturnOnlyOwnTenantEpisodesFromMixedResults() {
            EnhancedEpisode own = EnhancedEpisode.builder()
                    .id("ep-mix-own").agentId("agent").turnId("t1")
                    .tenantId(OWN_TENANT).input("own").output("own-out").build();

            EnhancedEpisode foreign = EnhancedEpisode.builder()
                    .id("ep-mix-foreign").agentId("agent").turnId("t2")
                    .tenantId(OTHER_TENANT).input("foreign").output("foreign-out").build();

            MemoryQuery query = MemoryQuery.builder().agentId("agent").build();
            when(delegate.queryEpisodes(any())).thenReturn(Promise.of(List.of(own, foreign)));

            List<EnhancedEpisode> result = runPromise(() -> plane.queryEpisodes(query));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("ep-mix-own");
        }

        @Test
        @DisplayName("writing an episode owned by a different tenant throws SecurityException")
        void shouldDenyWriteWhenEpisodeTenantMismatch() {
            // Caller is OWN_TENANT but episode belongs to OTHER_TENANT
            EnhancedEpisode foreignEpisode = EnhancedEpisode.builder()
                    .id("ep-foreign-write")
                    .agentId("agent")
                    .turnId("t-fw")
                    .tenantId(OTHER_TENANT)
                    .input("foreign input")
                    .output("foreign output")
                    .build();

            assertThatThrownBy(() -> runPromise(() -> plane.storeEpisode(foreignEpisode)))
                    .isInstanceOfAny(SecurityException.class, RuntimeException.class)
                    .satisfies(e -> {
                        Throwable root = e.getCause() != null ? e.getCause() : e;
                        assertThat(root).isInstanceOf(SecurityException.class);
                        assertThat(root.getMessage()).contains("tenant mismatch");
                    });
        }
    }
}
