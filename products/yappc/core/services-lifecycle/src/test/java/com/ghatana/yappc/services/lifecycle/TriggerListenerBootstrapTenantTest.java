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
 * preventing global (unscoped) event subscriptions. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Tenant isolation regression tests
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("TriggerListenerBootstrap — Tenant Isolation Regression [GH-90000]")
class TriggerListenerBootstrapTenantTest extends EventloopTestBase {

    private TriggerListener triggerListener;
    private EventCloud eventCloud;
    private YappcAepPipelineBootstrapper pipelineBootstrapper;
    private DlqPublisher dlqPublisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() { // GH-90000
        triggerListener = mock(TriggerListener.class); // GH-90000
        eventCloud = mock(EventCloud.class); // GH-90000
        pipelineBootstrapper = mock(YappcAepPipelineBootstrapper.class); // GH-90000
        dlqPublisher = mock(DlqPublisher.class); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
    }

    @Nested
    @DisplayName("Tenant ID Validation [GH-90000]")
    class TenantIdValidation {

        @Test
        @DisplayName("should fail with IllegalStateException when no tenant IDs are configured [GH-90000]")
        void shouldFailWithEmptyTenantIds() { // GH-90000
            // GIVEN — bootstrap with empty tenant list (default constructor) // GH-90000
            TriggerListenerBootstrap bootstrap = new TriggerListenerBootstrap( // GH-90000
                    triggerListener, eventCloud, pipelineBootstrapper, dlqPublisher, objectMapper);

            // WHEN/THEN — start() should fail // GH-90000
            assertThatThrownBy(() -> runPromise(() -> bootstrap.start())) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("requires at least one tenant ID [GH-90000]");
            clearFatalError(); // GH-90000
        }

        @Test
        @DisplayName("should fail with IllegalStateException when explicit empty list is provided [GH-90000]")
        void shouldFailWithExplicitEmptyList() { // GH-90000
            // GIVEN — bootstrap with explicit empty list
            TriggerListenerBootstrap bootstrap = new TriggerListenerBootstrap( // GH-90000
                    triggerListener, eventCloud, pipelineBootstrapper,
                    dlqPublisher, objectMapper, List.of()); // GH-90000

            // WHEN/THEN
            assertThatThrownBy(() -> runPromise(() -> bootstrap.start())) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("YAPPC_LIFECYCLE_TENANT_IDS [GH-90000]");
            clearFatalError(); // GH-90000
        }

        @Test
        @DisplayName("should subscribe per-tenant when tenant IDs are configured [GH-90000]")
        void shouldSubscribePerTenant() { // GH-90000
            // GIVEN — bootstrap with two tenants
            EventCloud.Subscription mockSub = mock(EventCloud.Subscription.class); // GH-90000
            when(eventCloud.subscribe(anyString(), anyString(), any())).thenReturn(mockSub); // GH-90000

            TriggerListenerBootstrap bootstrap = new TriggerListenerBootstrap( // GH-90000
                    triggerListener, eventCloud, pipelineBootstrapper,
                    dlqPublisher, objectMapper, List.of("tenant-a", "tenant-b")); // GH-90000

            // WHEN
            runPromise(() -> bootstrap.start()); // GH-90000

            // THEN — one subscription per tenant
            verify(eventCloud).subscribe(eq("tenant-a [GH-90000]"), eq("phase.transition.requested [GH-90000]"), any());
            verify(eventCloud).subscribe(eq("tenant-b [GH-90000]"), eq("phase.transition.requested [GH-90000]"), any());
            verify(eventCloud, times(2)).subscribe(anyString(), anyString(), any()); // GH-90000
        }
    }
}
