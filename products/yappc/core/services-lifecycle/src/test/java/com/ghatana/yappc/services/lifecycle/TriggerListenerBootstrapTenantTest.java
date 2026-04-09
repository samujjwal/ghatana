package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.orchestrator.subsys.TriggerListener;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tenant-isolation regression tests for {@link TriggerListenerBootstrap}.
 *
 * <p>Verifies that the bootstrap fails fast when no tenant IDs are configured,
 * preventing global (unscoped) event subscriptions.
 *
 * @doc.type class
 * @doc.purpose Tenant isolation regression tests
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("TriggerListenerBootstrap — Tenant Isolation Regression")
class TriggerListenerBootstrapTenantTest extends EventloopTestBase {

    private TriggerListener triggerListener;
    private EventCloud eventCloud;
    private YappcAepPipelineBootstrapper pipelineBootstrapper;
    private DlqPublisher dlqPublisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        triggerListener = mock(TriggerListener.class);
        eventCloud = mock(EventCloud.class);
        pipelineBootstrapper = mock(YappcAepPipelineBootstrapper.class);
        dlqPublisher = mock(DlqPublisher.class);
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Tenant ID Validation")
    class TenantIdValidation {

        @Test
        @DisplayName("should fail with IllegalStateException when no tenant IDs are configured")
        void shouldFailWithEmptyTenantIds() {
            // GIVEN — bootstrap with empty tenant list (default constructor)
            TriggerListenerBootstrap bootstrap = new TriggerListenerBootstrap(
                    triggerListener, eventCloud, pipelineBootstrapper, dlqPublisher, objectMapper);

            // WHEN/THEN — start() should fail
            assertThatThrownBy(() -> runPromise(() -> bootstrap.start()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("requires at least one tenant ID");
            clearFatalError();
        }

        @Test
        @DisplayName("should fail with IllegalStateException when explicit empty list is provided")
        void shouldFailWithExplicitEmptyList() {
            // GIVEN — bootstrap with explicit empty list
            TriggerListenerBootstrap bootstrap = new TriggerListenerBootstrap(
                    triggerListener, eventCloud, pipelineBootstrapper,
                    dlqPublisher, objectMapper, List.of());

            // WHEN/THEN
            assertThatThrownBy(() -> runPromise(() -> bootstrap.start()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("YAPPC_LIFECYCLE_TENANT_IDS");
            clearFatalError();
        }

        @Test
        @DisplayName("should subscribe per-tenant when tenant IDs are configured")
        void shouldSubscribePerTenant() {
            // GIVEN — bootstrap with two tenants
            EventCloud.Subscription mockSub = mock(EventCloud.Subscription.class);
            when(eventCloud.subscribe(anyString(), anyString(), any())).thenReturn(mockSub);

            TriggerListenerBootstrap bootstrap = new TriggerListenerBootstrap(
                    triggerListener, eventCloud, pipelineBootstrapper,
                    dlqPublisher, objectMapper, List.of("tenant-a", "tenant-b"));

            // WHEN
            runPromise(() -> bootstrap.start());

            // THEN — one subscription per tenant
            verify(eventCloud).subscribe(eq("tenant-a"), eq("phase.transition.requested"), any());
            verify(eventCloud).subscribe(eq("tenant-b"), eq("phase.transition.requested"), any());
            verify(eventCloud, times(2)).subscribe(anyString(), anyString(), any());
        }
    }
}
