package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.*;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalStatus;
import com.ghatana.plugin.approval.HumanApprovalPlugin;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import com.ghatana.plugin.consent.ConsentPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DigitalMarketingKernelAdapterImpl}.
 *
 * <p>Verifies lifecycle guards, authorization delegation, consent verification,
 * approval request creation, and audit recording behavior.</p>
 */
@DisplayName("DigitalMarketingKernelAdapterImpl")
@ExtendWith(MockitoExtension.class)
class DigitalMarketingKernelAdapterImplTest extends EventloopTestBase {

    @Mock
    private BridgeAuthorizationService authService;

    @Mock
    private BridgeAuditEmitter auditEmitter;

    @Mock
    private BridgeHealthIndicator healthIndicator;

    @Mock
    private ConsentPlugin consentPlugin;

    @Mock
    private HumanApprovalPlugin approvalPlugin;

    @Mock
    private AuditTrailPlugin auditTrailPlugin;

    private DigitalMarketingKernelAdapterImpl adapter;

    private static final DmTenantId    TENANT    = DmTenantId.of("acme-corp");
    private static final DmWorkspaceId WORKSPACE = DmWorkspaceId.of("ws-001");
    private static final ActorRef      ACTOR     = ActorRef.user("user-42");
    private static final DmCorrelationId CORR     = DmCorrelationId.of("corr-abc");

    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        adapter = new DigitalMarketingKernelAdapterImpl(
            authService, auditEmitter, healthIndicator, consentPlugin, approvalPlugin, auditTrailPlugin
        );
        ctx = DmOperationContext.builder()
            .tenantId(TENANT)
            .workspaceId(WORKSPACE)
            .actor(ACTOR)
            .correlationId(CORR)
            .build();

        // Lenient stubs used only in specific tests
        lenient().when(authService.isAuthorized(any(), anyString(), anyString()))
            .thenReturn(Promise.of(true));
        lenient().when(consentPlugin.verifyConsent(anyString(), anyString()))
            .thenReturn(Promise.of(true));
        lenient().doNothing().when(auditEmitter).emit(any());
        lenient().doNothing().when(healthIndicator).reportHealthy(anyString());
        lenient().doNothing().when(healthIndicator).reportDegraded(anyString(), anyString());
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("started() returns false before start()")
    void shouldNotBeStartedInitially() {
        assertThat(adapter.started()).isFalse();
    }

    @Test
    @DisplayName("started() returns true after start()")
    void shouldBeStartedAfterStart() {
        adapter.start();
        assertThat(adapter.started()).isTrue();
    }

    @Test
    @DisplayName("started() returns false after stop()")
    void shouldNotBeStartedAfterStop() {
        adapter.start();
        adapter.stop();
        assertThat(adapter.started()).isFalse();
    }

    @Test
    @DisplayName("isAuthorized() throws IllegalStateException when adapter is not started")
    void shouldThrowWhenNotStartedOnAuthorize() {
        assertThatIllegalStateException()
            .isThrownBy(() -> adapter.isAuthorized(ctx, "campaigns/c-1", "launch"))
            .withMessageContaining("not started");
    }

    @Test
    @DisplayName("verifyConsent() throws IllegalStateException when adapter is not started")
    void shouldThrowWhenNotStartedOnConsent() {
        assertThatIllegalStateException()
            .isThrownBy(() -> adapter.verifyConsent(ctx, "contact-1", "marketing-email"))
            .withMessageContaining("not started");
    }

    @Test
    @DisplayName("requestApproval() throws IllegalStateException when adapter is not started")
    void shouldThrowWhenNotStartedOnApproval() {
        assertThatIllegalStateException()
            .isThrownBy(() -> adapter.requestApproval(ctx, "LaunchCampaign", "campaign-1", "Launch campaign"))
            .withMessageContaining("not started");
    }

    @Test
    @DisplayName("recordAudit() throws IllegalStateException when adapter is not started")
    void shouldThrowWhenNotStartedOnAudit() {
        assertThatIllegalStateException()
            .isThrownBy(() -> adapter.recordAudit(ctx, "campaign-1", "launch", Map.of()))
            .withMessageContaining("not started");
    }

    // -----------------------------------------------------------------------
    // Authorization
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isAuthorized() returns true when auth service grants access")
    void shouldReturnTrueWhenAuthorized() {
        adapter.start();
        when(authService.isAuthorized(any(BridgeContext.class), eq("campaigns/c-1"), eq("launch")))
            .thenReturn(Promise.of(true));

        boolean result = runPromise(() -> adapter.isAuthorized(ctx, "campaigns/c-1", "launch"));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isAuthorized() returns false when auth service denies access")
    void shouldReturnFalseWhenNotAuthorized() {
        adapter.start();
        when(authService.isAuthorized(any(BridgeContext.class), eq("contacts/c-1"), eq("delete")))
            .thenReturn(Promise.of(false));

        boolean result = runPromise(() -> adapter.isAuthorized(ctx, "contacts/c-1", "delete"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isAuthorized() propagates correct tenantId to auth service")
    void shouldPropagateTenantIdToAuthService() {
        adapter.start();
        ArgumentCaptor<BridgeContext> ctxCaptor = ArgumentCaptor.forClass(BridgeContext.class);
        when(authService.isAuthorized(ctxCaptor.capture(), anyString(), anyString()))
            .thenReturn(Promise.of(true));

        runPromise(() -> adapter.isAuthorized(ctx, "campaigns/c-1", "read"));

        assertThat(ctxCaptor.getValue().getTenantId()).isEqualTo("acme-corp");
        assertThat(ctxCaptor.getValue().getPrincipalId()).isEqualTo("user-42");
        assertThat(ctxCaptor.getValue().getCorrelationId()).isEqualTo("corr-abc");
    }

    @Test
    @DisplayName("isAuthorized() rejects null context")
    void shouldRejectNullContextOnAuthorize() {
        adapter.start();
        assertThatNullPointerException()
            .isThrownBy(() -> adapter.isAuthorized(null, "campaigns/c-1", "launch"))
            .withMessageContaining("context");
    }

    // -----------------------------------------------------------------------
    // Consent
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("verifyConsent() returns true when consent plugin confirms consent")
    void shouldReturnTrueWhenConsentGranted() {
        adapter.start();
        when(consentPlugin.verifyConsent("contact-1", "marketing-email"))
            .thenReturn(Promise.of(true));

        boolean result = runPromise(() -> adapter.verifyConsent(ctx, "contact-1", "marketing-email"));

        assertThat(result).isTrue();
        verify(consentPlugin).verifyConsent("contact-1", "marketing-email");
    }

    @Test
    @DisplayName("verifyConsent() returns false when consent plugin denies consent")
    void shouldReturnFalseWhenConsentDenied() {
        adapter.start();
        when(consentPlugin.verifyConsent("contact-2", "marketing-email"))
            .thenReturn(Promise.of(false));

        boolean result = runPromise(() -> adapter.verifyConsent(ctx, "contact-2", "marketing-email"));

        assertThat(result).isFalse();
    }

    // -----------------------------------------------------------------------
    // Approval
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("requestApproval() returns the approval request ID from the plugin")
    void shouldReturnApprovalRequestId() {
        adapter.start();
        ApprovalRecord mockRecord = new ApprovalRecord(
            "req-123", "campaign-1", "user-42", "LaunchCampaign",
            ApprovalStatus.PENDING, Instant.now(), null, null, null, null
        );
        when(approvalPlugin.requestApproval(any(ApprovalRequest.class)))
            .thenReturn(Promise.of(mockRecord));

        String requestId = runPromise(
            () -> adapter.requestApproval(ctx, "LaunchCampaign", "campaign-1", "Launch Q4 campaign")
        );

        assertThat(requestId).isEqualTo("req-123");
    }

    @Test
    @DisplayName("requestApproval() sends request with correct subjectId and actor")
    void shouldSendCorrectApprovalRequest() {
        adapter.start();
        ArgumentCaptor<ApprovalRequest> reqCaptor = ArgumentCaptor.forClass(ApprovalRequest.class);
        ApprovalRecord mockRecord = new ApprovalRecord(
            "req-456", "campaign-1", "user-42", "LaunchCampaign",
            ApprovalStatus.PENDING, Instant.now(), null, null, null, null
        );
        when(approvalPlugin.requestApproval(reqCaptor.capture()))
            .thenReturn(Promise.of(mockRecord));

        runPromise(() -> adapter.requestApproval(ctx, "LaunchCampaign", "campaign-1", "Launch"));

        ApprovalRequest captured = reqCaptor.getValue();
        assertThat(captured.subjectId()).isEqualTo("campaign-1");
        assertThat(captured.requestedBy()).isEqualTo("user-42");
        assertThat(captured.action()).isEqualTo("LaunchCampaign");
        assertThat(captured.context()).containsEntry("tenantId", "acme-corp");
    }

    // -----------------------------------------------------------------------
    // Audit
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("recordAudit() returns the audit entry ID from the plugin")
    void shouldReturnAuditEntryId() {
        adapter.start();
        AuditTrailPlugin.AuditEntry mockEntry = new AuditTrailPlugin.AuditEntry(
            "entry-789", "campaign-1", "launch", Map.of(), "user-42", "hash1", null, Instant.now()
        );
        when(auditTrailPlugin.logEvent(eq("campaign-1"), eq("launch"), any()))
            .thenReturn(Promise.of(mockEntry));

        String entryId = runPromise(
            () -> adapter.recordAudit(ctx, "campaign-1", "launch", Map.of("channel", "email"))
        );

        assertThat(entryId).isEqualTo("entry-789");
    }

    @Test
    @DisplayName("recordAudit() enriches attributes with tenant and correlation context")
    void shouldEnrichAuditAttributesWithContext() {
        adapter.start();
        ArgumentCaptor<Map<String, Object>> attrsCaptor = ArgumentCaptor.forClass(Map.class);
        AuditTrailPlugin.AuditEntry mockEntry = new AuditTrailPlugin.AuditEntry(
            "entry-999", "campaign-1", "launch", Map.of(), "user-42", "hash1", null, Instant.now()
        );
        when(auditTrailPlugin.logEvent(eq("campaign-1"), eq("launch"), attrsCaptor.capture()))
            .thenReturn(Promise.of(mockEntry));

        runPromise(() -> adapter.recordAudit(ctx, "campaign-1", "launch", Map.of("channel", "email")));

        Map<String, Object> captured = attrsCaptor.getValue();
        assertThat(captured).containsEntry("tenantId", "acme-corp")
            .containsEntry("workspaceId", "ws-001")
            .containsEntry("correlationId", "corr-abc")
            .containsEntry("actor", "user-42")
            .containsEntry("channel", "email");  // original attributes preserved
    }

    // -----------------------------------------------------------------------
    // Constructor guards
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("constructor rejects null authService")
    void shouldRejectNullAuthService() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingKernelAdapterImpl(
                null, auditEmitter, healthIndicator, consentPlugin, approvalPlugin, auditTrailPlugin));
    }

    @Test
    @DisplayName("constructor rejects null consentPlugin")
    void shouldRejectNullConsentPlugin() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingKernelAdapterImpl(
                authService, auditEmitter, healthIndicator, null, approvalPlugin, auditTrailPlugin))
            .withMessageContaining("consentPlugin");
    }

    @Test
    @DisplayName("constructor rejects null approvalPlugin")
    void shouldRejectNullApprovalPlugin() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingKernelAdapterImpl(
                authService, auditEmitter, healthIndicator, consentPlugin, null, auditTrailPlugin))
            .withMessageContaining("approvalPlugin");
    }

    @Test
    @DisplayName("constructor rejects null auditTrailPlugin")
    void shouldRejectNullAuditTrailPlugin() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingKernelAdapterImpl(
                authService, auditEmitter, healthIndicator, consentPlugin, approvalPlugin, null))
            .withMessageContaining("auditTrailPlugin");
    }
}
