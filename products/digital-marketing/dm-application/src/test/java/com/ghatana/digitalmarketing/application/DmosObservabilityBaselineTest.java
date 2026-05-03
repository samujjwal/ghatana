package com.ghatana.digitalmarketing.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmosObservabilityBaseline")
class DmosObservabilityBaselineTest {

    @AfterEach
    void cleanMdc() {
        DmosObservabilityBaseline.clearMdcContext();
    }

    @Test
    @DisplayName("setMdcContext populates all MDC keys")
    void shouldSetAllMdcKeys() {
        DmosObservabilityBaseline.setMdcContext("tenant-1", "ws-1", "corr-1");

        assertThat(MDC.get(DmosObservabilityBaseline.MDC_TENANT_ID)).isEqualTo("tenant-1");
        assertThat(MDC.get(DmosObservabilityBaseline.MDC_WORKSPACE_ID)).isEqualTo("ws-1");
        assertThat(MDC.get(DmosObservabilityBaseline.MDC_CORRELATION_ID)).isEqualTo("corr-1");
    }

    @Test
    @DisplayName("clearMdcContext removes DMOS MDC keys")
    void shouldClearMdcKeys() {
        DmosObservabilityBaseline.setMdcContext("tenant-1", "ws-1", "corr-1");
        DmosObservabilityBaseline.clearMdcContext();

        assertThat(MDC.get(DmosObservabilityBaseline.MDC_TENANT_ID)).isNull();
        assertThat(MDC.get(DmosObservabilityBaseline.MDC_WORKSPACE_ID)).isNull();
        assertThat(MDC.get(DmosObservabilityBaseline.MDC_CORRELATION_ID)).isNull();
        assertThat(MDC.get(DmosObservabilityBaseline.MDC_OPERATION)).isNull();
    }

    @Test
    @DisplayName("setMdcContext ignores null tenantId")
    void shouldIgnoreNullTenantId() {
        DmosObservabilityBaseline.setMdcContext(null, "ws-1", "corr-1");

        assertThat(MDC.get(DmosObservabilityBaseline.MDC_TENANT_ID)).isNull();
        assertThat(MDC.get(DmosObservabilityBaseline.MDC_WORKSPACE_ID)).isEqualTo("ws-1");
    }

    @Test
    @DisplayName("setMdcContext ignores null workspaceId")
    void shouldIgnoreNullWorkspaceId() {
        DmosObservabilityBaseline.setMdcContext("tenant-1", null, "corr-1");

        assertThat(MDC.get(DmosObservabilityBaseline.MDC_WORKSPACE_ID)).isNull();
        assertThat(MDC.get(DmosObservabilityBaseline.MDC_TENANT_ID)).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("setMdcContext ignores null correlationId")
    void shouldIgnoreNullCorrelationId() {
        DmosObservabilityBaseline.setMdcContext("tenant-1", "ws-1", null);

        assertThat(MDC.get(DmosObservabilityBaseline.MDC_CORRELATION_ID)).isNull();
        assertThat(MDC.get(DmosObservabilityBaseline.MDC_TENANT_ID)).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("logServiceCall and logServiceSuccess do not throw")
    void logServiceCallAndSuccessDoNotThrow() {
        DmosObservabilityBaseline.logServiceCall("runAudit", "tenant-1", "ws-1");
        DmosObservabilityBaseline.logServiceSuccess("runAudit", "tenant-1");
        // if no exception, log emission works
    }

    @Test
    @DisplayName("logServiceWarning does not throw")
    void logServiceWarningDoesNotThrow() {
        DmosObservabilityBaseline.logServiceWarning("runAudit", "tenant-1", "resource not found");
    }

    @Test
    @DisplayName("logServiceError does not throw")
    void logServiceErrorDoesNotThrow() {
        DmosObservabilityBaseline.logServiceError("runAudit", "tenant-1", new RuntimeException("test"));
    }

    @Test
    @DisplayName("logSecurityEvent does not throw")
    void logSecurityEventDoesNotThrow() {
        DmosObservabilityBaseline.logSecurityEvent("unauthorized_access", "tenant-1", "user-1", "/v1/strategy");
    }

    @Test
    @DisplayName("logWorkflowTransition does not throw")
    void logWorkflowTransitionDoesNotThrow() {
        DmosObservabilityBaseline.logWorkflowTransition("CampaignLaunch", "wf-1", "PENDING", "EXECUTING");
    }
}
